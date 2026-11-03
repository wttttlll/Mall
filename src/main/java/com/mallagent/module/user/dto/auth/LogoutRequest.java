package com.mallagent.module.user.dto.auth;

/**
 * 登出请求。refreshToken 可选，传入后会同时撤销 Refresh Token。
 */
public record LogoutRequest(String refreshToken) {
}
