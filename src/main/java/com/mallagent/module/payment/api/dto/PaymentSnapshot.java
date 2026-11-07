package com.mallagent.module.payment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后模块读取的成功支付快照。退款金额必须以此金额为准。
 */
public record PaymentSnapshot(
        String paymentNo,
        String orderNo,
        Long userId,
        BigDecimal amount,
        String status,
        LocalDateTime paidAt
) {
}
