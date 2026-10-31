-- 阶段 2：商品分类、品牌、SPU/SKU、规格及关联表。

CREATE TABLE product_category
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类 ID，0 为根',
    name       VARCHAR(64)  NOT NULL COMMENT '分类名称',
    level      TINYINT      NOT NULL COMMENT '层级：1-3',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    icon_url   VARCHAR(512) DEFAULT NULL COMMENT '图标 URL',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by BIGINT       DEFAULT NULL COMMENT '创建人',
    updated_by BIGINT       DEFAULT NULL COMMENT '更新人',
    deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_category_parent_name (parent_id, name, deleted),
    KEY idx_product_category_parent (parent_id),
    KEY idx_product_category_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品分类';

CREATE TABLE product_brand
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '品牌名称',
    logo_url    VARCHAR(512) DEFAULT NULL COMMENT '品牌 Logo URL',
    description VARCHAR(255) DEFAULT NULL COMMENT '品牌描述',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by  BIGINT       DEFAULT NULL COMMENT '创建人',
    updated_by  BIGINT       DEFAULT NULL COMMENT '更新人',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_brand_name (name, deleted),
    KEY idx_product_brand_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品品牌';

CREATE TABLE product_spu
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT       NOT NULL COMMENT '分类 ID',
    brand_id    BIGINT       DEFAULT NULL COMMENT '品牌 ID',
    name        VARCHAR(128) NOT NULL COMMENT '商品名称',
    subtitle    VARCHAR(255) DEFAULT NULL COMMENT '副标题',
    main_image  VARCHAR(512) DEFAULT NULL COMMENT '主图 URL',
    description TEXT         DEFAULT NULL COMMENT '商品描述',
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ON_SHELF/OFF_SHELF',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by  BIGINT       DEFAULT NULL COMMENT '创建人',
    updated_by  BIGINT       DEFAULT NULL COMMENT '更新人',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_product_spu_category (category_id),
    KEY idx_product_spu_brand (brand_id),
    KEY idx_product_spu_status (status, deleted, sort)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品 SPU';

CREATE TABLE product_spu_image
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    spu_id     BIGINT       NOT NULL COMMENT 'SPU ID',
    image_url  VARCHAR(512) NOT NULL COMMENT '图片 URL',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_product_spu_image_spu (spu_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品 SPU 图片';

CREATE TABLE product_spec
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    spu_id     BIGINT      NOT NULL COMMENT 'SPU ID',
    name       VARCHAR(64) NOT NULL COMMENT '规格名称',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by BIGINT      DEFAULT NULL COMMENT '创建人',
    updated_by BIGINT      DEFAULT NULL COMMENT '更新人',
    deleted    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_spec_spu_name (spu_id, name, deleted),
    KEY idx_product_spec_spu (spu_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品规格';

CREATE TABLE product_spec_value
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    spec_id    BIGINT      NOT NULL COMMENT '规格 ID',
    spec_value VARCHAR(64) NOT NULL COMMENT '规格值',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by BIGINT      DEFAULT NULL COMMENT '创建人',
    updated_by BIGINT      DEFAULT NULL COMMENT '更新人',
    deleted    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_spec_value_spec_value (spec_id, spec_value, deleted),
    KEY idx_product_spec_value_spec (spec_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品规格值';

CREATE TABLE product_sku
(
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    spu_id         BIGINT        NOT NULL COMMENT 'SPU ID',
    sku_code       VARCHAR(64)   NOT NULL COMMENT 'SKU 编码',
    price          DECIMAL(18, 2) NOT NULL COMMENT '销售价',
    market_price   DECIMAL(18, 2) DEFAULT NULL COMMENT '市场价',
    image_url      VARCHAR(512)  DEFAULT NULL COMMENT 'SKU 图片 URL',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    spec_signature VARCHAR(512)  NOT NULL COMMENT '规格值组合签名，用于唯一约束',
    created_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by     BIGINT        DEFAULT NULL COMMENT '创建人',
    updated_by     BIGINT        DEFAULT NULL COMMENT '更新人',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version        INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_code (sku_code),
    UNIQUE KEY uk_product_sku_signature (spu_id, spec_signature),
    KEY idx_product_sku_spu (spu_id),
    KEY idx_product_sku_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品 SKU';

CREATE TABLE product_sku_spec
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id        BIGINT      NOT NULL COMMENT 'SKU ID',
    spec_id       BIGINT      NOT NULL COMMENT '规格 ID',
    spec_value_id BIGINT      NOT NULL COMMENT '规格值 ID',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_spec (sku_id, spec_id),
    KEY idx_product_sku_spec_value (spec_value_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SKU 规格关联';
