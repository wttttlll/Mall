# 订单模块设计（阶段 6 MVP 简化版）

> 状态：MVP 简化版已实现并验证（2026-11-02）；2026-11-05 完成超时自动取消与支付倒计时优化；2026-11-06 完成订单管理后台、简化发货和确认收货。
> 范围：从购物车提交订单、订单列表/详情、取消未支付订单、库存锁定与释放、发货、确认收货。
> 不包含：优惠券、运费、真实物流轨迹、售后。

## 1. 职责与边界

订单模块负责把用户已勾选购物车条目转成订单，在下单事务内重新读取商品当前价格、保存商品与收货地址快照，并调用库存 `lock` 锁定库存。

- 不负责商品上下架校验之外的业务规则；商品可购买状态和价格来自 `ProductSkuQueryApi`。
- 不直接访问购物车、用户地址、库存的 Mapper；分别通过 `CartCheckoutApi`、`UserAddressQueryApi`、`InventoryStockApi`。
- 取消订单只释放未支付订单的库存；已支付订单不能取消。
- 简化履约只维护订单状态和物流信息，不直接操作库存；不预埋优惠券、真实物流事件和售后表。

## 2. 包结构

```text
com.mallagent.module.order
├── api/                  OrderPaymentApi、OrderPaymentView、OrderPaymentItem
├── controller/           OrderController、AdminOrderController
├── dto/order/            CreateOrderRequest、OrderResponse、OrderSummaryResponse、OrderItemResponse
├── dto/admin/            AdminOrderSummaryResponse、AdminOrderResponse、ShipOrderRequest
├── entity/               OrderInfo、OrderItem
├── enums/                OrderStatus
├── error/                OrderErrorCode（16001–16008）
├── mapper/               OrderInfoMapper、OrderItemMapper
└── service/              OrderService、OrderPaymentApiImpl、OrderIdempotencyLookupService
```

## 3. 数据模型

- `order_info`：订单号、用户、状态、总金额、条目数、收货地址快照、幂等键、支付/取消/发货/完成时间、快递公司和物流单号、审计字段。
- `order_item`：订单明细快照，含 SKU、SPU、SKU 编码、商品名、图片、规格文本、下单单价、数量、行金额。
- 金额使用 `DECIMAL(18,2)` / `BigDecimal`；前端不能传入价格或金额。

状态：

| 状态 | 说明 |
| --- | --- |
| `PENDING_PAYMENT` | 待支付；库存已锁定，超过 30 分钟会被系统自动取消 |
| `PAID` | 已支付、待发货；库存已扣减 |
| `SHIPPED` | 已发货、待收货；保存快递公司和物流单号 |
| `COMPLETED` | 已完成；用户确认收货 |
| `CANCELLED` | 已取消（用户取消或超时自动取消）；库存已释放 |

## 4. 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | 登录 | 从购物车下单，必须携带 `Idempotency-Key` |
| GET | `/api/v1/orders` | 登录 | 我的订单分页，可选 `status` |
| GET | `/api/v1/orders/{orderNo}` | 登录 | 订单详情 |
| POST | `/api/v1/orders/{orderNo}/cancel` | 登录 | 取消待支付订单并释放库存 |
| POST | `/api/v1/orders/{orderNo}/confirm-receipt` | 登录 | 已发货订单确认收货 |
| GET | `/api/v1/admin/orders` | `order:info:read` | 管理端订单分页、状态筛选、订单号搜索 |
| GET | `/api/v1/admin/orders/{orderNo}` | `order:info:read` | 管理端订单详情 |
| POST | `/api/v1/admin/orders/{orderNo}/ship` | `order:info:ship` | 已支付订单发货 |

请求示例：

```http
POST /api/v1/orders
Idempotency-Key: 8f3c2b1a-...
Content-Type: application/json

{ "addressId": 1001 }
```

所有接口按当前登录用户隔离，访问他人订单返回 404 `16006`。

订单详情和列表响应包含 `payDeadline`，为后端按订单创建时间加超时配置计算出的支付截止时间，序列化为带 `+08:00` 偏移的 `OffsetDateTime`；前端只用于展示倒计时，最终状态以后端数据库为准。

## 5. 下单事务与幂等

下单事务：

1. 校验幂等键和收货地址归属。
2. 先插入订单占位行，`UNIQUE(user_id, idempotency_key)` 作为并发下单的幂等锚点。
3. 锁定并读取购物车已勾选条目（`FOR UPDATE`，按 SKU 升序）。
4. 批量读取商品当前价格与可购买状态，计算行金额和总金额。
5. 回填订单金额与条目数，写入订单明细。
6. 调用 `InventoryStockApi.lock` 锁定库存。
7. 删除已勾选购物车条目。

以上全部在同一本地事务内完成，失败整体回滚（包括订单占位行、明细、库存锁定和购物车清理）。

幂等：

