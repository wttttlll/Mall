-- 阶段 2 修正：逻辑删除后允许重新使用 SKU 编码与规格组合。
-- 唯一索引加入 deleted，保证仅未删除记录唯一；已删除记录不阻塞重建。

ALTER TABLE product_sku
    DROP INDEX uk_product_sku_code,
    ADD UNIQUE KEY uk_product_sku_code (sku_code, deleted);

ALTER TABLE product_sku
    DROP INDEX uk_product_sku_signature,
    ADD UNIQUE KEY uk_product_sku_signature (spu_id, spec_signature, deleted);
