package com.mallagent.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.user.dto.address.AddressRequest;
import com.mallagent.module.user.dto.address.AddressResponse;
import com.mallagent.module.user.entity.UserAddress;
import com.mallagent.module.user.error.UserErrorCode;
import com.mallagent.module.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户收货地址。
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private static final int MAX_ADDRESSES = 20;

    private final UserAddressMapper addressMapper;

    public List<AddressResponse> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AddressResponse get(Long userId, Long id) {
        return toResponse(requireAddress(userId, id));
    }

    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {
        long count = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));
        if (count >= MAX_ADDRESSES) {
            throw new BusinessException(UserErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }

        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || count == 0;
        if (makeDefault) {
            clearDefault(userId);
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        applyRequest(address, request);
        address.setIsDefault(makeDefault ? 1 : 0);
        addressMapper.insert(address);
        return toResponse(address);
    }

    @Transactional
    public AddressResponse update(Long userId, Long id, AddressRequest request) {
        UserAddress address = requireAddress(userId, id);
        applyRequest(address, request);

        if (request.isDefault() != null) {
            if (request.isDefault()) {
                clearDefault(userId);
                address.setIsDefault(1);
            } else {
                address.setIsDefault(0);
            }
        }

        int updated = addressMapper.updateById(address);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "地址信息已被修改，请刷新后重试");
        }
        return toResponse(address);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireAddress(userId, id);
        addressMapper.deleteById(id);
    }

    @Transactional
    public void setDefault(Long userId, Long id) {
        UserAddress address = requireAddress(userId, id);
        clearDefault(userId);
        address.setIsDefault(1);
        int updated = addressMapper.updateById(address);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "地址信息已被修改，请刷新后重试");
        }
    }

    private void clearDefault(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .set(UserAddress::getIsDefault, 0));
    }

    private UserAddress requireAddress(Long userId, Long id) {
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(UserErrorCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    private void applyRequest(UserAddress address, AddressRequest request) {
        address.setReceiverName(request.receiverName().trim());
        address.setReceiverPhone(request.receiverPhone().trim());
        address.setProvince(request.province().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setDetailAddress(request.detailAddress().trim());
        address.setPostalCode(request.postalCode() == null ? null : request.postalCode().trim());
    }

    private AddressResponse toResponse(UserAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetailAddress(),
                address.getPostalCode(),
                address.getIsDefault() != null && address.getIsDefault() == 1,
                address.getCreatedAt()
        );
    }
}
