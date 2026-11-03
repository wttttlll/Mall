package com.mallagent.framework.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置。
 */
@Data
@ConfigurationProperties(prefix = "mallagent.jwt")
public class JwtProperties {

    /**
     * 签名密钥，至少 32 字节，生产环境必须通过 JWT_SECRET 注入。
     */
    private String secret;

    private String issuer = "mallagent";

    private Duration accessTokenTtl = Duration.ofMinutes(30);

    private Duration refreshTokenTtl = Duration.ofDays(7);
}
