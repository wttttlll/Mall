# MallAgent 测试计划

> 适用范围：阶段 0–9。本文登记测试分层、关键用例与执行方式；每阶段新增并发、幂等或重复请求用例必须同步更新。
> 最后更新：2026-11-02（阶段 6/7 MVP 订单与模拟支付）。

## 1. 测试分层

| 层级 | 技术 | 覆盖内容 |
| --- | --- | --- |
| 单元测试 | JUnit 5 + Mockito | Service 校验、金额计算、状态判断 |
| 集成测试 | Spring Boot Test + MockMvc | HTTP 链路、数据库、Redis、安全、权限 |
| 并发测试 | 多线程直接调用 Service + 真实 MySQL/Redis | 库存、优惠券、下单、支付回调、退款、购物车 |
| 回归测试 | 全量 `mvn clean verify` | 每阶段结束执行 |

不使用 Testcontainers；集成测试使用 Docker Compose 提供的 MySQL、Redis、RabbitMQ，测试库与开发库隔离。

## 2. 执行方式

```bash
docker compose up -d --wait

# 使用 Docker Compose 基础设施（MySQL 宿主机 3307）
# MYSQL_PASSWORD 由本地环境注入，文档不记录实际密码
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD="$MYSQL_PASSWORD" mvn clean verify

# 空库迁移与全量测试；测试库需提前创建并授权
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD="$MYSQL_PASSWORD" \
MYSQL_TEST_DATABASE=mallagent_inventory_verify_s9 mvn clean verify

# 使用本机 MySQL 3306
mvn clean verify
```

- 测试库：`mallagent_test`，Redis database `1`。
- 测试数据使用唯一用户名、唯一 SKU 编码，不依赖执行顺序。
- 阶段完成前必须实际执行并如实报告结果。

## 3. 阶段 0–2 已有覆盖

- 健康检查、统一响应、traceId。
- 注册、登录、刷新、登出、登录失败锁定。
- RBAC、权限控制、401/403。
- 收货地址归属、默认地址。
- 商品分类层级与删除约束、品牌、SPU/SKU/规格、状态机、可见性、缓存失效、分页上限、`product:*` 权限。
- SUPER_ADMIN 的 `product` 根权限（V8）。
- 上架 SKU 规格完整性、规格值归属校验（12032）。
- 分类、品牌、规格、规格值、SKU 的多次删除与同业务键重建（V9）。
- 商品 `ProductSkuQueryApi`：可购买快照、下架状态、规格文本、缺失 SKU。

## 4. 阶段 3 购物车测试矩阵

### 4.1 单元测试

| 用例 | 期望 |
| --- | --- |
| 缺少或非法 `Idempotency-Key` | `13009`，不写购物车 |
| 数量非法 | `13003` |
| 汇总只统计已选且可购买条目 | `selectedQuantity`、`selectedAmount` 正确 |
| 不可购买条目 | `available=false`，金额 0 |

### 4.2 集成测试

| 用例 | 期望 |
| --- | --- |
| 添加并查询 | 条目、当前价格、规格、汇总正确 |
| SKU 不存在 | 404 `13001` |
| 商品已下架 | 409 `13006` |
| 相同幂等键相同请求 | 数量只增加一次 |
| 幂等重试时商品已下架 | 仍按重试返回 200，数量不再增加 |
| 不同幂等键重复添加 | 数量合并 |
| 相同幂等键不同请求 | 409 `13010`，数据不变 |
| 缺少幂等键 | 400 `13009` |
| 客户端传入 price/amount | 被忽略，按商品当前价格计算 |
| 数量超限回滚 | 幂等记录与购物车写入一起回滚，同 key 可重试 |
| 购物车种类上限 | 超过配置上限返回 `13005` 并回滚 |
| 修改数量、删除、重复删除 | 数量更新；删除幂等 |
| 单条勾选、全选/取消全选 | 选中集合与汇总正确 |
| 商品改价 | 购物车返回当前价格 |
| 商品下架 | `available=false`，原因含“下架”，金额 0 |
| SKU 删除 | `available=false`，原因含“SKU 不存在” |
| 用户隔离 | 用户 B 看不到且不能删除用户 A 的条目 |
| 未登录 | 401 |
| 数量上限 | 超过 99 返回 `13004`，数据库仍为 99 |

### 4.3 并发测试

| 用例 | 期望 |
| --- | --- |
| 20 线程并发添加同一 SKU | 最终数量 20，无重复行 |
| 并发同一幂等键 | 只添加一次 |
| 并发添加到上限 | 最终不超过 99，超限请求返回 `13004` |
| 并发添加不同 SKU | 各 SKU 数量正确，行数正确 |

### 4.4 幂等与重复请求规则

- 添加接口的幂等判断与购物车写入在同一事务内完成。
- 幂等唯一索引 `(user_id, idempotency_key)` 是最终防线。
- 事务回滚时幂等记录一并回滚，允许客户端用同一 key 重试。
- 结算、支付回调、退款等后续重复请求测试在对应阶段补充。

