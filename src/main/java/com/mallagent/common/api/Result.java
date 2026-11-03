package com.mallagent.common.api;

import com.mallagent.common.error.ErrorCode;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

/**
 * 统一响应结构。
 *
 * @param code      业务码，0 表示成功
 * @param message   提示信息
 * @param data      业务数据
 * @param traceId   请求链路 ID
 * @param timestamp 响应时间
 */
public record Result<T>(
        int code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MESSAGE = "ok";
    private static final String TRACE_ID_KEY = "traceId";

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, currentTraceId(), OffsetDateTime.now());
    }

    public static Result<Void> success() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null, currentTraceId(), OffsetDateTime.now());
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.message());
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null, currentTraceId(), OffsetDateTime.now());
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, currentTraceId(), OffsetDateTime.now());
    }

    private static String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }
}
