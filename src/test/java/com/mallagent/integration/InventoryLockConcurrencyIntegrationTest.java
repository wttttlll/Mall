package com.mallagent.integration;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.entity.StockFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

class InventoryLockConcurrencyIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("并发同一订单重复锁定只生效一次")
    void concurrentSameOrderLocksOnce() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));

        List<Throwable> failures = runConcurrently(10, index -> lock(bizNo, items));

        assertThat(failures).isEmpty();
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        assertThat(stock(skuId).getLockedStock()).isEqualTo(2);
        assertThat(orderLockFlows(bizNo)).hasSize(1);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("多个订单并发争抢同一 SKU 不超卖")
    void multipleOrdersCompeteWithoutOversell() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");

        List<Throwable> failures = runConcurrently(20, index -> lock(
                "ORDER-" + UUID.randomUUID(),
                List.of(new StockItem(skuId, 1))));

        int successes = 20 - failures.size();
        assertThat(successes).isEqualTo(10);
        assertThat(failures).allMatch(failure ->
                failure instanceof BusinessException business && business.getCode() == 14003);
        assertThat(stock(skuId).getAvailableStock()).isZero();
        assertThat(stock(skuId).getLockedStock()).isEqualTo(10);
        assertThat(flows(skuId).stream()
                .filter(flow -> "ORDER_LOCK".equals(flow.getBizType()))
                .toList()).hasSize(10);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("多 SKU 反向输入顺序并发锁定不死锁")
    void reverseSkuOrderDoesNotDeadlock() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("lock-seed-" + UUID.randomUUID(), second, 10, "初始库存");

        List<Throwable> failures = runConcurrently(2, index -> {
            if (index == 0) {
                lock("ORDER-" + UUID.randomUUID(), List.of(
                        new StockItem(first, 1), new StockItem(second, 1)));
            } else {
                lock("ORDER-" + UUID.randomUUID(), List.of(
                        new StockItem(second, 1), new StockItem(first, 1)));
            }
        });

        assertThat(failures).isEmpty();
        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(first).getLockedStock()).isEqualTo(2);
        assertThat(stock(second).getAvailableStock()).isEqualTo(8);
        assertThat(stock(second).getLockedStock()).isEqualTo(2);
        assertReconciled(first);
        assertReconciled(second);
    }

    private void assertReconciled(Long skuId) {
        var stock = stock(skuId);
        List<StockFlow> flows = flows(skuId);
        int availableDelta = flows.stream().mapToInt(StockFlow::getChangeAvailable).sum();
        int lockedDelta = flows.stream().mapToInt(StockFlow::getChangeLocked).sum();
        assertThat(availableDelta).isEqualTo(stock.getAvailableStock());
        assertThat(lockedDelta).isEqualTo(stock.getLockedStock());
    }

    private List<Throwable> runConcurrently(int threads, ThrowingIntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    action.accept(index);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    @FunctionalInterface
    private interface ThrowingIntConsumer {
        void accept(int value) throws Exception;
    }
}
