package com.mallagent.module.user.api.dto;

/**
 * 收货地址只读快照，供订单模块在下单时保存快照。
 */
public record AddressSnapshot(
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode
) {
}
