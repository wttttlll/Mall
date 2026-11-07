package com.mallagent.module.user.api.dto;

/**
 * 跨模块使用的用户简要信息，不包含密码等敏感字段。
 */
public record UserBrief(
        Long id,
        String username,
        String nickname
) {
}
