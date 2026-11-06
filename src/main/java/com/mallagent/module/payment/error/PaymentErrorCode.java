package com.mallagent.module.payment.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 模拟支付错误码，使用 17000-17999 区间。
 */
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_STATUS_CONFLICT(17001, "订单状态不允许支付"),
    PAYMENT_RECORD_CONFLICT(17002, "支付记录冲突"),
    PAYMENT_NOT_FOUND(17003, "支付记录不存在"),
    PAYMENT_COMMAND_INVALID(17004, "支付参数不合法"),
    PAYMENT_EXPIRED(17005, "订单已超时，无法支付"),
    PAYMENT_REFUND_CONFLICT(17006, "退款记录冲突"),
    PAYMENT_REFUND_AMOUNT_CONFLICT(17007, "退款金额与原支付不一致"),
    PAYMENT_REFUND_NOT_ALLOWED(17008, "当前支付记录不能退款");

    private final int code;
    private final String message;

    PaymentErrorCode(int code, String message) {
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
