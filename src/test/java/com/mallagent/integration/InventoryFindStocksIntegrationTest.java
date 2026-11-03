package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.api.dto.StockSnapshot;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.inventory.entity.StockSku;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 库存批量快照查询测试。所有断言均校验数据库真实状态与查询副作用。
 */
class InventoryFindStocksIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("单 SKU 查询返回真实库存快照")
    void singleSkuSnapshot() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("find-stocks-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");

        assertThat(inventoryStockApi.findStocks(List.of(skuId)))
                .containsExactly(new StockSnapshot(skuId, 10, 0, true));
    }

    @Test
    @DisplayName("多 SKU 批量查询保持首次出现顺序并去重")
    void multiSkuBatchDeduplicatesAndPreservesOrder() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("find-stocks-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("find-stocks-seed-" + UUID.randomUUID(), second, 20, "初始库存");

        List<StockItem> items = List.of(new StockItem(second, 3));
        lock("ORDER-" + UUID.randomUUID(), items);

        assertThat(inventoryStockApi.findStocks(List.of(second, first, second, first)))
                .containsExactly(
                        new StockSnapshot(second, 17, 3, true),
                        new StockSnapshot(first, 10, 0, true));
    }

    @Test
    @DisplayName("已初始化与未初始化 SKU 混合查询")
    void mixedInitializedAndUninitialized() throws Exception {
        TokenPair admin = adminLogin();
        Long initialized = createInventorySku(admin);
        Long uninitialized = createInventorySku(admin);
        adjustDirect("find-stocks-seed-" + UUID.randomUUID(), initialized, 8, "初始库存");

        assertThat(inventoryStockApi.findStocks(List.of(uninitialized, initialized)))
                .containsExactly(
                        new StockSnapshot(uninitialized, 0, 0, false),
                        new StockSnapshot(initialized, 8, 0, true));
        assertThat(stockSkuMapper.selectBySkuId(uninitialized)).isNull();
    }

    @Test
    @DisplayName("空集合查询返回空结果")
    void emptyCollectionReturnsEmpty() {
        assertThat(inventoryStockApi.findStocks(List.of())).isEmpty();
    }

    @Test
    @DisplayName("null 集合、null SKU、非正数 SKU 明确拒绝")
    void invalidArgumentsRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);

        assertBusinessCode(() -> inventoryStockApi.findStocks(null), 14009);
        assertBusinessCode(() -> inventoryStockApi.findStocks(Arrays.asList(skuId, null)), 14009);
        assertBusinessCode(() -> inventoryStockApi.findStocks(List.of(0L)), 14009);
        assertBusinessCode(() -> inventoryStockApi.findStocks(List.of(-1L)), 14009);
    }

    @Test
    @DisplayName("查询不修改库存余额、不创建库存记录或流水")
    void queryHasNoSideEffects() throws Exception {
        TokenPair admin = adminLogin();
        Long initialized = createInventorySku(admin);
        Long uninitialized = createInventorySku(admin);
        adjustDirect("find-stocks-seed-" + UUID.randomUUID(), initialized, 10, "初始库存");

        List<Long> skuIds = List.of(initialized, uninitialized, initialized);
        StockSku before = stock(initialized);
        long flowsBefore = flowCount(List.of(initialized, uninitialized));

        inventoryStockApi.findStocks(skuIds);

        StockSku after = stock(initialized);
        assertThat(after.getAvailableStock()).isEqualTo(before.getAvailableStock());
        assertThat(after.getLockedStock()).isEqualTo(before.getLockedStock());
        assertThat(flowCount(List.of(initialized, uninitialized))).isEqualTo(flowsBefore);
        assertThat(stock(uninitialized)).isNull();
    }

    @Test
    @DisplayName("lock、deduct、release、restore 后快照反映真实余额")
    void snapshotReflectsInventoryLifecycle() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("find-stocks-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        assertSnapshot(skuId, 10, 0);

        String deductOrder = "ORDER-" + UUID.randomUUID();
        List<StockItem> deductItems = List.of(new StockItem(skuId, 3));
        lock(deductOrder, deductItems);
        assertSnapshot(skuId, 7, 3);

        deduct(deductOrder, deductItems);
        assertSnapshot(skuId, 7, 0);

        String releaseOrder = "ORDER-" + UUID.randomUUID();
        List<StockItem> releaseItems = List.of(new StockItem(skuId, 2));
        lock(releaseOrder, releaseItems);
        release(releaseOrder, releaseItems);
        assertSnapshot(skuId, 7, 0);

        restore("AFTER-" + UUID.randomUUID(), deductOrder, deductItems);
        assertSnapshot(skuId, 10, 0);
    }

    private void assertSnapshot(Long skuId, int availableStock, int lockedStock) {
        assertThat(inventoryStockApi.findStocks(List.of(skuId)))
                .containsExactly(new StockSnapshot(skuId, availableStock, lockedStock, true));
    }

    private long flowCount(List<Long> skuIds) {
        return stockFlowMapper.selectCount(new LambdaQueryWrapper<StockFlow>()
                .in(StockFlow::getSkuId, skuIds));
    }

    private void assertBusinessCode(Runnable action, int code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(code);
    }
}
