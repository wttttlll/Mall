-- 阶段 2 修正：支持多次“创建 -> 删除 -> 同业务键重建 -> 再次删除”。
-- 使用 STORED 生成列：仅 deleted = 0 的记录生成业务键，deleted = 1 的记录生成 NULL。
-- MySQL 唯一索引允许多个 NULL，因此历史删除记录不再互相冲突，同时保留删除数据。

ALTER TABLE product_category
    ADD COLUMN active_name VARCHAR(64)
        GENERATED ALWAYS AS (IF(deleted = 0, name, NULL)) STORED COMMENT '仅未删除记录的分类名',
    DROP INDEX uk_product_category_parent_name,
    ADD UNIQUE KEY uk_product_category_active_parent_name (parent_id, active_name);

ALTER TABLE product_brand
    ADD COLUMN active_name VARCHAR(64)
        GENERATED ALWAYS AS (IF(deleted = 0, name, NULL)) STORED COMMENT '仅未删除记录的品牌名',
    DROP INDEX uk_product_brand_name,
    ADD UNIQUE KEY uk_product_brand_active_name (active_name);

ALTER TABLE product_spec
    ADD COLUMN active_name VARCHAR(64)
        GENERATED ALWAYS AS (IF(deleted = 0, name, NULL)) STORED COMMENT '仅未删除记录的规格名',
    DROP INDEX uk_product_spec_spu_name,
    ADD UNIQUE KEY uk_product_spec_active_spu_name (spu_id, active_name);

ALTER TABLE product_spec_value
    ADD COLUMN active_value VARCHAR(64)
        GENERATED ALWAYS AS (IF(deleted = 0, spec_value, NULL)) STORED COMMENT '仅未删除记录的规格值',
    DROP INDEX uk_product_spec_value_spec_value,
    ADD UNIQUE KEY uk_product_spec_value_active (spec_id, active_value);

ALTER TABLE product_sku
    ADD COLUMN active_sku_code VARCHAR(64)
        GENERATED ALWAYS AS (IF(deleted = 0, sku_code, NULL)) STORED COMMENT '仅未删除记录的 SKU 编码',
    ADD COLUMN active_spec_signature VARCHAR(512)
        GENERATED ALWAYS AS (IF(deleted = 0, spec_signature, NULL)) STORED COMMENT '仅未删除记录的规格组合签名',
    DROP INDEX uk_product_sku_code,
    DROP INDEX uk_product_sku_signature,
    ADD UNIQUE KEY uk_product_sku_active_code (active_sku_code),
    ADD UNIQUE KEY uk_product_sku_active_signature (spu_id, active_spec_signature);
