package com.mallagent.module.order.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * 订单状态。MVP 简化为待支付、已支付、已取消三态。
 */
public enum OrderStatus {

    PENDING_PAYMENT,
    PAID,
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
