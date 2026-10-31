-- 阶段 3：购物车条目与添加接口幂等记录。
-- 购物车条目使用物理删除：条目是高频临时数据，删除后重新添加直接插入新行。

CREATE TABLE cart_item
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL COMMENT '用户 ID',
    sku_id     BIGINT      NOT NULL COMMENT 'SKU ID',
    quantity   INT         NOT NULL COMMENT '购买数量',
    selected   TINYINT     NOT NULL DEFAULT 1 COMMENT '1 勾选，0 未勾选',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by BIGINT               DEFAULT NULL COMMENT '创建人',
    updated_by BIGINT               DEFAULT NULL COMMENT '更新人',
    version    INT         NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item_user_sku (user_id, sku_id),
    KEY idx_cart_item_user_created (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '购物车条目';

CREATE TABLE cart_idempotency
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT      NOT NULL COMMENT '用户 ID',
    idempotency_key VARCHAR(64) NOT NULL COMMENT '客户端幂等键',
    request_hash    CHAR(64)    NOT NULL COMMENT '请求内容 SHA-256',
    sku_id          BIGINT      NOT NULL COMMENT '本次请求 SKU ID',
    quantity        INT         NOT NULL COMMENT '本次请求数量',
    claim_token     CHAR(36)    NOT NULL COMMENT '本次插入生成的令牌，用于区分新请求与重复请求',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_idem_user_key (user_id, idempotency_key),
    KEY idx_cart_idem_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '购物车添加幂等记录';
