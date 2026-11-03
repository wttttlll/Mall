package com.mallagent.module.inventory.service;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.inventory.api.InventoryStockApi;
import com.mallagent.module.inventory.api.dto.StockCommand;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.api.dto.StockRestoreCommand;
import com.mallagent.module.inventory.api.dto.StockSnapshot;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.inventory.entity.StockSku;
import com.mallagent.module.inventory.enums.StockBizType;
import com.mallagent.module.inventory.error.InventoryErrorCode;
import com.mallagent.module.inventory.mapper.StockFlowMapper;
import com.mallagent.module.inventory.mapper.StockSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存内部 API 实现。
 *
 * <p>已实现 lock/deduct/release/restore 与 findStocks。
 * 写方法使用 REQUIRED 传播，加入调用方已有事务，不修改调用方隔离级别。
 * findStocks 为只读批量查询，不开启写事务、不使用行锁。</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryService implements InventoryStockApi {

    private static final int MAX_BIZ_NO_LENGTH = 64;

    private final StockSkuMapper stockSkuMapper;
    private final StockFlowMapper stockFlowMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void lock(StockCommand command) {
        List<StockItem> items = validateAndSort(command);
        String bizNo = command.bizNo().trim();
        Long operatorId = SecurityUtils.currentUserId().orElse(null);
        String requestToken = UUID.randomUUID().toString();

        int newFlows = 0;
        int duplicateFlows = 0;
        for (StockItem item : items) {
            StockFlow flow = buildOrderLockFlow(bizNo, item, requestToken, operatorId);
            try {
                stockFlowMapper.claim(flow);
                newFlows++;
            } catch (DuplicateKeyException ex) {
                // 仅唯一键重复进入幂等分支；其他 SQL 错误继续抛出。
                duplicateFlows++;
            }
        }

        List<StockFlow> existingFlows = stockFlowMapper.selectOrderLockFlows(bizNo);
        Map<Long, StockFlow> flowBySkuId = validateExactOrderLock(existingFlows, items);

        // 已进入扣减/释放终态的订单不允许再次锁定；当前阶段尚无终态写入，
        // 预先保留该检查，避免后续步骤实现 deduct/release 后出现状态回退。
        if (!stockFlowMapper.selectTerminalFlows(bizNo).isEmpty()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }

        if (newFlows == 0) {
            // 全部是已有流水：严格校验后幂等返回，不重复变更库存。
            for (StockFlow flow : existingFlows) {
                if (flow.getAvailableBefore() == null
                        || flow.getAvailableAfter() == null
                        || flow.getLockedBefore() == null
                        || flow.getLockedAfter() == null) {
                    throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
                }
            }
            return;
        }
        if (duplicateFlows > 0) {
            // 新旧流水混合属于异常状态，回滚本次新占位。
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }

        for (StockItem item : items) {
            int updated = stockSkuMapper.lockStock(item.skuId(), item.quantity(), operatorId);
            if (updated != 1) {
                StockSku current = stockSkuMapper.selectBySkuId(item.skuId());
                if (current == null) {
                    throw new BusinessException(InventoryErrorCode.STOCK_NOT_INITIALIZED);
                }
                throw new BusinessException(InventoryErrorCode.STOCK_INSUFFICIENT);
            }

            // 条件更新持有库存行锁；读取真实变更后值并回填 ORDER_LOCK 流水。
            StockSku after = stockSkuMapper.selectBySkuIdForUpdate(item.skuId());
            if (after == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_NOT_INITIALIZED);
            }
            int availableBefore = after.getAvailableStock() + item.quantity();
            int lockedBefore = after.getLockedStock() - item.quantity();
            StockFlow flow = flowBySkuId.get(item.skuId());
            if (flow == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
            int finalized = stockFlowMapper.finalize(flow.getId(), availableBefore,
                    after.getAvailableStock(), lockedBefore, after.getLockedStock());
            if (finalized == 0) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deduct(StockCommand command) {
        transitionTerminal(command, StockBizType.ORDER_DEDUCT);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void release(StockCommand command) {
        transitionTerminal(command, StockBizType.ORDER_RELEASE);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void restore(StockRestoreCommand command) {
        transitionRestore(command);
    }

    @Override
    public List<StockSnapshot> findStocks(Collection<Long> skuIds) {
        List<Long> distinctSkuIds = validateAndDeduplicateSkuIds(skuIds);
        if (distinctSkuIds.isEmpty()) {
            return List.of();
        }

        // 单次批量查询，避免逐 SKU 查询；不创建、不修改任何库存或流水。
        Map<Long, StockSku> stockBySkuId = stockSkuMapper.selectBySkuIds(distinctSkuIds).stream()
                .collect(Collectors.toMap(StockSku::getSkuId, Function.identity()));

        List<StockSnapshot> snapshots = new ArrayList<>(distinctSkuIds.size());
        for (Long skuId : distinctSkuIds) {
            StockSku stock = stockBySkuId.get(skuId);
            if (stock == null) {
                snapshots.add(new StockSnapshot(skuId, 0, 0, false));
            } else {
                snapshots.add(new StockSnapshot(skuId,
                        stock.getAvailableStock(), stock.getLockedStock(), true));
            }
        }
        return snapshots;
    }

    /**
     * 校验查询参数并按首次出现顺序去重。
     */
    private List<Long> validateAndDeduplicateSkuIds(Collection<Long> skuIds) {
        if (skuIds == null) {
            throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
        }
        Set<Long> distinctSkuIds = new LinkedHashSet<>();
        for (Long skuId : skuIds) {
            if (skuId == null || skuId <= 0) {
                throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
            }
            distinctSkuIds.add(skuId);
        }
        return List.copyOf(distinctSkuIds);
    }

    private List<StockItem> validateAndSort(StockCommand command) {
        if (command == null || !StringUtils.hasText(command.bizNo())) {
            throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
        }
        String bizNo = command.bizNo().trim();
        if (bizNo.length() > MAX_BIZ_NO_LENGTH || command.items() == null || command.items().isEmpty()) {
            throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
        }

        Set<Long> seenSkuIds = new HashSet<>();
        for (StockItem item : command.items()) {
            if (item == null || item.skuId() == null || item.skuId() <= 0) {
                throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new BusinessException(InventoryErrorCode.STOCK_QUANTITY_INVALID);
            }
            if (!seenSkuIds.add(item.skuId())) {
                throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
            }
        }

        return command.items().stream()
                .sorted(Comparator.comparing(StockItem::skuId))
                .toList();
    }

    private StockFlow buildOrderLockFlow(String bizNo, StockItem item, String requestToken, Long operatorId) {
        StockFlow flow = new StockFlow();
        flow.setBizType(StockBizType.ORDER_LOCK.name());
        flow.setBizNo(bizNo);
        flow.setSkuId(item.skuId());
        flow.setQuantity(item.quantity());
        flow.setChangeAvailable(-item.quantity());
        flow.setChangeLocked(item.quantity());
        flow.setRequestToken(requestToken);
        flow.setCreatedBy(operatorId);
        return flow;
    }

    /**
     * 校验订单全部 ORDER_LOCK 流水与命令的 SKU 集合、数量、变化量完全一致。
     */
    private Map<Long, StockFlow> validateExactOrderLock(List<StockFlow> flows, List<StockItem> items) {
        if (flows.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        Map<Long, StockFlow> bySkuId = flows.stream()
                .collect(Collectors.toMap(StockFlow::getSkuId, flow -> flow, (left, right) -> left));
        if (bySkuId.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        for (StockItem item : items) {
            StockFlow flow = bySkuId.get(item.skuId());
            if (flow == null
                    || !Objects.equals(flow.getQuantity(), item.quantity())
                    || !Objects.equals(flow.getChangeAvailable(), -item.quantity())
                    || !Objects.equals(flow.getChangeLocked(), item.quantity())) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }
        return bySkuId;
    }

    /**
     * deduct/release 共用流程：锁定 ORDER_LOCK 行，校验完整订单，处理终态或写入新终态。
     */
    private void transitionTerminal(StockCommand command, StockBizType terminalType) {
        List<StockItem> items = validateAndSort(command);
        String bizNo = command.bizNo().trim();
        Long operatorId = SecurityUtils.currentUserId().orElse(null);

        // 按 sku_id 升序锁定该订单全部 ORDER_LOCK 行；deduct/release 共用同一互斥点。
        List<StockFlow> lockFlows = stockFlowMapper.selectOrderLockRowsForUpdate(bizNo);
        if (lockFlows.isEmpty()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        validateLockFlowsForTransition(lockFlows, items);

        List<StockFlow> terminalFlows = stockFlowMapper.selectTerminalFlows(bizNo);
        if (!terminalFlows.isEmpty()) {
            verifyExistingTerminal(terminalFlows, items, terminalType);
            return;
        }

        String requestToken = UUID.randomUUID().toString();
        for (StockItem item : items) {
            StockFlow flow = buildTerminalFlow(bizNo, item, terminalType, requestToken, operatorId);
            try {
                stockFlowMapper.claim(flow);
            } catch (DuplicateKeyException ex) {
                // 已持有 ORDER_LOCK 行锁时出现重复终态，属于状态冲突，不是幂等成功。
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }

        List<StockFlow> insertedFlows = stockFlowMapper.selectTerminalFlows(bizNo);
        Map<Long, StockFlow> terminalBySkuId = validateTerminalFlows(
                insertedFlows, items, terminalType, false);

        for (StockItem item : items) {
            int updated = terminalType == StockBizType.ORDER_DEDUCT
                    ? stockSkuMapper.deductLocked(item.skuId(), item.quantity(), operatorId)
                    : stockSkuMapper.releaseLocked(item.skuId(), item.quantity(), operatorId);
            if (updated != 1) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }

            // 条件更新持有库存行锁；读取真实变更后值并回填终态流水。
            StockSku after = stockSkuMapper.selectBySkuIdForUpdate(item.skuId());
            if (after == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
            int availableAfter = after.getAvailableStock();
            int lockedAfter = after.getLockedStock();
            int availableBefore = terminalType == StockBizType.ORDER_DEDUCT
                    ? availableAfter
                    : availableAfter - item.quantity();
            int lockedBefore = lockedAfter + item.quantity();
            StockFlow flow = terminalBySkuId.get(item.skuId());
            if (flow == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
            int finalized = stockFlowMapper.finalize(flow.getId(), availableBefore,
                    availableAfter, lockedBefore, lockedAfter);
            if (finalized == 0) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }
    }

    /**
     * 校验订单全部 ORDER_LOCK 流水与命令的完整 SKU 集合、数量、变化量。
     */
    private void validateLockFlowsForTransition(List<StockFlow> flows, List<StockItem> items) {
        if (flows.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        Map<Long, StockFlow> bySkuId = flows.stream()
                .collect(Collectors.toMap(StockFlow::getSkuId, flow -> flow, (left, right) -> left));
        if (bySkuId.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        for (StockItem item : items) {
            StockFlow flow = bySkuId.get(item.skuId());
            if (flow == null
                    || !Objects.equals(flow.getQuantity(), item.quantity())
                    || !Objects.equals(flow.getChangeAvailable(), -item.quantity())
                    || !Objects.equals(flow.getChangeLocked(), item.quantity())
                    || flow.getAvailableBefore() == null
                    || flow.getAvailableAfter() == null
                    || flow.getLockedBefore() == null
                    || flow.getLockedAfter() == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }
    }

    /**
     * 订单已有终态时：只允许同类型、完整、数量一致的重复请求幂等成功。
     */
    private void verifyExistingTerminal(List<StockFlow> flows,
                                        List<StockItem> items,
                                        StockBizType terminalType) {
        boolean hasDeduct = flows.stream()
                .anyMatch(flow -> StockBizType.ORDER_DEDUCT.name().equals(flow.getBizType()));
        boolean hasRelease = flows.stream()
                .anyMatch(flow -> StockBizType.ORDER_RELEASE.name().equals(flow.getBizType()));
        if (hasDeduct && hasRelease) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        StockBizType existingType = hasDeduct ? StockBizType.ORDER_DEDUCT : StockBizType.ORDER_RELEASE;
        if (existingType != terminalType) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        validateTerminalFlows(flows, items, existingType, true);
    }

    /**
     * 校验终态流水完整覆盖订单，且数量/变化量与操作类型一致。
     */
    private Map<Long, StockFlow> validateTerminalFlows(List<StockFlow> flows,
                                                       List<StockItem> items,
                                                       StockBizType terminalType,
                                                       boolean requireCompleted) {
        if (flows.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        Map<Long, StockFlow> bySkuId = flows.stream()
                .collect(Collectors.toMap(StockFlow::getSkuId, flow -> flow, (left, right) -> left));
        if (bySkuId.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        for (StockItem item : items) {
            StockFlow flow = bySkuId.get(item.skuId());
            int expectedAvailableChange = terminalType == StockBizType.ORDER_RELEASE
                    ? item.quantity()
                    : 0;
            if (flow == null
                    || !terminalType.name().equals(flow.getBizType())
                    || !Objects.equals(flow.getQuantity(), item.quantity())
                    || !Objects.equals(flow.getChangeAvailable(), expectedAvailableChange)
                    || !Objects.equals(flow.getChangeLocked(), -item.quantity())) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
            if (requireCompleted
                    && (flow.getAvailableBefore() == null
                    || flow.getAvailableAfter() == null
                    || flow.getLockedBefore() == null
                    || flow.getLockedAfter() == null)) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }
        return bySkuId;
    }

    private StockFlow buildTerminalFlow(String bizNo,
                                        StockItem item,
                                        StockBizType terminalType,
                                        String requestToken,
                                        Long operatorId) {
        StockFlow flow = new StockFlow();
        flow.setBizType(terminalType.name());
        flow.setBizNo(bizNo);
        flow.setSkuId(item.skuId());
        flow.setQuantity(item.quantity());
        flow.setChangeAvailable(terminalType == StockBizType.ORDER_RELEASE ? item.quantity() : 0);
        flow.setChangeLocked(-item.quantity());
        flow.setRequestToken(requestToken);
        flow.setCreatedBy(operatorId);
        return flow;
    }

    /**
     * 售后退货回补：锁定原订单 ORDER_LOCK 行，校验 DEDUCT 终态、售后单幂等和累计回补上限。
     */
    private void transitionRestore(StockRestoreCommand command) {
        List<StockItem> items = validateRestoreAndSort(command);
        String bizNo = command.bizNo().trim();
        String refBizNo = command.refBizNo().trim();
        Long operatorId = SecurityUtils.currentUserId().orElse(null);

        // 与 deduct/release 使用同一 ORDER_LOCK 行锁互斥点。
        List<StockFlow> lockFlows = stockFlowMapper.selectOrderLockRowsForUpdate(refBizNo);
        if (lockFlows.isEmpty()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        validateLockFlowsForTransition(lockFlows, items);

        List<StockFlow> terminalFlows = stockFlowMapper.selectTerminalFlows(refBizNo);
        Map<Long, StockFlow> deductBySku = validateDeductForRestore(terminalFlows, items);

        String requestToken = UUID.randomUUID().toString();
        int newFlows = 0;
        int duplicateFlows = 0;
        for (StockItem item : items) {
            StockFlow flow = buildRestoreFlow(bizNo, refBizNo, item, requestToken, operatorId);
            try {
                stockFlowMapper.claim(flow);
                newFlows++;
            } catch (DuplicateKeyException ex) {
                // 只捕获唯一键重复；后续读取已有流水严格比较。
                duplicateFlows++;
            }
        }

        List<StockFlow> restoreFlows = stockFlowMapper.selectRestoreFlows(bizNo);
        Map<Long, StockFlow> restoreBySku = validateRestoreFlows(
                restoreFlows, items, refBizNo, false);

        if (newFlows == 0) {
            // 同一售后单重复请求：已生效流水完整且一致，直接幂等返回。
            // 必须在累计计算前返回，避免把本次数量重复计入。
            validateRestoreFlows(restoreFlows, items, refBizNo, true);
            return;
        }
        if (duplicateFlows > 0) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }

        // 只统计已完成回填的历史 RETURN_RESTORE；当前新占位 available_after 为空，不计入。
        for (StockItem item : items) {
            long restored = stockFlowMapper.sumFinalizedRestoreQuantity(refBizNo, item.skuId());
            StockFlow deducted = deductBySku.get(item.skuId());
            if (deducted == null || restored + item.quantity() > deducted.getQuantity()) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }

        for (StockItem item : items) {
            int updated = stockSkuMapper.restoreAvailable(item.skuId(), item.quantity(), operatorId);
            if (updated != 1) {
                StockSku current = stockSkuMapper.selectBySkuId(item.skuId());
                if (current == null) {
                    throw new BusinessException(InventoryErrorCode.STOCK_NOT_INITIALIZED);
                }
                // 库存存在但更新失败：可售库存增加会溢出 INT。
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }

            StockSku after = stockSkuMapper.selectBySkuIdForUpdate(item.skuId());
            if (after == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
            int availableBefore = after.getAvailableStock() - item.quantity();
            int locked = after.getLockedStock();
            StockFlow flow = restoreBySku.get(item.skuId());
            if (flow == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
            int finalized = stockFlowMapper.finalize(flow.getId(), availableBefore,
                    after.getAvailableStock(), locked, locked);
            if (finalized == 0) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }
    }

    private List<StockItem> validateRestoreAndSort(StockRestoreCommand command) {
        if (command == null
                || !StringUtils.hasText(command.bizNo())
                || !StringUtils.hasText(command.refBizNo())) {
            throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
        }
        String bizNo = command.bizNo().trim();
        String refBizNo = command.refBizNo().trim();
        if (bizNo.length() > MAX_BIZ_NO_LENGTH
                || refBizNo.length() > MAX_BIZ_NO_LENGTH
                || command.items() == null
                || command.items().isEmpty()) {
            throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
        }

        Set<Long> seenSkuIds = new HashSet<>();
        for (StockItem item : command.items()) {
            if (item == null || item.skuId() == null || item.skuId() <= 0) {
                throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new BusinessException(InventoryErrorCode.STOCK_QUANTITY_INVALID);
            }
            if (!seenSkuIds.add(item.skuId())) {
                throw new BusinessException(InventoryErrorCode.STOCK_COMMAND_INVALID);
            }
        }
        return command.items().stream()
                .sorted(Comparator.comparing(StockItem::skuId))
                .toList();
    }

    /**
     * 校验原订单已扣减且没有释放，扣减 SKU/数量与回补命令完全一致。
     */
    private Map<Long, StockFlow> validateDeductForRestore(List<StockFlow> flows, List<StockItem> items) {
        if (flows.size() != items.size()
                || flows.stream().anyMatch(flow ->
                !StockBizType.ORDER_DEDUCT.name().equals(flow.getBizType()))) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        Map<Long, StockFlow> bySkuId = flows.stream()
                .collect(Collectors.toMap(StockFlow::getSkuId, flow -> flow, (left, right) -> left));
        if (bySkuId.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        for (StockItem item : items) {
            StockFlow flow = bySkuId.get(item.skuId());
            if (flow == null
                    || !Objects.equals(flow.getQuantity(), item.quantity())
                    || !Objects.equals(flow.getChangeAvailable(), 0)
                    || !Objects.equals(flow.getChangeLocked(), -item.quantity())
                    || flow.getAvailableBefore() == null
                    || flow.getAvailableAfter() == null
                    || flow.getLockedBefore() == null
                    || flow.getLockedAfter() == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }
        return bySkuId;
    }

    /**
     * 校验同一售后单的 RETURN_RESTORE 流水与原订单、SKU 集合和数量一致。
     */
    private Map<Long, StockFlow> validateRestoreFlows(List<StockFlow> flows,
                                                       List<StockItem> items,
                                                       String refBizNo,
                                                       boolean requireCompleted) {
        if (flows.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        Map<Long, StockFlow> bySkuId = flows.stream()
                .collect(Collectors.toMap(StockFlow::getSkuId, flow -> flow, (left, right) -> left));
        if (bySkuId.size() != items.size()) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
        for (StockItem item : items) {
            StockFlow flow = bySkuId.get(item.skuId());
            if (flow == null
                    || !StockBizType.RETURN_RESTORE.name().equals(flow.getBizType())
                    || !Objects.equals(flow.getRefBizNo(), refBizNo)
                    || !Objects.equals(flow.getQuantity(), item.quantity())
                    || !Objects.equals(flow.getChangeAvailable(), item.quantity())
                    || !Objects.equals(flow.getChangeLocked(), 0)) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
            if (requireCompleted
                    && (flow.getAvailableBefore() == null
                    || flow.getAvailableAfter() == null
                    || flow.getLockedBefore() == null
                    || flow.getLockedAfter() == null)) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
        }
        return bySkuId;
    }

    private StockFlow buildRestoreFlow(String bizNo,
                                       String refBizNo,
                                       StockItem item,
                                       String requestToken,
                                       Long operatorId) {
        StockFlow flow = new StockFlow();
        flow.setBizType(StockBizType.RETURN_RESTORE.name());
        flow.setBizNo(bizNo);
        flow.setRefBizNo(refBizNo);
        flow.setSkuId(item.skuId());
        flow.setQuantity(item.quantity());
        flow.setChangeAvailable(item.quantity());
        flow.setChangeLocked(0);
        flow.setRequestToken(requestToken);
        flow.setCreatedBy(operatorId);
        return flow;
    }
}
