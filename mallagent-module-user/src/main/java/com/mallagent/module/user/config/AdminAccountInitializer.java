package com.mallagent.module.user.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.module.user.entity.SysRole;
import com.mallagent.module.user.entity.SysUser;
import com.mallagent.module.user.entity.SysUserRole;
import com.mallagent.module.user.mapper.SysRoleMapper;
import com.mallagent.module.user.mapper.SysUserMapper;
import com.mallagent.module.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动时创建初始管理员账号。
 *
 * <p>密码来自 mallagent.admin.init-password 或 ADMIN_INIT_PASSWORD 环境变量，
 * 不会写入 Flyway 迁移脚本。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!adminProperties.isInitEnabled()) {
            return;
        }

        Long existing = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, adminProperties.getUsername()));
        if (existing != null && existing > 0) {
            return;
        }

        SysRole superAdminRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, SUPER_ADMIN_ROLE_CODE)
                .eq(SysRole::getStatus, 1));
        if (superAdminRole == null) {
            log.error("未找到 SUPER_ADMIN 角色，跳过初始管理员创建");
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(adminProperties.getUsername());
        admin.setPassword(passwordEncoder.encode(adminProperties.getInitPassword()));
        admin.setNickname("系统管理员");
        admin.setStatus(1);
        admin.setTokenVersion(0);
        userMapper.insert(admin);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(superAdminRole.getId());
        userRoleMapper.insert(userRole);

        log.warn("已创建初始管理员账号 '{}'，请尽快修改初始密码", adminProperties.getUsername());
    }
}
