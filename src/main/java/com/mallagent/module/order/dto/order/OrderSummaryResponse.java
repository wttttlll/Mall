package com.mallagent.module.order.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 我的订单列表项。
 */
public record OrderSummaryResponse(
        String orderNo,
        String status,
        BigDecimal totalAmount,
        Integer itemCount,
        LocalDateTime createdAt,
        OffsetDateTime payDeadline,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt
) {
}
