-- 阶段 4：库存主记录与库存流水。
-- 库存以 stock_sku 为当前状态，stock_flow 为不可变流水；
-- 订单/售后通过 stock_flow 的 ORDER_LOCK 行作为订单 + SKU 的生命周期锚点。

CREATE TABLE stock_sku
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id          BIGINT      NOT NULL COMMENT 'SKU ID',
    available_stock INT         NOT NULL DEFAULT 0 COMMENT '可售库存',
    locked_stock    INT         NOT NULL DEFAULT 0 COMMENT '锁定库存',
    version         INT         NOT NULL DEFAULT 0 COMMENT '版本号，每次库存变更递增',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by      BIGINT               DEFAULT NULL COMMENT '创建人',
    updated_by      BIGINT               DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_sku_sku_id (sku_id),
    CONSTRAINT chk_stock_sku_available_non_negative CHECK (available_stock >= 0),
    CONSTRAINT chk_stock_sku_locked_non_negative CHECK (locked_stock >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SKU 库存主记录';

CREATE TABLE stock_flow
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type          VARCHAR(32) NOT NULL COMMENT 'ORDER_LOCK/ORDER_DEDUCT/ORDER_RELEASE/RETURN_RESTORE/ADMIN_ADJUST',
    biz_no            VARCHAR(64) NOT NULL COMMENT '订单号、售后单号或管理端幂等键',
    ref_biz_no        VARCHAR(64)          DEFAULT NULL COMMENT '关联业务单号；退货回补时存订单号',
    sku_id            BIGINT      NOT NULL COMMENT 'SKU ID',
    quantity          INT         NOT NULL COMMENT '本次操作数量，正数',
    change_available  INT         NOT NULL COMMENT '可售库存变化，带符号',
    change_locked     INT         NOT NULL COMMENT '锁定库存变化，带符号',
    available_before  INT                  DEFAULT NULL COMMENT '可售库存变更前值，事务内占位阶段可为空',
    available_after   INT                  DEFAULT NULL COMMENT '可售库存变更后值，提交前必须回填',
    locked_before     INT                  DEFAULT NULL COMMENT '锁定库存变更前值，事务内占位阶段可为空',
    locked_after      INT                  DEFAULT NULL COMMENT '锁定库存变更后值，提交前必须回填',
    request_token     CHAR(36)    NOT NULL COMMENT '每次库存操作尝试生成的唯一令牌，仅用于辅助识别',
    admin_biz_no      VARCHAR(64)
        GENERATED ALWAYS AS (IF(biz_type = 'ADMIN_ADJUST', biz_no, NULL)) STORED
        COMMENT '管理端调整幂等键，仅 ADMIN_ADJUST 生成，唯一约束覆盖全部管理端调整',
    remark            VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by        BIGINT               DEFAULT NULL COMMENT '操作人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_flow_biz (biz_type, biz_no, sku_id),
    UNIQUE KEY uk_stock_flow_admin_biz_no (admin_biz_no),
    KEY idx_stock_flow_ref (ref_biz_no, sku_id, biz_type),
    KEY idx_stock_flow_sku_created (sku_id, created_at),
    KEY idx_stock_flow_biz_no (biz_no),
    CONSTRAINT chk_stock_flow_quantity_positive CHECK (quantity > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存流水';
