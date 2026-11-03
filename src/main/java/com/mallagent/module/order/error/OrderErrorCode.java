package com.mallagent.module.order.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 订单模块错误码，使用 16000-16999 区间。
 */
public enum OrderErrorCode implements ErrorCode {

    ORDER_IDEMPOTENCY_KEY_REQUIRED(16001, "缺少或非法的 Idempotency-Key"),
    ORDER_ADDRESS_NOT_FOUND(16002, "收货地址不存在"),
    ORDER_CART_EMPTY(16003, "没有已勾选的可下单商品"),
    ORDER_PRODUCT_UNAVAILABLE(16004, "商品当前不可购买"),
    ORDER_STATUS_CONFLICT(16005, "订单状态不允许该操作"),
    ORDER_NOT_FOUND(16006, "订单不存在"),
    ORDER_COMMAND_INVALID(16007, "订单参数不合法"),
    ORDER_OPERATION_CONFLICT(16008, "订单操作冲突，请重试");

    private final int code;
    private final String message;

    OrderErrorCode(int code, String message) {
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
