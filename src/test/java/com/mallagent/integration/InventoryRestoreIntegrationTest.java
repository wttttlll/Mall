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

class InventoryRestoreIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("单 SKU 整单回补成功，流水前后值准确")
    void singleSkuRestoreSuccess() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 3));
        lock(orderNo, items);
        deduct(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        restore(aftersaleNo, orderNo, items);

        StockSku stock = stock(skuId);
        assertThat(stock.getAvailableStock()).isEqualTo(10);
        assertThat(stock.getLockedStock()).isZero();

        List<StockFlow> flows = restoreFlows(aftersaleNo);
        assertThat(flows).hasSize(1);
        StockFlow flow = flows.get(0);
        assertThat(flow.getBizType()).isEqualTo("RETURN_RESTORE");
        assertThat(flow.getRefBizNo()).isEqualTo(orderNo);
        assertThat(flow.getQuantity()).isEqualTo(3);
        assertThat(flow.getChangeAvailable()).isEqualTo(3);
        assertThat(flow.getChangeLocked()).isZero();
        assertThat(flow.getAvailableBefore()).isEqualTo(7);
        assertThat(flow.getAvailableAfter()).isEqualTo(10);
        assertThat(flow.getLockedBefore()).isZero();
        assertThat(flow.getLockedAfter()).isZero();
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("多 SKU 整单回补成功")
    void multiSkuRestoreSuccess() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("restore-seed-" + UUID.randomUUID(), second, 20, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(
                new StockItem(second, 5),
                new StockItem(first, 2));
        lock(orderNo, items);
        deduct(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        restore(aftersaleNo, orderNo, items);

        assertThat(stock(first).getAvailableStock()).isEqualTo(10);
        assertThat(stock(first).getLockedStock()).isZero();
        assertThat(stock(second).getAvailableStock()).isEqualTo(20);
        assertThat(stock(second).getLockedStock()).isZero();
        assertThat(restoreFlows(aftersaleNo)).hasSize(2);
        assertReconciled(first);
        assertReconciled(second);
    }

    @Test
    @DisplayName("未 lock 直接回补拒绝")
    void restoreWithoutLockRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(skuId, 1))), 14004);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(restoreFlows(aftersaleNo)).isEmpty();
    }

    @Test
    @DisplayName("lock 后未 deduct 直接回补拒绝")
    void restoreBeforeDeductRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        assertBusinessCode(() -> restore(aftersaleNo, orderNo, items), 14004);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        assertThat(stock(skuId).getLockedStock()).isEqualTo(2);
        assertThat(restoreFlows(aftersaleNo)).isEmpty();
    }

    @Test
    @DisplayName("release 后回补拒绝")
    void restoreAfterReleaseRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(orderNo, items);
        release(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        assertBusinessCode(() -> restore(aftersaleNo, orderNo, items), 14004);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(restoreFlows(aftersaleNo)).isEmpty();
    }

    @Test
    @DisplayName("回补 SKU 集合缺失、额外或数量不一致拒绝")
    void restoreSkuSetOrQuantityMismatchRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("restore-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(
                new StockItem(first, 2),
                new StockItem(second, 1));
        lock(orderNo, items);
        deduct(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(first, 2))), 14004);
        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(first, 2), new StockItem(second, 1),
                        new StockItem(999_999_999L, 1))), 14004);
        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(first, 3), new StockItem(second, 1))), 14004);

        assertThat(restoreFlows(aftersaleNo)).isEmpty();
        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(second).getAvailableStock()).isEqualTo(9);
    }

    @Test
    @DisplayName("售后单号、原订单号、SKU、数量非法时拒绝")
    void restoreCommandValidationRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(orderNo, items);
        deduct(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();
        String tooLong = "B".repeat(65);

        assertBusinessCode(() -> restore("", orderNo, items), 14009);
        assertBusinessCode(() -> restore("   ", orderNo, items), 14009);
        assertBusinessCode(() -> restore(tooLong, orderNo, items), 14009);
        assertBusinessCode(() -> restore(aftersaleNo, "", items), 14009);
        assertBusinessCode(() -> restore(aftersaleNo, tooLong, items), 14009);
        assertBusinessCode(() -> restore(aftersaleNo, orderNo, List.of()), 14009);
        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(0L, 1))), 14009);
        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(skuId, 1), new StockItem(skuId, 1))), 14009);
        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(skuId, 0))), 14005);
        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(skuId, -1))), 14005);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(restoreFlows(aftersaleNo)).isEmpty();
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("同一售后单重复回补幂等，只增加一次库存")
    void sameAftersaleRestoreIsIdempotent() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 3));
        lock(orderNo, items);
        deduct(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        restore(aftersaleNo, orderNo, items);
        restore(aftersaleNo, orderNo, items);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(restoreFlows(aftersaleNo)).hasSize(1);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("同一售后单不同订单号冲突")
    void sameAftersaleDifferentOrderConflicts() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String firstOrder = "ORDER-" + UUID.randomUUID();
        String secondOrder = "ORDER-" + UUID.randomUUID();
        List<StockItem> firstItems = List.of(new StockItem(skuId, 3));
        List<StockItem> secondItems = List.of(new StockItem(skuId, 2));
        lock(firstOrder, firstItems);
        deduct(firstOrder, firstItems);
        lock(secondOrder, secondItems);
        deduct(secondOrder, secondItems);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();
        restore(aftersaleNo, firstOrder, firstItems);

        assertBusinessCode(() -> restore(aftersaleNo, secondOrder, secondItems), 14004);

        assertThat(restoreFlows(aftersaleNo)).hasSize(1);
        assertThat(restoreFlows(aftersaleNo).get(0).getRefBizNo()).isEqualTo(firstOrder);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("同一售后单不同 SKU 集合或数量冲突")
    void sameAftersaleDifferentSetOrQuantityConflicts() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("restore-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(
                new StockItem(first, 2),
                new StockItem(second, 1));
        lock(orderNo, items);
        deduct(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();
        restore(aftersaleNo, orderNo, items);

        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(first, 2))), 14004);
        assertBusinessCode(() -> restore(aftersaleNo, orderNo,
                List.of(new StockItem(first, 2), new StockItem(second, 2))), 14004);

        assertThat(restoreFlows(aftersaleNo)).hasSize(2);
        assertThat(stock(first).getAvailableStock()).isEqualTo(10);
        assertThat(stock(second).getAvailableStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("不同售后单对同一订单重复整单回补，第二次拒绝")
    void differentAftersaleSameOrderRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 3));
        lock(orderNo, items);
        deduct(orderNo, items);
        String firstAftersale = "AFTER-" + UUID.randomUUID();
        String secondAftersale = "AFTER-" + UUID.randomUUID();

        restore(firstAftersale, orderNo, items);
        assertBusinessCode(() -> restore(secondAftersale, orderNo, items), 14004);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(restoreFlows(firstAftersale)).hasSize(1);
        assertThat(restoreFlows(secondAftersale)).isEmpty();
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("多 SKU 中途失败，全部回滚")
    void multiSkuRestoreFailureRollsBack() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("restore-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(
                new StockItem(first, 2),
                new StockItem(second, 2));
        lock(orderNo, items);
        deduct(orderNo, items);

        // 模拟第二个 SKU 可售库存已接近 INT 上限，使回补中途溢出失败。
        stockSkuMapper.update(null, new LambdaUpdateWrapper<StockSku>()
                .eq(StockSku::getSkuId, second)
                .set(StockSku::getAvailableStock, Integer.MAX_VALUE));
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        assertBusinessCode(() -> restore(aftersaleNo, orderNo, items), 14004);

        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(second).getAvailableStock()).isEqualTo(Integer.MAX_VALUE);
        assertThat(restoreFlows(aftersaleNo)).isEmpty();
    }

    @Test
    @DisplayName("available_stock INT 溢出拒绝")
    void restoreAvailableOverflowRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(orderNo, items);
        deduct(orderNo, items);
        stockSkuMapper.update(null, new LambdaUpdateWrapper<StockSku>()
                .eq(StockSku::getSkuId, skuId)
                .set(StockSku::getAvailableStock, Integer.MAX_VALUE));
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        assertBusinessCode(() -> restore(aftersaleNo, orderNo, items), 14004);

        assertThat(restoreFlows(aftersaleNo)).isEmpty();
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
        int availableDelta = 0;
        int lockedDelta = 0;
        for (StockFlow flow : flows) {
            assertThat(flow.getAvailableBefore()).as("flow %s availableBefore", flow.getId()).isNotNull();
            assertThat(flow.getAvailableAfter()).as("flow %s availableAfter", flow.getId()).isNotNull();
            assertThat(flow.getLockedBefore()).as("flow %s lockedBefore", flow.getId()).isNotNull();
            assertThat(flow.getLockedAfter()).as("flow %s lockedAfter", flow.getId()).isNotNull();
            assertThat(flow.getAvailableAfter() - flow.getAvailableBefore())
                    .as("flow %s available delta", flow.getId())
                    .isEqualTo(flow.getChangeAvailable());
            assertThat(flow.getLockedAfter() - flow.getLockedBefore())
                    .as("flow %s locked delta", flow.getId())
                    .isEqualTo(flow.getChangeLocked());
            availableDelta += flow.getChangeAvailable();
            lockedDelta += flow.getChangeLocked();
        }
        assertThat(availableDelta).isEqualTo(stock.getAvailableStock());
        assertThat(lockedDelta).isEqualTo(stock.getLockedStock());
    }
}
