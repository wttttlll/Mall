package com.mallagent.module.order.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 订单详情响应。
 */
public record OrderResponse(
        String orderNo,
        String status,
        BigDecimal totalAmount,
        Integer itemCount,
        String receiverName,
        String receiverPhone,
        String receiverProvince,
        String receiverCity,
        String receiverDistrict,
        String receiverDetailAddress,
        String receiverPostalCode,
        List<OrderItemResponse> items,
        LocalDateTime createdAt,
        OffsetDateTime payDeadline,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        String shippingCompany,
        String trackingNo,
        LocalDateTime shippedAt,
        LocalDateTime completedAt
) {

    public OrderResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
