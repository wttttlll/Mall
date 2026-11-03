package com.mallagent.module.cart.service;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.cart.config.CartProperties;
import com.mallagent.module.cart.entity.CartIdempotency;
import com.mallagent.module.cart.error.CartErrorCode;
import com.mallagent.module.cart.mapper.CartIdempotencyMapper;
import com.mallagent.module.cart.mapper.CartItemMapper;
import com.mallagent.module.product.api.ProductSkuQueryApi;
import com.mallagent.module.product.api.dto.SkuAvailability;
import com.mallagent.module.product.api.dto.SkuSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 购物车写事务。
 *
 * <p>幂等记录与购物车写入必须在同一事务内完成；外层 {@link CartService} 负责死锁重试。</p>
 */
@Service
@RequiredArgsConstructor
public class CartWriteService {

    private final CartItemMapper cartItemMapper;
    private final CartIdempotencyMapper cartIdempotencyMapper;
    private final ProductSkuQueryApi productSkuQueryApi;
    private final CartProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, String key, Long skuId, int quantity, String requestHash) {
        String claimToken = UUID.randomUUID().toString();
        cartIdempotencyMapper.claim(userId, key, requestHash, skuId, quantity, claimToken);

        CartIdempotency record = cartIdempotencyMapper.selectByKey(userId, key);
        if (record == null) {
            throw new BusinessException(CartErrorCode.CART_OPERATION_CONFLICT);
        }
        if (!claimToken.equals(record.getClaimToken())) {
            if (!requestHash.equals(record.getRequestHash())) {
                throw new BusinessException(CartErrorCode.CART_IDEMPOTENCY_CONFLICT);
            }
            return;
        }

        // 只有新请求才校验商品状态；幂等重试不受商品后续下架影响。
        SkuSnapshot snapshot = productSkuQueryApi.findSku(skuId)
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_SKU_NOT_FOUND));
        if (!snapshot.purchasable()) {
            throw new BusinessException(CartErrorCode.CART_PRODUCT_UNAVAILABLE,
                    unavailableMessage(snapshot.availability()));
        }

        cartItemMapper.upsertAdd(userId, skuId, quantity);
        Integer newQuantity = cartItemMapper.selectQuantity(userId, skuId);
        if (newQuantity == null) {
            throw new BusinessException(CartErrorCode.CART_OPERATION_CONFLICT);
        }
        if (newQuantity > properties.getMaxQuantityPerItem()) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_EXCEEDED);
        }
        if (cartItemMapper.countItems(userId) > properties.getMaxItems()) {
            throw new BusinessException(CartErrorCode.CART_ITEM_LIMIT_EXCEEDED);
        }
    }

    private String unavailableMessage(SkuAvailability availability) {
        return switch (availability) {
            case AVAILABLE -> "商品当前不可购买";
            case SKU_DISABLED -> "SKU 已禁用";
            case SPU_NOT_FOUND -> "商品不存在";
            case SPU_NOT_ON_SHELF -> "商品已下架";
            case CATEGORY_NOT_FOUND -> "商品分类不存在";
            case CATEGORY_DISABLED -> "商品分类已禁用";
            case BRAND_NOT_FOUND -> "商品品牌不存在";
            case BRAND_DISABLED -> "商品品牌已禁用";
        };
    }
}
