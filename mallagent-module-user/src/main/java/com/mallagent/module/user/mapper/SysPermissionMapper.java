package com.mallagent.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.user.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
            SELECT DISTINCT p.permission_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
                AND r.deleted = 0
                AND r.status = 1
            JOIN sys_role_permission rp ON rp.role_id = r.id
            JOIN sys_permission p ON p.id = rp.permission_id
                AND p.deleted = 0
                AND p.status = 1
            WHERE ur.user_id = #{userId}
            ORDER BY p.permission_code
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
