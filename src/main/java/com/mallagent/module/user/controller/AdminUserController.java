package com.mallagent.module.user.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.module.user.dto.user.AssignRolesRequest;
import com.mallagent.module.user.dto.user.ResetPasswordRequest;
import com.mallagent.module.user.dto.user.UpdateStatusRequest;
import com.mallagent.module.user.dto.user.UserResponse;
import com.mallagent.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理。
 */
@Tag(name = "管理端-用户")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "用户分页查询")
    @PreAuthorize("hasAuthority('system:user:read')")
    @GetMapping
    public Result<PageResult<UserResponse>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status) {
        return Result.success(userService.pageUsers(pageNum, pageSize, username, status));
    }

    @Operation(summary = "用户详情")
    @PreAuthorize("hasAuthority('system:user:read')")
    @GetMapping("/{id}")
    public Result<UserResponse> detail(@PathVariable Long id) {
        return Result.success(userService.getUser(id));
    }

    @Operation(summary = "启用或禁用用户")
    @PreAuthorize("hasAuthority('system:user:update')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        userService.updateStatus(id, request.status());
        return Result.success();
    }

    @Operation(summary = "重置用户密码", description = "重置后该用户所有旧令牌失效")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return Result.success();
    }

    @Operation(summary = "分配用户角色")
    @PreAuthorize("hasAuthority('system:user:assign-role')")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id,
                                    @Valid @RequestBody AssignRolesRequest request) {
        userService.assignRoles(id, request);
        return Result.success();
    }
}
