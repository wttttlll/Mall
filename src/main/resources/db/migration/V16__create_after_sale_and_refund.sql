-- 阶段 9（MVP 简化版）：整单退货退款、管理员审核、模拟退款与库存回补。
-- 一个订单最多存在一个有效售后（APPLIED/APPROVED/COMPLETED）；REJECTED 后可重新申请。
-- 一个订单最多一条模拟退款记录，库存回补使用售后单号作为 RETURN_RESTORE 的 biz_no。

CREATE TABLE after_sale_order
(
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    after_sale_no VARCHAR(64)    NOT NULL COMMENT '售后单号',
    order_no      VARCHAR(64)    NOT NULL COMMENT '原订单号',
    user_id       BIGINT         NOT NULL COMMENT '申请用户 ID',
    status        VARCHAR(32)    NOT NULL COMMENT 'APPLIED/APPROVED/REJECTED/COMPLETED',
    refund_amount DECIMAL(18, 2) NOT NULL COMMENT '原支付金额快照',
    reason        VARCHAR(255)   NOT NULL COMMENT '退货原因',
    description   VARCHAR(1000)           DEFAULT NULL COMMENT '问题说明',
    reject_reason VARCHAR(255)            DEFAULT NULL COMMENT '拒绝原因',
    reviewed_by   BIGINT                  DEFAULT NULL COMMENT '审核管理员 ID',
    reviewed_at   DATETIME(3)             DEFAULT NULL COMMENT '审核时间',
    completed_by  BIGINT                  DEFAULT NULL COMMENT '完成退款管理员 ID',
    completed_at  DATETIME(3)             DEFAULT NULL COMMENT '退款和库存回补完成时间',
    created_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    updated_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    active_order_no VARCHAR(64)
        GENERATED ALWAYS AS (
            IF(status IN ('APPLIED', 'APPROVED', 'COMPLETED'), order_no, NULL)
        ) STORED COMMENT '仅有效售后占用订单号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sale_no (after_sale_no),
    UNIQUE KEY uk_after_sale_active_order (active_order_no),
    KEY idx_after_sale_user (user_id, id),
    KEY idx_after_sale_status (status, id),
    KEY idx_after_sale_order (order_no),
    CONSTRAINT chk_after_sale_status
        CHECK (status IN ('APPLIED', 'APPROVED', 'REJECTED', 'COMPLETED')),
    CONSTRAINT chk_after_sale_refund_amount CHECK (refund_amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '整单售后申请';

CREATE TABLE payment_refund
(
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    refund_no     VARCHAR(64)    NOT NULL COMMENT '退款单号',
    after_sale_no VARCHAR(64)    NOT NULL COMMENT '售后单号',
    order_no      VARCHAR(64)    NOT NULL COMMENT '原订单号',
    payment_no    VARCHAR(64)    NOT NULL COMMENT '原支付单号',
    user_id       BIGINT         NOT NULL COMMENT '用户 ID',
    amount        DECIMAL(18, 2) NOT NULL COMMENT '退款金额，来自原支付单',
    channel       VARCHAR(16)    NOT NULL DEFAULT 'MOCK' COMMENT '退款渠道，当前仅 MOCK',
    status        VARCHAR(32)    NOT NULL COMMENT 'SUCCESS',
    refunded_at   DATETIME(3)    NOT NULL COMMENT '模拟退款成功时间',
    created_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_refund_no (refund_no),
    UNIQUE KEY uk_payment_refund_after_sale (after_sale_no),
    UNIQUE KEY uk_payment_refund_order (order_no),
    KEY idx_payment_refund_user (user_id),
    CONSTRAINT chk_payment_refund_amount CHECK (amount >= 0),
    CONSTRAINT chk_payment_refund_status CHECK (status IN ('SUCCESS'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '模拟退款单';

INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, type, path, method, sort, status)
VALUES
    (90, 0, 'aftersale', '售后管理', 1, '/admin/after-sales', NULL, 40, 1),
    (91, 90, 'aftersale:order', '售后处理', 1, '/admin/after-sales', NULL, 1, 1),
    (92, 91, 'aftersale:order:read', '查询售后', 3, '/api/v1/admin/after-sales', 'GET', 1, 1),
    (93, 91, 'aftersale:order:review', '审核售后', 3, '/api/v1/admin/after-sales/*', 'POST', 2, 1),
    (94, 91, 'aftersale:order:complete', '确认退货退款', 3, '/api/v1/admin/after-sales/*/complete', 'POST', 3, 1);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code LIKE 'aftersale%'
WHERE r.role_code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE role_id = role_id;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'aftersale',
    'aftersale:order',
    'aftersale:order:read'
)
WHERE r.role_code = 'OPERATOR'
ON DUPLICATE KEY UPDATE role_id = role_id;
