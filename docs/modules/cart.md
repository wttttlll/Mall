# 购物车模块设计（阶段 3）

> 状态：已实现，等待技术负责人验收。
> 范围：添加、合并数量、修改数量、删除、勾选/取消勾选、全选/取消全选、查询与汇总。
> 不包含：库存预占、下单、支付、购物车下单后清理（阶段 4/6）。

## 1. 设计约束

- MySQL 是购物车唯一事实来源；购物车状态不缓存，查询直接读 MySQL。
- 商品价格、名称、规格、上下架状态每次从商品模块实时读取。
- 添加接口必须携带 `Idempotency-Key`；幂等判断与购物车写入在同一事务内。
- 价格、名称、规格、上下架状态不落购物车，每次查询从商品模块读取。
- 金额使用 `BigDecimal`，`DECIMAL(18,2)`；客户端不能传入价格或用户 ID。
- 购物车只能操作当前登录用户的数据。
- 不提前实现订单、库存预占和下单清理。

## 2. 模块结构

```text
mallagent-module-cart
├── config/        CartProperties
├── controller/    CartController
├── dto/cart/      请求与响应 DTO
├── entity/        CartItem、CartIdempotency
├── error/         CartErrorCode（13001–13010）
├── mapper/        CartItemMapper、CartIdempotencyMapper
└── service/       CartService（编排、重试）、CartWriteService（写事务）
```

依赖方向：

```text
bootstrap -> cart -> product(api)
cart -> framework -> common
```

购物车只使用 `com.mallagent.module.product.api` 下的 `ProductSkuQueryApi`，不直接引用商品 Mapper、实体或 Service 实现。

## 3. 数据模型

### 3.1 `cart_item`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `user_id` | BIGINT | 用户 ID |
| `sku_id` | BIGINT | SKU ID |
| `quantity` | INT | 数量 1–99 |
| `selected` | TINYINT | 1 勾选，0 未勾选 |
| `created_at` / `updated_at` | DATETIME(3) | 审计时间 |
| `created_by` / `updated_by` | BIGINT | 审计用户 |
| `version` | INT | 版本号 |

索引：

- `UNIQUE (user_id, sku_id)`：同一用户同一 SKU 只有一行。
- `KEY (user_id, created_at)`：购物车列表按加入顺序查询。

删除策略：物理删除。购物车条目是高频临时数据，删除后重新添加直接插入新行；不使用逻辑删除，避免唯一索引与墓碑记录冲突。

### 3.2 `cart_idempotency`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 自增主键 |
| `user_id` | BIGINT | 用户 ID |
| `idempotency_key` | VARCHAR(64) | 客户端幂等键 |
| `request_hash` | CHAR(64) | `skuId:quantity` 的 SHA-256 |
| `sku_id` | BIGINT | 本次请求 SKU |
| `quantity` | INT | 本次请求数量 |
| `claim_token` | CHAR(36) | 本次插入生成的令牌 |
| `created_at` | DATETIME(3) | 创建时间 |

索引：`UNIQUE (user_id, idempotency_key)`。

`claim_token` 用于区分“本次请求新插入”与“读到其他请求的既有记录”，避免依赖 JDBC 对 `ON DUPLICATE KEY UPDATE` 影响行数的差异。

## 4. 缓存策略

购物车状态**不做 Redis 缓存**：

- 查询直接读取 MySQL，避免缓存删除失败或并发回填导致用户看到旧数量/旧勾选状态。
- 商品价格、名称、规格、上下架状态仍通过 `ProductSkuQueryApi` 实时读取商品模块。
- Redis 基础设施保留给商品分类树、商品详情等其他缓存，不为购物车引入版本号或锁机制。
- 购物车列表数据量小、按 `user_id` 索引查询，直接查库的代价可接受。

## 5. API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/cart/items` | 添加 SKU；需要 `Idempotency-Key` |
| PUT | `/api/v1/cart/items/{skuId}` | 设置数量 |
| DELETE | `/api/v1/cart/items/{skuId}` | 删除条目（幂等） |
| PUT | `/api/v1/cart/items/{skuId}/selected` | 单条勾选/取消 |
| PUT | `/api/v1/cart/selected` | 全选/取消全选 |
| GET | `/api/v1/cart` | 查询购物车与汇总 |

响应字段：

- `items[]`：skuId、spuId、skuCode、spuName、imageUrl、specText、price、quantity、selected、amount、available、unavailableReason。
- `summary`：itemCount、totalQuantity、selectedQuantity、selectedAmount、totalAmount。

汇总口径：

- `itemCount`：SKU 种类数，含不可购买条目。
- `totalQuantity`：所有条目数量之和，含不可购买条目。
- `selectedQuantity`：已选且可购买的数量之和。
- `selectedAmount`：已选且可购买的金额。
- `totalAmount`：所有可购买条目的金额。

## 6. 幂等与并发

### 6.1 添加流程

1. 校验 `Idempotency-Key` 格式（8–64 位字母、数字、下划线、短横线）。
2. 校验 `skuId` 与数量。
3. 在同一事务内：
   - 向 `cart_idempotency` 插入记录，唯一索引兜底；
   - 读取记录并比较 `claim_token`：
     - 相同：本次是新请求，继续校验商品可购买状态；
     - 不同且 `request_hash` 相同：重复请求，不写入、不重复校验商品状态；
     - 不同且 `request_hash` 不同：返回 `13010`。
4. 新请求校验商品存在且可购买，然后执行购物车 upsert。
5. upsert 后检查单件数量上限，超限回滚整个事务。

### 6.2 数据库并发控制

- `UNIQUE (user_id, sku_id)` 防止重复行。
- `INSERT ... ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)` 在行锁内原子累加。
- 超限检查在 upsert 之后执行，超限抛异常回滚。
- 修改数量、删除、勾选使用条件 `UPDATE/DELETE`，影响行数为 0 时按业务语义处理。
- 幂等表与购物车表并发写入可能死锁，外层做最多 3 次有限重试并记录日志。

## 7. 业务边界

- 商品下架、SKU 删除、价格变化：查询时实时读取商品模块；不可购买条目保留并标记，金额不计入汇总。
- 不自动删除不可购买条目，由用户决定是否删除。
- 不扣库存、不预占库存、不生成订单。
- 不做价格快照；客户端传入价格一律忽略（DTO 中没有价格字段）。
- 购物车种类上限 100 为应用层软上限，不作为强并发约束。

## 8. 测试

- 单元测试：幂等键校验、数量校验、汇总口径。
- 集成测试：SKU 不存在、商品不可购买、添加、幂等重试、不同 key 合并、数量修改、删除、勾选/全选、改价、下架、SKU 删除、用户隔离、401、客户端伪造价格。
- 并发测试：并发添加同一 SKU、并发同幂等键、并发上限、并发添加不同 SKU。

执行：

```bash
docker compose up -d --wait
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD=mallagent mvn clean verify
```

## 9. 已知限制

- 幂等记录当前不清理，后续可增加定时清理。
- 购物车种类上限为软上限，极端并发下可能短暂超出 1。
- 商品可购买性在添加时校验，添加后被下架的商品在查询时标记为不可购买。
- 购物车状态直接查 MySQL；后续如引入缓存，必须重新设计一致性方案。
