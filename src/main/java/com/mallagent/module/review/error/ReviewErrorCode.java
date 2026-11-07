package com.mallagent.module.review.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 评价错误码，使用 20000-20999 增量区间。
 */
public enum ReviewErrorCode implements ErrorCode {

    REVIEW_NOT_ALLOWED(20003, "仅已完成订单的购买用户可以评价"),
    REVIEW_COMMAND_INVALID(20004, "评价参数不合法"),
    REVIEW_NOT_FOUND(20005, "评价不存在");

    private final int code;
    private final String message;

    ReviewErrorCode(int code, String message) {
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
