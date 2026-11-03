package com.mallagent.module.inventory.service;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.inventory.dto.admin.StockAdjustRequest;
import com.mallagent.module.inventory.dto.admin.StockResponse;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.inventory.entity.StockSku;
import com.mallagent.module.inventory.enums.StockBizType;
import com.mallagent.module.inventory.error.InventoryErrorCode;
import com.mallagent.module.inventory.mapper.StockFlowMapper;
import com.mallagent.module.inventory.mapper.StockSkuMapper;
import com.mallagent.module.product.api.ProductSkuQueryApi;
import com.mallagent.module.product.api.dto.SkuSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 管理端库存调整。
 *
 * <p>管理端调整是独立的最外层事务，显式使用 READ_COMMITTED；
 * 库存内部 API 不会修改调用方事务隔离级别。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAdminService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final StockSkuMapper stockSkuMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ProductSkuQueryApi productSkuQueryApi;

    /**
     * 调整或初始化单个 SKU 的可售库存。
     *
     * <p>管理端幂等由 stock_flow.admin_biz_no 唯一约束保证；相同 Key 的
     * SKU、delta、remark 任一不同都视为冲突。</p>
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public StockResponse adjust(String idempotencyKey, StockAdjustRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        Long skuId = requireSkuId(request.skuId());
        int delta = requireDelta(request.delta());
        String remark = normalizeRemark(request.remark());

        SkuSnapshot sku = productSkuQueryApi.findSku(skuId)
                .orElseThrow(() -> new BusinessException(InventoryErrorCode.STOCK_SKU_NOT_FOUND));
        Long operatorId = SecurityUtils.currentUserId().orElse(null);

        StockFlow claim = buildAdminFlow(key, skuId, delta, remark, operatorId);
        boolean inserted;
        try {
            stockFlowMapper.claim(claim);
            inserted = true;
        } catch (DuplicateKeyException ex) {
            // 只把唯一键重复视为幂等候选；其他 SQL 错误继续抛出。
            inserted = false;
        }

        StockFlow existing = stockFlowMapper.selectAdminAdjustByBizNo(key);
        if (existing == null) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }

        if (!inserted) {
            verifyDuplicate(existing, skuId, delta, remark);
            if (existing.getAvailableAfter() == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
            }
            StockSku current = stockSkuMapper.selectBySkuId(skuId);
            if (current == null) {
                throw new BusinessException(InventoryErrorCode.STOCK_NOT_INITIALIZED);
            }
            return toResponse(current, sku);
        }

        if (delta > 0) {
            stockSkuMapper.upsertPositive(skuId, delta, operatorId);
        } else {
            int updated = stockSkuMapper.adjustAvailable(skuId, delta, operatorId);
            if (updated == 0) {
                StockSku current = stockSkuMapper.selectBySkuId(skuId);
                if (current == null) {
                    throw new BusinessException(InventoryErrorCode.STOCK_NOT_INITIALIZED);
                }
                throw new BusinessException(InventoryErrorCode.STOCK_ADJUST_INVALID);
            }
        }

        // 条件更新/upsert 持有库存行锁；在事务内读取真实变更后值再回填流水。
        StockSku after = stockSkuMapper.selectBySkuIdForUpdate(skuId);
        if (after == null) {
            throw new BusinessException(InventoryErrorCode.STOCK_NOT_INITIALIZED);
        }
        int availableBefore = after.getAvailableStock() - delta;
        int locked = after.getLockedStock();
        int finalized = stockFlowMapper.finalize(existing.getId(), availableBefore,
                after.getAvailableStock(), locked, locked);
        if (finalized == 0) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }

        log.info("库存调整成功 key={}, skuId={}, delta={}, availableAfter={}",
                key, skuId, delta, after.getAvailableStock());
        return toResponse(after, sku);
    }

    private StockFlow buildAdminFlow(String key,
                                     Long skuId,
                                     int delta,
                                     String remark,
                                     Long operatorId) {
        StockFlow flow = new StockFlow();
        flow.setBizType(StockBizType.ADMIN_ADJUST.name());
        flow.setBizNo(key);
        flow.setSkuId(skuId);
        flow.setQuantity(Math.abs(delta));
        flow.setChangeAvailable(delta);
        flow.setChangeLocked(0);
        flow.setRequestToken(UUID.randomUUID().toString());
        flow.setRemark(remark);
        flow.setCreatedBy(operatorId);
        return flow;
    }

    private void verifyDuplicate(StockFlow existing, Long skuId, int delta, String remark) {
        boolean same = Objects.equals(existing.getSkuId(), skuId)
                && Objects.equals(existing.getQuantity(), Math.abs(delta))
                && Objects.equals(existing.getChangeAvailable(), delta)
                && Objects.equals(normalizeRemark(existing.getRemark()), remark);
        if (!same) {
            throw new BusinessException(InventoryErrorCode.STOCK_OPERATION_CONFLICT);
        }
    }

    private String requireIdempotencyKey(String key) {
        if (!StringUtils.hasText(key) || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(InventoryErrorCode.STOCK_IDEMPOTENCY_KEY_REQUIRED);
        }
        return key;
    }

    private Long requireSkuId(Long skuId) {
        if (skuId == null || skuId <= 0) {
            throw new BusinessException(InventoryErrorCode.STOCK_SKU_NOT_FOUND);
        }
        return skuId;
    }

    private int requireDelta(Integer delta) {
        if (delta == null || delta == 0 || delta == Integer.MIN_VALUE) {
            throw new BusinessException(InventoryErrorCode.STOCK_QUANTITY_INVALID);
        }
        return delta;
    }

    private String normalizeRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        return remark.trim();
    }

    private StockResponse toResponse(StockSku stock, SkuSnapshot sku) {
        return new StockResponse(
                stock.getSkuId(),
                sku == null ? null : sku.skuCode(),
                sku == null ? null : sku.spuName(),
                stock.getAvailableStock(),
                stock.getLockedStock(),
                stock.getAvailableStock() + stock.getLockedStock(),
                stock.getVersion(),
                stock.getUpdatedAt()
        );
    }
}
