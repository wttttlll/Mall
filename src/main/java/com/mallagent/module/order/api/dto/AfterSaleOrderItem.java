package com.mallagent.module.order.api.dto;

import java.math.BigDecimal;

/**
 * 售后模块使用的订单明细快照。
 */
public record AfterSaleOrderItem(
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
