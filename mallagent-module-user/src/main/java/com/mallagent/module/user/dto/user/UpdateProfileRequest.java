package com.mallagent.module.user.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改个人资料请求。字段为 null 表示不修改。
 */
public record UpdateProfileRequest(
        @Size(max = 64, message = "昵称长度不能超过 64") String nickname,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,

        @Size(max = 512, message = "头像地址长度不能超过 512") String avatar
) {
}
