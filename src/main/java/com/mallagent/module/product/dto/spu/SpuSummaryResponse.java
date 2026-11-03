package com.mallagent.module.product.dto.spu;

import java.time.LocalDateTime;

/**
 * 管理端商品列表项。
 */
public record SpuSummaryResponse(
        Long id,
        String name,
        String subtitle,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        String mainImage,
        String status,
        Integer sort,
        LocalDateTime updatedAt
) {
}
