package com.mallagent.module.user.dto.address;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 收货地址信息。
 */
public record AddressResponse(
        Long id,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode,
        @JsonProperty("isDefault") boolean isDefault,
        LocalDateTime createdAt
) {
}
