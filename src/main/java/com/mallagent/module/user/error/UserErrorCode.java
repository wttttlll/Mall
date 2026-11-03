package com.mallagent.module.user.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 用户、认证与 RBAC 错误码，使用 11000-11999 区间。
 */
public enum UserErrorCode implements ErrorCode {

    USERNAME_EXISTS(11001, "用户名已存在"),
    PHONE_EXISTS(11002, "手机号已被使用"),
    EMAIL_EXISTS(11003, "邮箱已被使用"),
    USER_NOT_FOUND(11004, "用户不存在"),
    USER_DISABLED(11005, "账号已被禁用"),
    BAD_CREDENTIALS(11006, "用户名或密码错误"),
    LOGIN_LOCKED(11007, "登录失败次数过多，请稍后再试"),
    INVALID_REFRESH_TOKEN(11008, "刷新令牌无效或已过期"),
    ROLE_NOT_FOUND(11009, "角色不存在"),
    ROLE_CODE_EXISTS(11010, "角色编码已存在"),
    ROLE_IN_USE(11011, "角色已被使用，不能删除"),
    BUILT_IN_ROLE(11012, "内置角色不允许该操作"),
    PERMISSION_NOT_FOUND(11013, "权限不存在"),
    ADDRESS_NOT_FOUND(11014, "收货地址不存在"),
    ADDRESS_LIMIT_EXCEEDED(11015, "收货地址数量已达上限"),
    CANNOT_DISABLE_SELF(11016, "不能禁用当前登录账号"),
    LAST_SUPER_ADMIN(11017, "系统至少需要保留一个可用的超级管理员"),
    INVALID_STATUS(11018, "无效的状态值");

    private final int code;
    private final String message;

    UserErrorCode(int code, String message) {
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
