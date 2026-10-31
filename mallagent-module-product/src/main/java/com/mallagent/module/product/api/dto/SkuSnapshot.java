package com.mallagent.module.product.api.dto;

import java.math.BigDecimal;

/**
 * 跨模块使用的 SKU 只读快照。
 *
 * <p>价格始终来自商品模块的当前数据，调用方不得缓存为权威价格。</p>
 */
public record SkuSnapshot(
        Long skuId,
        Long spuId,
        String skuCode,
        String spuName,
        String imageUrl,
        BigDecimal price,
        String specText,
        SkuAvailability availability
) {

    public boolean purchasable() {
        return availability == SkuAvailability.AVAILABLE;
    }
}
