package com.mallagent.module.user.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.module.user.entity.SysUser;
import com.mallagent.module.user.mapper.SysPermissionMapper;
import com.mallagent.module.user.mapper.SysRoleMapper;
import com.mallagent.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从数据库加载用户、角色和权限。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<String> roleCodes = roleMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissionCodes = permissionMapper.selectPermissionCodesByUserId(user.getId());

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getNickname(),
                user.getStatus() != null && user.getStatus() == 1,
                user.getTokenVersion() == null ? 0 : user.getTokenVersion(),
                roleCodes,
                permissionCodes
        );
    }
}
