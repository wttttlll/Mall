package com.mallagent.common.error;

/**
 * 业务错误码契约。
 */
public interface ErrorCode {

    /**
     * 业务码。
     */
    int code();

    /**
     * 默认提示信息。
     */
    String message();
}
