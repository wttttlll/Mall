package com.mallagent.module.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付模块读取的订单视图。status 为 OrderStatus 名称。
 */
public record OrderPaymentView(
        String orderNo,
        Long userId,
        String status,
        BigDecimal totalAmount,
        LocalDateTime payDeadline,
        List<OrderPaymentItem> items
) {

    public OrderPaymentView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
