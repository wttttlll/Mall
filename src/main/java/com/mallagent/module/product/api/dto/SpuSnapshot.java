package com.mallagent.module.product.api.dto;

/**
 * 跨模块使用的商品 SPU 简要快照，不包含商品模块内部结构。
 */
public record SpuSnapshot(
        Long id,
        String name,
        String subtitle,
        String mainImage,
        String status
) {
}
