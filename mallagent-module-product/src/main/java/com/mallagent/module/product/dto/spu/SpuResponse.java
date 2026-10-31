package com.mallagent.module.product.dto.spu;

import com.mallagent.module.product.dto.sku.SkuResponse;
import com.mallagent.module.product.dto.spec.SpecResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端商品详情。
 */
public record SpuResponse(
        Long id,
        String name,
        String subtitle,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        String mainImage,
        List<String> images,
        String description,
        String status,
        Integer sort,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<SpecResponse> specs,
        List<SkuResponse> skus
) {
}
