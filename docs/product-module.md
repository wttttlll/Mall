# MallAgent 商品模块设计（阶段 2）

> 状态：已通过验收（2026-10-31）；阶段 3 起新增只读跨模块契约 `ProductSkuQueryApi`。复核发现的规格完整性与逻辑删除循环问题已修复（错误码 12032、Flyway V9），详见 `docs/development-plan.md` 6.5 与第 10 节。
> 范围：分类、品牌、SPU/SKU、规格、商品状态机、管理端/用户端查询、Redis 缓存。
> 不包含：库存预占、购物车、订单、支付、物流、售后、优惠券、互动。

## 1. 设计约束

- Java 21、Spring Boot 3、MyBatis-Plus、MySQL 8、Redis、Flyway。
- 金额使用 `BigDecimal`，数据库 `DECIMAL(18,2)`，禁止浮点数。
- 商品分类最多 3 级，父分类创建后不可修改。
- 规格关系使用关联表，不使用 JSON 存储核心规格关系。
- 用户端搜索使用 MySQL `LIKE`，不引入 Elasticsearch。
- 商品图片只保存 URL，不实现文件上传。
- 商品分页最大 `pageSize = 100`。
- 权限统一使用 `product:*` 命名空间，管理端逐接口校验。

## 2. 数据模型

| 表 | 用途 | 关键索引 / 约束 |
| --- | --- | --- |
| `product_category` | 商品分类 | `UNIQUE(parent_id, active_name)`；父分类、状态索引 |
| `product_brand` | 商品品牌 | `UNIQUE(active_name)`；状态索引 |
| `product_spu` | 商品 SPU | 分类、品牌、`(status, deleted, sort)` 索引 |
| `product_spu_image` | SPU 图集 | `spu_id` 索引 |
| `product_spec` | SPU 规格定义 | `UNIQUE(spu_id, active_name)` |
| `product_spec_value` | 规格值 | `UNIQUE(spec_id, active_value)` |
| `product_sku` | SKU | `UNIQUE(active_sku_code)`；`UNIQUE(spu_id, active_spec_signature)`；SPU、状态索引 |
| `product_sku_spec` | SKU 与规格值关联 | `UNIQUE(sku_id, spec_id)`；规格值索引 |

关键规则：

- 分类 `level` 由服务端计算，最多 3 级。
- 同一 SPU 下，SKU 必须覆盖全部规格且每个规格恰好一个值。
- SKU 会生成 `spec_signature`（按规格 ID 排序的 `specId:valueId` 串），由数据库唯一约束防重复。
- 主键 `BIGINT AUTO_INCREMENT`。
- 商品、SKU、规格、规格值、分类、品牌使用 `deleted` 逻辑删除。
- `active_*` 为 STORED 生成列：`deleted = 0` 时取业务键，否则为 `NULL`；唯一索引只约束未删除记录，支持多次删除/重建。
- 图集和 SKU 规格关系在更新时物理替换；商品删除时物理清理。

## 3. 商品状态机

| 当前状态 | 允许流转 | 说明 |
| --- | --- | --- |
| `DRAFT` | `ON_SHELF` | 上架前校验主图、分类/品牌、至少一个启用 SKU |
| `ON_SHELF` | `OFF_SHELF` | 上架状态不允许结构性修改 |
| `OFF_SHELF` | `ON_SHELF`、`DRAFT` | 可重新上架，或转草稿修改规格结构 |
| 其他流转 | 拒绝 | 返回 `12011` |

状态与删除的区别：

- **下架（`OFF_SHELF`）**：记录仍存在，管理端可见、可编辑、可重新上架；用户端不可见。
- **删除（逻辑删除）**：`deleted = 1`，所有查询默认排除；本阶段不提供恢复接口。子 SKU/规格/规格值一并逻辑删除，图集与 SKU 规格关系物理清理。
- 上架商品不能直接删除，必须先下架。

编辑限制：

