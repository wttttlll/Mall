package com.mallagent.module.cart.service;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.cart.config.CartProperties;
import com.mallagent.module.cart.dto.cart.AddCartItemRequest;
import com.mallagent.module.cart.dto.cart.CartItemResponse;
import com.mallagent.module.cart.dto.cart.CartResponse;
import com.mallagent.module.cart.dto.cart.CartSummaryResponse;
import com.mallagent.module.cart.dto.cart.UpdateCartItemRequest;
import com.mallagent.module.cart.dto.cart.UpdateSelectedRequest;
import com.mallagent.module.cart.entity.CartItem;
import com.mallagent.module.cart.error.CartErrorCode;
import com.mallagent.module.cart.mapper.CartItemMapper;
import com.mallagent.module.product.api.ProductSkuQueryApi;
import com.mallagent.module.product.api.dto.SkuAvailability;
import com.mallagent.module.product.api.dto.SkuSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 购物车业务编排。
 *
 * <p>MySQL 是唯一事实来源，购物车状态直接查询 MySQL。添加操作通过幂等表 + 唯一索引保证重复请求安全。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int MAX_WRITE_ATTEMPTS = 3;

    private final CartItemMapper cartItemMapper;
    private final CartWriteService cartWriteService;
    private final ProductSkuQueryApi productSkuQueryApi;
    private final CartProperties properties;

    /**
     * 添加购物车。重复使用同一 Idempotency-Key 且请求内容相同时不会重复累加。
     */
    public void addItem(Long userId, String idempotencyKey, AddCartItemRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        Long skuId = requireSkuId(request.skuId());
        int quantity = validateQuantity(request.quantity());

        String requestHash = requestHash(skuId, quantity);
        writeWithRetry(userId, key, skuId, quantity, requestHash);
    }

    /**
     * 幂等表与购物车表并发写入可能触发死锁，做有限次数重试。
     */
    private void writeWithRetry(Long userId, String key, Long skuId, int quantity, String requestHash) {
        for (int attempt = 1; ; attempt++) {
            try {
                cartWriteService.addItem(userId, key, skuId, quantity, requestHash);
                return;
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException ex) {
                if (attempt >= MAX_WRITE_ATTEMPTS) {
                    throw new BusinessException(CartErrorCode.CART_OPERATION_CONFLICT,
                            "购物车写入冲突，请重试", ex);
                }
                log.warn("购物车写入死锁，准备重试 attempt={}/{}, userId={}, skuId={}",
                        attempt, MAX_WRITE_ATTEMPTS, userId, skuId);
                try {
                    Thread.sleep(20L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(CartErrorCode.CART_OPERATION_CONFLICT,
                            "购物车写入被中断", interrupted);
                }
            }
        }
    }

    /**
     * 设置购买数量，绝对覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, Long skuId, UpdateCartItemRequest request) {
        Long safeSkuId = requireSkuId(skuId);
        int quantity = validateQuantity(request.quantity());
        int updated = cartItemMapper.updateQuantity(userId, safeSkuId, quantity);
        if (updated == 0) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
    }

    /**
     * 删除条目。条目不存在视为成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long userId, Long skuId) {
        cartItemMapper.deleteItem(userId, requireSkuId(skuId));
    }

    /**
     * 勾选或取消勾选单个条目。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setSelected(Long userId, Long skuId, UpdateSelectedRequest request) {
        int selected = Boolean.TRUE.equals(request.selected()) ? 1 : 0;
        int updated = cartItemMapper.updateSelected(userId, requireSkuId(skuId), selected);
        if (updated == 0) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
    }

    /**
     * 全选或取消全选。
     */
    @Transactional(rollbackFor = Exception.class)
    public void selectAll(Long userId, UpdateSelectedRequest request) {
        int selected = Boolean.TRUE.equals(request.selected()) ? 1 : 0;
        cartItemMapper.updateAllSelected(userId, selected);
    }

    /**
     * 查询当前用户购物车。购物车状态直接查询 MySQL，商品信息实时读取商品模块。
     */
    public CartResponse getCart(Long userId) {
        List<CartItem> cartItems = cartItemMapper.selectByUser(userId);
        if (cartItems.isEmpty()) {
            return emptyCart();
        }

        List<Long> skuIds = cartItems.stream()
                .map(CartItem::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SkuSnapshot> skuById = productSkuQueryApi.findSkus(skuIds).stream()
                .collect(Collectors.toMap(SkuSnapshot::skuId, Function.identity(), (left, right) -> left));

        List<CartItemResponse> items = new ArrayList<>(cartItems.size());
        int totalQuantity = 0;
        int selectedQuantity = 0;
        BigDecimal selectedAmount = ZERO_AMOUNT;
        BigDecimal totalAmount = ZERO_AMOUNT;

        for (CartItem cartItem : cartItems) {
            SkuSnapshot sku = skuById.get(cartItem.getSkuId());
            int quantity = cartItem.getQuantity() == null ? 0 : cartItem.getQuantity();
            boolean selected = cartItem.getSelected() != null && cartItem.getSelected() == 1;
            boolean available = sku != null && sku.purchasable();
            BigDecimal price = sku == null ? null : sku.price();
            BigDecimal amount = available && price != null
                    ? price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP)
                    : ZERO_AMOUNT;

            totalQuantity += quantity;
            if (selected && available) {
                selectedQuantity += quantity;
                selectedAmount = selectedAmount.add(amount);
            }
            if (available) {
                totalAmount = totalAmount.add(amount);
            }

            items.add(new CartItemResponse(
                    cartItem.getSkuId(),
                    sku == null ? null : sku.spuId(),
                    sku == null ? null : sku.skuCode(),
                    sku == null ? null : sku.spuName(),
                    sku == null ? null : sku.imageUrl(),
                    sku == null ? null : sku.specText(),
                    price,
                    quantity,
                    selected,
                    amount,
                    available,
                    available ? null : unavailableReason(sku)
            ));
        }

        return new CartResponse(items, new CartSummaryResponse(
                items.size(),
                totalQuantity,
                selectedQuantity,
                selectedAmount.setScale(2, RoundingMode.HALF_UP),
                totalAmount.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    private CartResponse emptyCart() {
        return new CartResponse(List.of(), new CartSummaryResponse(0, 0, 0, ZERO_AMOUNT, ZERO_AMOUNT));
    }

    private Long requireSkuId(Long skuId) {
        if (skuId == null || skuId <= 0) {
            throw new BusinessException(CartErrorCode.CART_SKU_NOT_FOUND);
        }
        return skuId;
    }

    private int validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_INVALID);
        }
        if (quantity > properties.getMaxQuantityPerItem()) {
            throw new BusinessException(CartErrorCode.CART_QUANTITY_EXCEEDED);
        }
        return quantity;
    }

    private String requireIdempotencyKey(String key) {
        if (!StringUtils.hasText(key) || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(CartErrorCode.CART_IDEMPOTENCY_KEY_REQUIRED);
        }
        return key;
    }

    private String requestHash(Long skuId, int quantity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((skuId + ":" + quantity).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }

    private String unavailableReason(SkuSnapshot sku) {
        if (sku == null) {
            return "SKU 不存在";
        }
        return unavailableMessage(sku.availability());
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
