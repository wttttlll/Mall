package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.order.entity.OrderInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPaymentConcurrencyIntegrationTest extends AbstractOrderIntegrationTest {

    @Test
    @DisplayName("并发相同 Idempotency-Key 下单只生成一笔订单")
    void concurrentSameKeyCreatesOneOrder() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String key = "order-" + UUID.randomUUID();

        List<MvcResult> results = runConcurrently(10, index -> createOrder(user.token(), key, addressId));

        assertThat(results).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));
        Set<String> orderNos = results.stream().map(result -> {
            try {
                return body(result).path("data").path("orderNo").asText();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }).collect(Collectors.toSet());
        assertThat(orderNos).hasSize(1);
        assertThat(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, user.userId())
                .eq(OrderInfo::getIdempotencyKey, key))).isEqualTo(1);
        assertThat(orderLockFlows(orderNos.iterator().next())).hasSize(1);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isEqualTo(2);
        assertThat(cartItems(user.userId())).isEmpty();
    }

    @Test
    @DisplayName("并发不同 Idempotency-Key 下单只能消费同一批购物车一次")
    void concurrentDifferentKeysCreatesOnlyOneOrder() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);

        List<MvcResult> results = runConcurrently(2, index -> createOrder(
                user.token(), "order-" + UUID.randomUUID(), addressId));

        List<MvcResult> created = results.stream()
                .filter(result -> result.getResponse().getStatus() == 201)
                .toList();
        List<MvcResult> rejected = results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .toList();
        assertThat(created).hasSize(1);
        assertThat(rejected).hasSize(1);
        assertThat(body(rejected.get(0)).path("code").asInt()).isEqualTo(16003);
        assertThat(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, user.userId()))).isEqualTo(1);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isEqualTo(2);
        assertThat(cartItems(user.userId())).isEmpty();
    }

    @Test
    @DisplayName("支付与取消并发恰好一个成功")
    void payAndCancelCompeteExactlyOneWins() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));

        List<MvcResult> results = runConcurrently(2,
                index -> index == 0 ? pay(user.token(), orderNo) : cancel(user.token(), orderNo));

        List<MvcResult> successes = results.stream()
                .filter(result -> result.getResponse().getStatus() == 200)
                .toList();
        List<MvcResult> failures = results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .toList();
        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(body(failures.get(0)).path("code").asInt()).isIn(16005, 17001);

        OrderInfo order = order(orderNo);
        List<StockFlow> terminals = terminalFlows(orderNo);
        assertThat(terminals).hasSize(1);
        if ("PAID".equals(order.getStatus())) {
            assertThat(terminals.get(0).getBizType()).isEqualTo("ORDER_DEDUCT");
            assertThat(paymentCount(orderNo)).isEqualTo(1);
            assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
            assertThat(stock(product.skuId()).getLockedStock()).isZero();
        } else {
            assertThat(order.getStatus()).isEqualTo("CANCELLED");
            assertThat(terminals.get(0).getBizType()).isEqualTo("ORDER_RELEASE");
            assertThat(paymentCount(orderNo)).isZero();
            assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
            assertThat(stock(product.skuId()).getLockedStock()).isZero();
        }
    }

    @Test
    @DisplayName("并发重复支付只扣减一次库存")
    void concurrentDuplicatePayDeductsOnce() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));

        List<MvcResult> results = runConcurrently(10, index -> pay(user.token(), orderNo));

        assertThat(results).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        assertThat(order(orderNo).getStatus()).isEqualTo("PAID");
        assertThat(paymentCount(orderNo)).isEqualTo(1);
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(terminalFlows(orderNo).get(0).getBizType()).isEqualTo("ORDER_DEDUCT");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
    }
}
