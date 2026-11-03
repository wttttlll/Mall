package com.mallagent.module.order.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的订单列表项。
 */
public record OrderSummaryResponse(
        String orderNo,
        String status,
        BigDecimal totalAmount,
        Integer itemCount,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt
) {
}
