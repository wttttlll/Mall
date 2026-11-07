package com.mallagent.module.aftersale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后列表项。
 */
public record AfterSaleSummaryResponse(
        String afterSaleNo,
        String orderNo,
        Long userId,
        String username,
        String nickname,
        String status,
        BigDecimal refundAmount,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime completedAt
) {
}
