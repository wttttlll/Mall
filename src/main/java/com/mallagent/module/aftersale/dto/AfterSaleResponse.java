package com.mallagent.module.aftersale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后详情，包含原订单、退款、库存回补和收货信息。
 */
public record AfterSaleResponse(
        String afterSaleNo,
        String orderNo,
        Long userId,
        String username,
        String nickname,
        String status,
        BigDecimal refundAmount,
        String reason,
        String description,
        String rejectReason,
        String refundStatus,
        String restoreStatus,
        String refundNo,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime completedAt,
        LocalDateTime refundedAt,
        String orderStatus,
        BigDecimal orderTotalAmount,
        LocalDateTime orderPaidAt,
        LocalDateTime orderCompletedAt,
        String receiverName,
        String receiverPhone,
        String receiverProvince,
        String receiverCity,
        String receiverDistrict,
        String receiverDetailAddress,
        List<AfterSaleItemResponse> items
) {

    public AfterSaleResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
