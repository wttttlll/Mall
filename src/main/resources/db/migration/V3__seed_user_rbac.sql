-- 阶段 1：内置角色与权限种子数据。
-- 管理员账号不在这里创建，由 AdminAccountInitializer 使用环境变量密码初始化。

INSERT INTO sys_role (id, role_code, role_name, description, status, sort)
VALUES (1, 'SUPER_ADMIN', '超级管理员', '拥有系统全部权限', 1, 1),
       (2, 'OPERATOR', '运营', '拥有基础查询权限', 1, 2),
       (3, 'CUSTOMER', '普通用户', '前台注册用户默认角色', 1, 3);

INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, type, path, method, sort, status)
VALUES (1, 0, 'system:user', '用户管理', 1, '/admin/users', NULL, 1, 1),
       (2, 1, 'system:user:read', '查询用户', 3, '/api/v1/admin/users', 'GET', 1, 1),
       (3, 1, 'system:user:update', '修改用户状态', 3, '/api/v1/admin/users/*/status', 'PUT', 2, 1),
       (4, 1, 'system:user:assign-role', '分配用户角色', 3, '/api/v1/admin/users/*/roles', 'PUT', 3, 1),
       (5, 1, 'system:user:reset-password', '重置用户密码', 3, '/api/v1/admin/users/*/password', 'PUT', 4, 1),

       (6, 0, 'system:role', '角色管理', 1, '/admin/roles', NULL, 2, 1),
       (7, 6, 'system:role:read', '查询角色', 3, '/api/v1/admin/roles', 'GET', 1, 1),
       (8, 6, 'system:role:create', '创建角色', 3, '/api/v1/admin/roles', 'POST', 2, 1),
       (9, 6, 'system:role:update', '修改角色', 3, '/api/v1/admin/roles/*', 'PUT', 3, 1),
       (10, 6, 'system:role:delete', '删除角色', 3, '/api/v1/admin/roles/*', 'DELETE', 4, 1),
       (11, 6, 'system:role:assign-permission', '分配角色权限', 3, '/api/v1/admin/roles/*/permissions', 'PUT', 5, 1),

       (12, 0, 'system:permission', '权限管理', 1, '/admin/permissions', NULL, 3, 1),
       (13, 12, 'system:permission:read', '查询权限', 3, '/api/v1/admin/permissions/tree', 'GET', 1, 1);

-- 超级管理员拥有全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 运营默认只读
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id
FROM sys_permission
WHERE permission_code IN (
    'system:user',
    'system:user:read',
    'system:role',
    'system:role:read',
    'system:permission',
    'system:permission:read'
);
