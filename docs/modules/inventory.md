# 库存模块设计（阶段 4）

> 状态：已通过技术验收（2026-11-02）。
> 适用阶段：阶段 4。库存模块是订单与售后的基础设施，不包含订单状态机、支付、售后单据本身。
> 相关文档：[`../architecture.md`](../architecture.md)、[`../database.md`](../database.md)、[`../api-design.md`](../api-design.md)、[`../test-plan.md`](../test-plan.md)。

## 1. 职责与业务边界

库存模块负责 SKU 维度的可售库存、锁定库存和不可变库存流水，向订单、支付、售后模块提供进程内 API。

职责：

- 下单锁定：可售库存减少，锁定库存增加。
- 支付确认扣减：锁定库存减少，可售库存不变。
- 取消/超时释放：锁定库存减少，可售库存恢复。
- 售后退货回补：可售库存增加，锁定库存不变。
- 管理端库存查询、手工调整、库存流水查询。
- 批量库存快照查询，供其他模块读取。

边界：

- 不负责订单状态机、支付状态、售后单状态。
- 不校验 SKU 是否可购买、上下架、价格；这些属于商品模块，由调用方在事务内完成。
- 不创建库存记录以外的业务单据；回补必须基于已存在的订单扣减流水。
- 不引入 Redis/RabbitMQ 库存逻辑；MySQL 是唯一事实来源。
- 阶段 4 只支持**整单**售后回补，不支持部分退款、部分退货或复杂售后状态机。

## 2. 当前单 Maven 模块中的包结构

工程为单 Maven 模块，根 `pom.xml` 的 `artifactId` 为 `mallagent`，启动类为 `com.mallagent.MallAgentApplication`。库存代码位于：

```text
src/main/java/com/mallagent/module/inventory/
├── api/                  InventoryStockApi
│   └── dto/              StockCommand、StockItem、StockRestoreCommand、StockSnapshot
├── controller/           AdminInventoryController
├── dto/admin/            StockAdjustRequest、StockResponse、StockFlowResponse
├── entity/               StockSku、StockFlow
├── enums/                StockBizType
├── error/                InventoryErrorCode
├── mapper/               StockSkuMapper、StockFlowMapper
└── service/              InventoryService、InventoryAdminService、InventoryQueryService

src/main/resources/db/migration/
├── V10__create_inventory.sql
└── V11__seed_inventory_permissions.sql

src/test/java/com/mallagent/integration/
├── AbstractInventoryIntegrationTest.java
├── InventoryAdminIntegrationTest.java
├── InventoryAdminConcurrencyIntegrationTest.java
├── InventoryLockIntegrationTest.java
├── InventoryLockConcurrencyIntegrationTest.java
├── InventoryDeductReleaseIntegrationTest.java
├── InventoryDeductReleaseConcurrencyIntegrationTest.java
├── InventoryRestoreIntegrationTest.java
├── InventoryRestoreConcurrencyIntegrationTest.java
├── InventoryFindStocksIntegrationTest.java
├── InventoryFlowConsistencyIntegrationTest.java
└── InventoryTransactionBoundaryIntegrationTest.java
```

“模块”在本文中指 `com.mallagent.module.inventory` 包边界，不是 Maven 子模块。

## 3. 依赖关系

```text
com.mallagent.module.order / payment / aftersale（未来）
                 ↓ 仅通过 api 包
com.mallagent.module.inventory
                 ↓
com.mallagent.module.product.api（只读 SKU 快照）
com.mallagent.framework
                 ↓
com.mallagent.common
```

- 购物车不调用库存；购物车只读取 `ProductSkuQueryApi`。
- 库存管理端在调整库存前调用 `ProductSkuQueryApi.findSku` 校验 SKU 存在；这是管理端入口的额外校验，`InventoryStockApi` 内部不再调用商品模块。
- 订单、支付、售后模块只能依赖 `InventoryStockApi` 及 `api/dto`，禁止直接引用库存 Mapper、实体或 Service 实现。
- 库存不反向依赖订单、售后模块，避免循环依赖。

## 4. `stock_sku` 表

