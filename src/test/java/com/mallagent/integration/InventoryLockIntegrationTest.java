package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.inventory.entity.StockSku;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryLockIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("单 SKU 锁定成功并记录完整流水")
    void singleSkuLockSuccess() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();

        lock(bizNo, List.of(new StockItem(skuId, 3)));

        StockSku stock = stock(skuId);
        assertThat(stock.getAvailableStock()).isEqualTo(7);
        assertThat(stock.getLockedStock()).isEqualTo(3);

        List<StockFlow> flows = orderLockFlows(bizNo);
        assertThat(flows).hasSize(1);
        StockFlow flow = flows.get(0);
        assertThat(flow.getBizType()).isEqualTo("ORDER_LOCK");
        assertThat(flow.getQuantity()).isEqualTo(3);
        assertThat(flow.getChangeAvailable()).isEqualTo(-3);
        assertThat(flow.getChangeLocked()).isEqualTo(3);
        assertThat(flow.getAvailableBefore()).isEqualTo(10);
        assertThat(flow.getAvailableAfter()).isEqualTo(7);
        assertThat(flow.getLockedBefore()).isZero();
        assertThat(flow.getLockedAfter()).isEqualTo(3);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("多 SKU 锁定成功")
    void multiSkuLockSuccess() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("lock-seed-" + UUID.randomUUID(), second, 20, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();

        lock(bizNo, List.of(new StockItem(second, 5), new StockItem(first, 2)));

        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(first).getLockedStock()).isEqualTo(2);
        assertThat(stock(second).getAvailableStock()).isEqualTo(15);
        assertThat(stock(second).getLockedStock()).isEqualTo(5);
        assertThat(orderLockFlows(bizNo)).hasSize(2);
        assertReconciled(first);
        assertReconciled(second);
    }

    @Test
    @DisplayName("库存不足拒绝，不写流水")
    void insufficientStockRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), skuId, 2, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();

        assertBusinessCode(() -> lock(bizNo, List.of(new StockItem(skuId, 3))), 14003);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(2);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(orderLockFlows(bizNo)).isEmpty();
    }

    @Test
    @DisplayName("未初始化库存拒绝")
    void uninitializedStockRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        String bizNo = "ORDER-" + UUID.randomUUID();

        assertBusinessCode(() -> lock(bizNo, List.of(new StockItem(skuId, 1))), 14002);

        assertThat(stock(skuId)).isNull();
        assertThat(orderLockFlows(bizNo)).isEmpty();
    }

    @Test
    @DisplayName("多 SKU 部分失败时整体回滚")
    void multiSkuPartialFailureRollsBack() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("lock-seed-" + UUID.randomUUID(), second, 1, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();

        assertBusinessCode(() -> lock(bizNo, List.of(
                new StockItem(first, 5),
                new StockItem(second, 2))), 14003);

        assertThat(stock(first).getAvailableStock()).isEqualTo(10);
        assertThat(stock(first).getLockedStock()).isZero();
        assertThat(stock(second).getAvailableStock()).isEqualTo(1);
        assertThat(stock(second).getLockedStock()).isZero();
        assertThat(orderLockFlows(bizNo)).isEmpty();
    }

    @Test
    @DisplayName("同订单完整请求重复锁定只生效一次")
    void sameOrderSameItemsIsIdempotent() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));

        lock(bizNo, items);
        lock(bizNo, items);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        assertThat(stock(skuId).getLockedStock()).isEqualTo(2);
        assertThat(orderLockFlows(bizNo)).hasSize(1);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("多 SKU 同订单重复锁定只生效一次")
    void sameMultiSkuOrderIsIdempotent() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("lock-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(
                new StockItem(second, 3),
                new StockItem(first, 2));

        lock(bizNo, items);
        lock(bizNo, items);

        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(first).getLockedStock()).isEqualTo(2);
        assertThat(stock(second).getAvailableStock()).isEqualTo(7);
        assertThat(stock(second).getLockedStock()).isEqualTo(3);
        assertThat(orderLockFlows(bizNo)).hasSize(2);
        assertReconciled(first);
        assertReconciled(second);
    }

    @Test
    @DisplayName("同订单不同数量冲突")
    void sameOrderDifferentQuantityConflicts() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        lock(bizNo, List.of(new StockItem(skuId, 2)));

        assertBusinessCode(() -> lock(bizNo, List.of(new StockItem(skuId, 3))), 14004);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        assertThat(stock(skuId).getLockedStock()).isEqualTo(2);
        assertThat(orderLockFlows(bizNo)).hasSize(1);
    }

    @Test
    @DisplayName("同订单不同 SKU 集合冲突，包括额外 SKU")
    void sameOrderDifferentSkuSetConflicts() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("lock-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        lock(bizNo, List.of(new StockItem(first, 2)));

        assertBusinessCode(() -> lock(bizNo, List.of(new StockItem(second, 2))), 14004);
        assertBusinessCode(() -> lock(bizNo, List.of(
                new StockItem(first, 2),
                new StockItem(second, 2))), 14004);

        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(first).getLockedStock()).isEqualTo(2);
        assertThat(stock(second).getAvailableStock()).isEqualTo(10);
        assertThat(stock(second).getLockedStock()).isZero();
        assertThat(orderLockFlows(bizNo)).hasSize(1);
    }

    @Test
    @DisplayName("非法参数与 INT 溢出边界")
    void invalidParametersAndOverflow() throws Exception {
        assertBusinessCode(() -> lock(" ", List.of(new StockItem(1L, 1))), 14009);
        assertBusinessCode(() -> lock("ORDER-" + UUID.randomUUID(), List.of()), 14009);
        assertBusinessCode(() -> lock("ORDER-" + UUID.randomUUID(), List.of(
                new StockItem(1L, 1), new StockItem(1L, 2))), 14009);
        assertBusinessCode(() -> lock("ORDER-" + UUID.randomUUID(), List.of(
                new StockItem(1L, 0))), 14005);
        assertBusinessCode(() -> lock("ORDER-" + UUID.randomUUID(), List.of(
                new StockItem(1L, -1))), 14005);
        assertBusinessCode(() -> lock("ORDER-" + UUID.randomUUID(), List.of(
                new StockItem(0L, 1))), 14009);
        assertBusinessCode(() -> lock("X".repeat(65), List.of(new StockItem(1L, 1))), 14009);

        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("lock-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        stockSkuMapper.update(null, new LambdaUpdateWrapper<StockSku>()
                .eq(StockSku::getSkuId, skuId)
                .set(StockSku::getLockedStock, Integer.MAX_VALUE));
        String bizNo = "ORDER-" + UUID.randomUUID();

        assertBusinessCode(() -> lock(bizNo, List.of(new StockItem(skuId, 1))), 14003);
        assertThat(orderLockFlows(bizNo)).isEmpty();
    }

    private void assertBusinessCode(Runnable action, int code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(code);
    }

    private void assertReconciled(Long skuId) {
        StockSku stock = stock(skuId);
        List<StockFlow> flows = flows(skuId);
        int availableDelta = flows.stream().mapToInt(StockFlow::getChangeAvailable).sum();
        int lockedDelta = flows.stream().mapToInt(StockFlow::getChangeLocked).sum();
        assertThat(availableDelta).isEqualTo(stock.getAvailableStock());
        assertThat(lockedDelta).isEqualTo(stock.getLockedStock());
    }
}
