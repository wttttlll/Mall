package com.mallagent.module.order.api.dto;

/**
 * 支付时需要扣减的订单 SKU 与数量。
 */
public record OrderPaymentItem(
        Long skuId,
        Integer quantity
) {
}
