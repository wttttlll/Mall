-- 阶段 10（增量）：商品收藏与商品评价。
-- 收藏按用户 + SPU 唯一；评价按订单明细唯一，均由数据库唯一约束兜底。

CREATE TABLE user_favorite
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL COMMENT '用户 ID',
    spu_id     BIGINT      NOT NULL COMMENT '商品 SPU ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_favorite_user_spu (user_id, spu_id),
    KEY idx_user_favorite_user (user_id, id),
    KEY idx_user_favorite_spu (spu_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户商品收藏';

CREATE TABLE product_review
(
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT        NOT NULL COMMENT '评价用户 ID',
    order_no      VARCHAR(64)   NOT NULL COMMENT '订单号',
    order_item_id BIGINT        NOT NULL COMMENT '订单明细 ID',
    spu_id        BIGINT        NOT NULL COMMENT '商品 SPU ID',
    sku_id        BIGINT        NOT NULL COMMENT 'SKU ID',
    spu_name      VARCHAR(255)           DEFAULT NULL COMMENT '商品名称快照',
    sku_code      VARCHAR(64)            DEFAULT NULL COMMENT 'SKU 编码快照',
    image_url     VARCHAR(512)           DEFAULT NULL COMMENT '商品图片快照',
    rating        TINYINT       NOT NULL COMMENT '评分 1-5',
    content       VARCHAR(1000)          DEFAULT NULL COMMENT '文字评价',
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_review_order_item (order_item_id),
    KEY idx_product_review_spu (spu_id, id),
    KEY idx_product_review_user (user_id, id),
    KEY idx_product_review_order (user_id, order_no),
    CONSTRAINT chk_product_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品评价';