## 5. 阶段 4 库存测试矩阵

库存测试全部直接查询 MySQL 校验真实状态，不只检查返回值。测试类与用例见 `src/test/java/com/mallagent/integration/Inventory*`。

### 5.1 生命周期与幂等

| 用例 | 期望 |
| --- | --- |
| 管理端首次正向调整 | 初始化 `stock_sku`，写入完整 `ADMIN_ADJUST` 流水 |
| lock → deduct | 可售减少、锁定归零，`ORDER_DEDUCT` 流水完整 |
| lock → release | 锁定归零、可售恢复，`ORDER_RELEASE` 流水完整 |
| lock → deduct → restore | 可售恢复，写入 `RETURN_RESTORE` 流水 |
| deduct 后 release / release 后 deduct | 409 `14004`，不改变终态 |
| release 后 restore | 409 `14004` |
| 重复 lock / deduct / release | 幂等，库存只变更一次 |
| 重复 restore | 幂等，库存只回补一次 |
| 不同售后单重复整单回补 | 第二次拒绝 |
| 多 SKU 任一步失败 | 库存与流水整体回滚 |

### 5.2 并发与事务

| 用例 | 期望 |
| --- | --- |
| 20 线程多订单争抢 10 件库存 | 恰好 10 个成功，其余 14003，无超卖 |
| 10 线程重复 lock 同一订单 | 只生成 1 条 `ORDER_LOCK` 流水 |
| deduct 与 release 并发 | 恰好一个终态成功 |
| 不同售后单并发 restore 同一订单 | 最多一个成功 |
| 同一售后单 10 线程 restore | 只回补一次 |
| 管理端调整与订单锁定并发 | 最终余额正确，流水对账一致 |
| 多 SKU 反向输入顺序并发 lock | 不死锁，余额正确 |
| 外层事务重复操作 | 幂等且正常提交 |
| 外层事务抛异常 | 库存与流水整体回滚 |
| 库存 API 内部重试 | 无局部重试，不破坏调用方事务 |

### 5.3 流水对账

| 检查项 | 期望 |
| --- | --- |
| before/after 非空 | 所有已提交流水完整 |
| `change_available` / `change_locked` | 与 before/after 差值一致 |
| 同一 SKU 流水链 | 按 ID 顺序前后值连续 |
| 业务唯一键 | 无重复 |
| 终态互斥 | 同一订单不同时存在 DEDUCT 与 RELEASE |
| 回补上限 | `RETURN_RESTORE` 数量不超过原 DEDUCT |
| 负数库存 | 0 条 |
| 回滚残留 | 无未完成占位流水 |

### 5.4 findStocks

| 用例 | 期望 |
| --- | --- |
| 单 SKU | 返回真实可售/锁定库存与 `initialized=true` |
| 多 SKU 批量 | 一次批量查询，保持首次出现顺序并去重 |
| 已初始化/未初始化混合 | 未初始化返回 `(0,0,false)` |
| 空集合 | 返回空列表 |
| null 集合、null SKU、非正数 SKU | 400 `14009` |
| 查询副作用 | 库存余额、库存记录、流水数量均不变 |
| 生命周期各阶段查询 | 快照与数据库一致 |

### 5.5 迁移与权限

| 用例 | 期望 |
| --- | --- |
| 空库 Flyway V1–V11 | 全部成功，库存仅 2 张表 |
| V9 已有库升级到 V11 | V10/V11 成功执行 |
| SUPER_ADMIN | 拥有库存根权限和调整权限 |
| OPERATOR | 可查询，不可调整；调整返回 403 |
| 历史迁移 V1–V9 | 校验和未被修改 |
| 索引与约束 | 唯一键、CHECK 与代码一致 |

## 6. 阶段 6/7 MVP 订单与模拟支付测试矩阵

测试类：`OrderIntegrationTest`、`OrderPaymentConcurrencyIntegrationTest`。

### 6.1 下单

| 用例 | 期望 |
| --- | --- |
| 从购物车下单 | 服务端计价、保存订单明细快照、锁定库存、清空已勾选购物车 |
| 下单前商品改价 | 订单使用商品当前价格，不使用购物车旧价格 |
| 相同 Idempotency-Key 重复下单 | 返回同一订单，只锁一次库存 |
| 并发相同 Idempotency-Key | 只生成一笔订单、一条 `ORDER_LOCK` |
| 并发不同 Idempotency-Key | 同批购物车只能消费一次，另一请求 409 `16003` |
| 缺少幂等键 | 400 `16001` |
| 地址不存在或不属于当前用户 | 404 `16002` |
| 空购物车 | 409 `16003`，订单占位回滚 |
| 商品下架 | 409 `16004`，订单占位回滚 |
| 库存不足 | 409 `14003`，订单、明细、购物车全部回滚 |
| 订单列表/详情 | 只返回当前用户数据；支持状态筛选 |
| 访问他人订单 | 404 `16006` |

### 6.2 模拟支付与取消