库存主记录，每个 SKU 最多一行。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `sku_id` | BIGINT | SKU ID |
| `available_stock` | INT | 可售库存，非负 |
| `locked_stock` | INT | 锁定库存，非负 |
| `version` | INT | 每次库存变更递增 |
| `created_at` / `updated_at` | DATETIME(3) | 审计时间 |
| `created_by` / `updated_by` | BIGINT | 操作人 |

索引与约束：

- `UNIQUE KEY uk_stock_sku_sku_id (sku_id)`：一个 SKU 只有一条库存记录。
- `CHECK (available_stock >= 0)`、`CHECK (locked_stock >= 0)`：数据库层兜底禁止负库存。
- 所有库存变更使用条件更新和影响行数校验，不采用“先查询再更新”。

## 5. `stock_flow` 表

不可变库存流水。每次库存变更先写占位行，再在同一事务内回填变更前后值。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `biz_type` | VARCHAR(32) | `ORDER_LOCK` / `ORDER_DEDUCT` / `ORDER_RELEASE` / `RETURN_RESTORE` / `ADMIN_ADJUST` |
| `biz_no` | VARCHAR(64) | 订单号、售后单号或管理端幂等键 |
| `ref_biz_no` | VARCHAR(64) | 关联业务单号；售后回补时存原订单号 |
| `sku_id` | BIGINT | SKU ID |
| `quantity` | INT | 本次操作数量，正数 |
| `change_available` | INT | 可售库存变化，带符号 |
| `change_locked` | INT | 锁定库存变化，带符号 |
| `available_before` / `available_after` | INT | 可售库存变更前后值 |
| `locked_before` / `locked_after` | INT | 锁定库存变更前后值 |
| `request_token` | CHAR(36) | 每次操作尝试生成的 UUID，仅辅助识别 |
| `admin_biz_no` | VARCHAR(64) | 生成列，仅 `ADMIN_ADJUST` 有值 |
| `remark` | VARCHAR(255) | 备注 |
| `created_at` | DATETIME(3) | 创建时间 |
| `created_by` | BIGINT | 操作人 |

索引与约束：

- `UNIQUE KEY uk_stock_flow_biz (biz_type, biz_no, sku_id)`：订单/售后/管理端幂等的最终防线。
- `UNIQUE KEY uk_stock_flow_admin_biz_no (admin_biz_no)`：管理端一个 `Idempotency-Key` 只能对应一条流水。
- `KEY idx_stock_flow_ref (ref_biz_no, sku_id, biz_type)`：按原订单和 SKU 查询回补历史。
- `KEY idx_stock_flow_sku_created (sku_id, created_at)`：SKU 维度流水查询。
- `KEY idx_stock_flow_biz_no (biz_no)`：按业务单号查询。
- `CHECK (quantity > 0)`。

`admin_biz_no` 为 STORED 生成列：`biz_type = 'ADMIN_ADJUST'` 时取 `biz_no`，否则为 `NULL`；MySQL 唯一索引允许多个 `NULL`，因此不会影响订单和售后流水。

流水语义：

| 业务类型 | `change_available` | `change_locked` | 触发时点 |
| --- | --- | --- | --- |
| `ORDER_LOCK` | `-quantity` | `+quantity` | 下单锁定 |
| `ORDER_DEDUCT` | `0` | `-quantity` | 支付确认 |
| `ORDER_RELEASE` | `+quantity` | `-quantity` | 取消/超时释放 |
| `RETURN_RESTORE` | `+quantity` | `0` | 售后退货回补 |
| `ADMIN_ADJUST` | `delta` | `0` | 管理端手工调整 |

## 6. Flyway V10/V11

### V10 `V10__create_inventory.sql`

创建 `stock_sku`、`stock_flow` 两张业务表，以及唯一索引、普通索引、CHECK 约束和 `admin_biz_no` 生成列。阶段 4 仅新增这两张表，没有第三张库存表。

### V11 `V11__seed_inventory_permissions.sql`

- 新增库存根权限 `inventory`、资源权限 `inventory:stock`、`inventory:flow` 及叶子权限。
- 按角色编码和权限编码授权，不依赖固定角色 ID。
- SUPER_ADMIN 获得全部库存权限；OPERATOR 获得只读权限，不含 `inventory:stock:adjust`。

## 7. 管理端 API

