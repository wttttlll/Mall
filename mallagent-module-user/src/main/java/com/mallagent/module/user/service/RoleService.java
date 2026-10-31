package com.mallagent.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.user.dto.role.AssignPermissionsRequest;
import com.mallagent.module.user.dto.role.RoleCreateRequest;
import com.mallagent.module.user.dto.role.RoleResponse;
import com.mallagent.module.user.dto.role.RoleUpdateRequest;
import com.mallagent.module.user.entity.SysPermission;
import com.mallagent.module.user.entity.SysRole;
import com.mallagent.module.user.entity.SysRolePermission;
import com.mallagent.module.user.entity.SysUserRole;
import com.mallagent.module.user.error.UserErrorCode;
import com.mallagent.module.user.mapper.SysPermissionMapper;
import com.mallagent.module.user.mapper.SysRoleMapper;
import com.mallagent.module.user.mapper.SysRolePermissionMapper;
import com.mallagent.module.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理与角色权限分配。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> BUILT_IN_ROLE_CODES =
            Set.of("SUPER_ADMIN", "OPERATOR", "CUSTOMER");

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;

    public List<RoleResponse> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .orderByAsc(SysRole::getSort)
                        .orderByAsc(SysRole::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        String roleCode = request.roleCode().trim().toUpperCase(Locale.ROOT);
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)) > 0) {
            throw new BusinessException(UserErrorCode.ROLE_CODE_EXISTS);
        }

        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(request.roleName().trim());
        role.setDescription(request.description());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setSort(request.sort() == null ? 0 : request.sort());

        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(UserErrorCode.ROLE_CODE_EXISTS);
        }
        return toResponse(role);
    }

    @Transactional
    public RoleResponse update(Long id, RoleUpdateRequest request) {
        SysRole role = requireRole(id);
        if ("SUPER_ADMIN".equals(role.getRoleCode()) && request.status() != null && request.status() == 0) {
            throw new BusinessException(UserErrorCode.BUILT_IN_ROLE);
        }

        role.setRoleName(request.roleName().trim());
        role.setDescription(request.description());
        if (request.status() != null) {
            role.setStatus(request.status());
        }
        if (request.sort() != null) {
            role.setSort(request.sort());
        }

        int updated = roleMapper.updateById(role);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "角色信息已被修改，请刷新后重试");
        }
        return toResponse(role);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (BUILT_IN_ROLE_CODES.contains(role.getRoleCode())) {
            throw new BusinessException(UserErrorCode.BUILT_IN_ROLE);
        }

        long assignedUsers = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (assignedUsers > 0) {
            throw new BusinessException(UserErrorCode.ROLE_IN_USE);
        }

        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Transactional
    public void assignPermissions(Long id, AssignPermissionsRequest request) {
        SysRole role = requireRole(id);
        if ("SUPER_ADMIN".equals(role.getRoleCode())) {
            throw new BusinessException(UserErrorCode.BUILT_IN_ROLE);
        }

        Set<Long> permissionIds = request.permissionIds() == null
                ? Set.of()
                : request.permissionIds().stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!permissionIds.isEmpty()) {
            List<SysPermission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                    .in(SysPermission::getId, permissionIds)
                    .eq(SysPermission::getStatus, 1));
            if (permissions.size() != permissionIds.size()) {
                throw new BusinessException(UserErrorCode.PERMISSION_NOT_FOUND);
            }
        }

        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        for (Long permissionId : permissionIds) {
            SysRolePermission rolePermission = new SysRolePermission();
            rolePermission.setRoleId(id);
            rolePermission.setPermissionId(permissionId);
            rolePermissionMapper.insert(rolePermission);
        }
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(UserErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private RoleResponse toResponse(SysRole role) {
        return new RoleResponse(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                role.getStatus(),
                role.getSort()
        );
    }
}
