package com.mallagent.module.payment.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 模拟支付错误码，使用 17000-17999 区间。
 */
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_STATUS_CONFLICT(17001, "订单状态不允许支付"),
    PAYMENT_RECORD_CONFLICT(17002, "支付记录冲突"),
    PAYMENT_NOT_FOUND(17003, "支付记录不存在"),
    PAYMENT_COMMAND_INVALID(17004, "支付参数不合法");

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
