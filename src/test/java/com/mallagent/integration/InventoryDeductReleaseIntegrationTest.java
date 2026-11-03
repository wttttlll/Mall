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

class InventoryDeductReleaseIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("单 SKU 支付扣减成功，流水前后值准确")
    void singleSkuDeductSuccess() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("deduct-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 3));
        lock(bizNo, items);

        deduct(bizNo, items);

        StockSku stock = stock(skuId);
        assertThat(stock.getAvailableStock()).isEqualTo(7);
        assertThat(stock.getLockedStock()).isZero();

        List<StockFlow> terminals = terminalFlows(bizNo);
        assertThat(terminals).hasSize(1);
        StockFlow flow = terminals.get(0);
        assertThat(flow.getBizType()).isEqualTo("ORDER_DEDUCT");
        assertThat(flow.getQuantity()).isEqualTo(3);
        assertThat(flow.getChangeAvailable()).isZero();
        assertThat(flow.getChangeLocked()).isEqualTo(-3);
        assertThat(flow.getAvailableBefore()).isEqualTo(7);
        assertThat(flow.getAvailableAfter()).isEqualTo(7);
        assertThat(flow.getLockedBefore()).isEqualTo(3);
        assertThat(flow.getLockedAfter()).isZero();
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("单 SKU 取消释放成功，流水前后值准确")
    void singleSkuReleaseSuccess() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("release-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 3));
        lock(bizNo, items);

        release(bizNo, items);

        StockSku stock = stock(skuId);
        assertThat(stock.getAvailableStock()).isEqualTo(10);
        assertThat(stock.getLockedStock()).isZero();

        List<StockFlow> terminals = terminalFlows(bizNo);
        assertThat(terminals).hasSize(1);
        StockFlow flow = terminals.get(0);
        assertThat(flow.getBizType()).isEqualTo("ORDER_RELEASE");
        assertThat(flow.getChangeAvailable()).isEqualTo(3);
        assertThat(flow.getChangeLocked()).isEqualTo(-3);
        assertThat(flow.getAvailableBefore()).isEqualTo(7);
        assertThat(flow.getAvailableAfter()).isEqualTo(10);
        assertThat(flow.getLockedBefore()).isEqualTo(3);
        assertThat(flow.getLockedAfter()).isZero();
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("多 SKU 扣减与释放成功")
    void multiSkuDeductAndReleaseSuccess() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("deduct-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("deduct-seed-" + UUID.randomUUID(), second, 20, "初始库存");

        String deductBiz = "ORDER-" + UUID.randomUUID();
        List<StockItem> deductItems = List.of(
                new StockItem(second, 5), new StockItem(first, 2));
        lock(deductBiz, deductItems);
        deduct(deductBiz, deductItems);
        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(first).getLockedStock()).isZero();
        assertThat(stock(second).getAvailableStock()).isEqualTo(15);
        assertThat(stock(second).getLockedStock()).isZero();
        assertThat(terminalFlows(deductBiz)).hasSize(2);
        assertReconciled(first);
        assertReconciled(second);

        String releaseBiz = "ORDER-" + UUID.randomUUID();
        List<StockItem> releaseItems = List.of(
                new StockItem(second, 4), new StockItem(first, 1));
        lock(releaseBiz, releaseItems);
        release(releaseBiz, releaseItems);
        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(first).getLockedStock()).isZero();
        assertThat(stock(second).getAvailableStock()).isEqualTo(15);
        assertThat(stock(second).getLockedStock()).isZero();
        assertThat(terminalFlows(releaseBiz)).hasSize(2);
        assertReconciled(first);
        assertReconciled(second);
    }

    @Test
    @DisplayName("重复 deduct/release 幂等，不重复变更库存")
    void repeatedDeductAndReleaseAreIdempotent() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("deduct-seed-" + UUID.randomUUID(), skuId, 20, "初始库存");

        String deductBiz = "ORDER-" + UUID.randomUUID();
        List<StockItem> deductItems = List.of(new StockItem(skuId, 3));
        lock(deductBiz, deductItems);
        deduct(deductBiz, deductItems);
        deduct(deductBiz, deductItems);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(17);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(terminalFlows(deductBiz)).hasSize(1);

        String releaseBiz = "ORDER-" + UUID.randomUUID();
        List<StockItem> releaseItems = List.of(new StockItem(skuId, 2));
        lock(releaseBiz, releaseItems);
        release(releaseBiz, releaseItems);
        release(releaseBiz, releaseItems);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(17);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(terminalFlows(releaseBiz)).hasSize(1);
        assertReconciled(skuId);
    }

    @Test
    @DisplayName("数量不匹配拒绝")
    void quantityMismatchRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("deduct-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        lock(bizNo, List.of(new StockItem(skuId, 2)));

        assertBusinessCode(() -> deduct(bizNo, List.of(new StockItem(skuId, 3))), 14004);
        assertBusinessCode(() -> release(bizNo, List.of(new StockItem(skuId, 1))), 14004);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        assertThat(stock(skuId).getLockedStock()).isEqualTo(2);
        assertThat(terminalFlows(bizNo)).isEmpty();
    }

    @Test
    @DisplayName("SKU 集合缺失或额外拒绝")
    void skuSetMismatchRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("deduct-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("deduct-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        lock(bizNo, List.of(
                new StockItem(first, 2),
                new StockItem(second, 1)));

        assertBusinessCode(() -> deduct(bizNo, List.of(new StockItem(first, 2))), 14004);
        assertBusinessCode(() -> deduct(bizNo, List.of(
                new StockItem(first, 2),
                new StockItem(second, 1),
                new StockItem(999_999_999L, 1))), 14004);

        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(first).getLockedStock()).isEqualTo(2);
        assertThat(stock(second).getAvailableStock()).isEqualTo(9);
        assertThat(stock(second).getLockedStock()).isEqualTo(1);
        assertThat(terminalFlows(bizNo)).isEmpty();
    }

    @Test
    @DisplayName("无 ORDER_LOCK 直接 deduct/release 拒绝")
    void terminalWithoutLockRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("deduct-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();

        assertBusinessCode(() -> deduct(bizNo, List.of(new StockItem(skuId, 1))), 14004);
        assertBusinessCode(() -> release(bizNo, List.of(new StockItem(skuId, 1))), 14004);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(terminalFlows(bizNo)).isEmpty();
    }

    @Test
    @DisplayName("deduct 后 release 冲突，release 后 deduct 冲突")
    void oppositeTerminalConflicts() throws Exception {
        TokenPair admin = adminLogin();
        Long deductedSku = createInventorySku(admin);
        Long releasedSku = createInventorySku(admin);
        adjustDirect("deduct-seed-" + UUID.randomUUID(), deductedSku, 10, "初始库存");
        adjustDirect("release-seed-" + UUID.randomUUID(), releasedSku, 10, "初始库存");

        String deductedBiz = "ORDER-" + UUID.randomUUID();
        List<StockItem> deductedItems = List.of(new StockItem(deductedSku, 2));
        lock(deductedBiz, deductedItems);
        deduct(deductedBiz, deductedItems);
        assertBusinessCode(() -> release(deductedBiz, deductedItems), 14004);

        String releasedBiz = "ORDER-" + UUID.randomUUID();
        List<StockItem> releasedItems = List.of(new StockItem(releasedSku, 2));
        lock(releasedBiz, releasedItems);
        release(releasedBiz, releasedItems);
        assertBusinessCode(() -> deduct(releasedBiz, releasedItems), 14004);

        assertThat(terminalFlows(deductedBiz)).hasSize(1);
        assertThat(terminalFlows(releasedBiz)).hasSize(1);
        assertReconciled(deductedSku);
        assertReconciled(releasedSku);
    }

    @Test
    @DisplayName("多 SKU 异常导致批量失败时整体回滚")
    void multiSkuFailureRollsBack() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("deduct-seed-" + UUID.randomUUID(), first, 10, "初始库存");
        adjustDirect("deduct-seed-" + UUID.randomUUID(), second, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(
                new StockItem(first, 2),
                new StockItem(second, 2));
        lock(bizNo, items);

        // 模拟异常数据：第二个 SKU 的锁定库存被破坏，使批量扣减中途失败。
        stockSkuMapper.update(null, new LambdaUpdateWrapper<StockSku>()
                .eq(StockSku::getSkuId, second)
                .set(StockSku::getLockedStock, 0));

        assertBusinessCode(() -> deduct(bizNo, items), 14004);

        assertThat(stock(first).getLockedStock()).isEqualTo(2);
        assertThat(stock(first).getAvailableStock()).isEqualTo(8);
        assertThat(stock(second).getLockedStock()).isZero();
        assertThat(terminalFlows(bizNo)).isEmpty();
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
