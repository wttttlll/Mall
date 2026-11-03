package com.mallagent.framework.security;

import org.springframework.security.core.userdetails.UserDetails;

/**
 * 已认证用户扩展信息，供审计字段和 JWT 校验使用。
 */
public interface AuthenticatedUser extends UserDetails {

    Long getUserId();

    int getTokenVersion();
}
