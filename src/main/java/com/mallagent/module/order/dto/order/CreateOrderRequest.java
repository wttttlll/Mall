package com.mallagent.module.order.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 从购物车提交订单。金额由服务端根据商品当前价格重新计算。
 */
public record CreateOrderRequest(

        @NotNull(message = "收货地址不能为空")
        @Positive(message = "收货地址 ID 必须为正数")
        Long addressId
) {
}
