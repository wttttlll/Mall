package com.mallagent.module.cart.service;

import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.cart.api.CartCheckoutApi;
import com.mallagent.module.cart.api.dto.CartCheckoutItem;
import com.mallagent.module.cart.entity.CartItem;
import com.mallagent.module.cart.mapper.CartItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 购物车下单契约实现。必须在调用方事务内执行，行锁才会覆盖到订单提交。
 */
@Service
@RequiredArgsConstructor
public class CartCheckoutApiImpl implements CartCheckoutApi {

    private final CartItemMapper cartItemMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CartCheckoutItem> lockSelectedItems(Long userId) {
        requireUserId(userId);
        return cartItemMapper.selectSelectedForUpdate(userId).stream()
                .map(item -> new CartCheckoutItem(item.getSkuId(), item.getQuantity()))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void clearSelectedItems(Long userId) {
        requireUserId(userId);
        cartItemMapper.deleteSelected(userId);
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
