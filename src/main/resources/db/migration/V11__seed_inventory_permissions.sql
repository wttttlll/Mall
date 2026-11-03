-- 阶段 4：库存模块权限。
-- 权限码沿用 module:resource:action；库存根权限单独授予 SUPER_ADMIN。
-- 使用角色编码和权限编码定位授权，不依赖固定角色 ID。

INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, type, path, method, sort, status)
VALUES
    (70, 0, 'inventory', '库存管理', 1, '/admin/inventory', NULL, 20, 1),

    (71, 70, 'inventory:stock', '库存查询与调整', 1, '/admin/inventory/stocks', NULL, 1, 1),
    (72, 71, 'inventory:stock:read', '查询库存', 3, '/api/v1/admin/inventory/stocks', 'GET', 1, 1),
    (73, 71, 'inventory:stock:adjust', '调整库存', 3, '/api/v1/admin/inventory/stocks/adjust', 'POST', 2, 1),

    (74, 70, 'inventory:flow', '库存流水', 1, '/admin/inventory/flows', NULL, 2, 1),
    (75, 74, 'inventory:flow:read', '查询库存流水', 3, '/api/v1/admin/inventory/flows', 'GET', 1, 1);

-- 超级管理员获得全部库存权限，包括 inventory 根权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code LIKE 'inventory%'
WHERE r.role_code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 运营默认只读，不授予 inventory:stock:adjust
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'inventory',
    'inventory:stock',
    'inventory:stock:read',
    'inventory:flow',
    'inventory:flow:read'
)
WHERE r.role_code = 'OPERATOR'
ON DUPLICATE KEY UPDATE role_id = role_id;
