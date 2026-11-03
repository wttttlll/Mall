package com.mallagent.module.product.dto.brand;

/**
 * 品牌信息。
 */
public record BrandResponse(
        Long id,
        String name,
        String logoUrl,
        String description,
        Integer status,
        Integer sort
) {
}
