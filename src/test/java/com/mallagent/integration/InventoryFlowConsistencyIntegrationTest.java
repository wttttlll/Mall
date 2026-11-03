package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.inventory.entity.StockSku;
import com.mallagent.module.inventory.enums.StockBizType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 库存流水完整性与对账验收：逐 SKU 验证 before/after 链、余额汇总、业务唯一键和终态互斥。
 */
class InventoryFlowConsistencyIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("库存流水完整、对账一致且无异常终态")
    void flowsAreCompleteAndReconciled() throws Exception {
        TokenPair admin = adminLogin();
        Long first = createInventorySku(admin);
        Long second = createInventorySku(admin);
        adjustDirect("audit-seed-" + UUID.randomUUID(), first, 20, "初始库存");
        adjustDirect("audit-seed-" + UUID.randomUUID(), second, 20, "初始库存");

        String deductOrder = "ORDER-" + UUID.randomUUID();
        List<StockItem> deductItems = List.of(new StockItem(second, 4), new StockItem(first, 2));
        lock(deductOrder, deductItems);
        deduct(deductOrder, deductItems);
        restore("AFTER-" + UUID.randomUUID(), deductOrder, deductItems);

        String releaseOrder = "ORDER-" + UUID.randomUUID();
        List<StockItem> releaseItems = List.of(new StockItem(first, 1), new StockItem(second, 3));
        lock(releaseOrder, releaseItems);
        release(releaseOrder, releaseItems);

        String deductOnlyOrder = "ORDER-" + UUID.randomUUID();
        List<StockItem> deductOnlyItems = List.of(new StockItem(first, 1), new StockItem(second, 1));
        lock(deductOnlyOrder, deductOnlyItems);
        deduct(deductOnlyOrder, deductOnlyItems);

        adjustDirect("audit-adjust-" + UUID.randomUUID(), first, -2, "盘亏");
        adjustDirect("audit-adjust-" + UUID.randomUUID(), second, 5, "补货");

        String failedOrder = "ORDER-" + UUID.randomUUID();
        assertThatThrownBy(() -> lock(failedOrder, List.of(new StockItem(first, 1000))))
                .isInstanceOf(BusinessException.class);
        assertThat(orderLockFlows(failedOrder)).isEmpty();
        assertThat(terminalFlows(failedOrder)).isEmpty();

        assertSkuConsistent(first, true);
        assertSkuConsistent(second, true);
        assertOrderTerminalExclusive(first, second);
        assertRestoreWithinDeduct(first, second);
    }

    private void assertSkuConsistent(Long skuId, boolean requireAllTypes) {
        StockSku stock = stock(skuId);
        assertThat(stock).isNotNull();
        assertThat(stock.getAvailableStock()).isGreaterThanOrEqualTo(0);
        assertThat(stock.getLockedStock()).isGreaterThanOrEqualTo(0);

        List<StockFlow> flows = flows(skuId);
        assertThat(flows).isNotEmpty();
        if (requireAllTypes) {
            assertThat(flows.stream().map(StockFlow::getBizType).collect(Collectors.toSet()))
                    .containsExactlyInAnyOrder(
                            StockBizType.ADMIN_ADJUST.name(),
                            StockBizType.ORDER_LOCK.name(),
                            StockBizType.ORDER_DEDUCT.name(),
                            StockBizType.ORDER_RELEASE.name(),
                            StockBizType.RETURN_RESTORE.name());
        }

        Set<String> bizKeys = new HashSet<>();
        int available = 0;
        int locked = 0;
        for (StockFlow flow : flows) {
            // 已提交流水必须完成回填，且类型必须合法。
            StockBizType.valueOf(flow.getBizType());
            assertThat(flow.getAvailableBefore()).as("flow %s availableBefore", flow.getId()).isNotNull();
            assertThat(flow.getAvailableAfter()).as("flow %s availableAfter", flow.getId()).isNotNull();
            assertThat(flow.getLockedBefore()).as("flow %s lockedBefore", flow.getId()).isNotNull();
            assertThat(flow.getLockedAfter()).as("flow %s lockedAfter", flow.getId()).isNotNull();

            // 变更量与 before/after 差值一致。
            assertThat(flow.getAvailableAfter() - flow.getAvailableBefore())
                    .as("flow %s available delta", flow.getId())
                    .isEqualTo(flow.getChangeAvailable());
            assertThat(flow.getLockedAfter() - flow.getLockedBefore())
                    .as("flow %s locked delta", flow.getId())
                    .isEqualTo(flow.getChangeLocked());

            // 同一 SKU 的流水按 ID 顺序形成连续的 before/after 链。
            assertThat(flow.getAvailableBefore())
                    .as("flow %s availableBefore chain", flow.getId())
                    .isEqualTo(available);
            assertThat(flow.getLockedBefore())
                    .as("flow %s lockedBefore chain", flow.getId())
                    .isEqualTo(locked);
            available = flow.getAvailableAfter();
            locked = flow.getLockedAfter();

            // 业务唯一键不得重复。
            assertThat(bizKeys.add(flow.getBizType() + "|" + flow.getBizNo() + "|" + flow.getSkuId()))
                    .as("duplicate business key at flow %s", flow.getId())
                    .isTrue();
        }

        assertThat(available).isEqualTo(stock.getAvailableStock());
        assertThat(locked).isEqualTo(stock.getLockedStock());
    }

    private void assertOrderTerminalExclusive(Long... skuIds) {
        List<StockFlow> terminals = stockFlowMapper.selectList(
                new LambdaQueryWrapper<StockFlow>()
                        .in(StockFlow::getSkuId, List.of(skuIds))
                        .in(StockFlow::getBizType,
                                StockBizType.ORDER_DEDUCT.name(), StockBizType.ORDER_RELEASE.name()));
        Map<String, Set<String>> typesByOrder = terminals.stream().collect(Collectors.groupingBy(
                StockFlow::getBizNo,
                Collectors.mapping(StockFlow::getBizType, Collectors.toSet())));
        assertThat(typesByOrder.values()).allSatisfy(types -> assertThat(types).hasSize(1));
    }

    private void assertRestoreWithinDeduct(Long... skuIds) {
        List<StockFlow> flows = stockFlowMapper.selectList(
                new LambdaQueryWrapper<StockFlow>()
                        .in(StockFlow::getSkuId, List.of(skuIds))
                        .in(StockFlow::getBizType,
                                StockBizType.ORDER_DEDUCT.name(), StockBizType.RETURN_RESTORE.name()));

        Map<String, Integer> deducted = flows.stream()
                .filter(flow -> StockBizType.ORDER_DEDUCT.name().equals(flow.getBizType()))
                .collect(Collectors.toMap(
                        flow -> flow.getBizNo() + "|" + flow.getSkuId(),
                        StockFlow::getQuantity,
                        Integer::sum));
        Map<String, Integer> restored = flows.stream()
                .filter(flow -> StockBizType.RETURN_RESTORE.name().equals(flow.getBizType()))
                .collect(Collectors.toMap(
                        flow -> flow.getRefBizNo() + "|" + flow.getSkuId(),
                        StockFlow::getQuantity,
                        Integer::sum));

        restored.forEach((key, quantity) -> assertThat(quantity)
                .as("restore quantity for %s", key)
                .isLessThanOrEqualTo(deducted.getOrDefault(key, 0)));
    }
}