- `UNIQUE(user_id, idempotency_key)` 防止同一用户重复下单。
- 相同 key 重复请求返回同一订单，不重复锁库存、不重复清购物车。
- 并发相同 key 的重复插入会在唯一索引上等待；冲突后通过独立 `READ_COMMITTED` 只读事务读取已提交订单，避免当前事务快照和行锁死锁。
- 并发不同 key 的请求通过购物车行锁串行化，只有第一个请求能消费该批已勾选条目。

## 6. 取消与并发

### 6.1 用户取消

- `cancel` 使用 `SELECT ... FOR UPDATE` 锁定订单行，仅 `PENDING_PAYMENT` 可取消。
- 取消时调用 `InventoryStockApi.release`，随后条件更新状态为 `CANCELLED`。
- 已取消订单重复取消幂等成功。
- 已支付订单取消返回 409 `16005`。
- 支付与取消共用订单行锁，因此并发时恰好一个成功。

### 6.2 超时自动取消

- 配置项 `mallagent.order.timeout-minutes` 默认 30 分钟，基于数据库 `order_info.created_at` 判定，不依赖前端倒计时。
- `OrderTimeoutScheduler` 使用 `@Scheduled(fixedDelay=...)` 周期性扫描，默认 60 秒一次，启动后 10 秒首次扫描；应用重启后直接从数据库恢复扫描。
- 扫描只查询候选订单号：`status = 'PENDING_PAYMENT' AND created_at <= cutoff`，V13 索引为 `(status, created_at)`。
- 每笔订单在独立 `REQUIRED + READ_COMMITTED` 事务中执行 `SELECT ... FOR UPDATE` 锁定订单行，再重新确认状态和创建时间；重复扫描、重启或多个实例同时扫描时，只有第一个成功取消并释放库存。
- 释放库存与状态更新在同一事务内；失败整体回滚，下一轮扫描会重试。
- 单笔订单取消失败只记录日志并继续处理同批其他订单；每笔订单独立事务，失败不会拖垮整轮扫描。
- 支付与超时取消共用订单行锁，互斥执行；支付接口在持锁后还会检查 `payDeadline`，超过截止时间返回 409 `17005`，不会扣减库存。
- 订单详情/列表返回 `payDeadline`，前端据此显示剩余支付时间；截止后前端刷新订单，最终以数据库状态为准。

## 7. 发货与确认收货

- 状态机：`PENDING_PAYMENT → PAID → SHIPPED → COMPLETED`，保留 `PENDING_PAYMENT → CANCELLED`。
- V14 在 `order_info` 增加 `shipping_company`、`tracking_no`、`shipped_at`、`completed_at`，并更新状态约束。
- 管理端发货要求 `order:info:ship` 权限；在订单行锁内重新检查状态，仅 `PAID` 可发货，条件更新 `status = 'PAID'`。
- 发货只保存快递公司和物流单号，不调用真实物流接口，也不修改库存；库存已在模拟支付时扣减。
- 已 `SHIPPED`/`COMPLETED` 订单重复发货：快递公司和物流单号完全一致时幂等返回当前订单；任一不同返回 409 `16005`，不覆盖首次物流信息，也不更新时间。
- 用户确认收货在订单行锁内校验订单归属和 `SHIPPED` 状态，条件更新为 `COMPLETED`；重复确认幂等返回当前订单。
- 未支付/已取消订单发货、未发货订单确认收货均返回 409 `16005`。
- 管理端订单列表支持分页、状态筛选和订单号模糊搜索；详情返回用户、收货地址、商品明细、支付状态和物流信息。

## 8. 错误码

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 16001 | `ORDER_IDEMPOTENCY_KEY_REQUIRED` | 400 | 缺少或非法幂等键 |
| 16002 | `ORDER_ADDRESS_NOT_FOUND` | 404 | 收货地址不存在或不属于当前用户 |
| 16003 | `ORDER_CART_EMPTY` | 409 | 没有已勾选商品 |
| 16004 | `ORDER_PRODUCT_UNAVAILABLE` | 409 | 商品下架或价格不可用 |
| 16005 | `ORDER_STATUS_CONFLICT` | 409 | 订单状态不允许操作 |
| 16006 | `ORDER_NOT_FOUND` | 404 | 订单不存在或不属于当前用户 |
| 16007 | `ORDER_COMMAND_INVALID` | 400 | 参数不合法 |
| 16008 | `ORDER_OPERATION_CONFLICT` | 409 | 并发冲突 |

## 9. 已知限制

- 超时取消最多延迟一个扫描周期（默认 60 秒）生效；支付接口已按截止时间直接拒绝过期支付，因此不会出现截止后支付成功。
- 物流只保存快递公司和物流单号，不实现真实物流轨迹、物流事件和售后。
- 没有运费、优惠券、发票、多地址拆单。
- 订单明细为下单时快照；商品后续改名、改价不影响历史订单。
- 订单占位行在事务失败时随事务回滚，不会留下 total=0 的已提交订单。
- 自动取消与用户取消都落到 `CANCELLED`，当前没有单独记录取消来源。