- SPU 基础信息与图片：`DRAFT` / `OFF_SHELF`。
- 规格结构：`DRAFT`。
- SKU 新增/修改/删除：`DRAFT` / `OFF_SHELF`。
- 上架商品修改基础信息或规格/SKU：返回 `12010` / `12029`。
- 删除：仅 `DRAFT` / `OFF_SHELF`，否则返回 `12028`。

## 4. 用户端可见性

商品必须同时满足：

1. SPU 状态为 `ON_SHELF`；
2. 所属分类未删除且状态启用；
3. 品牌为空，或品牌未删除且状态启用；
4. 至少存在一个未删除且启用的 SKU。

用户端分类树只返回启用分类。普通用户仍需登录后访问商品接口。

## 5. API 清单

### 管理端分类

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/admin/product/categories` | `product:category:read` |
| GET | `/api/v1/admin/product/categories/{id}` | `product:category:read` |
| POST | `/api/v1/admin/product/categories` | `product:category:create` |
| PUT | `/api/v1/admin/product/categories/{id}` | `product:category:update` |
| DELETE | `/api/v1/admin/product/categories/{id}` | `product:category:delete` |

### 管理端品牌

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/admin/product/brands` | `product:brand:read` |
| GET | `/api/v1/admin/product/brands/{id}` | `product:brand:read` |
| POST | `/api/v1/admin/product/brands` | `product:brand:create` |
| PUT | `/api/v1/admin/product/brands/{id}` | `product:brand:update` |
| DELETE | `/api/v1/admin/product/brands/{id}` | `product:brand:delete` |

### 管理端 SPU

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/admin/product/spus` | `product:spu:read` |
| GET | `/api/v1/admin/product/spus/{id}` | `product:spu:read` |
| POST | `/api/v1/admin/product/spus` | `product:spu:create` |
| PUT | `/api/v1/admin/product/spus/{id}` | `product:spu:update` |
| DELETE | `/api/v1/admin/product/spus/{id}` | `product:spu:delete` |
| PUT | `/api/v1/admin/product/spus/{id}/on-shelf` | `product:spu:on-shelf` |
| PUT | `/api/v1/admin/product/spus/{id}/off-shelf` | `product:spu:off-shelf` |
| PUT | `/api/v1/admin/product/spus/{id}/draft` | `product:spu:update` |

### 管理端规格

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/admin/product/spus/{spuId}/specs` | `product:spec:read` |
| POST | `/api/v1/admin/product/spus/{spuId}/specs` | `product:spec:create` |
| PUT | `/api/v1/admin/product/spus/{spuId}/specs/{specId}` | `product:spec:update` |
| DELETE | `/api/v1/admin/product/spus/{spuId}/specs/{specId}` | `product:spec:delete` |
| POST | `/api/v1/admin/product/spus/{spuId}/specs/{specId}/values` | `product:spec:create` |
| PUT | `/api/v1/admin/product/spus/{spuId}/specs/{specId}/values/{valueId}` | `product:spec:update` |
| DELETE | `/api/v1/admin/product/spus/{spuId}/specs/{specId}/values/{valueId}` | `product:spec:delete` |

### 管理端 SKU

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| GET | `/api/v1/admin/product/spus/{spuId}/skus` | `product:sku:read` |
| POST | `/api/v1/admin/product/spus/{spuId}/skus` | `product:sku:create` |
| PUT | `/api/v1/admin/product/spus/{spuId}/skus/{skuId}` | `product:sku:update` |
| DELETE | `/api/v1/admin/product/spus/{spuId}/skus/{skuId}` | `product:sku:delete` |

### 用户端

| 方法 | 路径 | 认证 |
| --- | --- | --- |
| GET | `/api/v1/products/categories/tree` | 登录用户 |
| GET | `/api/v1/products` | 登录用户 |
| GET | `/api/v1/products/{id}` | 登录用户 |

商品分页参数：`pageNum`、`pageSize`（最大 100）、`categoryId`、`brandId`、`keyword`。

## 6. Redis 缓存策略

| 缓存 | Key | TTL |
| --- | --- | --- |
| 用户端分类树 | `mallagent:product:category:tree:v1` | 1 小时（可配置） |
| 用户端商品详情 | `mallagent:product:spu:detail:v1:{spuId}` | 30 分钟（可配置） |

