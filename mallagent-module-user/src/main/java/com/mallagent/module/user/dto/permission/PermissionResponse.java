package com.mallagent.module.user.dto.permission;

import java.util.List;

/**
 * 权限树节点。
 */
public record PermissionResponse(
        Long id,
        Long parentId,
        String permissionCode,
        String permissionName,
        Integer type,
        Integer sort,
        List<PermissionResponse> children
) {
}
