# 模拟支付模块设计（阶段 7 MVP 简化版）

> 状态：MVP 简化版已实现并验证（2026-11-02）。
> 范围：模拟支付成功接口、订单支付状态联动、库存扣减。
> 不包含：真实微信/支付宝、回调验签、支付单预创建、退款、对账。

## 1. 职责与边界

模拟支付模块负责把待支付订单标记为已支付，并调用库存 `deduct` 扣减锁定库存。

- 不接真实支付渠道，不涉及真实资金；支付渠道固定 `MOCK`。
- 不直接访问订单 Mapper/Entity；通过 `OrderPaymentApi` 锁定订单并读取支付视图、回写状态。
- 不做支付单预创建、回调、超时关闭；只有“模拟支付成功”一个入口。
- 支付与订单取消共享订单行锁，保证并发互斥。

## 2. 包结构

```text
com.mallagent.module.payment
├── controller/           PaymentController
├── dto/payment/          PaymentResponse
├── entity/               PaymentOrder
├── enums/                PaymentStatus
├── error/                PaymentErrorCode（17001–17004）
├── mapper/               PaymentOrderMapper
└── service/              PaymentService
```

订单侧契约：

```text
com.mallagent.module.order.api.OrderPaymentApi
com.mallagent.module.order.api.dto.OrderPaymentView
com.mallagent.module.order.api.dto.OrderPaymentItem
```

## 3. 数据模型

`payment_order`：

| 字段 | 说明 |
| --- | --- |
| `payment_no` | 支付单号，唯一 |
| `order_no` | 订单号，唯一；一个订单最多一条成功支付 |
| `user_id` | 用户 |
| `amount` | 支付金额，等于订单总金额 |
| `channel` | 当前固定 `MOCK` |
| `status` | 当前仅 `SUCCESS` |
| `paid_at` | 支付成功时间 |

## 4. 接口

```http
POST /api/v1/payments/orders/{orderNo}/pay
Authorization: Bearer <token>
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "paymentNo": "PAY...",
    "orderNo": "ORD...",
    "amount": 177.60,
    "channel": "MOCK",
    "status": "SUCCESS",
    "paidAt": "2026-11-02T21:00:00"
  }
}
```

接口只允许支付当前用户自己的订单；订单不存在或不属于当前用户返回 404 `16006`。

## 5. 支付事务与并发

支付事务：

1. 锁定订单行（`SELECT ... FOR UPDATE`），与取消订单共用互斥点。
2. 订单不存在或不属于当前用户：订单不存在。
3. 已支付：读取已有支付单并幂等返回，不再扣库存。
4. 已取消：返回 409 `17001`。
5. 待支付：调用 `InventoryStockApi.deduct` 扣减库存。
6. 写入 `payment_order`，一个订单最多一条。
7. 条件更新订单状态 `PENDING_PAYMENT -> PAID`。

以上在同一本地事务内完成；库存扣减、支付单或订单状态任一失败，全部回滚。

并发保证：

- 重复支付：订单行锁串行化；首个请求支付后，后续请求看到 `PAID` 并返回同一支付单，不重复扣库存。
- 支付与取消并发：订单行锁使两者串行，只有一个状态流转成功。
- `UNIQUE(order_no)` 保证一个订单最多一条支付记录。

## 6. 错误码

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 17001 | `PAYMENT_STATUS_CONFLICT` | 409 | 订单已取消或状态不允许支付 |
| 17002 | `PAYMENT_RECORD_CONFLICT` | 409 | 支付记录冲突或缺失 |
| 17003 | `PAYMENT_NOT_FOUND` | 404 | 支付记录不存在 |
| 17004 | `PAYMENT_COMMAND_INVALID` | 400 | 支付参数不合法 |

## 7. 已知限制

- 不接真实支付渠道，不实现回调验签、支付超时、退款和对账。
- 不创建 `PENDING`/`FAILED` 支付单；只记录支付成功。
- 不实现支付查询接口；需要时可通过订单详情查看状态和支付时间。
- 金额完全取订单总金额，不接受前端传入金额。
