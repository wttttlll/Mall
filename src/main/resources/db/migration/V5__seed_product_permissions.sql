-- 阶段 2：商品模块权限，延续 product:* 命名空间。

INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, type, path, method, sort, status)
VALUES
    (20, 0, 'product', '商品管理', 1, '/admin/product', NULL, 10, 1),

    (21, 20, 'product:category', '分类管理', 1, '/admin/product/categories', NULL, 1, 1),
    (22, 21, 'product:category:read', '查询分类', 3, '/api/v1/admin/product/categories', 'GET', 1, 1),
    (23, 21, 'product:category:create', '创建分类', 3, '/api/v1/admin/product/categories', 'POST', 2, 1),
    (24, 21, 'product:category:update', '修改分类', 3, '/api/v1/admin/product/categories/*', 'PUT', 3, 1),
    (25, 21, 'product:category:delete', '删除分类', 3, '/api/v1/admin/product/categories/*', 'DELETE', 4, 1),

    (30, 20, 'product:brand', '品牌管理', 1, '/admin/product/brands', NULL, 2, 1),
    (31, 30, 'product:brand:read', '查询品牌', 3, '/api/v1/admin/product/brands', 'GET', 1, 1),
    (32, 30, 'product:brand:create', '创建品牌', 3, '/api/v1/admin/product/brands', 'POST', 2, 1),
    (33, 30, 'product:brand:update', '修改品牌', 3, '/api/v1/admin/product/brands/*', 'PUT', 3, 1),
    (34, 30, 'product:brand:delete', '删除品牌', 3, '/api/v1/admin/product/brands/*', 'DELETE', 4, 1),

    (40, 20, 'product:spu', '商品管理', 1, '/admin/product/spus', NULL, 3, 1),
    (41, 40, 'product:spu:read', '查询商品', 3, '/api/v1/admin/product/spus', 'GET', 1, 1),
    (42, 40, 'product:spu:create', '创建商品', 3, '/api/v1/admin/product/spus', 'POST', 2, 1),
    (43, 40, 'product:spu:update', '修改商品', 3, '/api/v1/admin/product/spus/*', 'PUT', 3, 1),
    (44, 40, 'product:spu:delete', '删除商品', 3, '/api/v1/admin/product/spus/*', 'DELETE', 4, 1),
    (45, 40, 'product:spu:on-shelf', '商品上架', 3, '/api/v1/admin/product/spus/*/on-shelf', 'PUT', 5, 1),
    (46, 40, 'product:spu:off-shelf', '商品下架', 3, '/api/v1/admin/product/spus/*/off-shelf', 'PUT', 6, 1),

    (50, 40, 'product:sku', 'SKU 管理', 1, '/admin/product/skus', NULL, 1, 1),
    (51, 50, 'product:sku:read', '查询 SKU', 3, '/api/v1/admin/product/spus/*/skus', 'GET', 1, 1),
    (52, 50, 'product:sku:create', '创建 SKU', 3, '/api/v1/admin/product/spus/*/skus', 'POST', 2, 1),
    (53, 50, 'product:sku:update', '修改 SKU', 3, '/api/v1/admin/product/spus/*/skus/*', 'PUT', 3, 1),
    (54, 50, 'product:sku:delete', '删除 SKU', 3, '/api/v1/admin/product/spus/*/skus/*', 'DELETE', 4, 1),

    (60, 40, 'product:spec', '规格管理', 1, '/admin/product/specs', NULL, 2, 1),
    (61, 60, 'product:spec:read', '查询规格', 3, '/api/v1/admin/product/spus/*/specs', 'GET', 1, 1),
    (62, 60, 'product:spec:create', '创建规格', 3, '/api/v1/admin/product/spus/*/specs', 'POST', 2, 1),
    (63, 60, 'product:spec:update', '修改规格', 3, '/api/v1/admin/product/spus/*/specs/*', 'PUT', 3, 1),
    (64, 60, 'product:spec:delete', '删除规格', 3, '/api/v1/admin/product/spus/*/specs/*', 'DELETE', 4, 1);

-- 超级管理员获得全部商品权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE permission_code LIKE 'product:%';

-- 运营默认只读
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id
FROM sys_permission
WHERE permission_code IN (
    'product',
    'product:category',
    'product:category:read',
    'product:brand',
    'product:brand:read',
    'product:spu',
    'product:spu:read',
    'product:sku',
    'product:sku:read',
    'product:spec',
    'product:spec:read'
);
