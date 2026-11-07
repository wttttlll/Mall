package com.mallagent.module.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 整单退货退款申请。退款金额由后端从原支付快照读取。
 */
public record CreateAfterSaleRequest(
        @NotBlank(message = "订单号不能为空")
        @Size(max = 64, message = "订单号不能超过 64 个字符")
        String orderNo,

        @NotBlank(message = "退货原因不能为空")
        @Size(max = 255, message = "退货原因不能超过 255 个字符")
        String reason,

        @Size(max = 1000, message = "问题说明不能超过 1000 个字符")
        String description
) {
}
