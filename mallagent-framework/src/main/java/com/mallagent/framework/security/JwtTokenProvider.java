package com.mallagent.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。Access Token 与 Refresh Token 通过 type 声明区分。
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TOKEN_VERSION = "ver";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        if (properties.getSecret() == null
                || properties.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("mallagent.jwt.secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username, int tokenVersion, String jti) {
        return createToken(userId, username, tokenVersion, jti, TYPE_ACCESS, properties.getAccessTokenTtl());
    }

    public String createRefreshToken(Long userId, String username, int tokenVersion, String jti) {
        return createToken(userId, username, tokenVersion, jti, TYPE_REFRESH, properties.getRefreshTokenTtl());
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Duration getAccessTokenTtl() {
        return properties.getAccessTokenTtl();
    }

    public Duration getRefreshTokenTtl() {
        return properties.getRefreshTokenTtl();
    }

    public static String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String createToken(Long userId,
                               String username,
                               int tokenVersion,
                               String jti,
                               String type,
                               Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .id(jti)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
