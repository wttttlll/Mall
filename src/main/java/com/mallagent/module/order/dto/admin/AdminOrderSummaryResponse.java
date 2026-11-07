package com.mallagent.module.order.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端订单列表项。
 */
public record AdminOrderSummaryResponse(
        String orderNo,
        Long userId,
        String username,
        String nickname,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        Integer itemCount,
        String receiverName,
        String receiverPhone,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        String shippingCompany,
        String trackingNo,
        LocalDateTime shippedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt
) {
}
