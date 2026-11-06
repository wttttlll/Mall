package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.entity.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIntegrationTest extends AbstractOrderIntegrationTest {

    @Test
    @DisplayName("从购物车下单：服务端计价、锁定库存、清空已选购物车")
    void createOrderLocksStockAndClearsCart() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "88.80", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = createOrder(user.token(), "order-" + UUID.randomUUID(), addressId);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = body(result).path("data");
        String orderNo = data.path("orderNo").asText();
        assertThat(data.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(data.path("totalAmount").decimalValue()).isEqualByComparingTo("177.60");
        assertThat(data.path("itemCount").asInt()).isEqualTo(1);
        assertThat(data.path("payDeadline").asText()).endsWith("+08:00");
        JsonNode item = data.path("items").get(0);
        assertThat(item.path("skuId").asLong()).isEqualTo(product.skuId());
        assertThat(item.path("unitPrice").decimalValue()).isEqualByComparingTo("88.80");
        assertThat(item.path("lineAmount").decimalValue()).isEqualByComparingTo("177.60");
        assertThat(item.path("quantity").asInt()).isEqualTo(2);

        OrderInfo order = order(orderNo);
        assertThat(order.getUserId()).isEqualTo(user.userId());
        assertThat(order.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("177.60");
        assertThat(order.getPaidAt()).isNull();
        assertThat(orderItems(order.getId())).hasSize(1);

        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isEqualTo(2);
        List<StockFlow> lockFlows = orderLockFlows(orderNo);
        assertThat(lockFlows).hasSize(1);
        assertThat(lockFlows.get(0).getBizType()).isEqualTo("ORDER_LOCK");
        assertThat(lockFlows.get(0).getQuantity()).isEqualTo(2);
        assertThat(cartItems(user.userId())).isEmpty();
    }

    @Test
    @DisplayName("下单重新读取商品当前价格，不使用购物车旧价格")
    void createOrderReReadsCurrentPrice() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "10.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 3).getResponse().getStatus()).isEqualTo(200);
        updatePrice(admin.accessToken(), product, "12.34");

        MvcResult result = createOrder(user.token(), "order-" + UUID.randomUUID(), addressId);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = body(result).path("data");
        assertThat(data.path("totalAmount").decimalValue()).isEqualByComparingTo("37.02");
        assertThat(data.path("items").get(0).path("unitPrice").decimalValue())
                .isEqualByComparingTo("12.34");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(7);
        assertThat(stock(product.skuId()).getLockedStock()).isEqualTo(3);
    }

    @Test
    @DisplayName("相同 Idempotency-Key 重复下单返回同一订单，只锁一次库存")
    void sameIdempotencyKeyReturnsSameOrder() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String key = "order-" + UUID.randomUUID();

        MvcResult first = createOrder(user.token(), key, addressId);
        MvcResult second = createOrder(user.token(), key, addressId);

        String firstNo = orderNo(first);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(second).path("data").path("orderNo").asText()).isEqualTo(firstNo);
        assertThat(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, user.userId())
                .eq(OrderInfo::getIdempotencyKey, key))).isEqualTo(1);
        assertThat(orderLockFlows(firstNo)).hasSize(1);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isEqualTo(2);
        assertThat(cartItems(user.userId())).isEmpty();
    }

    @Test
    @DisplayName("下单校验幂等键和收货地址归属")
    void createOrderValidatesKeyAndAddress() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderUser other = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        Long otherAddressId = createAddress(other.token());
        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus()).isEqualTo(200);

        MvcResult missingKey = createOrder(user.token(), null, addressId);
        assertThat(missingKey.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(missingKey).path("code").asInt()).isEqualTo(16001);

        MvcResult missingAddress = createOrder(user.token(), "order-" + UUID.randomUUID(), 999_999_999L);
        assertThat(missingAddress.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(missingAddress).path("code").asInt()).isEqualTo(16002);

        MvcResult foreignAddress = createOrder(user.token(), "order-" + UUID.randomUUID(), otherAddressId);
        assertThat(foreignAddress.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(foreignAddress).path("code").asInt()).isEqualTo(16002);
        assertThat(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, user.userId()))).isZero();
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("空购物车拒绝下单")
    void createOrderRejectsEmptyCart() throws Exception {
        OrderUser user = registerOrderUser();
        Long addressId = createAddress(user.token());

        MvcResult result = createOrder(user.token(), "order-" + UUID.randomUUID(), addressId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(16003);
        assertThat(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, user.userId()))).isZero();
    }

    @Test
    @DisplayName("商品下架后拒绝下单")
    void createOrderRejectsUnavailableProduct() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus()).isEqualTo(200);
        assertThat(offShelf(admin.accessToken(), product.spuId()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = createOrder(user.token(), "order-" + UUID.randomUUID(), addressId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(16004);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
        assertThat(cartItems(user.userId())).hasSize(1);
        assertThat(orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, user.userId()))).isZero();
    }

    @Test
    @DisplayName("库存不足时订单、明细和购物车整体回滚")
    void createOrderInsufficientStockRollsBack() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 1);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String key = "order-" + UUID.randomUUID();

        MvcResult result = createOrder(user.token(), key, addressId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(14003);
        assertThat(orderInfoMapper.selectByUserIdAndIdempotencyKey(user.userId(), key)).isNull();
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(1);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(cartItems(user.userId())).hasSize(1);
    }

    @Test
    @DisplayName("模拟支付扣减库存，重复支付幂等")
    void payDeductsStockAndIsIdempotent() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "88.80", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));

        MvcResult paid = pay(user.token(), orderNo);

        assertThat(paid.getResponse().getStatus()).isEqualTo(200);
        JsonNode payment = body(paid).path("data");
        String paymentNo = payment.path("paymentNo").asText();
        assertThat(payment.path("orderNo").asText()).isEqualTo(orderNo);
        assertThat(payment.path("amount").decimalValue()).isEqualByComparingTo("177.60");
        assertThat(payment.path("channel").asText()).isEqualTo("MOCK");
        assertThat(payment.path("status").asText()).isEqualTo("SUCCESS");

        OrderInfo order = order(orderNo);
        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(terminalFlows(orderNo).get(0).getBizType()).isEqualTo("ORDER_DEDUCT");
        assertThat(paymentCount(orderNo)).isEqualTo(1);

        MvcResult repeated = pay(user.token(), orderNo);

        assertThat(repeated.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(repeated).path("data").path("paymentNo").asText()).isEqualTo(paymentNo);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(paymentCount(orderNo)).isEqualTo(1);
    }

    @Test
    @DisplayName("取消未支付订单释放库存，重复取消幂等，取消后不能支付")
    void cancelUnpaidOrderReleasesStock() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "15.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));

        MvcResult cancelled = cancel(user.token(), orderNo);

        assertThat(cancelled.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(cancelled).path("data").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(order(orderNo).getStatus()).isEqualTo("CANCELLED");
        assertThat(order(orderNo).getCancelledAt()).isNotNull();
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(terminalFlows(orderNo).get(0).getBizType()).isEqualTo("ORDER_RELEASE");

        MvcResult repeated = cancel(user.token(), orderNo);
        assertThat(repeated.getResponse().getStatus()).isEqualTo(200);
        assertThat(terminalFlows(orderNo)).hasSize(1);

        MvcResult payCancelled = pay(user.token(), orderNo);
        assertThat(payCancelled.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(payCancelled).path("code").asInt()).isEqualTo(17001);
        assertThat(order(orderNo).getStatus()).isEqualTo("CANCELLED");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
        assertThat(paymentCount(orderNo)).isZero();
    }

    @Test
    @DisplayName("已支付订单不能取消")
    void paidOrderCannotCancel() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "15.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));
        assertThat(pay(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = cancel(user.token(), orderNo);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(16005);
        assertThat(order(orderNo).getStatus()).isEqualTo("PAID");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(terminalFlows(orderNo).get(0).getBizType()).isEqualTo("ORDER_DEDUCT");
    }

    @Test
    @DisplayName("订单列表与详情按用户隔离")
    void ordersAreUserIsolated() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser owner = registerOrderUser();
        OrderUser other = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "30.00", 10);
        Long addressId = createAddress(owner.token());
        assertThat(addCartItem(owner.token(), product.skuId(), 1).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(owner.token(), "order-" + UUID.randomUUID(), addressId));

        JsonNode ownerDetail = orderDetail(owner.token(), orderNo);
        assertThat(ownerDetail.path("code").asInt()).isZero();
        assertThat(ownerDetail.path("data").path("orderNo").asText()).isEqualTo(orderNo);

        JsonNode otherList = orderPage(other.token(), "");
        assertThat(otherList.path("data").path("total").asLong()).isZero();

        MvcResult otherDetail = getJson("/api/v1/orders/" + orderNo, other.token());
        assertThat(otherDetail.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(otherDetail).path("code").asInt()).isEqualTo(16006);

        MvcResult otherPay = pay(other.token(), orderNo);
        assertThat(otherPay.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(otherPay).path("code").asInt()).isEqualTo(16006);

        MvcResult otherCancel = cancel(other.token(), orderNo);
        assertThat(otherCancel.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(otherCancel).path("code").asInt()).isEqualTo(16006);
        assertThat(order(orderNo).getStatus()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("订单列表支持状态筛选")
    void orderListSupportsStatusFilter() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "10.00", 10);
        Long addressId = createAddress(user.token());

        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus()).isEqualTo(200);
        String first = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));
        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus()).isEqualTo(200);
        String second = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));
        assertThat(pay(user.token(), first).getResponse().getStatus()).isEqualTo(200);

        JsonNode pending = orderPage(user.token(), "?status=PENDING_PAYMENT");
        assertThat(pending.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(pending.path("data").path("list").get(0).path("orderNo").asText()).isEqualTo(second);

        JsonNode paid = orderPage(user.token(), "?status=PAID");
        assertThat(paid.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(paid.path("data").path("list").get(0).path("orderNo").asText()).isEqualTo(first);

        MvcResult invalid = getJson("/api/v1/orders?status=UNKNOWN", user.token());
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(invalid).path("code").asInt()).isEqualTo(16007);
    }
}
