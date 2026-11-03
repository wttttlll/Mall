package com.mallagent.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.common.api.PageResult;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.inventory.dto.admin.StockFlowResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端库存与流水只读查询。
 */
@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StockSkuMapper stockSkuMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ProductSkuQueryApi productSkuQueryApi;

    public PageResult<StockResponse> pageStocks(long pageNum, long pageSize, Long skuId) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        LambdaQueryWrapper<StockSku> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) {
            wrapper.eq(StockSku::getSkuId, skuId);
        }
        wrapper.orderByDesc(StockSku::getId);

        Page<StockSku> page = stockSkuMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        Map<Long, SkuSnapshot> skuById = loadSkuSnapshots(page.getRecords());
        List<StockResponse> responses = page.getRecords().stream()
                .map(stock -> toResponse(stock, skuById.get(stock.getSkuId())))
                .toList();
        return new PageResult<>(responses, page.getTotal(), safePageNum, safePageSize);
    }

    public StockResponse getStock(Long skuId) {
        StockSku stock = stockSkuMapper.selectBySkuId(skuId);
        if (stock == null) {
            throw new BusinessException(InventoryErrorCode.STOCK_NOT_FOUND);
        }
        SkuSnapshot sku = productSkuQueryApi.findSku(skuId).orElse(null);
        return toResponse(stock, sku);
    }

    public PageResult<StockFlowResponse> pageFlows(long pageNum,
                                                   long pageSize,
                                                   Long skuId,
                                                   String bizType,
                                                   String bizNo,
                                                   String refBizNo) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        StockBizType parsedBizType = parseBizType(bizType);

        LambdaQueryWrapper<StockFlow> wrapper = new LambdaQueryWrapper<>();
        if (skuId != null) {
            wrapper.eq(StockFlow::getSkuId, skuId);
        }
        if (parsedBizType != null) {
            wrapper.eq(StockFlow::getBizType, parsedBizType.name());
        }
        if (StringUtils.hasText(bizNo)) {
            wrapper.eq(StockFlow::getBizNo, bizNo.trim());
        }
        if (StringUtils.hasText(refBizNo)) {
            wrapper.eq(StockFlow::getRefBizNo, refBizNo.trim());
        }
        wrapper.orderByDesc(StockFlow::getId);

        Page<StockFlow> page = stockFlowMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        List<StockFlowResponse> responses = page.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(responses, page.getTotal(), safePageNum, safePageSize);
    }

    private Map<Long, SkuSnapshot> loadSkuSnapshots(List<StockSku> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return Map.of();
        }
        List<Long> skuIds = stocks.stream()
                .map(StockSku::getSkuId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        return productSkuQueryApi.findSkus(skuIds).stream()
                .collect(Collectors.toMap(SkuSnapshot::skuId, Function.identity(), (left, right) -> left));
    }

    private StockBizType parseBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return null;
        }
        try {
            return StockBizType.valueOf(bizType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "bizType 不合法");
        }
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

    private StockFlowResponse toResponse(StockFlow flow) {
        return new StockFlowResponse(
                flow.getId(),
                flow.getBizType(),
                flow.getBizNo(),
                flow.getRefBizNo(),
                flow.getSkuId(),
                flow.getQuantity(),
                flow.getChangeAvailable(),
                flow.getChangeLocked(),
                flow.getAvailableBefore(),
                flow.getAvailableAfter(),
                flow.getLockedBefore(),
                flow.getLockedAfter(),
                flow.getRemark(),
                flow.getCreatedAt(),
                flow.getCreatedBy()
        );
    }
}
