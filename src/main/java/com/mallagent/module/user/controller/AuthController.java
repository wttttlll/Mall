package com.mallagent.module.user.controller;

import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.user.dto.auth.LoginRequest;
import com.mallagent.module.user.dto.auth.LogoutRequest;
import com.mallagent.module.user.dto.auth.RefreshTokenRequest;
import com.mallagent.module.user.dto.auth.RegisterRequest;
import com.mallagent.module.user.dto.auth.TokenResponse;
import com.mallagent.module.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册、登录、刷新与登出接口。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册", description = "注册成功后直接返回令牌")
    @PostMapping("/register")
    public ResponseEntity<Result<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(authService.register(request)));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "刷新令牌", description = "Refresh Token 一次性使用，刷新后旧令牌失效")
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request));
    }

    @Operation(summary = "登出", description = "撤销当前 Access Token，可选撤销 Refresh Token")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        authService.logout(
                userId,
                SecurityUtils.currentTokenDetails().orElse(null),
                request == null ? null : request.refreshToken()
        );
        return Result.success();
    }
}
