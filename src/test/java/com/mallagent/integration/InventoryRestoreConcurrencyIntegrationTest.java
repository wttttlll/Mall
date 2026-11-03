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

class InventoryRestoreConcurrencyIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("并发不同售后单回补同一订单，最多一个成功")
    void differentAftersalesCompeteAtMostOneWins() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("restore-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(
                new StockItem(first, 2),
                new StockItem(second, 3));
        lock(orderNo, items);
        deduct(orderNo, items);
        String firstAftersale = "AFTER-" + UUID.randomUUID();
        String secondAftersale = "AFTER-" + UUID.randomUUID();

        List<Throwable> failures = runConcurrently(2, index -> {
            if (index == 0) {
                restore(firstAftersale, orderNo, items);
            } else {
                restore(secondAftersale, orderNo, items);
            }
        });

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failures.get(0)).getCode()).isEqualTo(14004);

        int totalRestoreFlows = restoreFlows(firstAftersale).size()
                + restoreFlows(secondAftersale).size();
        assertThat(totalRestoreFlows).isEqualTo(2);
        assertThat(stock(first).getAvailableStock()).isEqualTo(10);
        assertThat(stock(second).getAvailableStock()).isEqualTo(10);
        assertReconciled(first);
        assertReconciled(second);
    }

    @Test
    @DisplayName("并发同一售后单重复回补只生效一次")
    void sameAftersaleConcurrentRestoreAppliesOnce() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(orderNo, items);
        deduct(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        List<Throwable> failures = runConcurrently(10, index -> restore(aftersaleNo, orderNo, items));

        assertThat(failures).isEmpty();
        assertThat(restoreFlows(aftersaleNo)).hasSize(1);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertReconciled(skuId);
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
