package com.mallagent.module.cart.dto.cart;

import jakarta.validation.constraints.NotNull;

/**
 * 勾选或取消勾选请求。
 */
public record UpdateSelectedRequest(
        @NotNull Boolean selected
) {
}
