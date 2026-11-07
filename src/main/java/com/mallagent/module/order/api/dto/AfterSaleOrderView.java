package com.mallagent.module.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后模块读取的原订单视图。
 */
public record AfterSaleOrderView(
        String orderNo,
        Long userId,
        String status,
        BigDecimal totalAmount,
        LocalDateTime paidAt,
        LocalDateTime completedAt,
        String receiverName,
        String receiverPhone,
        String receiverProvince,
        String receiverCity,
        String receiverDistrict,
        String receiverDetailAddress,
        List<AfterSaleOrderItem> items
) {

    public AfterSaleOrderView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
