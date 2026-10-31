package com.mallagent.module.product.dto.product;

import java.math.BigDecimal;

/**
 * 用户端商品列表项。
 */
public record ProductListItemResponse(
        Long id,
        String name,
        String subtitle,
        String mainImage,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
