package com.mallagent.module.user.mapper;

import lombok.Data;

/**
 * 用户角色批量查询结果。
 */
@Data
public class UserRoleRow {

    private Long userId;

    private Long roleId;

    private String roleCode;

    private String roleName;
}
