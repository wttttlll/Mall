package com.mallagent.module.user.controller;

import com.mallagent.common.api.Result;
import com.mallagent.module.user.dto.role.AssignPermissionsRequest;
import com.mallagent.module.user.dto.role.RoleCreateRequest;
import com.mallagent.module.user.dto.role.RoleResponse;
import com.mallagent.module.user.dto.role.RoleUpdateRequest;
import com.mallagent.module.user.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端角色管理。
 */
@Tag(name = "管理端-角色")
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleService roleService;

    @Operation(summary = "角色列表")
    @PreAuthorize("hasAuthority('system:role:read')")
    @GetMapping
    public Result<List<RoleResponse>> list() {
        return Result.success(roleService.list());
    }

    @Operation(summary = "创建角色")
    @PreAuthorize("hasAuthority('system:role:create')")
    @PostMapping
    public ResponseEntity<Result<RoleResponse>> create(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(roleService.create(request)));
    }

    @Operation(summary = "修改角色")
    @PreAuthorize("hasAuthority('system:role:update')")
    @PutMapping("/{id}")
    public Result<RoleResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody RoleUpdateRequest request) {
        return Result.success(roleService.update(id, request));
    }

    @Operation(summary = "删除角色")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "给角色分配权限")
    @PreAuthorize("hasAuthority('system:role:assign-permission')")
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody AssignPermissionsRequest request) {
        roleService.assignPermissions(id, request);
        return Result.success();
    }
}
