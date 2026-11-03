package com.mallagent.module.user.dto.role;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建角色请求。
 */
public record RoleCreateRequest(

        @NotBlank(message = "角色编码不能为空")
        @Size(max = 64, message = "角色编码长度不能超过 64")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码只能包含大写字母、数字和下划线，且以字母开头")
        String roleCode,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过 64")
        String roleName,

        @Size(max = 255, message = "角色描述长度不能超过 255")
        String description,

        @Min(value = 0, message = "状态只能是 0 或 1")
        @Max(value = 1, message = "状态只能是 0 或 1")
        Integer status,

        Integer sort
) {
}
