package com.mallagent.module.cart.dto.cart;

import java.util.List;

/**
 * 购物车查询与写操作响应。
 */
public record CartResponse(
        List<CartItemResponse> items,
        CartSummaryResponse summary
) {

    public CartResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
