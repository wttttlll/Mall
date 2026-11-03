-- 阶段 6/7（MVP 简化版）：订单、订单明细与模拟支付。
-- 订单和模拟支付与库存共享本地事务；库存流水继续作为库存生命周期锚点。

CREATE TABLE order_info
(
    id                      BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no                VARCHAR(64)    NOT NULL COMMENT '订单号',
    user_id                 BIGINT         NOT NULL COMMENT '用户 ID',
    status                  VARCHAR(32)    NOT NULL COMMENT 'PENDING_PAYMENT/PAID/CANCELLED',
    total_amount            DECIMAL(18, 2) NOT NULL COMMENT '商品总金额',
    item_count              INT            NOT NULL COMMENT '商品条目数',
    receiver_name           VARCHAR(64)    NOT NULL COMMENT '收货人',
    receiver_phone          VARCHAR(32)    NOT NULL COMMENT '收货人手机号',
    receiver_province       VARCHAR(64)    NOT NULL COMMENT '省份',
    receiver_city           VARCHAR(64)    NOT NULL COMMENT '城市',
    receiver_district       VARCHAR(64)    NOT NULL COMMENT '区县',
    receiver_detail_address VARCHAR(255)   NOT NULL COMMENT '详细地址',
    receiver_postal_code    VARCHAR(16)             DEFAULT NULL COMMENT '邮编',
    idempotency_key         VARCHAR(64)    NOT NULL COMMENT '下单幂等键',
    paid_at                 DATETIME(3)             DEFAULT NULL COMMENT '支付时间',
    cancelled_at            DATETIME(3)             DEFAULT NULL COMMENT '取消时间',
    created_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by              BIGINT                  DEFAULT NULL COMMENT '创建人',
    updated_by              BIGINT                  DEFAULT NULL COMMENT '更新人',
    version                 INT            NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_info_order_no (order_no),
    UNIQUE KEY uk_order_info_user_idempotency (user_id, idempotency_key),
    KEY idx_order_info_user_id (user_id, id),
    KEY idx_order_info_user_status (user_id, status),
    CONSTRAINT chk_order_info_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED')),
    CONSTRAINT chk_order_info_total_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_order_info_item_count CHECK (item_count > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单';

CREATE TABLE order_item
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT         NOT NULL COMMENT '订单 ID',
    sku_id      BIGINT         NOT NULL COMMENT 'SKU ID',
    spu_id      BIGINT                  DEFAULT NULL COMMENT 'SPU ID',
    sku_code    VARCHAR(64)             DEFAULT NULL COMMENT 'SKU 编码快照',
    spu_name    VARCHAR(255)            DEFAULT NULL COMMENT '商品名称快照',
    image_url   VARCHAR(512)            DEFAULT NULL COMMENT '商品图片快照',
    spec_text   VARCHAR(512)            DEFAULT NULL COMMENT '规格文本快照',
    unit_price  DECIMAL(18, 2) NOT NULL COMMENT '下单时单价',
    quantity    INT            NOT NULL COMMENT '购买数量',
    line_amount DECIMAL(18, 2) NOT NULL COMMENT '行金额',
    created_at  DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item_order_sku (order_id, sku_id),
    KEY idx_order_item_order (order_id),
    KEY idx_order_item_sku (sku_id),
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_item_line_amount CHECK (line_amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单明细';

CREATE TABLE payment_order
(
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    payment_no VARCHAR(64)    NOT NULL COMMENT '支付单号',
    order_no   VARCHAR(64)    NOT NULL COMMENT '订单号',
    user_id    BIGINT         NOT NULL COMMENT '用户 ID',
    amount     DECIMAL(18, 2) NOT NULL COMMENT '支付金额',
    channel    VARCHAR(16)    NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道，当前仅 MOCK',
    status     VARCHAR(32)    NOT NULL COMMENT 'SUCCESS',
    paid_at    DATETIME(3)    NOT NULL COMMENT '支付成功时间',
    created_at DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_no (payment_no),
    UNIQUE KEY uk_payment_biz_order_no (order_no),
    KEY idx_payment_user (user_id),
    CONSTRAINT chk_payment_amount CHECK (amount >= 0),
    CONSTRAINT chk_payment_status CHECK (status IN ('SUCCESS'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '模拟支付单';
