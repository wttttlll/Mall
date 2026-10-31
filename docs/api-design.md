# MallAgent API 设计规范

> 适用范围：`/api/v1` 全部接口。本文记录通用约定、购物车接口和错误码；商品接口详见 `docs/product-module.md`。
> 最后更新：2026-10-31（阶段 3）。

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
- 用户端接口默认要求登录；管理端接口使用方法级权限码。
- 权限码格式 `module:resource:action`，例如 `product:spu:create`。
- 401 与 403 不混用；越权访问返回 403。

## 3. 幂等

- 敏感写接口使用 `Idempotency-Key` 请求头。
- 当前阶段 3 仅 `POST /api/v1/cart/items` 强制要求：
  - 长度 8–64，字符集 `[A-Za-z0-9_-]`；
  - 同一用户 + 同一 key + 相同请求体：视为重试，不重复生效；
  - 同一用户 + 同一 key + 不同请求体：返回 409 `13010`。
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

## 6. 现有接口摘要

| 模块 | 路径 | 说明 |
| --- | --- | --- |
| 系统 | `GET /api/v1/system/health` | 健康检查 |
| 认证 | `/api/v1/auth/**` | 注册、登录、刷新、登出 |
| 用户 | `/api/v1/users/me`、`/api/v1/users/me/addresses/**` | 当前用户与地址 |
| 管理端用户 | `/api/v1/admin/users/**`、`/roles/**`、`/permissions/**` | 用户、角色、权限 |
| 商品管理 | `/api/v1/admin/product/**` | 分类、品牌、SPU/SKU、规格 |
| 商品查询 | `/api/v1/products/**` | 分类树、列表、详情 |
| 购物车 | `/api/v1/cart/**` | 阶段 3 |

完整参数与示例以 Swagger UI 为准：`/swagger-ui.html`、`/v3/api-docs`。
