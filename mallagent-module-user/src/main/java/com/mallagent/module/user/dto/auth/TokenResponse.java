package com.mallagent.module.user.dto.auth;

/**
 * 令牌响应。
 *
 * @param expiresIn      Access Token 有效期（秒）
 * @param refreshExpiresIn Refresh Token 有效期（秒）
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn
) {
}
