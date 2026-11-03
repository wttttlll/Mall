package com.mallagent.module.cart.dto.cart;

import java.math.BigDecimal;

/**
 * 购物车汇总。
 *
 * @param itemCount        SKU 种类数，包含不可购买条目
 * @param totalQuantity    所有条目数量之和，包含不可购买条目
 * @param selectedQuantity 已选且可购买的数量之和
 * @param selectedAmount   已选且可购买的金额
 * @param totalAmount      所有可购买条目的金额
 */
public record CartSummaryResponse(
        int itemCount,
        int totalQuantity,
        int selectedQuantity,
        BigDecimal selectedAmount,
        BigDecimal totalAmount
) {
}
