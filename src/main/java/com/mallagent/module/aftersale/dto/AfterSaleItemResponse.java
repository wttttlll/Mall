package com.mallagent.module.aftersale.dto;

import java.math.BigDecimal;

/**
 * 售后详情中的原订单商品明细。
 */
public record AfterSaleItemResponse(
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
