package com.mallagent.module.user.dto.user;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 给用户分配角色。
 */
public record AssignRolesRequest(
        @NotNull(message = "角色列表不能为空") List<Long> roleIds
) {
}
