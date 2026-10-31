package com.mallagent.framework.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 当前安全上下文工具。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.ofNullable(user.getUserId());
        }
        return Optional.empty();
    }

    public static Optional<String> currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.ofNullable(user.getUsername());
        }
        return Optional.empty();
    }

    public static Optional<JwtTokenDetails> currentTokenDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof JwtTokenDetails details) {
            return Optional.of(details);
        }
        return Optional.empty();
    }
}
