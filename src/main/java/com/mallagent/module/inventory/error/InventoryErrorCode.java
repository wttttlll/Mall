package com.mallagent.module.inventory.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 库存模块错误码，使用 14000-14999 区间。
 */
public enum InventoryErrorCode implements ErrorCode {

    STOCK_NOT_FOUND(14001, "库存记录不存在"),
    STOCK_NOT_INITIALIZED(14002, "库存未初始化"),
    STOCK_INSUFFICIENT(14003, "库存不足"),
    STOCK_OPERATION_CONFLICT(14004, "库存操作冲突"),
    STOCK_QUANTITY_INVALID(14005, "库存数量不合法"),
    STOCK_ADJUST_INVALID(14006, "库存调整不合法"),
    STOCK_IDEMPOTENCY_KEY_REQUIRED(14007, "缺少或非法的 Idempotency-Key"),
    STOCK_SKU_NOT_FOUND(14008, "SKU 不存在"),
    STOCK_COMMAND_INVALID(14009, "库存命令不合法");

    private final int code;
    private final String message;

    InventoryErrorCode(int code, String message) {
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
