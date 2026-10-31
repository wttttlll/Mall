package com.mallagent.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.module.user.dto.permission.PermissionResponse;
import com.mallagent.module.user.entity.SysPermission;
import com.mallagent.module.user.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限查询与权限树构建。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysPermissionMapper permissionMapper;

    public List<PermissionResponse> tree() {
        List<SysPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getStatus, 1)
                        .orderByAsc(SysPermission::getSort)
                        .orderByAsc(SysPermission::getId));

        Map<Long, List<SysPermission>> children = permissions.stream()
                .collect(Collectors.groupingBy(
                        permission -> permission.getParentId() == null ? 0L : permission.getParentId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return buildChildren(0L, children);
    }

    private List<PermissionResponse> buildChildren(Long parentId,
                                                   Map<Long, List<SysPermission>> childrenByParent) {
        return childrenByParent.getOrDefault(parentId, List.of()).stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(),
                        permission.getParentId(),
                        permission.getPermissionCode(),
                        permission.getPermissionName(),
                        permission.getType(),
                        permission.getSort(),
                        buildChildren(permission.getId(), childrenByParent)
                ))
                .toList();
    }
}
