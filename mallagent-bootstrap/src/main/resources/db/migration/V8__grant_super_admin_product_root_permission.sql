-- 阶段 2 修正：SUPER_ADMIN 补授 product 根权限。
-- 使用角色编码和权限编码定位，不依赖固定 ID。

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'product'
WHERE r.role_code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE role_id = role_id;
