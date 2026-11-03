# MallAgent 数据库设计

> 适用范围：阶段 0–9（MVP）。本文登记表、索引与约束；字段级业务说明见各模块文档。
> 最后更新：2026-11-02（阶段 6/7 MVP 简化版新增订单与模拟支付三表）。

## 1. 通用约定

- MySQL 8，InnoDB，`utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。
- 主键 `BIGINT AUTO_INCREMENT`，MyBatis-Plus `IdType.AUTO`；业务单号单独生成。
- 金额 `DECIMAL(18,2)`，Java 使用 `BigDecimal`；禁止 `FLOAT`/`DOUBLE`。
- 时间 `DATETIME(3)`，应用时区 `Asia/Shanghai`。
- 审计字段：`created_at`、`updated_at`、`created_by`、`updated_by`；需要时增加 `deleted`、`version`。
- 不建物理外键，由应用层保证一致性，用索引和唯一约束兜底。
- 业务唯一性必须落到数据库唯一索引。
- 关联字段、查询字段、排序字段必须有索引依据。

## 2. Flyway 迁移

| 版本 | 文件 | 内容 |
| --- | --- | --- |
| V1 | `V1__baseline.sql` | `sys_config` |
| V2 | `V2__create_user_rbac.sql` | 用户、角色、权限、关联表、收货地址 |
| V3 | `V3__seed_user_rbac.sql` | 内置角色、权限种子数据 |
| V4 | `V4__create_product.sql` | 商品分类、品牌、SPU/SKU、规格及关联表 |
| V5 | `V5__seed_product_permissions.sql` | `product:*` 权限种子 |
| V6 | `V6__fix_product_sku_unique_indexes.sql` | SKU 唯一索引纳入 `deleted` |
| V7 | `V7__create_cart.sql` | 购物车条目、添加幂等记录 |
| V8 | `V8__grant_super_admin_product_root_permission.sql` | 补授 SUPER_ADMIN 的 `product` 根权限 |
| V9 | `V9__fix_product_logical_delete_unique_indexes.sql` | 生成列唯一索引，支持多次删除与同业务键重建 |
| V10 | `V10__create_inventory.sql` | 库存主记录 `stock_sku`、库存流水 `stock_flow` |
| V11 | `V11__seed_inventory_permissions.sql` | `inventory:*` 权限种子及角色授权 |
| V12 | `V12__create_order_payment.sql` | 订单 `order_info`、订单明细 `order_item`、模拟支付单 `payment_order` |

已执行迁移禁止修改；修正错误只能新增版本。

## 3. 表清单

### 3.1 系统与用户

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `sys_config` | 系统配置 | 主键 |
| `sys_user` | 用户 | 用户名、手机号唯一 |
| `sys_role` | 角色 | 角色编码唯一 |
| `sys_permission` | 权限 | 权限码唯一 |
| `sys_user_role` | 用户-角色 | `(user_id, role_id)` 唯一 |
| `sys_role_permission` | 角色-权限 | `(role_id, permission_id)` 唯一 |
| `user_address` | 收货地址 | 用户维度索引；默认地址规则由应用层保证 |

### 3.2 商品

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `product_category` | 分类 | `(parent_id, active_name)` 唯一；`active_name` 为仅未删除记录生成 |
| `product_brand` | 品牌 | `(active_name)` 唯一；状态索引 |
| `product_spu` | SPU | 分类、品牌、`(status, deleted, sort)` 索引 |
| `product_spu_image` | SPU 图集 | `spu_id` 索引 |
| `product_spec` | 规格定义 | `(spu_id, active_name)` 唯一 |
| `product_spec_value` | 规格值 | `(spec_id, active_value)` 唯一 |
| `product_sku` | SKU | `(active_sku_code)` 唯一；`(spu_id, active_spec_signature)` 唯一 |
| `product_sku_spec` | SKU-规格值关联 | `(sku_id, spec_id)` 唯一；规格值索引 |

商品使用逻辑删除；`active_*` 为 STORED 生成列，`deleted = 0` 时取业务键，否则为 `NULL`。MySQL 唯一索引允许多个 `NULL`，因此历史删除记录不冲突，未删除记录仍唯一。详情见 `docs/product-module.md`。

### 3.3 购物车（阶段 3）

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `cart_item` | 购物车条目 | `(user_id, sku_id)` 唯一；`(user_id, created_at)` 索引 |
| `cart_idempotency` | 添加接口幂等记录 | `(user_id, idempotency_key)` 唯一；`created_at` 索引 |

`cart_item` 关键字段：`user_id`、`sku_id`、`quantity`、`selected`、审计字段、`version`。

删除策略：**物理删除**。购物车条目是高频临时数据，删除后重新添加直接插入新行；不使用 `deleted` 列，避免唯一索引与墓碑记录冲突。

`cart_idempotency` 关键字段：`user_id`、`idempotency_key`、`request_hash`、`sku_id`、`quantity`、`claim_token`、`created_at`。`claim_token` 用于区分新请求与重复请求。

### 3.4 库存（阶段 4）

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `stock_sku` | SKU 库存主记录 | `UNIQUE (sku_id)`；`available_stock >= 0`、`locked_stock >= 0` 两个 CHECK |
| `stock_flow` | 不可变库存流水 | `UNIQUE (biz_type, biz_no, sku_id)`；`UNIQUE (admin_biz_no)`；`quantity > 0` |

`stock_sku` 关键字段：`sku_id`、`available_stock`（可售）、`locked_stock`（锁定）、`version`、审计字段。

`stock_flow` 关键字段：`biz_type`、`biz_no`、`ref_biz_no`、`sku_id`、`quantity`、`change_available`、`change_locked`、`available_before/after`、`locked_before/after`、`request_token`、`admin_biz_no`、`remark`、`created_at`、`created_by`。

索引：

- `UNIQUE (biz_type, biz_no, sku_id)`：订单、售后、管理端调整的幂等最终防线。
- `UNIQUE (admin_biz_no)`：`admin_biz_no` 是 `ADMIN_ADJUST` 时取 `biz_no` 的 STORED 生成列，其余业务类型为 `NULL`。
- `KEY (ref_biz_no, sku_id, biz_type)`：售后退货按原订单和 SKU 汇总回补。
- `KEY (sku_id, created_at)`：SKU 维度流水查询。
- `KEY (biz_no)`：按业务单号查询。

`stock_flow` 为只追加流水，不提供更新/删除业务字段的接口；流水占位与 before/after 回填在同一事务内完成。详细规则见 [`modules/inventory.md`](modules/inventory.md)。

### 3.5 订单与模拟支付（阶段 6/7 MVP 简化版）

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `order_info` | 订单主记录 | `UNIQUE(order_no)`；`UNIQUE(user_id, idempotency_key)`；状态 CHECK |
| `order_item` | 订单明细快照 | `UNIQUE(order_id, sku_id)`；数量、单价、行金额 CHECK |
| `payment_order` | 模拟支付单 | `UNIQUE(payment_no)`；`UNIQUE(order_no)`；金额 CHECK |

`order_info` 关键字段：`order_no`、`user_id`、`status`、`total_amount`、`item_count`、收货人快照、`idempotency_key`、`paid_at`、`cancelled_at`、审计字段、`version`。

订单状态：`PENDING_PAYMENT`、`PAID`、`CANCELLED`。MVP 简化版不实现超时关闭、物流和售后。

`order_item` 关键字段：`order_id`、`sku_id`、`spu_id`、`sku_code`、`spu_name`、`image_url`、`spec_text`、`unit_price`、`quantity`、`line_amount`。商品信息为下单时快照，不随后续商品变更而改变。

`payment_order` 关键字段：`payment_no`、`order_no`、`user_id`、`amount`、`channel`（当前固定 `MOCK`）、`status`（当前仅 `SUCCESS`）、`paid_at`。

索引：

- `order_info`：`UK(order_no)`、`UK(user_id, idempotency_key)`、`KEY(user_id, id)`、`KEY(user_id, status)`。
- `order_item`：`UK(order_id, sku_id)`、`KEY(order_id)`、`KEY(sku_id)`。
- `payment_order`：`UK(payment_no)`、`UK(order_no)`、`KEY(user_id)`。

订单状态流转由条件更新和订单行锁保证；模拟支付与取消共用同一订单行锁。详见 [`modules/order.md`](modules/order.md)。

## 4. 索引设计原则

- 唯一索引承载业务唯一性，不能只靠代码先查后插。
- 唯一索引优先包含区分度高的业务键；逻辑删除表使用生成列，仅对未删除记录保持唯一。
- 列表查询索引按 `等值条件 → 范围/排序` 组织，例如购物车 `(user_id, created_at)`。
- 避免无索引大范围扫描；新增查询必须说明索引依据。
- 不建物理外键；跨表一致性由应用层在同一事务内保证。

## 5. 与 Redis 的边界

- MySQL 是订单、支付、库存、优惠券、购物车的权威数据源。
- Redis 只做缓存：商品分类树、商品详情。
- 购物车状态当前不缓存，查询直接读 MySQL。
- 库存余额与库存流水不缓存，全部读 MySQL；库存并发正确性依赖条件更新、唯一约束和行锁。
- 缓存丢失或过期必须能回源 MySQL，不影响数据正确性。
- 幂等判断以 MySQL 唯一索引为准，Redis 不能替代。
