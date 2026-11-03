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

class InventoryDeductReleaseConcurrencyIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("同一订单 deduct 与 release 并发，恰好一个成功")
    void deductAndReleaseCompeteExactlyOneWins() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("competition-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(bizNo, items);

        List<Throwable> failures = runConcurrently(2, index -> {
            if (index == 0) {
                deduct(bizNo, items);
            } else {
                release(bizNo, items);
            }
        });

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failures.get(0)).getCode()).isEqualTo(14004);

        List<StockFlow> terminals = terminalFlows(bizNo);
        assertThat(terminals).hasSize(1);
        assertThat(stock(skuId).getLockedStock()).isZero();
        if ("ORDER_DEDUCT".equals(terminals.get(0).getBizType())) {
            assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        } else {
            assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        }
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("多 SKU 并发 deduct/release 不出现部分终态")
    void multiSkuCompetitionHasNoPartialTerminal() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("competition-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("competition-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(
                new StockItem(first, 2),
                new StockItem(second, 2));
        lock(bizNo, items);

        List<Throwable> failures = runConcurrently(2, index -> {
            if (index == 0) {
                deduct(bizNo, items);
            } else {
                release(bizNo, items);
            }
        });

        assertThat(failures).hasSize(1);
        List<StockFlow> terminals = terminalFlows(bizNo);
        assertThat(terminals).hasSize(2);
        assertThat(terminals).allMatch(flow -> flow.getBizType().equals(terminals.get(0).getBizType()));
        assertThat(stock(first).getLockedStock()).isZero();
        assertThat(stock(second).getLockedStock()).isZero();
        assertReconciled(first);
        assertReconciled(second);
    }

    @Test
    @DisplayName("并发重复 deduct 只生效一次")
    void concurrentDuplicateDeductAppliesOnce() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("duplicate-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(bizNo, items);

        List<Throwable> failures = runConcurrently(10, index -> deduct(bizNo, items));

        assertThat(failures).isEmpty();
        assertThat(terminalFlows(bizNo)).hasSize(1);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
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
