package com.mallagent.module.user.dto.address;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 收货地址请求。
 */
public record AddressRequest(

        @NotBlank(message = "收货人不能为空")
        @Size(max = 64, message = "收货人长度不能超过 64")
        String receiverName,

        @NotBlank(message = "收货人手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收货人手机号格式不正确")
        String receiverPhone,

        @NotBlank(message = "省份不能为空")
        @Size(max = 64, message = "省份长度不能超过 64")
        String province,

        @NotBlank(message = "城市不能为空")
        @Size(max = 64, message = "城市长度不能超过 64")
        String city,

        @NotBlank(message = "区县不能为空")
        @Size(max = 64, message = "区县长度不能超过 64")
        String district,

        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址长度不能超过 255")
        String detailAddress,

        @Size(max = 16, message = "邮编长度不能超过 16")
        String postalCode,

        @JsonProperty("isDefault")
        Boolean isDefault
) {
}
