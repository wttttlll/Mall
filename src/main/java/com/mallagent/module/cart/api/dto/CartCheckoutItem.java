package com.mallagent.module.cart.api.dto;

/**
 * 下单时需要从购物车读取的条目。
 */
public record CartCheckoutItem(
        Long skuId,
        Integer quantity
) {
}
