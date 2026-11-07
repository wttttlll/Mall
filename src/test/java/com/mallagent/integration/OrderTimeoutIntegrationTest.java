package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.service.OrderService;
import com.mallagent.module.order.service.OrderTimeoutScheduler;
import com.mallagent.module.payment.dto.payment.PaymentResponse;
import com.mallagent.module.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTimeoutIntegrationTest extends AbstractOrderIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderTimeoutScheduler orderTimeoutScheduler;

    @Test
    @DisplayName("超时订单被扫描取消并释放库存，重复扫描不重复释放")
    void expiredOrderCancelledAndReleaseIsIdempotent() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = createPendingOrder(user, product, addressId, 2);
        // 放到极早时间，确保它在测试库已有历史待支付订单之前被扫描到。
        setCreatedAt(orderNo, LocalDateTime.of(2000, 1, 1, 0, 0));

        int cancelled = orderTimeoutScheduler.scanExpiredOrders();

        assertThat(cancelled).isGreaterThanOrEqualTo(1);
        assertThat(order(orderNo).getStatus()).isEqualTo("CANCELLED");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        List<StockFlow> flows = terminalFlows(orderNo);
        assertThat(flows).hasSize(1);
        assertThat(flows.get(0).getBizType()).isEqualTo("ORDER_RELEASE");
        assertThat(paymentCount(orderNo)).isZero();

        boolean repeated = orderService.cancelExpired(orderNo, LocalDateTime.now());
        assertThat(repeated).isFalse();
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("未超过 30 分钟的待支付订单不会被取消")
    void recentOrderIsNotCancelled() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = createPendingOrder(user, product, addressId, 2);
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(29);
        setCreatedAt(orderNo, createdAt);

        boolean cancelled = orderService.cancelExpired(orderNo, LocalDateTime.now());

        assertThat(cancelled).isFalse();
        assertThat(order(orderNo).getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("超过支付截止时间后支付被拒绝，随后可被超时取消")
    void paymentAfterDeadlineIsRejected() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = createPendingOrder(user, product, addressId, 2);
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(31);
        setCreatedAt(orderNo, createdAt);

        assertThatThrownBy(() -> paymentService.pay(user.userId(), orderNo))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(17005);
        assertThat(order(orderNo).getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentCount(orderNo)).isZero();
        assertThat(stock(product.skuId()).getLockedStock()).isEqualTo(2);

        assertThat(orderService.cancelExpired(orderNo, LocalDateTime.now())).isTrue();
        assertThat(order(orderNo).getStatus()).isEqualTo("CANCELLED");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(terminalFlows(orderNo).get(0).getBizType()).isEqualTo("ORDER_RELEASE");
    }

    @Test
    @DisplayName("已支付订单不会被超时扫描取消")
    void paidOrderIsNotCancelledByTimeout() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = createPendingOrder(user, product, addressId, 2);
        assertThat(pay(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);
        setCreatedAt(orderNo, LocalDateTime.now().minusMinutes(31));

        boolean cancelled = orderService.cancelExpired(orderNo, LocalDateTime.now());

        assertThat(cancelled).isFalse();
        assertThat(order(orderNo).getStatus()).isEqualTo("PAID");
        assertThat(paymentCount(orderNo)).isEqualTo(1);
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(terminalFlows(orderNo).get(0).getBizType()).isEqualTo("ORDER_DEDUCT");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
    }

    @Test
    @DisplayName("多个扫描实例并发取消同一订单只释放一次库存")
    void concurrentScansReleaseOnlyOnce() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = createPendingOrder(user, product, addressId, 2);
        setCreatedAt(orderNo, LocalDateTime.now().minusMinutes(31));

        List<Boolean> results = runTasks(5,
                () -> orderService.cancelExpired(orderNo, LocalDateTime.now()));

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(order(orderNo).getStatus()).isEqualTo("CANCELLED");
        assertThat(terminalFlows(orderNo)).hasSize(1);
        assertThat(terminalFlows(orderNo).get(0).getBizType()).isEqualTo("ORDER_RELEASE");
        assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
    }

    @Test
    @DisplayName("单笔订单取消失败不影响同批其他订单")
    void singleOrderFailureDoesNotBlockOtherOrders() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct validProduct = createOrderProduct(admin.accessToken(), "20.00", 10);
        OrderProduct brokenProduct = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());

        String validOrder = createPendingOrder(user, validProduct, addressId, 2);
        String brokenOrder = createPendingOrder(user, brokenProduct, addressId, 2);
        // 删除损坏订单的 ORDER_LOCK 流水，使其释放库存时失败；扫描必须继续处理其他订单。
        stockFlowMapper.delete(new LambdaQueryWrapper<StockFlow>()
                .eq(StockFlow::getBizType, "ORDER_LOCK")
                .eq(StockFlow::getBizNo, brokenOrder));

        setCreatedAt(validOrder, LocalDateTime.of(1900, 1, 1, 0, 0));
        setCreatedAt(brokenOrder, LocalDateTime.of(1900, 1, 2, 0, 0));

        int cancelled = orderTimeoutScheduler.scanExpiredOrders();

        assertThat(cancelled).isGreaterThanOrEqualTo(1);
        assertThat(order(validOrder).getStatus()).isEqualTo("CANCELLED");
        assertThat(terminalFlows(validOrder)).hasSize(1);
        assertThat(terminalFlows(validOrder).get(0).getBizType()).isEqualTo("ORDER_RELEASE");
        assertThat(stock(validProduct.skuId()).getAvailableStock()).isEqualTo(10);
        assertThat(stock(validProduct.skuId()).getLockedStock()).isZero();

        // 失败订单保持待支付，库存未被错误释放。
        assertThat(order(brokenOrder).getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(stock(brokenProduct.skuId()).getAvailableStock()).isEqualTo(8);
        assertThat(stock(brokenProduct.skuId()).getLockedStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("支付与超时取消并发时状态和库存只能有一个结果")
    void payAndTimeoutCompeteSafely() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = createPendingOrder(user, product, addressId, 2);
        // 支付截止时间还有 30 秒，随后模拟超时任务在截止后扫描。
        setCreatedAt(orderNo, LocalDateTime.now().minusMinutes(30).plusSeconds(30));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<PaymentResponse> payFuture = pool.submit(() -> {
            start.await(10, TimeUnit.SECONDS);
            return paymentService.pay(user.userId(), orderNo);
        });
        Future<Boolean> timeoutFuture = pool.submit(() -> {
            start.await(10, TimeUnit.SECONDS);
            return orderService.cancelExpired(orderNo, LocalDateTime.now().plusMinutes(5));
        });
        start.countDown();

        boolean paySucceeded = false;
        boolean timeoutCancelled = false;
        try {
            payFuture.get(30, TimeUnit.SECONDS);
            paySucceeded = true;
        } catch (ExecutionException ex) {
            assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
        }
        timeoutCancelled = timeoutFuture.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(paySucceeded ^ timeoutCancelled).isTrue();
        OrderInfo finalOrder = order(orderNo);
        List<StockFlow> terminals = terminalFlows(orderNo);
        assertThat(terminals).hasSize(1);
        if (paySucceeded) {
            assertThat(finalOrder.getStatus()).isEqualTo("PAID");
            assertThat(terminals.get(0).getBizType()).isEqualTo("ORDER_DEDUCT");
            assertThat(paymentCount(orderNo)).isEqualTo(1);
            assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(8);
        } else {
            assertThat(finalOrder.getStatus()).isEqualTo("CANCELLED");
            assertThat(terminals.get(0).getBizType()).isEqualTo("ORDER_RELEASE");
            assertThat(paymentCount(orderNo)).isZero();
            assertThat(stock(product.skuId()).getAvailableStock()).isEqualTo(10);
        }
        assertThat(stock(product.skuId()).getLockedStock()).isZero();
    }

    private String createPendingOrder(OrderUser user,
                                      OrderProduct product,
                                      Long addressId,
                                      int quantity) throws Exception {
        assertThat(addCartItem(user.token(), product.skuId(), quantity).getResponse().getStatus())
                .isEqualTo(200);
        return orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));
    }

    private void setCreatedAt(String orderNo, LocalDateTime createdAt) {
        OrderInfo info = order(orderNo);
        orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, info.getId())
                .set(OrderInfo::getCreatedAt, createdAt));
    }

    private List<Boolean> runTasks(int count, BooleanTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return task.run();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>(count);
            for (Future<Boolean> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface BooleanTask {
        boolean run() throws Exception;
    }
}
