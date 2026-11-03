package com.mallagent.common.error;

/**
 * 通用错误码，使用 10000-10999 区间。
 */
public enum CommonErrorCode implements ErrorCode {

    BAD_REQUEST(10000, "请求参数错误"),
    UNAUTHORIZED(10001, "未认证或登录已过期"),
    FORBIDDEN(10002, "无访问权限"),
    NOT_FOUND(10003, "资源不存在"),
    CONFLICT(10004, "数据冲突"),
    VALIDATION_ERROR(10005, "参数校验失败"),
    METHOD_NOT_ALLOWED(10006, "请求方法不支持"),
    INTERNAL_ERROR(10500, "系统内部错误"),
    SERVICE_UNAVAILABLE(10503, "服务暂不可用");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
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
