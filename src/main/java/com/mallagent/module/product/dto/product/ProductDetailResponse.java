package com.mallagent.module.product.dto.product;

import com.mallagent.module.product.dto.sku.SkuResponse;
import com.mallagent.module.product.dto.spec.SpecResponse;

import java.util.List;

/**
 * 用户端商品详情。
 */
public record ProductDetailResponse(
        Long id,
        String name,
        String subtitle,
        String description,
        String mainImage,
        List<String> images,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        List<SpecResponse> specs,
        List<SkuResponse> skus
) {
}
