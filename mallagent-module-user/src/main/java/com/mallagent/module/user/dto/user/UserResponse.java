package com.mallagent.module.user.dto.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户信息。
 */
public record UserResponse(
        Long id,
        String username,
        String nickname,
        String phone,
        String email,
        String avatar,
        Integer status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        List<RoleBrief> roles
) {

    public record RoleBrief(Long id, String roleCode, String roleName) {
    }
}
