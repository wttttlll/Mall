package com.mallagent.framework.exception;

import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.error.ErrorCode;
import com.mallagent.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：将异常转换为统一响应结构，且不向客户端暴露堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("Business exception: code={}, message={}", errorCode.code(), ex.getMessage());
        return ResponseEntity.status(httpStatusOf(errorCode.code()))
                .body(Result.error(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR, message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR, message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.error(CommonErrorCode.BAD_REQUEST, "请求参数格式错误"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(CommonErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(CommonErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("Duplicate key: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(CommonErrorCode.CONFLICT, "数据已存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(CommonErrorCode.INTERNAL_ERROR));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private HttpStatus httpStatusOf(int code) {
        return switch (code) {
            case 10000, 10005 -> HttpStatus.BAD_REQUEST;
            case 10001 -> HttpStatus.UNAUTHORIZED;
            case 10002 -> HttpStatus.FORBIDDEN;
            case 10003 -> HttpStatus.NOT_FOUND;
            case 10004 -> HttpStatus.CONFLICT;
            case 10006 -> HttpStatus.METHOD_NOT_ALLOWED;
            case 10503 -> HttpStatus.SERVICE_UNAVAILABLE;
            // 用户 / 认证 / RBAC
            case 11001, 11002, 11003, 11010, 11011, 11012, 11017 -> HttpStatus.CONFLICT;
            case 11004, 11009, 11013, 11014 -> HttpStatus.NOT_FOUND;
            case 11005 -> HttpStatus.FORBIDDEN;
            case 11006, 11008 -> HttpStatus.UNAUTHORIZED;
            case 11007 -> HttpStatus.TOO_MANY_REQUESTS;
            case 11015, 11016, 11018 -> HttpStatus.BAD_REQUEST;
            // 商品
            case 12001, 12006, 12009, 12012, 12013, 12017, 12019 -> HttpStatus.NOT_FOUND;
            case 12002, 12004, 12005, 12007, 12008, 12010, 12011, 12014, 12016,
                 12018, 12020, 12021, 12022, 12023, 12024, 12025, 12028, 12029 -> HttpStatus.CONFLICT;
            case 12003, 12015, 12026, 12027, 12030, 12031 -> HttpStatus.BAD_REQUEST;
            // 购物车
            case 13001, 13002 -> HttpStatus.NOT_FOUND;
            case 13003, 13004, 13009 -> HttpStatus.BAD_REQUEST;
            case 13005, 13006, 13008, 13010 -> HttpStatus.CONFLICT;
            case 13007 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
