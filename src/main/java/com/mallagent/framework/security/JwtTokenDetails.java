package com.mallagent.framework.security;

import java.time.Instant;

/**
 * 当前请求所携带 Access Token 的元信息。
 *
 * @param jti       令牌 ID
 * @param expiresAt 过期时间
 */
public record JwtTokenDetails(String jti, Instant expiresAt) {
}
