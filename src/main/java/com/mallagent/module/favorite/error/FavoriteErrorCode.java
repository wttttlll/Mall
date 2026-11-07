package com.mallagent.module.favorite.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 收藏错误码，使用 20000-20999 增量区间。
 */
public enum FavoriteErrorCode implements ErrorCode {

    FAVORITE_COMMAND_INVALID(20001, "收藏参数不合法"),
    FAVORITE_PRODUCT_UNAVAILABLE(20002, "商品不存在或已下架");

    private final int code;
    private final String message;

    FavoriteErrorCode(int code, String message) {
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
