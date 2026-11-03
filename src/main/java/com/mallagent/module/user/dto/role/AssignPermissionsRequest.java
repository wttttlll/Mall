package com.mallagent.module.user.dto.role;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 给角色分配权限。
 */
public record AssignPermissionsRequest(
        @NotNull(message = "权限列表不能为空") List<Long> permissionIds
) {
}
