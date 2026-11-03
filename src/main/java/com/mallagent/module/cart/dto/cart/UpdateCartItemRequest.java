package com.mallagent.module.cart.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 修改购物车数量请求，数量为绝对值。
 */
public record UpdateCartItemRequest(
        @NotNull @Min(1) @Max(99) Integer quantity
) {
}
