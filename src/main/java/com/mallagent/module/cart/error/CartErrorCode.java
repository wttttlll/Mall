package com.mallagent.module.cart.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 购物车模块错误码，使用 13000-13999 区间。
 */
public enum CartErrorCode implements ErrorCode {

    CART_SKU_NOT_FOUND(13001, "SKU 不存在"),
    CART_ITEM_NOT_FOUND(13002, "购物车条目不存在"),
    CART_QUANTITY_INVALID(13003, "购买数量不合法"),
    CART_QUANTITY_EXCEEDED(13004, "购买数量超过单件上限"),
    CART_ITEM_LIMIT_EXCEEDED(13005, "购物车商品种类超过上限"),
    CART_PRODUCT_UNAVAILABLE(13006, "商品当前不可购买"),
    CART_STORAGE_UNAVAILABLE(13007, "购物车存储暂不可用"),
    CART_OPERATION_CONFLICT(13008, "购物车操作冲突，请重试"),
    CART_IDEMPOTENCY_KEY_REQUIRED(13009, "缺少或非法的 Idempotency-Key"),
    CART_IDEMPOTENCY_CONFLICT(13010, "Idempotency-Key 已被不同请求使用");

    private final int code;
    private final String message;

    CartErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
