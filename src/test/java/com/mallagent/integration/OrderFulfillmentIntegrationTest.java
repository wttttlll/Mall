package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.order.entity.OrderInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class OrderFulfillmentIntegrationTest extends AbstractOrderIntegrationTest {

    @Test
    @DisplayName("已支付订单发货后用户确认收货，库存不再变化")
    void shipThenConfirmReceipt() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "88.80", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));
        assertThat(pay(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();

        MvcResult shipped = ship(admin.accessToken(), orderNo, "顺丰速运", "SF123456789");
        assertThat(shipped.getResponse().getStatus()).isEqualTo(200);
        JsonNode shippedOrder = body(shipped).path("data");
        assertThat(shippedOrder.path("status").asText()).isEqualTo("SHIPPED");
        assertThat(shippedOrder.path("shippingCompany").asText()).isEqualTo("顺丰速运");
        assertThat(shippedOrder.path("trackingNo").asText()).isEqualTo("SF123456789");
        assertThat(shippedOrder.path("shippedAt").isNull()).isFalse();
        assertThat(shippedOrder.path("username").asText()).isEqualTo(user.username());

        OrderInfo afterShip = order(orderNo);
        assertThat(afterShip.getStatus()).isEqualTo("SHIPPED");
        assertThat(afterShip.getShippingCompany()).isEqualTo("顺丰速运");
        assertThat(afterShip.getTrackingNo()).isEqualTo("SF123456789");
        assertThat(afterShip.getShippedAt()).isNotNull();
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(terminalFlows(orderNo)).hasSize(1);

        MvcResult adminPage = getJson("/api/v1/admin/orders?orderNo=" + orderNo, admin.accessToken());
        assertThat(adminPage.getResponse().getStatus()).isEqualTo(200);
        JsonNode pageOrder = body(adminPage).path("data").path("list").get(0);
        assertThat(pageOrder.path("orderNo").asText()).isEqualTo(orderNo);
        assertThat(pageOrder.path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(pageOrder.path("shippingCompany").asText()).isEqualTo("顺丰速运");

        JsonNode userDetail = orderDetail(user.token(), orderNo);
        assertThat(userDetail.path("data").path("shippingCompany").asText()).isEqualTo("顺丰速运");
        assertThat(userDetail.path("data").path("trackingNo").asText()).isEqualTo("SF123456789");

        MvcResult completed = confirmReceipt(user.token(), orderNo);
        assertThat(completed.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(completed).path("data").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body(completed).path("data").path("completedAt").isNull()).isFalse();
        assertThat(order(orderNo).getStatus()).isEqualTo("COMPLETED");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(terminalFlows(orderNo)).hasSize(1);
    }

    @Test
    @DisplayName("未支付订单不能发货，未发货订单不能确认收货")
    void rejectInvalidStateTransitions() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 2).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));

        MvcResult shipUnpaid = ship(admin.accessToken(), orderNo, "中通快递", "ZT123456");
        assertThat(shipUnpaid.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(shipUnpaid).path("code").asInt()).isEqualTo(16005);
        OrderInfo unpaid = order(orderNo);
        assertThat(unpaid.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(unpaid.getShippingCompany()).isNull();
        assertThat(unpaid.getTrackingNo()).isNull();

        assertThat(pay(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);
        MvcResult confirmUnshipped = confirmReceipt(user.token(), orderNo);
        assertThat(confirmUnshipped.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(confirmUnshipped).path("code").asInt()).isEqualTo(16005);
        assertThat(order(orderNo).getStatus()).isEqualTo("PAID");
        assertThat(order(orderNo).getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("重复发货和重复确认收货幂等，不重复修改状态和时间")
    void duplicateShipAndConfirmAreIdempotent() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));
        assertThat(pay(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);

        MvcResult firstShip = ship(admin.accessToken(), orderNo, "顺丰速运", "SF-FIRST");
        assertThat(firstShip.getResponse().getStatus()).isEqualTo(200);
        String firstShippedAt = body(firstShip).path("data").path("shippedAt").asText();

        // 相同物流信息重复发货幂等成功。
        MvcResult sameShip = ship(admin.accessToken(), orderNo, "顺丰速运", "SF-FIRST");
        assertThat(sameShip.getResponse().getStatus()).isEqualTo(200);
        JsonNode repeatedShip = body(sameShip).path("data");
        assertThat(repeatedShip.path("status").asText()).isEqualTo("SHIPPED");
        assertThat(repeatedShip.path("shippingCompany").asText()).isEqualTo("顺丰速运");
        assertThat(repeatedShip.path("trackingNo").asText()).isEqualTo("SF-FIRST");
        assertThat(repeatedShip.path("shippedAt").asText()).isEqualTo(firstShippedAt);

        // 不同物流信息不能覆盖首次发货记录。
        MvcResult differentShip = ship(admin.accessToken(), orderNo, "中通快递", "ZT-SECOND");
        assertThat(differentShip.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(differentShip).path("code").asInt()).isEqualTo(16005);
        assertThat(order(orderNo).getShippingCompany()).isEqualTo("顺丰速运");
        assertThat(order(orderNo).getTrackingNo()).isEqualTo("SF-FIRST");

        MvcResult firstConfirm = confirmReceipt(user.token(), orderNo);
        assertThat(firstConfirm.getResponse().getStatus()).isEqualTo(200);
        String firstCompletedAt = body(firstConfirm).path("data").path("completedAt").asText();

        MvcResult secondConfirm = confirmReceipt(user.token(), orderNo);
        assertThat(secondConfirm.getResponse().getStatus()).isEqualTo(200);
        JsonNode repeatedConfirm = body(secondConfirm).path("data");
        assertThat(repeatedConfirm.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(repeatedConfirm.path("completedAt").asText()).isEqualTo(firstCompletedAt);
        assertThat(order(orderNo).getStatus()).isEqualTo("COMPLETED");
        assertThat(order(orderNo).getShippingCompany()).isEqualTo("顺丰速运");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(9);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
    }

    @Test
    @DisplayName("普通用户不能访问管理端订单查询和发货接口")
    void customerCannotAccessAdminOrderApi() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus()).isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));

        MvcResult list = getJson("/api/v1/admin/orders", user.token());
        assertThat(list.getResponse().getStatus()).isEqualTo(403);

        MvcResult detail = getJson("/api/v1/admin/orders/" + orderNo, user.token());
        assertThat(detail.getResponse().getStatus()).isEqualTo(403);

        MvcResult ship = ship(user.token(), orderNo, "顺丰速运", "SF-FORBIDDEN");
        assertThat(ship.getResponse().getStatus()).isEqualTo(403);
        assertThat(order(orderNo).getStatus()).isEqualTo("PENDING_PAYMENT");
    }

    private MvcResult ship(String token, String orderNo, String shippingCompany, String trackingNo)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("shippingCompany", shippingCompany);
        request.put("trackingNo", trackingNo);
        return postJson("/api/v1/admin/orders/" + orderNo + "/ship", request, token);
    }

    private MvcResult confirmReceipt(String token, String orderNo) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/orders/" + orderNo + "/confirm-receipt")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(builder).andReturn();
    }
}
