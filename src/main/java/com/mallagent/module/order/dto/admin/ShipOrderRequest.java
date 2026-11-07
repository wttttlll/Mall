package com.mallagent.module.order.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端发货请求。仅保存快递公司和运单号，不对接真实物流公司。
 */
public record ShipOrderRequest(
        @NotBlank(message = "快递公司不能为空")
        @Size(max = 64, message = "快递公司不能超过 64 个字符")
        String shippingCompany,

        @NotBlank(message = "物流单号不能为空")
        @Size(max = 64, message = "物流单号不能超过 64 个字符")
        String trackingNo
) {
}
