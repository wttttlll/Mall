package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.aftersale.entity.AfterSaleOrder;
import com.mallagent.module.aftersale.mapper.AfterSaleOrderMapper;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.payment.entity.PaymentRefund;
import com.mallagent.module.payment.mapper.PaymentRefundMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AfterSaleIntegrationTest extends AbstractOrderIntegrationTest {

    @Autowired
    private AfterSaleOrderMapper afterSaleOrderMapper;

    @Autowired
    private PaymentRefundMapper paymentRefundMapper;

    @Test
    @DisplayName("已完成订单成功申请整单售后，重复申请冲突，用户数据隔离")
    void completedOrderCanApplyAndUsersAreIsolated() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser owner = registerOrderUser();
        OrderUser other = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(owner.token());
        String orderNo = completedOrder(admin, owner, product, addressId, 1);

        MvcResult applied = apply(owner.token(), orderNo, "商品质量问题", "收到后发现瑕疵");
        assertThat(applied.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = body(applied).path("data");
        String afterSaleNo = data.path("afterSaleNo").asText();
        assertThat(data.path("status").asText()).isEqualTo("APPLIED");
        assertThat(data.path("orderNo").asText()).isEqualTo(orderNo);
        assertThat(data.path("refundAmount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(data.path("items")).hasSize(1);

        MvcResult duplicate = apply(owner.token(), orderNo, "质量问题", "重复申请");
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicate).path("code").asInt()).isEqualTo(19001);

        JsonNode list = body(getJson("/api/v1/after-sales?pageNum=1&pageSize=10", owner.token()));
        assertThat(list.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(list.path("data").path("list").get(0).path("afterSaleNo").asText())
                .isEqualTo(afterSaleNo);

        assertThat(body(getJson("/api/v1/after-sales/" + afterSaleNo, owner.token()))
                .path("data").path("status").asText()).isEqualTo("APPLIED");

        MvcResult otherDetail = getJson("/api/v1/after-sales/" + afterSaleNo, other.token());
        assertThat(otherDetail.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(otherDetail).path("code").asInt()).isEqualTo(19002);
        assertThat(body(getJson("/api/v1/after-sales", other.token()))
                .path("data").path("total").asLong()).isZero();

        MvcResult otherApply = apply(other.token(), orderNo, "不是我的订单", "越权申请");
        assertThat(otherApply.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(otherApply).path("code").asInt()).isEqualTo(19004);

        assertThat(getJson("/api/v1/admin/after-sales", owner.token()).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("待支付、已支付未完成和已取消订单不能申请售后")
    void incompleteOrdersCannotApply() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus())
                .isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));

        MvcResult pending = apply(user.token(), orderNo, "不想要了", null);
        assertThat(pending.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(pending).path("code").asInt()).isEqualTo(19004);

        assertThat(pay(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);
        MvcResult paid = apply(user.token(), orderNo, "不想要了", null);
        assertThat(paid.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(paid).path("code").asInt()).isEqualTo(19004);

        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus())
                .isEqualTo(200);
        String cancelledOrderNo = orderNo(createOrder(user.token(),
                "order-" + UUID.randomUUID(), addressId));
        assertThat(cancel(user.token(), cancelledOrderNo).getResponse().getStatus()).isEqualTo(200);
        MvcResult cancelled = apply(user.token(), cancelledOrderNo, "不想要了", null);
        assertThat(cancelled.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(cancelled).path("code").asInt()).isEqualTo(19004);
    }

    @Test
    @DisplayName("管理员拒绝售后不退款、不回补库存，拒绝后可重新申请")
    void rejectDoesNotRefundOrRestore() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = completedOrder(admin, user, product, addressId, 1);
        String afterSaleNo = afterSaleNo(apply(user.token(), orderNo, "商品问题", null));

        MvcResult rejected = reject(admin.accessToken(), afterSaleNo, "不符合退货条件");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(rejected).path("data").path("status").asText()).isEqualTo("REJECTED");
        assertThat(body(rejected).path("data").path("rejectReason").asText())
                .isEqualTo("不符合退货条件");
        assertThat(paymentRefundMapper.selectCount(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getAfterSaleNo, afterSaleNo))).isZero();
        assertThat(restoreFlows(afterSaleNo)).isEmpty();
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(9);

        // REJECTED 不占用订单的 active 唯一键，可以重新申请。
        MvcResult reapply = apply(user.token(), orderNo, "再次申请", "补充说明");
        assertThat(reapply.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(reapply).path("data").path("status").asText()).isEqualTo("APPLIED");
    }

    @Test
    @DisplayName("审核通过并确认退货后只退款、回补一次")
    void approveAndCompleteRefundAndRestoreOnce() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = completedOrder(admin, user, product, addressId, 2);
        String afterSaleNo = afterSaleNo(apply(user.token(), orderNo, "尺寸不合适", "整单退货"));
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);

        MvcResult approved = approve(admin.accessToken(), afterSaleNo);
        assertThat(approved.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(approved).path("data").path("status").asText()).isEqualTo("APPROVED");
        assertThat(paymentRefundMapper.selectCount(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getAfterSaleNo, afterSaleNo))).isZero();
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);

        // 并发重复完成：售后行锁保证只有一次退款和一次库存回补。
        List<MvcResult> completions = runConcurrently(3,
                index -> complete(admin.accessToken(), afterSaleNo));
        assertThat(completions).allSatisfy(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(200));
        assertThat(paymentRefundMapper.selectCount(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getAfterSaleNo, afterSaleNo))).isEqualTo(1);
        assertThat(paymentRefundMapper.selectCount(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getOrderNo, orderNo))).isEqualTo(1);
        assertThat(restoreFlows(afterSaleNo)).hasSize(1);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(order(orderNo).getStatus()).isEqualTo("COMPLETED");

        JsonNode detail = body(getJson("/api/v1/after-sales/" + afterSaleNo, user.token()));
        assertThat(detail.path("data").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.path("data").path("refundStatus").asText()).isEqualTo("SUCCESS");
        assertThat(detail.path("data").path("restoreStatus").asText()).isEqualTo("SUCCESS");
        assertThat(detail.path("data").path("refundNo").asText()).startsWith("REF");

        // 相同售后单重复完成仍幂等。
        assertThat(complete(admin.accessToken(), afterSaleNo).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(paymentRefundMapper.selectCount(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getOrderNo, orderNo))).isEqualTo(1);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);

        // 已完成的订单不能再次申请售后。
        MvcResult reapply = apply(user.token(), orderNo, "再次退款", null);
        assertThat(reapply.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(reapply).path("code").asInt()).isEqualTo(19001);

        // 不同售后单号不能再次回补同一订单。
        assertBusinessCode(() -> restore("AS-OTHER-" + UUID.randomUUID(),
                orderNo, List.of(new StockItem(product.skuId(), 2))), 14004);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("库存回补失败时退款记录和售后状态全部回滚")
    void failureDuringRestoreRollsBackRefundAndStatus() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = completedOrder(admin, user, product, addressId, 1);
        String afterSaleNo = afterSaleNo(apply(user.token(), orderNo, "退货", null));
        assertThat(approve(admin.accessToken(), afterSaleNo).getResponse().getStatus())
                .isEqualTo(200);

        // 删除原订单 ORDER_LOCK 流水，使库存 restore 在退款记录写入后失败。
        stockFlowMapper.delete(new LambdaQueryWrapper<StockFlow>()
                .eq(StockFlow::getBizType, "ORDER_LOCK")
                .eq(StockFlow::getBizNo, orderNo));

        MvcResult completed = complete(admin.accessToken(), afterSaleNo);
        assertThat(completed.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(completed).path("code").asInt()).isEqualTo(14004);
        assertThat(afterSale(afterSaleNo).getStatus()).isEqualTo("APPROVED");
        assertThat(paymentRefundMapper.selectCount(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getAfterSaleNo, afterSaleNo))).isZero();
        assertThat(restoreFlows(afterSaleNo)).isEmpty();
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(9);
    }

    private String completedOrder(TokenPair admin,
                                  OrderUser user,
                                  OrderProduct product,
                                  Long addressId,
                                  int quantity) throws Exception {
        assertThat(addCartItem(user.token(), product.skuId(), quantity).getResponse().getStatus())
                .isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));
        assertThat(pay(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);
        assertThat(ship(admin.accessToken(), orderNo, "顺丰速运",
                "SF-" + UUID.randomUUID()).getResponse().getStatus()).isEqualTo(200);
        assertThat(confirmReceipt(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);
        return orderNo;
    }

    private MvcResult apply(String token, String orderNo, String reason, String description)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderNo", orderNo);
        request.put("reason", reason);
        if (description != null) {
            request.put("description", description);
        }
        return postJson("/api/v1/after-sales", request, token);
    }

    private MvcResult approve(String token, String afterSaleNo) throws Exception {
        return postJson("/api/v1/admin/after-sales/" + afterSaleNo + "/approve", Map.of(), token);
    }

    private MvcResult reject(String token, String afterSaleNo, String reason) throws Exception {
        return postJson("/api/v1/admin/after-sales/" + afterSaleNo + "/reject",
                Map.of("rejectReason", reason), token);
    }

    private MvcResult complete(String token, String afterSaleNo) throws Exception {
        return postJson("/api/v1/admin/after-sales/" + afterSaleNo + "/complete", Map.of(), token);
    }

    private MvcResult ship(String token, String orderNo, String company, String trackingNo)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("shippingCompany", company);
        request.put("trackingNo", trackingNo);
        return postJson("/api/v1/admin/orders/" + orderNo + "/ship", request, token);
    }

    private MvcResult confirmReceipt(String token, String orderNo) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/orders/" + orderNo + "/confirm-receipt")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(builder).andReturn();
    }

    private String afterSaleNo(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("afterSaleNo").asText();
    }

    private AfterSaleOrder afterSale(String afterSaleNo) {
        return afterSaleOrderMapper.selectByAfterSaleNo(afterSaleNo);
    }
}
