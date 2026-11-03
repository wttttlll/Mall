package com.mallagent.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 购物车种类上限测试。使用独立上下文把上限调低，验证应用层软上限行为。
 */
@TestPropertySource(properties = "mallagent.cart.max-items=1")
class CartItemLimitIntegrationTest extends AbstractCartIntegrationTest {

    @Test
    @DisplayName("超过购物车种类上限时回滚")
    void itemLimitIsEnforced() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture first = createPublishedProduct(admin.accessToken(), "10.00");
        ProductFixture second = createPublishedProduct(admin.accessToken(), "20.00");

        assertThat(addCartItem(user.token(), UUID.randomUUID().toString(), first.skuId(), 1)
                .getResponse().getStatus()).isEqualTo(200);

        MvcResult exceeded = addCartItem(user.token(), UUID.randomUUID().toString(), second.skuId(), 1);

        assertThat(exceeded.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(exceeded).path("code").asInt()).isEqualTo(13005);
        assertThat(cartItemMapper.selectQuantity(user.userId(), first.skuId())).isEqualTo(1);
        assertThat(cartItemMapper.selectQuantity(user.userId(), second.skuId())).isNull();
    }
}
