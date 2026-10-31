package com.mallagent.module.cart.dto.cart;

import java.math.BigDecimal;

/**
 * 购物车条目响应。价格来自商品模块当前数据。
 */
public record CartItemResponse(
        Long skuId,
        Long spuId,
        String skuCode,
        String spuName,
        String imageUrl,
        String specText,
        BigDecimal price,
        Integer quantity,
        Boolean selected,
        BigDecimal amount,
        Boolean available,
        String unavailableReason
) {
}