控制器：[`AdminInventoryController`](../../src/main/java/com/mallagent/module/inventory/controller/AdminInventoryController.java)。基础路径 `/api/v1/admin/inventory`。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/admin/inventory/stocks` | `inventory:stock:read` | 库存分页查询；`pageNum`、`pageSize`、可选 `skuId` |
| GET | `/api/v1/admin/inventory/stocks/{skuId}` | `inventory:stock:read` | 库存详情；不存在返回 404 `14001` |
| POST | `/api/v1/admin/inventory/stocks/adjust` | `inventory:stock:adjust` | 调整可售库存；必须携带 `Idempotency-Key` |
| GET | `/api/v1/admin/inventory/flows` | `inventory:flow:read` | 流水分页查询；可选 `skuId`、`bizType`、`bizNo`、`refBizNo` |

调整请求：

```json
{
  "skuId": 1001,
  "delta": 10,
  "remark": "采购入库"
}
```

- `delta` 允许正负，不能为 0；调整后可售库存不能为负。
- `Idempotency-Key` 长度 8–64，字符集 `[A-Za-z0-9_-]`。
- 相同 Key、相同 SKU/`delta`/`remark` 幂等成功；任一不同返回 409 `14004`。
- 管理端调整不修改 `locked_stock`，并写入 `ADMIN_ADJUST` 流水。

响应 DTO：

- `StockResponse`：`skuId`、`skuCode`、`spuName`、`availableStock`、`lockedStock`、`totalStock`、`version`、`updatedAt`。
- `StockFlowResponse`：流水主键、业务类型/单号/关联单号、SKU、数量、变化量、before/after、备注、创建时间/人。

分页统一使用 `PageResult<T>`，`pageNum` 从 1 开始，`pageSize` 最大 100。

## 8. `InventoryStockApi` 内部方法

接口位于 `com.mallagent.module.inventory.api`，仅供进程内其他模块调用，不暴露 HTTP。

### 8.1 `lock(StockCommand)`

- `StockCommand.bizNo` 为订单号；`items` 必须覆盖订单全部 SKU。
- 校验订单号非空且 ≤64、items 非空、`skuId > 0`、`quantity > 0`、无重复 SKU；按 `skuId` 升序处理。
- 插入 `ORDER_LOCK` 占位流水；已存在终态时拒绝状态回退。
- 条件更新：`available_stock -= quantity`、`locked_stock += quantity`，要求可售库存充足且锁定库存不溢出。
- 回填流水前后值；重复完整请求幂等返回，不重复扣减。

### 8.2 `deduct(StockCommand)`

- 只允许在订单全部 `ORDER_LOCK` 流水存在且数量一致时调用。
- 写入 `ORDER_DEDUCT` 终态流水，`locked_stock -= quantity`，`available_stock` 不变。
- 重复相同请求幂等；已释放或终态异常返回 `14004`。

### 8.3 `release(StockCommand)`

- 与 `deduct` 共用终态流程，写入 `ORDER_RELEASE`。
- `locked_stock -= quantity`、`available_stock += quantity`。
- 重复相同请求幂等；已扣减或终态异常返回 `14004`。

### 8.4 `restore(StockRestoreCommand)`

- `bizNo` 为售后单号，`refBizNo` 为原订单号。
- 阶段 4 只允许整单回补：回补 SKU 集合和数量必须与原订单 `ORDER_LOCK`、`ORDER_DEDUCT` 完全一致。
- 原订单必须存在完整 `ORDER_DEDUCT` 终态；未锁定、未扣减、已释放或终态异常均拒绝。
- 写入 `RETURN_RESTORE` 流水，`available_stock += quantity`，`locked_stock` 不变。
- 同一售后单重复请求幂等；不同售后单对同一订单累计回补不得超过原扣减数量。

### 8.5 `findStocks(Collection<Long>)`

- 单次批量 SQL 查询 `stock_sku`，避免 N+1。
- 保持输入 SKU 首次出现顺序，重复 SKU 去重。
- 空集合返回空列表；`null` 集合、`null` SKU、非正数 SKU 返回 `14009`。
- 已初始化返回真实 `availableStock`、`lockedStock`、`initialized=true`。
- 未初始化返回 `(skuId, 0, 0, false)`，不创建库存记录或流水。
- 纯只读：不使用 `FOR UPDATE`、不修改库存、不调用商品模块、不使用 Redis。

## 9. `ORDER_LOCK` 行锁与并发互斥

- `StockFlowMapper.selectOrderLockRowsForUpdate(bizNo)` 按 `sku_id` 升序对原订单全部 `ORDER_LOCK` 行执行 `SELECT ... FOR UPDATE`。
- `deduct`、`release`、`restore` 都先获取该订单的行锁，再检查终态或售后历史，因此同订单生命周期操作严格串行。
- 订单 + SKU 的 `ORDER_LOCK` 行是统一互斥锚点，不仅依赖 `stock_sku` 当前余额判断订单状态。
- 多 SKU 库存更新统一按 `skuId` 升序执行，避免不同订单反向加锁导致死锁。
- 库存 API 内部不做局部死锁重试，不破坏调用方事务。

## 10. 订单完整 SKU 集合校验

- `lock`、`deduct`、`release` 的 `items` 必须与原订单 `ORDER_LOCK` 流水的 SKU 集合和数量完全一致。
- `restore` 同时校验 `ORDER_LOCK` 和 `ORDER_DEDUCT`，避免只根据当前库存余额判断是否允许回补。
- 缺少 SKU、额外 SKU、数量不一致均返回 `14004`。
- 多 SKU 请求中任一 SKU 失败，整个事务回滚，不产生部分终态或残留占位。

## 11. 订单级幂等与业务唯一键

- 幂等最终防线：`UNIQUE (biz_type, biz_no, sku_id)`。
- 流水使用普通 `INSERT`，只捕获预期的 `DuplicateKeyException`；不使用 `INSERT IGNORE`。
- 捕获重复后读取已有流水，严格比较业务单号、SKU 集合、数量、变化量和完成状态；不一致返回冲突，不能把非预期唯一键冲突当作幂等成功。
- 幂等判断与库存变更在同一事务内完成。
- `request_token` 只是每次尝试的辅助标识，不参与幂等成功判定。
- 管理端调整使用生成列 `admin_biz_no` 唯一索引，保证一个 `Idempotency-Key` 只能对应一条调整流水。

## 12. `deduct` / `release` 终态互斥

- 订单终态只有 `ORDER_DEDUCT` 或 `ORDER_RELEASE` 之一。
- 已存在同类型、完整、数量一致的终态时幂等成功。
- 已有不同终态时返回 `14004`；同一订单同时提交扣减和释放，并发下恰好一个成功。
- 终态写入与库存变更在同一事务内，任一失败整体回滚。

## 13. 售后整单回补与跨售后单累计限制

- 阶段 4 只支持整单回补；请求必须完全匹配原订单 SKU 集合和数量，不支持部分条目。
- 同一售后单重复请求：唯一键冲突后校验已有 `RETURN_RESTORE` 流水，完全一致则幂等成功，不再增加库存。
- 同一售后单但原订单号、SKU 集合或数量不同：返回 `14004`。
- 不同售后单回补同一订单：在订单 `ORDER_LOCK` 行锁下，按 `ref_biz_no + sku_id` 汇总已完成回补数量（`available_after IS NOT NULL`），累计不得超过原 `ORDER_DEDUCT` 数量。
- 整单模式下第一次完整回补成功后，其他售后单再次回补会被累计上限拒绝。
- 本次新插入的占位流水 `available_after` 为空，不计入累计，避免重复累计。

## 14. `findStocks` 批量查询与未初始化语义

- 只执行一条 `SELECT ... WHERE sku_id IN (...)`。
- 查询前完成参数校验和去重，去重集合保持首次出现顺序。
- 未初始化 SKU 只返回占位快照，不写 `stock_sku`，也不写 `stock_flow`。
- 查询不调用商品模块，因此不校验 SKU 是否存在、是否上架；调用方如需商品信息应另行读取商品 API。
- 查询不引入 Redis 缓存。

## 15. 事务传播、回滚与隔离级别

- `lock`、`deduct`、`release`、`restore` 使用 `@Transactional(propagation = REQUIRED, rollbackFor = Exception.class)`。
- 加入调用方已有事务；不设置 `isolation`，不修改调用方隔离级别。
- 由最外层事务发起方负责提交/回滚；库存 API 不使用 `REQUIRES_NEW`。
- 库存变更和流水写入必须在同一事务内提交。
- 管理端调整是独立最外层事务，显式使用 `READ_COMMITTED`。
- 库存 API 不做内部局部重试；死锁或异常交由调用方和数据库处理。

## 16. 库存流水 before/after 记录规则

- 流水只追加，不更新业务字段、不删除。
- 库存条件更新后，在持有库存行锁的情况下读取真实后值。
- `available_before = available_after - change_available`，`locked_before = locked_after - change_locked`。
- 只有 `finalize` 成功（回填前后值）的事务才能提交；已提交流水不得存在空 before/after。
- 下单锁定：可售减少、锁定增加；扣减：锁定减少、可售不变；释放：锁定减少、可售增加；回补：可售增加、锁定不变；管理端调整：只改可售。
- 流水按 SKU 的 `change_available`、`change_locked` 汇总应等于当前库存余额。

## 17. 错误码与 HTTP 状态

错误码区间 `14000–14999`。

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 14001 | `STOCK_NOT_FOUND` | 404 | 库存记录不存在 |
| 14002 | `STOCK_NOT_INITIALIZED` | 409 | 库存未初始化 |
| 14003 | `STOCK_INSUFFICIENT` | 409 | 库存不足 |
| 14004 | `STOCK_OPERATION_CONFLICT` | 409 | 库存操作冲突（状态、数量、幂等、溢出等） |
| 14005 | `STOCK_QUANTITY_INVALID` | 400 | 库存数量不合法 |
| 14006 | `STOCK_ADJUST_INVALID` | 400 | 库存调整不合法 |
| 14007 | `STOCK_IDEMPOTENCY_KEY_REQUIRED` | 400 | 缺少或非法 `Idempotency-Key` |
| 14008 | `STOCK_SKU_NOT_FOUND` | 404 | SKU 不存在 |
| 14009 | `STOCK_COMMAND_INVALID` | 400 | 库存命令参数不合法 |

## 18. 权限

V11 建立库存权限：

| 权限码 | 说明 |
| --- | --- |
| `inventory` | 库存根权限 |
| `inventory:stock` | 库存查询与调整资源 |
| `inventory:stock:read` | 查询库存 |
| `inventory:stock:adjust` | 调整库存 |
| `inventory:flow` | 库存流水资源 |
| `inventory:flow:read` | 查询库存流水 |

- SUPER_ADMIN 获得全部库存权限，包括 `inventory` 根权限和调整权限。
- OPERATOR 获得 5 项只读权限，不含 `inventory:stock:adjust`。
- OPERATOR 调整库存返回 403；未认证返回 401。

## 19. 已知限制、技术债与扩展方向

已知限制：

1. 仅支持整单售后回补，不支持部分退款/部分退货。
2. `findStocks` 对超大 SKU 集合未分批，可能生成较长的 `IN` 列表。
3. 可售库存 INT 溢出统一返回 `14004`，没有独立错误码。
4. 库存流水尚无归档或清理策略，长期运行需要归档设计。
5. 库存 API 内部不做死锁重试；由调用方负责有限重试和事务边界。
6. 库存模块不校验 SKU 可购买状态，未来订单模块必须在下单事务内完成商品校验、库存锁定和订单状态变更。
7. 测试中有 4 个夹具 SKU 通过 SQL 直接修改库存以构造溢出/回滚场景，测试库内其余额与流水汇总不一致；这是测试夹具行为，不代表生产代码路径。
8. 不引入 Redis/RabbitMQ 库存逻辑，当前以数据库条件更新和行锁保证正确性。

后续扩展方向：

- 阶段 6 订单模块在同一事务内调用 `lock`，取消/超时调用 `release`。
- 阶段 7 支付确认调用 `deduct`。
- 阶段 9 售后确认退货后调用 `restore`；如需部分退货，必须先重新设计累计回补和售后明细模型。
- 需要处理超大 SKU 批量查询时，再按固定大小分批；需要归档时新增流水归档表或离线任务，不修改历史流水。