配置项：

```yaml
mallagent:
  product:
    cache:
      category-tree-ttl: 1h
      spu-detail-ttl: 30m
```

更新策略：

- 写数据库成功后删除缓存，删除动作在事务提交后执行。
- 分类新增/修改/删除：删除分类树缓存；分类修改还会删除该分类下 SPU 的详情缓存。
- 品牌修改：删除该品牌下 SPU 的详情缓存。
- SPU 基础信息、图片、状态变化：删除该 SPU 详情缓存。
- 规格、规格值、SKU 变化：删除所属 SPU 详情缓存。
- 管理端查询不走缓存，保证看到最新数据。
- 缓存读写失败时降级查库（fail-open），不阻断商品查询。
- 测试环境使用 Redis database 1，与开发库 database 0 隔离。

## 7. 事务、幂等与并发

- SPU 创建/修改/删除、图片替换在本地事务内完成。
- 规格与规格值修改在本地事务内完成。
- SKU 创建/修改/删除在本地事务内完成，SKU 与规格值关联同时写入。
- 分类/品牌删除先校验子分类、商品引用。
- SKU 编码、规格组合、SKU 规格关系由数据库唯一约束兜底；重复写入返回明确冲突。
- 并发编辑使用 `version` 乐观锁（SPU、SKU、分类、品牌）。
- 商品状态流转使用当前状态校验，非法流转返回 409。
- 未使用分布式锁；缓存失效失败由 TTL 兜底。

## 8. 测试

执行：

```bash
mvn clean verify
```

阶段 2 交付时共 25 个测试，其中商品模块新增 11 个，覆盖：

- 分类 3 级限制、重名、子分类删除约束；
- 品牌 CRUD、重名、商品引用删除约束；
- 分类树缓存 TTL 与提交后失效；
- SPU/SKU/规格创建、价格 `BigDecimal`、规格组合去重、SKU 编码唯一；
- 上架/下架/转草稿状态机与非法流转；
- 上架条件校验（主图、启用 SKU）；
- 用户端可见性、分类/品牌禁用隐藏、详情缓存失效；
- `product:*` 权限校验与分页上限 100；
- 真实 HTTP 链路（中文分类/品牌/商品/规格、价格 88.80、上下架、逻辑删除）已单独验证。

## 9. 已知限制

- 搜索为 MySQL `LIKE`，没有分词、相关度排序和搜索引擎能力。
- 不提供图片上传，仅接受 URL。
- 分类父节点不可变更，避免递归改层级。
- 规格结构只允许在 `DRAFT` 修改；上架商品需先下架。
- 逻辑删除使用生成列唯一索引；历史删除数据保留，支持多次删除/重建。
- 上架校验要求每个启用 SKU 覆盖 SPU 全部规格、每个规格恰好一个值且值属于对应规格；不满足返回 12032。
- 无库存字段；库存预占与扣减属于阶段 4。
- 商品删除不提供恢复接口。

## 10. 跨模块只读契约（阶段 3）

购物车需要读取 SKU 当前信息，商品模块新增只读接口，不修改现有业务规则：

```text
com.mallagent.module.product.api.ProductSkuQueryApi
com.mallagent.module.product.api.dto.SkuSnapshot
com.mallagent.module.product.api.dto.SkuAvailability
```

- `findSku(skuId)`：单个 SKU 快照。
- `findSkus(skuIds)`：批量快照，缺失的 SKU 不返回。
- 快照字段：skuId、spuId、skuCode、spuName、imageUrl、price、specText、availability。
- `availability` 综合 SKU 状态、SPU 上下架、分类与品牌状态；购物车据此判断是否可添加、是否展示为不可购买。
- 价格始终来自 `product_sku.price`，调用方不得把快照价格当作长期权威价格。
- 实现类 `ProductSkuQueryApiImpl` 只使用现有 Mapper 做只读查询，不改变商品状态机、可见性或删除规则。
