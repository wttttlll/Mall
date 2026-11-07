package com.mallagent.module.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员拒绝售后申请。
 */
public record RejectAfterSaleRequest(
        @NotBlank(message = "拒绝原因不能为空")
        @Size(max = 255, message = "拒绝原因不能超过 255 个字符")
        String rejectReason
) {
}
