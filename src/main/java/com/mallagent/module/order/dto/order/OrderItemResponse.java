package com.mallagent.module.order.dto.order;

import java.math.BigDecimal;

/**
 * 订单明细响应。
 */
public record OrderItemResponse(
        Long skuId,
        Long spuId,
        String skuCode,
        String spuName,
        String imageUrl,
        String specText,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineAmount
) {
}
