package com.mallagent.module.inventory.dto.admin;

import java.time.LocalDateTime;

/**
 * 管理端库存响应。
 */
public record StockResponse(
        Long skuId,
        String skuCode,
        String spuName,
        Integer availableStock,
        Integer lockedStock,
        Integer totalStock,
        Integer version,
        LocalDateTime updatedAt
) {
}
