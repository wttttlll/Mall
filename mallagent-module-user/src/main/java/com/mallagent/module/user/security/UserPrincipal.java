package com.mallagent.module.user.security;

import com.mallagent.framework.security.AuthenticatedUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 登录用户主体。权限集合由角色编码（ROLE_ 前缀）和权限码组成。
 */
public class UserPrincipal implements AuthenticatedUser {

    private final Long userId;
    private final String username;
    private final String password;
    private final String nickname;
    private final boolean enabled;
    private final int tokenVersion;
    private final List<String> roleCodes;
    private final List<String> permissionCodes;
    private final Set<GrantedAuthority> authorities;

    public UserPrincipal(Long userId,
                         String username,
                         String password,
                         String nickname,
                         boolean enabled,
                         int tokenVersion,
                         List<String> roleCodes,
                         List<String> permissionCodes) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.enabled = enabled;
        this.tokenVersion = tokenVersion;
        this.roleCodes = List.copyOf(roleCodes);
        this.permissionCodes = List.copyOf(permissionCodes);

        Set<GrantedAuthority> grantedAuthorities = new LinkedHashSet<>();
        roleCodes.forEach(code -> grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
        permissionCodes.forEach(code -> grantedAuthorities.add(new SimpleGrantedAuthority(code)));
        this.authorities = Set.copyOf(grantedAuthorities);
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    @Override
    public int getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public String getNickname() {
        return nickname;
    }

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }
}
