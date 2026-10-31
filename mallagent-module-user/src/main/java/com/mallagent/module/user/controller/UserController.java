package com.mallagent.module.user.controller;

import com.mallagent.common.api.Result;
import com.mallagent.module.user.dto.user.CurrentUserResponse;
import com.mallagent.module.user.dto.user.UpdateProfileRequest;
import com.mallagent.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户接口。
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "当前用户信息")
    @GetMapping
    public Result<CurrentUserResponse> me() {
        return Result.success(userService.currentUser());
    }

    @Operation(summary = "修改个人资料")
    @PutMapping
    public Result<CurrentUserResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(request));
    }
}
