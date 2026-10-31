package com.mallagent.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.common.api.PageResult;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.user.dto.user.AssignRolesRequest;
import com.mallagent.module.user.dto.user.CurrentUserResponse;
import com.mallagent.module.user.dto.user.UpdateProfileRequest;
import com.mallagent.module.user.dto.user.UserResponse;
import com.mallagent.module.user.entity.SysRole;
import com.mallagent.module.user.entity.SysUser;
import com.mallagent.module.user.entity.SysUserRole;
import com.mallagent.module.user.error.UserErrorCode;
import com.mallagent.module.user.mapper.SysPermissionMapper;
import com.mallagent.module.user.mapper.SysRoleMapper;
import com.mallagent.module.user.mapper.SysUserMapper;
import com.mallagent.module.user.mapper.SysUserRoleMapper;
import com.mallagent.module.user.mapper.UserRoleRow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户资料、管理端用户操作与角色分配。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public CurrentUserResponse currentUser() {
        SysUser user = requireUser(currentUserId());
        return toCurrentUserResponse(user);
    }

    @Transactional
    public CurrentUserResponse updateProfile(UpdateProfileRequest request) {
        Long userId = currentUserId();
        SysUser user = requireUser(userId);

        if (request.nickname() != null) {
            user.setNickname(normalize(request.nickname()));
        }
        if (request.phone() != null) {
            String phone = normalize(request.phone());
            if (!Objects.equals(phone, user.getPhone())) {
                ensurePhoneEmailUnique(phone, null, userId);
                user.setPhone(phone);
            }
        }
        if (request.email() != null) {
            String email = normalize(request.email());
            if (!Objects.equals(email, user.getEmail())) {
                ensurePhoneEmailUnique(null, email, userId);
                user.setEmail(email);
            }
        }
        if (request.avatar() != null) {
            user.setAvatar(normalize(request.avatar()));
        }

        int updated = userMapper.updateById(user);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "用户信息已被修改，请刷新后重试");
        }
        return toCurrentUserResponse(user);
    }

    public PageResult<UserResponse> pageUsers(long pageNum, long pageSize, String username, Integer status) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), 100);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(SysUser::getUsername, username.trim());
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        Map<Long, List<UserRoleRow>> roles = loadRoles(page.getRecords().stream()
                .map(SysUser::getId)
                .toList());

        List<UserResponse> list = page.getRecords().stream()
                .map(user -> toUserResponse(user, roles.getOrDefault(user.getId(), List.of())))
                .toList();

        return new PageResult<>(list, page.getTotal(), safePageNum, safePageSize);
    }

    public UserResponse getUser(Long id) {
        SysUser user = requireUser(id);
        List<UserRoleRow> roles = roleMapper.selectRolesByUserIds(List.of(id));
        return toUserResponse(user, roles);
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(UserErrorCode.INVALID_STATUS);
        }

        Long currentUserId = SecurityUtils.currentUserId().orElse(null);
        if (status == 0 && id.equals(currentUserId)) {
            throw new BusinessException(UserErrorCode.CANNOT_DISABLE_SELF);
        }

        SysUser user = requireUser(id);
        if (status == 0
                && userRoleMapper.countSuperAdminRolesByUserId(id) > 0
                && userRoleMapper.countEnabledSuperAdmins() <= 1) {
            throw new BusinessException(UserErrorCode.LAST_SUPER_ADMIN);
        }

        user.setStatus(status);
        if (status == 0) {
            user.setTokenVersion(nextVersion(user.getTokenVersion()));
        }

        int updated = userMapper.updateById(user);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "用户信息已被修改，请刷新后重试");
        }
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUser user = requireUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(nextVersion(user.getTokenVersion()));

        int updated = userMapper.updateById(user);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "用户信息已被修改，请刷新后重试");
        }
    }

    @Transactional
    public void assignRoles(Long id, AssignRolesRequest request) {
        requireUser(id);

        Set<Long> roleIds = request.roleIds() == null
                ? Set.of()
                : request.roleIds().stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<SysRole> roles = roleIds.isEmpty()
                ? List.of()
                : roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getStatus, 1));
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(UserErrorCode.ROLE_NOT_FOUND);
        }

        SysRole superAdminRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, SUPER_ADMIN_ROLE_CODE));
        boolean currentlySuperAdmin = userRoleMapper.countSuperAdminRolesByUserId(id) > 0;
        boolean willBeSuperAdmin = superAdminRole != null && roleIds.contains(superAdminRole.getId());
        if (currentlySuperAdmin && !willBeSuperAdmin && userRoleMapper.countEnabledSuperAdmins() <= 1) {
            throw new BusinessException(UserErrorCode.LAST_SUPER_ADMIN);
        }

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(id);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    private CurrentUserResponse toCurrentUserResponse(SysUser user) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = permissionMapper.selectPermissionCodesByUserId(user.getId());
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                user.getEmail(),
                user.getAvatar(),
                roles,
                permissions,
                user.getCreatedAt()
        );
    }

    private UserResponse toUserResponse(SysUser user, List<UserRoleRow> roles) {
        List<UserResponse.RoleBrief> roleBriefs = roles.stream()
                .map(role -> new UserResponse.RoleBrief(role.getRoleId(), role.getRoleCode(), role.getRoleName()))
                .toList();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                user.getEmail(),
                user.getAvatar(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                roleBriefs
        );
    }

    private Map<Long, List<UserRoleRow>> loadRoles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return roleMapper.selectRolesByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(UserRoleRow::getUserId));
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }

    private void ensurePhoneEmailUnique(String phone, String email, Long excludeUserId) {
        if (phone != null) {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, phone)
                    .ne(SysUser::getId, excludeUserId);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(UserErrorCode.PHONE_EXISTS);
            }
        }
        if (email != null) {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, email)
                    .ne(SysUser::getId, excludeUserId);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(UserErrorCode.EMAIL_EXISTS);
            }
        }
    }

    private int nextVersion(Integer version) {
        return (version == null ? 0 : version) + 1;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
