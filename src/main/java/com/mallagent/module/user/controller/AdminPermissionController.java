package com.mallagent.module.user.controller;

import com.mallagent.common.api.Result;
import com.mallagent.module.user.dto.permission.PermissionResponse;
import com.mallagent.module.user.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端权限查询。
 */
@Tag(name = "管理端-权限")
@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "权限树")
    @PreAuthorize("hasAuthority('system:permission:read')")
    @GetMapping("/tree")
    public Result<List<PermissionResponse>> tree() {
        return Result.success(permissionService.tree());
    }
}
