package com.mallagent.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("""
            SELECT COUNT(*)
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 1
            WHERE ur.user_id = #{userId}
              AND r.role_code = 'SUPER_ADMIN'
            """)
    long countSuperAdminRolesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 1
            WHERE r.role_code = 'SUPER_ADMIN'
            """)
    long countEnabledSuperAdmins();
}
