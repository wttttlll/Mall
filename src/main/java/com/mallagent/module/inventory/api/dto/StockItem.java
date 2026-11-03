package com.mallagent.module.inventory.api.dto;

/**
 * 单个 SKU 的库存操作项。
 */
public record StockItem(
        Long skuId,
        Integer quantity
) {
}
