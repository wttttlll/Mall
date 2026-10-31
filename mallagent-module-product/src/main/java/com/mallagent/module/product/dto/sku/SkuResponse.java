package com.mallagent.module.product.dto.sku;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU 信息。
 */
public record SkuResponse(
        Long id,
        String skuCode,
        BigDecimal price,
        BigDecimal marketPrice,
        String imageUrl,
        Integer status,
        List<SkuSpecValueResponse> specValues
) {
}
