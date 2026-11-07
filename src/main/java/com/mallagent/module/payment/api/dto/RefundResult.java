package com.mallagent.module.payment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟退款结果。
 */
public record RefundResult(
        String refundNo,
        String afterSaleNo,
        String orderNo,
        BigDecimal amount,
        String channel,
        String status,
        LocalDateTime refundedAt
) {
}
