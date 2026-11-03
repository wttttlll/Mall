package com.mallagent.integration;

import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.inventory.entity.StockSku;
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

class InventoryAdminConcurrencyIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("不同幂等键并发初始化同一 SKU 正确累计")
    void concurrentInitializationAccumulates() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);

        List<Throwable> failures = runConcurrently(2, index -> adjustDirect(
                "init-concurrent-" + UUID.randomUUID(),
                skuId,
                index == 0 ? 10 : 20,
                "并发初始化"));

        assertThat(failures).isEmpty();
        StockSku stock = stock(skuId);
        assertThat(stock).isNotNull();
        assertThat(stock.getAvailableStock()).isEqualTo(30);
        assertThat(stock.getLockedStock()).isZero();
        assertThat(flows(skuId)).hasSize(2);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("并发同 Key 调整只生效一次")
    void concurrentSameKeyAppliesOnce() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        String key = "same-key-" + UUID.randomUUID();

        List<Throwable> failures = runConcurrently(10, index -> adjustDirect(key, skuId, 10, "并发同 Key"));

        assertThat(failures).isEmpty();
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(flows(skuId)).hasSize(1);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("并发正负调整不丢更新")
    void concurrentPositiveAndNegativeAdjustments() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("seed-" + UUID.randomUUID(), skuId, 100, "初始库存");

        List<Throwable> failures = runConcurrently(20, index -> adjustDirect(
                "mixed-" + UUID.randomUUID(),
                skuId,
                index % 2 == 0 ? 1 : -1,
                "并发调整"));

        assertThat(failures).isEmpty();
        StockSku stock = stock(skuId);
        assertThat(stock.getAvailableStock()).isEqualTo(100);
        assertThat(stock.getLockedStock()).isZero();
        assertThat(flows(skuId)).hasSize(21);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("管理端调整与订单锁定并发，余额和流水正确")
    void adminAdjustAndOrderLockConcurrent() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 3));

        List<Throwable> failures = runConcurrently(2, index -> {
            if (index == 0) {
                lock(orderNo, items);
            } else {
                adjustDirect("concurrent-admin-" + UUID.randomUUID(), skuId, 5, "并发补货");
            }
        });

        assertThat(failures).isEmpty();
        StockSku stock = stock(skuId);
        assertThat(stock.getAvailableStock()).isEqualTo(12);
        assertThat(stock.getLockedStock()).isEqualTo(3);
        assertThat(orderLockFlows(orderNo)).hasSize(1);
        assertReconciled(skuId);
    }

    private void assertReconciled(Long skuId) {
        StockSku stock = stock(skuId);
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
