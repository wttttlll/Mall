package com.mallagent.module.product.dto.sku;

/**
 * SKU 对应的规格值。
 */
public record SkuSpecValueResponse(
        Long specId,
        String specName,
        Long specValueId,
        String specValue
) {
}
