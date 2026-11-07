package com.mallagent.module.order.dto.admin;

import com.mallagent.module.order.dto.order.OrderItemResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理端订单详情。
 */
public record AdminOrderResponse(
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
        String receiverProvince,
        String receiverCity,
        String receiverDistrict,
        String receiverDetailAddress,
        String receiverPostalCode,
        String shippingCompany,
        String trackingNo,
        LocalDateTime shippedAt,
        LocalDateTime completedAt,
        List<OrderItemResponse> items,
        LocalDateTime createdAt,
        OffsetDateTime payDeadline,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt
) {

    public AdminOrderResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
