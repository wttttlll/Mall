package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.cart.mapper.CartIdempotencyMapper;
import com.mallagent.module.cart.mapper.CartItemMapper;
import com.mallagent.module.cart.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 购物车集成测试基类，复用商品测试夹具。
 */
abstract class AbstractCartIntegrationTest extends AbstractProductIntegrationTest {

    @Autowired
    protected CartService cartService;

    @Autowired
    protected CartItemMapper cartItemMapper;

    @Autowired
    protected CartIdempotencyMapper cartIdempotencyMapper;

    protected CartUser registerCartUser() throws Exception {
        String username = uniqueUsername();
        TokenPair token = register(username);
        return new CartUser(username, user(username).getId(), token.accessToken());
    }

    protected ProductFixture createPublishedProduct(String adminToken, String price) throws Exception {
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(adminToken, "购物车分类-" + suffix, 0L, 1);
        Long brandId = createBrand(adminToken, "购物车品牌-" + suffix);
        Long spuId = createSpu(adminToken, "购物车商品-" + suffix, categoryId, brandId,
                "https://example.com/cart-main-" + suffix + ".png");
        Long skuId = createSku(adminToken, spuId, "CART-SKU-" + suffix, price, java.util.List.of());
        MvcResult published = publish(adminToken, spuId);
        org.assertj.core.api.Assertions.assertThat(published.getResponse().getStatus()).isEqualTo(200);
        return new ProductFixture(spuId, skuId, categoryId, brandId);
    }

    protected MvcResult addCartItem(String token, String idempotencyKey, Long skuId, int quantity) throws Exception {
        return addCartItemWithBody(token, idempotencyKey, Map.of("skuId", skuId, "quantity", quantity));
    }

    protected MvcResult addCartItemWithBody(String token,
                                            String idempotencyKey,
                                            Map<String, Object> body) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return performAuthenticated(builder, token);
    }

    protected MvcResult updateCartItem(String token, Long skuId, int quantity) throws Exception {
        MockHttpServletRequestBuilder builder = put("/api/v1/cart/items/" + skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", quantity)));
        return performAuthenticated(builder, token);
    }

    protected MvcResult deleteCartItem(String token, Long skuId) throws Exception {
        return performAuthenticated(delete("/api/v1/cart/items/" + skuId), token);
    }

    protected MvcResult setCartItemSelected(String token, Long skuId, boolean selected) throws Exception {
        MockHttpServletRequestBuilder builder = put("/api/v1/cart/items/" + skuId + "/selected")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("selected", selected)));
        return performAuthenticated(builder, token);
    }

    protected MvcResult selectAllCartItems(String token, boolean selected) throws Exception {
        MockHttpServletRequestBuilder builder = put("/api/v1/cart/selected")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("selected", selected)));
        return performAuthenticated(builder, token);
    }

    protected JsonNode cart(String token) throws Exception {
        return body(getJson("/api/v1/cart", token));
    }

    private MvcResult performAuthenticated(MockHttpServletRequestBuilder builder, String token) throws Exception {
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn();
    }

    protected record CartUser(String username, Long userId, String token) {
    }

    protected record ProductFixture(Long spuId, Long skuId, Long categoryId, Long brandId) {
    }
}
