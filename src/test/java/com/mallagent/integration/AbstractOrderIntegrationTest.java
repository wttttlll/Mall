package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.cart.entity.CartItem;
import com.mallagent.module.cart.mapper.CartItemMapper;
import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.entity.OrderItem;
import com.mallagent.module.order.mapper.OrderInfoMapper;
import com.mallagent.module.order.mapper.OrderItemMapper;
import com.mallagent.module.payment.entity.PaymentOrder;
import com.mallagent.module.payment.mapper.PaymentOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 订单/模拟支付集成测试基类，复用商品、库存、购物车与地址能力。
 */
abstract class AbstractOrderIntegrationTest extends AbstractInventoryIntegrationTest {

    @Autowired
    protected CartItemMapper cartItemMapper;

    @Autowired
    protected OrderInfoMapper orderInfoMapper;

    @Autowired
    protected OrderItemMapper orderItemMapper;

    @Autowired
    protected PaymentOrderMapper paymentOrderMapper;

    protected OrderUser registerOrderUser() throws Exception {
        String username = uniqueUsername();
        TokenPair token = register(username);
        return new OrderUser(username, user(username).getId(), token.accessToken());
    }

    /**
     * 创建已上架、已初始化库存的商品。
     */
    protected OrderProduct createOrderProduct(String adminToken, String price, int stock) throws Exception {
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(adminToken, "order-cat-" + suffix, 0L, 1);
        Long spuId = createSpu(adminToken, "order-spu-" + suffix, categoryId, null,
                "https://example.com/order-main-" + suffix + ".png");
        Long skuId = createSku(adminToken, spuId, "ORDER-SKU-" + suffix, price, List.of());
        assertThat(publish(adminToken, spuId).getResponse().getStatus()).isEqualTo(200);
        adjustDirect("order-seed-" + UUID.randomUUID(), skuId, stock, "初始库存");
        return new OrderProduct(spuId, skuId);
    }

    protected Long createAddress(String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverName", "张三");
        body.put("receiverPhone", "13800138000");
        body.put("province", "广东省");
        body.put("city", "深圳市");
        body.put("district", "南山区");
        body.put("detailAddress", "科技园 1 号");
        body.put("postalCode", "518000");
        body.put("isDefault", true);
        MvcResult result = postJson("/api/v1/users/me/addresses", body, token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("id").asLong();
    }

    protected MvcResult addCartItem(String token, Long skuId, int quantity) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("skuId", skuId, "quantity", quantity)))
                .header("Idempotency-Key", "cart-" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(builder).andReturn();
    }

    protected MvcResult createOrder(String token, String idempotencyKey, Long addressId) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("addressId", addressId)));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(builder).andReturn();
    }

    protected MvcResult pay(String token, String orderNo) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/payments/orders/" + orderNo + "/pay")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(builder).andReturn();
    }

    protected MvcResult cancel(String token, String orderNo) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/orders/" + orderNo + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(builder).andReturn();
    }

    protected JsonNode orderDetail(String token, String orderNo) throws Exception {
        return body(getJson("/api/v1/orders/" + orderNo, token));
    }

    protected JsonNode orderPage(String token, String query) throws Exception {
        return body(getJson("/api/v1/orders" + query, token));
    }

    protected String orderNo(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("orderNo").asText();
    }

    protected void updatePrice(String adminToken, OrderProduct product, String price) throws Exception {
        assertThat(offShelf(adminToken, product.spuId()).getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("price", new BigDecimal(price));
        body.put("specValueIds", List.of());
        MvcResult updated = putJson("/api/v1/admin/product/spus/" + product.spuId()
                + "/skus/" + product.skuId(), body, adminToken);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(publish(adminToken, product.spuId()).getResponse().getStatus()).isEqualTo(200);
    }

    protected OrderInfo order(String orderNo) {
        return orderInfoMapper.selectByOrderNo(orderNo);
    }

    protected List<OrderItem> orderItems(Long orderId) {
        return orderItemMapper.selectByOrderId(orderId);
    }

    protected PaymentOrder payment(String orderNo) {
        return paymentOrderMapper.selectByOrderNo(orderNo);
    }

    protected long paymentCount(String orderNo) {
        return paymentOrderMapper.selectCount(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo));
    }

    protected List<CartItem> cartItems(Long userId) {
        return cartItemMapper.selectByUser(userId);
    }

    protected void assertBusinessCode(Runnable action, int code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(code);
    }

    protected List<MvcResult> runConcurrently(int threads, ThrowingIntFunction action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return action.apply(index);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<MvcResult> results = new ArrayList<>(threads);
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    protected interface ThrowingIntFunction {
        MvcResult apply(int value) throws Exception;
    }

    protected record OrderUser(String username, Long userId, String token) {
    }

    protected record OrderProduct(Long spuId, Long skuId) {
    }
}
