package com.mallagent.module.user.dto.role;

/**
 * 角色信息。
 */
public record RoleResponse(
        Long id,
        String roleCode,
        String roleName,
        String description,
        Integer status,
        Integer sort
) {
}
