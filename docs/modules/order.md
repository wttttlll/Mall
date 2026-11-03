# 订单模块设计（阶段 6 MVP 简化版）

> 状态：MVP 简化版已实现并验证（2026-11-02）。
> 范围：从购物车提交订单、订单列表/详情、取消未支付订单、库存锁定与释放。
> 不包含：优惠券、运费、超时自动关闭、管理端订单、物流、售后。

## 1. 职责与边界

订单模块负责把用户已勾选购物车条目转成订单，在下单事务内重新读取商品当前价格、保存商品与收货地址快照，并调用库存 `lock` 锁定库存。

- 不负责商品上下架校验之外的业务规则；商品可购买状态和价格来自 `ProductSkuQueryApi`。
- 不直接访问购物车、用户地址、库存的 Mapper；分别通过 `CartCheckoutApi`、`UserAddressQueryApi`、`InventoryStockApi`。
- 取消订单只释放未支付订单的库存；已支付订单不能取消。
- 不为优惠券、物流、售后预埋表或代码。

## 2. 包结构

```text
com.mallagent.module.order
├── api/                  OrderPaymentApi、OrderPaymentView、OrderPaymentItem
├── controller/           OrderController
├── dto/order/            CreateOrderRequest、OrderResponse、OrderSummaryResponse、OrderItemResponse
├── entity/               OrderInfo、OrderItem
├── enums/                OrderStatus
├── error/                OrderErrorCode（16001–16008）
├── mapper/               OrderInfoMapper、OrderItemMapper
└── service/              OrderService、OrderPaymentApiImpl、OrderIdempotencyLookupService
```

## 3. 数据模型

- `order_info`：订单号、用户、状态、总金额、条目数、收货地址快照、幂等键、支付/取消时间、审计字段。
- `order_item`：订单明细快照，含 SKU、SPU、SKU 编码、商品名、图片、规格文本、下单单价、数量、行金额。
- 金额使用 `DECIMAL(18,2)` / `BigDecimal`；前端不能传入价格或金额。

状态：

| 状态 | 说明 |
| --- | --- |
| `PENDING_PAYMENT` | 待支付；库存已锁定 |
| `PAID` | 已支付；库存已扣减 |
| `CANCELLED` | 已取消；库存已释放 |

## 4. 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | 登录 | 从购物车下单，必须携带 `Idempotency-Key` |
| GET | `/api/v1/orders` | 登录 | 我的订单分页，可选 `status` |
| GET | `/api/v1/orders/{orderNo}` | 登录 | 订单详情 |
| POST | `/api/v1/orders/{orderNo}/cancel` | 登录 | 取消待支付订单并释放库存 |

请求示例：

```http
POST /api/v1/orders
Idempotency-Key: 8f3c2b1a-...
Content-Type: application/json

{ "addressId": 1001 }
```

所有接口按当前登录用户隔离，访问他人订单返回 404 `16006`。

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

- `cancel` 使用 `SELECT ... FOR UPDATE` 锁定订单行，仅 `PENDING_PAYMENT` 可取消。
- 取消时调用 `InventoryStockApi.release`，随后条件更新状态为 `CANCELLED`。
- 已取消订单重复取消幂等成功。
- 已支付订单取消返回 409 `16005`。
- 支付与取消共用订单行锁，因此并发时恰好一个成功。

## 7. 错误码

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

## 8. 已知限制

- 不实现订单超时关闭；待支付订单会一直保留。
- 不实现管理端订单查询、发货、物流和售后。
- 没有运费、优惠券、发票、多地址拆单。
- 订单明细为下单时快照；商品后续改名、改价不影响历史订单。
- 订单占位行在事务失败时随事务回滚，不会留下 total=0 的已提交订单。