| 用例 | 期望 |
| --- | --- |
| 支付待支付订单 | 订单 `PAID`、库存扣减、写入 `ORDER_DEDUCT` 与支付单 |
| 重复支付 | 返回同一支付单，不重复扣库存，只有一条支付记录 |
| 并发重复支付 | 10 线程全部幂等成功，库存只扣一次 |
| 取消待支付订单 | 订单 `CANCELLED`、库存释放、写入 `ORDER_RELEASE` |
| 重复取消 | 幂等成功，只释放一次 |
| 已支付订单取消 | 409 `16005`，状态和库存不变 |
| 已取消订单支付 | 409 `17001`，不创建支付单、不扣库存 |
| 支付与取消并发 | 恰好一个成功，不能同时出现 DEDUCT 和 RELEASE |

### 6.3 数据库一致性

| 检查项 | 期望 |
| --- | --- |
| `order_info.total_amount` | 等于订单明细 `line_amount` 之和 |
| `order_info.item_count` | 等于订单明细行数 |
| 每笔订单 | 至少存在完整 `ORDER_LOCK` |
| `PAID` 订单 | 恰好一条 `ORDER_DEDUCT` 和一条 `payment_order` |
| `CANCELLED` 订单 | 恰好一条 `ORDER_RELEASE`，无支付单 |
| 订单终态 | 不同时存在 DEDUCT 和 RELEASE |
| 唯一键 | 无重复 `(user_id, idempotency_key)`、无重复 `payment_order.order_no` |
| 库存 | 无负数可售/锁定库存 |

## 7. 后续阶段测试重点

| 阶段 | 重点 |
| --- | --- |
| 4 库存 | ✅ 已通过技术验收，见第 5 节 |
| 5 优惠券 | 不超发、不重复领取、锁定/核销/释放幂等（尚未开始） |
| 6 订单 | ✅ MVP 简化版已实现，见第 6 节；超时关闭未实现 |
| 7 支付 | ✅ 模拟支付成功已实现，见第 6 节；回调、退款未实现 |
| 8 物流 | 物流事件幂等、乱序推进、确认收货约束 |
| 9 售后 | 可退金额、重复退款防护、退货回补幂等 |

## 8. 测试数据与隔离

- 每个集成测试注册唯一用户，不依赖执行顺序。
- 商品测试使用唯一分类/品牌/SKU 编码，避免唯一索引冲突。
- 购物车测试使用唯一用户；购物车状态直接读 MySQL，不涉及购物车缓存。
- 测试库与开发库分离；Redis 测试使用 database `1`。
- 不使用物理外键，测试数据由 API 创建，保证业务规则生效。

## 9. 验收执行记录

- 2026-10-31：阶段 2（商品）、阶段 3（购物车）通过验收。
  - 全量 `mvn clean verify`：60 个测试，0 失败、0 错误、0 跳过。
  - Flyway V1–V9 在空库 `mallagent_final_verify` 与已有库 `mallagent_test` / `mallagent` 均执行成功。
  - 测试库 `mallagent_test` 的 SPU 157（`规格商品-27d45fb1` / SKU 145）确认为阶段 2 诊断测试残留数据，暂不删除；开发库 `mallagent` 已上架 SKU 规格完整性审计为 0 条。
- 2026-11-02：阶段 4（库存）通过技术验收。
  - 已有库 `mallagent_test`：`Tests run: 131, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
  - 空库 `mallagent_inventory_verify_s9`：131 个测试全部通过，Flyway V1–V11 全部成功。
  - 升级验证：开发库 `mallagent` 处于 V9，复制到 `mallagent_upgrade_s9` 后 V10/V11 成功执行。
  - 库存仅 `stock_sku`、`stock_flow` 两张表；SUPER_ADMIN 6 项库存权限，OPERATOR 5 项只读权限。
  - 已知测试夹具：4 个 SKU 由测试直接 SQL 修改库存构造溢出/回滚场景，测试库内余额与流水汇总不一致，不属于生产代码路径。
- 2026-11-02：阶段 6/7 MVP 简化版（订单、模拟支付）实现完成。
  - 已有库 `mallagent_test`：`Tests run: 147, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
  - 空库 `mallagent_order_verify`：147 个测试全部通过，Flyway V1–V12 全部成功。
  - 升级验证：V9 开发库副本 `mallagent_order_upgrade` 启动后自动升级到 V12，订单/支付三表创建成功。
  - 新增表：`order_info`、`order_item`、`payment_order`。
  - 数据库一致性检查：订单总额=明细合计、支付订单有 DEDUCT 和支付单、取消订单有 RELEASE、无重复幂等键/支付单、无负数库存。
- 2026-11-02：Vue 3 前端 MVP 完成。
  - `npm test`：4 个 Vitest 单测通过。
  - `npm run build`：Vite 生产构建成功。
  - 真实联调：Vite `5173` 页面 200，`/api` 代理登录、商品列表和健康检查成功；后端 API 完成“注册 → 地址 → 加购 → 下单 → 模拟支付 → 取消订单 → 库存核对”全流程。
