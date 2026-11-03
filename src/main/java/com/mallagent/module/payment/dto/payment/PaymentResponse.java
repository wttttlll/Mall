package com.mallagent.module.payment.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟支付结果。
 */
public record PaymentResponse(
        String paymentNo,
        String orderNo,
        BigDecimal amount,
        String channel,
        String status,
        LocalDateTime paidAt
) {
}
