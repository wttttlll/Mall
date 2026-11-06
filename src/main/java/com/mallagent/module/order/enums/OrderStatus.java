package com.mallagent.module.order.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * 订单状态：待支付 -> 已支付 -> 已发货 -> 已完成；待支付可取消。
 */
public enum OrderStatus {

    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED;

    public static Optional<OrderStatus> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
