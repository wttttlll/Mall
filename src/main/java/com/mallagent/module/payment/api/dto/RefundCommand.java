package com.mallagent.module.payment.api.dto;

import java.math.BigDecimal;

/**
 * 模拟退款命令。金额由售后模块从原支付快照带入，不能由客户端指定。
 */
public record RefundCommand(
        String afterSaleNo,
        String orderNo,
        Long userId,
        BigDecimal amount
) {
}
