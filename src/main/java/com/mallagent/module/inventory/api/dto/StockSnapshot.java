package com.mallagent.module.inventory.api.dto;

/**
 * 库存只读快照。库存记录不存在时 initialized=false，库存数量为 0。
 */
public record StockSnapshot(
        Long skuId,
        Integer availableStock,
        Integer lockedStock,
        Boolean initialized
) {
}
