package com.mallagent.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT DISTINCT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.deleted = 0
              AND r.status = 1
            ORDER BY r.role_code
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT ur.user_id, r.id AS role_id, r.role_code, r.role_name
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.deleted = 0
              AND r.status = 1
              AND ur.user_id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
              </foreach>
            ORDER BY ur.user_id, r.id
            </script>
            """)
    List<UserRoleRow> selectRolesByUserIds(@Param("userIds") Collection<Long> userIds);
}
