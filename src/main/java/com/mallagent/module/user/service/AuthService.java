package com.mallagent.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.JwtTokenDetails;
import com.mallagent.framework.security.JwtTokenProvider;
import com.mallagent.framework.security.TokenBlacklistService;
import com.mallagent.module.user.config.AuthProperties;
import com.mallagent.module.user.dto.auth.LoginRequest;
import com.mallagent.module.user.dto.auth.RefreshTokenRequest;
import com.mallagent.module.user.dto.auth.RegisterRequest;
import com.mallagent.module.user.dto.auth.TokenResponse;
import com.mallagent.module.user.entity.SysRole;
import com.mallagent.module.user.entity.SysUser;
import com.mallagent.module.user.entity.SysUserRole;
import com.mallagent.module.user.error.UserErrorCode;
import com.mallagent.module.user.mapper.SysRoleMapper;
import com.mallagent.module.user.mapper.SysUserMapper;
import com.mallagent.module.user.mapper.SysUserRoleMapper;
import com.mallagent.module.user.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 注册、登录、刷新与登出。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "mallagent:auth:refresh:";
    private static final String LOGIN_FAIL_KEY_PREFIX = "mallagent:auth:login:fail:";
    private static final String DEFAULT_ROLE_CODE = "CUSTOMER";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService blacklistService;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String phone = normalize(request.phone());
        String email = normalize(request.email());

        ensureUnique(username, phone, email);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(normalize(request.nickname()));
        user.setPhone(phone);
        user.setEmail(email);
        user.setStatus(1);
        user.setTokenVersion(0);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "用户名、手机号或邮箱已存在");
        }

        SysRole customerRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, DEFAULT_ROLE_CODE)
                .eq(SysRole::getStatus, 1));
        if (customerRole == null) {
            throw new BusinessException(UserErrorCode.ROLE_NOT_FOUND);
        }

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(customerRole.getId());
        userRoleMapper.insert(userRole);

        return issueTokens(user.getId(), user.getUsername(), user.getTokenVersion());
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String username = request.username().trim();
        ensureNotLocked(username);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            clearFailures(username);

            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, principal.getUserId())
                    .set(SysUser::getLastLoginAt, LocalDateTime.now()));

            return issueTokens(principal.getUserId(), principal.getUsername(), principal.getTokenVersion());
        } catch (DisabledException ex) {
            throw new BusinessException(UserErrorCode.USER_DISABLED);
        } catch (AuthenticationException ex) {
            recordFailure(username);
            throw new BusinessException(UserErrorCode.BAD_CREDENTIALS);
        }
    }

    public TokenResponse refresh(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = tokenProvider.parse(request.refreshToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!JwtTokenProvider.TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        String jti = claims.getId();
        String storedUserId = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + jti);
        if (storedUserId == null || !storedUserId.equals(String.valueOf(userId))) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 轮换：旧 Refresh Token 只能使用一次
        redisTemplate.delete(REFRESH_KEY_PREFIX + jti);

        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        Integer tokenVersion = claims.get(JwtTokenProvider.CLAIM_TOKEN_VERSION, Integer.class);
        if (tokenVersion == null || tokenVersion != currentVersion) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        return issueTokens(user.getId(), user.getUsername(), currentVersion);
    }

    public void logout(Long userId, JwtTokenDetails tokenDetails, String refreshToken) {
        if (tokenDetails != null) {
            Duration remaining = Duration.between(Instant.now(), tokenDetails.expiresAt());
            blacklistService.blacklist(tokenDetails.jti(), remaining);
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Claims claims = tokenProvider.parse(refreshToken);
            if (JwtTokenProvider.TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))
                    && String.valueOf(userId).equals(claims.getSubject())) {
                redisTemplate.delete(REFRESH_KEY_PREFIX + claims.getId());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // 登出保持幂等：非法 Refresh Token 不影响 Access Token 撤销
        }
    }

    private TokenResponse issueTokens(Long userId, String username, int tokenVersion) {
        String accessJti = JwtTokenProvider.newJti();
        String refreshJti = JwtTokenProvider.newJti();

        String accessToken = tokenProvider.createAccessToken(userId, username, tokenVersion, accessJti);
        String refreshToken = tokenProvider.createRefreshToken(userId, username, tokenVersion, refreshJti);

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + refreshJti,
                String.valueOf(userId),
                tokenProvider.getRefreshTokenTtl()
        );

        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                tokenProvider.getAccessTokenTtl().toSeconds(),
                tokenProvider.getRefreshTokenTtl().toSeconds()
        );
    }

    private void ensureUnique(String username, String phone, String email) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException(UserErrorCode.USERNAME_EXISTS);
        }
        if (phone != null && userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)) > 0) {
            throw new BusinessException(UserErrorCode.PHONE_EXISTS);
        }
        if (email != null && userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)) > 0) {
            throw new BusinessException(UserErrorCode.EMAIL_EXISTS);
        }
    }

    private void ensureNotLocked(String username) {
        String value = redisTemplate.opsForValue().get(LOGIN_FAIL_KEY_PREFIX + username);
        if (value == null) {
            return;
        }
        try {
            if (Long.parseLong(value) >= authProperties.getMaxFailures()) {
                throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
            }
        } catch (NumberFormatException ex) {
            redisTemplate.delete(LOGIN_FAIL_KEY_PREFIX + username);
        }
    }

    private void recordFailure(String username) {
        String key = LOGIN_FAIL_KEY_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, authProperties.getLockDuration());
        }
    }

    private void clearFailures(String username) {
        redisTemplate.delete(LOGIN_FAIL_KEY_PREFIX + username);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
