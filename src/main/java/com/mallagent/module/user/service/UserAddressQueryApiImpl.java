package com.mallagent.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.module.user.api.UserAddressQueryApi;
import com.mallagent.module.user.api.dto.AddressSnapshot;
import com.mallagent.module.user.entity.UserAddress;
import com.mallagent.module.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户收货地址只读查询实现。
 */
@Service
@RequiredArgsConstructor
public class UserAddressQueryApiImpl implements UserAddressQueryApi {

    private final UserAddressMapper addressMapper;

    @Override
    public Optional<AddressSnapshot> findForUser(Long userId, Long addressId) {
        if (userId == null || addressId == null) {
            return Optional.empty();
        }
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            return Optional.empty();
        }
        return Optional.of(new AddressSnapshot(
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetailAddress(),
                address.getPostalCode()
        ));
    }
}
