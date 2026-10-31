package com.mallagent.module.cart.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 添加购物车请求。价格和用户 ID 不接受客户端传入。
 */
public record AddCartItemRequest(
        @NotNull @Positive Long skuId,
        @NotNull @Min(1) @Max(99) Integer quantity
) {
}
