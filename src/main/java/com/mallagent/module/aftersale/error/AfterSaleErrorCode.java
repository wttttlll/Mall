package com.mallagent.module.aftersale.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 售后错误码，使用 19000-19999 区间。
 */
public enum AfterSaleErrorCode implements ErrorCode {

    AFTER_SALE_ALREADY_EXISTS(19001, "该订单已有有效售后申请"),
    AFTER_SALE_NOT_FOUND(19002, "售后单不存在"),
    AFTER_SALE_STATUS_CONFLICT(19003, "售后状态不允许该操作"),
    AFTER_SALE_NOT_ALLOWED(19004, "当前订单不允许申请售后"),
    AFTER_SALE_COMMAND_INVALID(19005, "售后参数不合法");

    private final int code;
    private final String message;

    AfterSaleErrorCode(int code, String message) {
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
