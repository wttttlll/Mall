package com.mallagent.module.user.dto.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前登录用户信息。
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String nickname,
        String phone,
        String email,
        String avatar,
        List<String> roles,
        List<String> permissions,
        LocalDateTime createdAt
) {
}
