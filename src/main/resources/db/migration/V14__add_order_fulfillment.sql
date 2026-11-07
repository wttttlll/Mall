-- 阶段 8（MVP 简化版）：订单发货、确认收货与订单管理权限。
-- 物流仅保存快递公司和运单号，不接真实物流公司；发货和确认收货不再修改库存。

ALTER TABLE order_info
    ADD COLUMN shipping_company VARCHAR(64) DEFAULT NULL COMMENT '快递公司' AFTER cancelled_at,
    ADD COLUMN tracking_no      VARCHAR(64) DEFAULT NULL COMMENT '物流单号' AFTER shipping_company,
    ADD COLUMN shipped_at       DATETIME(3) DEFAULT NULL COMMENT '发货时间' AFTER tracking_no,
    ADD COLUMN completed_at     DATETIME(3) DEFAULT NULL COMMENT '完成时间' AFTER shipped_at,
    DROP CHECK chk_order_info_status,
    ADD CONSTRAINT chk_order_info_status
        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
    ADD KEY idx_order_info_status_id (status, id);

INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, type, path, method, sort, status)
VALUES
    (80, 0, 'order', '订单管理', 1, '/admin/orders', NULL, 30, 1),
    (81, 80, 'order:info', '订单管理', 1, '/admin/orders', NULL, 1, 1),
    (82, 81, 'order:info:read', '查询订单', 3, '/api/v1/admin/orders', 'GET', 1, 1),
    (83, 81, 'order:info:ship', '订单发货', 3, '/api/v1/admin/orders/*/ship', 'POST', 2, 1);

-- 超级管理员拥有订单管理全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code LIKE 'order%'
WHERE r.role_code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 运营默认只读，不能发货
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'order',
    'order:info',
    'order:info:read'
)
WHERE r.role_code = 'OPERATOR'
ON DUPLICATE KEY UPDATE role_id = role_id;
