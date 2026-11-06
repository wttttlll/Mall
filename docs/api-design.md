# MallAgent API 设计规范

> 适用范围：`/api/v1` 全部接口。本文记录通用约定、购物车/库存/订单/支付/发货接口和错误码；商品接口详见 `docs/product-module.md`，库存、订单、支付细节见 `docs/modules/`。
> 最后更新：2026-11-06（阶段 8/9 简化履约与售后 + 个人中心、收藏与评价）。

## 1. 通用约定

- 基础路径 `/api/v1`；管理端 `/api/v1/admin/**`，用户端 `/api/v1/**`。
- 请求/响应 JSON 使用 lowerCamelCase；日期时间使用 ISO-8601。
- 统一响应体：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "a1b2c3d4",
  "timestamp": "2026-10-31T20:00:00+08:00"
}
```

- `code = 0` 表示成功；业务失败使用非 0 错误码。
- HTTP 状态码保留语义：400 参数错误、401 未认证、403 无权限、404 不存在、409 冲突、500 服务异常、503 暂不可用。
- 分页参数 `pageNum`（从 1 开始）、`pageSize`（默认 10，最大 100），响应 `PageResult<T>`。
- 请求参数使用 Jakarta Validation 校验；Controller 使用 `@Valid`。
- 禁止直接返回实体，必须使用 DTO/VO。
- 所有 Controller 使用 Springdoc 注解，按模块分组。

## 2. 认证与权限

- JWT Access Token 放在 `Authorization: Bearer <token>`。
- 商品列表、商品详情和商品公开评价 `GET /api/v1/products/**` 允许未登录访问；收藏、购物车、订单、售后仍要求登录。
- 用户端接口默认要求登录；管理端接口使用方法级权限码。
- 权限码格式 `module:resource:action`，例如 `product:spu:create`。
- 库存管理端权限：`inventory:stock:read`、`inventory:stock:adjust`、`inventory:flow:read`；SUPER_ADMIN 全部拥有，OPERATOR 只读。
- 401 与 403 不混用；越权访问返回 403。

## 3. 幂等

- 敏感写接口使用 `Idempotency-Key` 请求头。
- 当前强制要求 `Idempotency-Key` 的 HTTP 接口：
  - `POST /api/v1/cart/items`：
    - 长度 8–64，字符集 `[A-Za-z0-9_-]`；
    - 同一用户 + 同一 key + 相同请求体：视为重试，不重复生效；
    - 同一用户 + 同一 key + 不同请求体：返回 409 `13010`。
  - `POST /api/v1/admin/inventory/stocks/adjust`：
    - 长度 8–64，字符集 `[A-Za-z0-9_-]`；
    - 同一 key + 相同 SKU/`delta`/`remark`：幂等成功；
    - 同一 key + 任一字段不同：返回 409 `14004`。
  - `POST /api/v1/orders`：
    - 长度 8–64，字符集 `[A-Za-z0-9_-]`；
    - 同一用户 + 同一 key：返回同一订单，不重复锁定库存或清理购物车。
- 幂等判断与业务状态变更在同一数据库事务内完成。
- 幂等最终防线是数据库唯一索引，Redis 不能替代。

## 4. 错误码

| 区间 | 模块 |
| --- | --- |
| 10000–10999 | 通用 |
| 11000–11999 | 用户 / 认证 / RBAC |
| 12000–12999 | 商品 |
| 13000–13999 | 购物车 |
| 14000–14999 | 库存 |
| 15000–15999 | 优惠券 |
| 16000–16999 | 订单 |
| 17000–17999 | 支付 |
| 18000–18999 | 物流 |
| 19000–19999 | 售后 |
| 20000–20999 | 互动 |

### 4.1 购物车错误码

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 13001 | `CART_SKU_NOT_FOUND` | 404 | SKU 不存在 |
| 13002 | `CART_ITEM_NOT_FOUND` | 404 | 购物车条目不存在 |
| 13003 | `CART_QUANTITY_INVALID` | 400 | 数量不合法 |
| 13004 | `CART_QUANTITY_EXCEEDED` | 400 | 超过单件上限 |
| 13005 | `CART_ITEM_LIMIT_EXCEEDED` | 409 | 超过商品种类上限 |
| 13006 | `CART_PRODUCT_UNAVAILABLE` | 409 | 商品不可购买 |
| 13007 | `CART_STORAGE_UNAVAILABLE` | 503 | 购物车存储暂不可用 |
| 13008 | `CART_OPERATION_CONFLICT` | 409 | 并发操作冲突 |
| 13009 | `CART_IDEMPOTENCY_KEY_REQUIRED` | 400 | 缺少或非法幂等键 |
| 13010 | `CART_IDEMPOTENCY_CONFLICT` | 409 | 幂等键被不同请求复用 |

### 4.2 库存错误码

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 14001 | `STOCK_NOT_FOUND` | 404 | 库存记录不存在 |
| 14002 | `STOCK_NOT_INITIALIZED` | 409 | 库存未初始化 |
| 14003 | `STOCK_INSUFFICIENT` | 409 | 库存不足 |
| 14004 | `STOCK_OPERATION_CONFLICT` | 409 | 库存操作冲突 |
| 14005 | `STOCK_QUANTITY_INVALID` | 400 | 库存数量不合法 |
| 14006 | `STOCK_ADJUST_INVALID` | 400 | 库存调整不合法 |
| 14007 | `STOCK_IDEMPOTENCY_KEY_REQUIRED` | 400 | 缺少或非法 `Idempotency-Key` |
| 14008 | `STOCK_SKU_NOT_FOUND` | 404 | SKU 不存在 |
| 14009 | `STOCK_COMMAND_INVALID` | 400 | 库存命令参数不合法 |

### 4.3 订单错误码

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 16001 | `ORDER_IDEMPOTENCY_KEY_REQUIRED` | 400 | 缺少或非法 `Idempotency-Key` |
| 16002 | `ORDER_ADDRESS_NOT_FOUND` | 404 | 收货地址不存在或不属于当前用户 |
| 16003 | `ORDER_CART_EMPTY` | 409 | 没有已勾选的可下单商品 |
| 16004 | `ORDER_PRODUCT_UNAVAILABLE` | 409 | 商品不可购买 |
| 16005 | `ORDER_STATUS_CONFLICT` | 409 | 订单状态不允许操作 |
| 16006 | `ORDER_NOT_FOUND` | 404 | 订单不存在或不属于当前用户 |
| 16007 | `ORDER_COMMAND_INVALID` | 400 | 订单参数不合法 |
| 16008 | `ORDER_OPERATION_CONFLICT` | 409 | 订单并发操作冲突 |

### 4.4 模拟支付错误码

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 17001 | `PAYMENT_STATUS_CONFLICT` | 409 | 订单已取消或状态不允许支付 |
| 17002 | `PAYMENT_RECORD_CONFLICT` | 409 | 支付记录冲突或缺失 |
| 17003 | `PAYMENT_NOT_FOUND` | 404 | 支付记录不存在 |
| 17004 | `PAYMENT_COMMAND_INVALID` | 400 | 支付参数不合法 |
| 17005 | `PAYMENT_EXPIRED` | 409 | 订单已超过支付截止时间 |
| 17006 | `PAYMENT_REFUND_CONFLICT` | 409 | 退款记录冲突或重复 |
| 17007 | `PAYMENT_REFUND_AMOUNT_CONFLICT` | 409 | 退款金额与原支付不一致 |
| 17008 | `PAYMENT_REFUND_NOT_ALLOWED` | 409 | 支付记录不允许退款 |

### 4.5 收藏与评价错误码

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 20001 | `FAVORITE_COMMAND_INVALID` | 400 | 收藏参数不合法 |
| 20002 | `FAVORITE_PRODUCT_UNAVAILABLE` | 409 | 商品不存在或已下架 |
| 20003 | `REVIEW_NOT_ALLOWED` | 409 | 非已完成订单购买用户不能评价 |
| 20004 | `REVIEW_COMMAND_INVALID` | 400 | 评价参数不合法 |
| 20005 | `REVIEW_NOT_FOUND` | 404 | 评价不存在 |

### 4.6 售后错误码

| 错误码 | 枚举 | HTTP | 说明 |
| --- | --- | --- | --- |
| 19001 | `AFTER_SALE_ALREADY_EXISTS` | 409 | 该订单已有有效售后申请 |
| 19002 | `AFTER_SALE_NOT_FOUND` | 404 | 售后单不存在或不属于当前用户 |
| 19003 | `AFTER_SALE_STATUS_CONFLICT` | 409 | 售后状态不允许该操作 |
| 19004 | `AFTER_SALE_NOT_ALLOWED` | 409 | 当前订单不允许申请售后 |
| 19005 | `AFTER_SALE_COMMAND_INVALID` | 400 | 售后参数不合法 |

## 5. 购物车接口

全部要求登录，只操作当前用户数据。

### 5.1 添加购物车

```http
POST /api/v1/cart/items
Idempotency-Key: 8f3c2b1a-...
Content-Type: application/json

{ "skuId": 1001, "quantity": 2 }
```

- 重复添加同一 SKU 合并数量。
- 客户端不能传价格、金额、用户 ID。
- 返回最新购物车。

### 5.2 修改数量

```http
PUT /api/v1/cart/items/{skuId}
Content-Type: application/json

{ "quantity": 5 }
```

数量为绝对值，范围 1–99；条目不存在返回 404 `13002`。

### 5.3 删除条目

```http
DELETE /api/v1/cart/items/{skuId}
```

条目不存在视为成功，返回最新购物车。

### 5.4 勾选/取消勾选

```http
PUT /api/v1/cart/items/{skuId}/selected
Content-Type: application/json

{ "selected": true }
```

### 5.5 全选/取消全选

```http
PUT /api/v1/cart/selected
Content-Type: application/json

{ "selected": true }
```

### 5.6 查询购物车

```http
GET /api/v1/cart
```

响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "skuId": 1001,
        "spuId": 500,
        "skuCode": "SKU-001",
        "spuName": "测试商品",
        "imageUrl": "https://example.com/sku.png",
        "specText": "颜色=黑色 / 尺码=L",
        "price": 88.80,
        "quantity": 2,
        "selected": true,
        "amount": 177.60,
        "available": true,
        "unavailableReason": null
      }
    ],
    "summary": {
      "itemCount": 1,
      "totalQuantity": 2,
      "selectedQuantity": 2,
      "selectedAmount": 177.60,
      "totalAmount": 177.60
    }
  },
  "traceId": "a1b2c3d4",
  "timestamp": "2026-10-31T20:00:00+08:00"
}
```

- 价格来自商品模块当前数据。
- 下架、SKU 删除、分类/品牌禁用时 `available=false`，`unavailableReason` 说明原因，金额为 0。
- 不可购买条目不自动删除。

## 6. 库存管理接口

基础路径 `/api/v1/admin/inventory`，全部使用管理端权限码。详细字段与并发/幂等规则见 [`modules/inventory.md`](modules/inventory.md)。

### 6.1 库存分页查询

```http
GET /api/v1/admin/inventory/stocks?pageNum=1&pageSize=10&skuId=1001
```

权限：`inventory:stock:read`。响应 `PageResult<StockResponse>`。

### 6.2 库存详情

```http
GET /api/v1/admin/inventory/stocks/{skuId}
```

权限：`inventory:stock:read`。库存记录不存在返回 404 `14001`。

### 6.3 调整库存

```http
POST /api/v1/admin/inventory/stocks/adjust
Idempotency-Key: 8f3c2b1a-...
Content-Type: application/json

{ "skuId": 1001, "delta": 10, "remark": "采购入库" }
```

权限：`inventory:stock:adjust`。`delta` 允许正负、不能为 0；调整后可售库存不能为负；不直接修改锁定库存。错误返回：400 `14005/14006/14007`、404 `14008`、409 `14004`。

### 6.4 库存流水分页查询

```http
GET /api/v1/admin/inventory/flows?pageNum=1&pageSize=10&skuId=1001&bizType=ORDER_LOCK&bizNo=ORDER-xxx&refBizNo=ORDER-xxx
```

权限：`inventory:flow:read`。`bizType` 取 `ORDER_LOCK`、`ORDER_DEDUCT`、`ORDER_RELEASE`、`RETURN_RESTORE`、`ADMIN_ADJUST`。响应 `PageResult<StockFlowResponse>`。

库存内部 API `InventoryStockApi`（lock/deduct/release/restore/findStocks）仅供进程内调用，不暴露 HTTP。

## 7. 订单与模拟支付接口

全部要求登录，只操作当前用户数据。详细设计见 [`modules/order.md`](modules/order.md) 与 [`modules/payment.md`](modules/payment.md)。

### 7.1 从购物车下单

```http
POST /api/v1/orders
Idempotency-Key: 8f3c2b1a-...
Content-Type: application/json

{ "addressId": 1001 }
```

- 服务端从已勾选购物车条目读取 SKU 和数量，重新读取商品当前价格。
- 金额由服务端计算，不接受前端价格。
- 同一事务内创建订单/明细、锁定库存、清理购物车。
- 重复 `Idempotency-Key` 返回同一订单。

### 7.2 我的订单

```http
GET /api/v1/orders?pageNum=1&pageSize=10&status=PENDING_PAYMENT
GET /api/v1/orders/{orderNo}
```

只返回当前用户订单；访问他人订单返回 404 `16006`。详情和列表响应包含带 `+08:00` 偏移的 `payDeadline`，前端据此展示剩余支付时间。待支付订单超过 30 分钟会被定时任务自动取消并释放库存，最终状态以后端数据库为准。

### 7.3 取消未支付订单

```http
POST /api/v1/orders/{orderNo}/cancel
```

释放库存并转为 `CANCELLED`；重复取消幂等；已支付订单返回 409 `16005`。

### 7.4 模拟支付成功

```http
POST /api/v1/payments/orders/{orderNo}/pay
```

- 仅待支付订单可支付；支付成功后订单转 `PAID` 并扣减库存。
- 重复支付幂等返回同一支付单，不重复扣库存。
- 已取消订单支付返回 409 `17001`。
- 超过 `payDeadline` 的订单返回 409 `17005`，不会扣减库存，随后由超时任务取消。
- 支付与取消并发时，订单行锁保证只有一个成功。

### 7.5 确认收货

```http
POST /api/v1/orders/{orderNo}/confirm-receipt
```

仅 `SHIPPED` 订单可确认，成功后转为 `COMPLETED` 并记录 `completedAt`。重复确认幂等返回当前订单；未发货或已取消订单返回 409 `16005`。

### 7.6 管理端订单与发货

```http
GET  /api/v1/admin/orders?pageNum=1&pageSize=10&status=PAID&orderNo=ORD
GET  /api/v1/admin/orders/{orderNo}
POST /api/v1/admin/orders/{orderNo}/ship
Content-Type: application/json

{ "shippingCompany": "顺丰速运", "trackingNo": "SF123456789" }
```

- 查询权限 `order:info:read`，发货权限 `order:info:ship`；普通用户访问返回 403。
- 仅 `PAID` 订单可发货，成功后转为 `SHIPPED` 并保存 `shippingCompany`、`trackingNo`、`shippedAt`。
- 相同快递公司和物流单号重复发货幂等返回当前订单；不同物流信息返回 409 `16005`，不覆盖首次发货记录，也不重复修改状态。
- 发货和确认收货不再修改库存；库存已在模拟支付时扣减。
- 订单列表支持分页、订单号模糊搜索和状态筛选，返回用户、金额、收货人与物流摘要；详情额外返回收货地址和商品明细。

## 8. 个人中心、收藏与评价

### 8.1 个人资料

```http
GET /api/v1/users/me
PUT /api/v1/users/me

{ "nickname": "新昵称", "phone": "13800138000", "email": "user@example.com" }
```

复用现有当前用户接口，只允许修改当前登录用户；用户名不可修改。头像仅保留现有字段，前端不提供文件上传。

### 8.2 商品收藏

```http
GET    /api/v1/favorites?pageNum=1&pageSize=10
GET    /api/v1/favorites/status?spuIds=1001,1002
POST   /api/v1/favorites/{spuId}
DELETE /api/v1/favorites/{spuId}
```

- 全部接口只操作当前用户数据，普通用户之间互不可见。
- 重复收藏由 `UNIQUE(user_id, spu_id)` 兜底，接口幂等成功。
- 取消未收藏商品也幂等成功。
- 收藏列表保留已下架/删除商品记录，`available=false` 并返回 `unavailableReason`；删除商品 `status=DELETED`。
- 收藏仅允许上架商品。

### 8.3 商品评价

```http
GET  /api/v1/products/{spuId}/reviews?pageNum=1&pageSize=10
POST /api/v1/reviews

{ "orderNo": "ORD...", "skuId": 1001, "rating": 5, "content": "很好用" }

GET  /api/v1/reviews/mine?pageNum=1&pageSize=10&orderNo=ORD...
```

- 只有订单属于当前用户且状态为 `COMPLETED` 的订单明细可以评价。
- 后端根据订单明细确定用户、商品、SKU 和订单归属，请求不能指定其他用户。
- `rating` 范围 1–5，`content` 可选，最长 1000 字符。
- 同一订单明细由 `UNIQUE(order_item_id)` 防重复；重复提交幂等返回已存在评价。
- 商品评价列表返回平均分、评价数量和公开评价；昵称脱敏后展示，不返回用户名和其他用户信息。

### 8.4 整单售后与模拟退款

```http
POST /api/v1/after-sales
{ "orderNo": "ORD...", "reason": "商品质量问题", "description": "..." }

GET  /api/v1/after-sales?pageNum=1&pageSize=10&status=APPLIED&orderNo=ORD...
GET  /api/v1/after-sales/{afterSaleNo}

GET  /api/v1/admin/after-sales?pageNum=1&pageSize=10&status=APPLIED&afterSaleNo=AS...
GET  /api/v1/admin/after-sales/{afterSaleNo}
POST /api/v1/admin/after-sales/{afterSaleNo}/approve
POST /api/v1/admin/after-sales/{afterSaleNo}/reject
{ "rejectReason": "不符合退货条件" }
POST /api/v1/admin/after-sales/{afterSaleNo}/complete
```

- 用户只能查看自己的售后；管理端接口使用 `aftersale:order:read`、`aftersale:order:review`、`aftersale:order:complete` 权限。
- 仅当前用户 `COMPLETED` 订单可申请；退款金额取原支付单金额，请求不接受金额字段。
- 状态：`APPLIED → APPROVED → COMPLETED`，或 `APPLIED → REJECTED`；拒绝后可重新申请。
- 一个订单最多一个有效售后，数据库 `active_order_no` 生成列唯一约束；一个订单最多一条退款记录。
- `complete` 在同一事务内写入 `payment_refund`、调用 `InventoryStockApi.restore`（bizNo=售后单号，refBizNo=原订单号）并更新售后状态；任一步失败全部回滚。
- 重复完成同一售后幂等；不同售后单不能再次回补同一订单；原订单保持 `COMPLETED`。

## 9. 现有接口摘要

| 模块 | 路径 | 说明 |
| --- | --- | --- |
| 系统 | `GET /api/v1/system/health` | 健康检查 |
| 认证 | `/api/v1/auth/**` | 注册、登录、刷新、登出 |
| 用户 | `/api/v1/users/me`、`/api/v1/users/me/addresses/**` | 当前用户与地址 |
| 管理端用户 | `/api/v1/admin/users/**`、`/roles/**`、`/permissions/**` | 用户、角色、权限 |
| 商品管理 | `/api/v1/admin/product/**` | 分类、品牌、SPU/SKU、规格 |
| 商品查询 | `/api/v1/products/**` | 分类树、列表、详情 |
| 购物车 | `/api/v1/cart/**` | 阶段 3 |
| 库存管理 | `/api/v1/admin/inventory/**` | 库存查询、调整、流水查询（阶段 4） |
| 订单 | `/api/v1/orders/**` | 下单、列表、详情、取消、确认收货（阶段 6/8 MVP） |
| 模拟支付 | `POST /api/v1/payments/orders/{orderNo}/pay` | 模拟支付成功（阶段 7 MVP） |
| 管理端订单 | `/api/v1/admin/orders/**` | 订单查询、详情、发货（阶段 8 简化履约） |
| 个人中心 | `GET/PUT /api/v1/users/me` | 当前用户资料 |
| 收藏 | `/api/v1/favorites/**` | 收藏状态、列表、添加、取消 |
| 评价 | `/api/v1/reviews/**`、`/api/v1/products/{spuId}/reviews` | 提交评价、我的评价、商品公开评价 |
| 售后 | `/api/v1/after-sales/**` | 整单售后申请、列表、详情 |
| 管理端售后 | `/api/v1/admin/after-sales/**` | 售后查询、审核、确认退货退款 |

完整参数与示例以 Swagger UI 为准：`/swagger-ui.html`、`/v3/api-docs`。
