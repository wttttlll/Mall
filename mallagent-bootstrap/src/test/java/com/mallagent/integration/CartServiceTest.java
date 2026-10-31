package com.mallagent.integration;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.cart.config.CartProperties;
import com.mallagent.module.cart.dto.cart.AddCartItemRequest;
import com.mallagent.module.cart.dto.cart.CartItemResponse;
import com.mallagent.module.cart.dto.cart.CartResponse;
import com.mallagent.module.cart.entity.CartItem;
import com.mallagent.module.cart.mapper.CartItemMapper;
import com.mallagent.module.cart.service.CartService;
import com.mallagent.module.cart.service.CartWriteService;
import com.mallagent.module.product.api.ProductSkuQueryApi;
import com.mallagent.module.product.api.dto.SkuAvailability;
import com.mallagent.module.product.api.dto.SkuSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private CartWriteService cartWriteService;

    @Mock
    private ProductSkuQueryApi productSkuQueryApi;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        CartProperties properties = new CartProperties();
        properties.setMaxQuantityPerItem(99);
        properties.setMaxItems(100);
        cartService = new CartService(cartItemMapper, cartWriteService,
                productSkuQueryApi, properties);
    }

    @Test
    @DisplayName("缺少幂等键时拒绝添加")
    void missingIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> cartService.addItem(1L, null, new AddCartItemRequest(101L, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(13009);
    }

    @Test
    @DisplayName("汇总只统计已选且可购买条目")
    void summaryExcludesUnavailableItems() {
        when(cartItemMapper.selectByUser(1L)).thenReturn(List.of(
                item(101L, 2, 1),
                item(102L, 1, 0),
                item(103L, 3, 1)));
        when(productSkuQueryApi.findSkus(any())).thenReturn(List.of(
                snapshot(101L, "10.00", SkuAvailability.AVAILABLE),
                snapshot(102L, "20.00", SkuAvailability.AVAILABLE),
                snapshot(103L, "5.00", SkuAvailability.SPU_NOT_ON_SHELF)));

        CartResponse response = cartService.getCart(1L);

        assertThat(response.summary().itemCount()).isEqualTo(3);
        assertThat(response.summary().totalQuantity()).isEqualTo(6);
        assertThat(response.summary().selectedQuantity()).isEqualTo(2);
        assertThat(response.summary().selectedAmount()).isEqualByComparingTo("20.00");
        assertThat(response.summary().totalAmount()).isEqualByComparingTo("40.00");

        CartItemResponse unavailable = response.items().stream()
                .filter(item -> item.skuId().equals(103L))
                .findFirst()
                .orElseThrow();
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.amount()).isEqualByComparingTo("0.00");
        assertThat(unavailable.unavailableReason()).contains("下架");
    }

    private SkuSnapshot snapshot(Long skuId, String price, SkuAvailability availability) {
        return new SkuSnapshot(skuId, 1000L, "SKU-" + skuId, "商品-" + skuId,
                "https://example.com/sku.png", new BigDecimal(price), null, availability);
    }

    private CartItem item(Long skuId, int quantity, int selected) {
        CartItem item = new CartItem();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        item.setSelected(selected);
        item.setCreatedAt(LocalDateTime.now());
        return item;
    }

}
