
package com.mallagent.module.inventory.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.module.inventory.dto.admin.*;
import com.mallagent.module.inventory.service.InventoryAdminService;
import com.mallagent.module.inventory.service.InventoryQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-库存")
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryAdminService adminService;
    private final InventoryQueryService queryService;

    // 1. 分页查询库存
    @Operation(summary = "库存分页查询")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    @GetMapping("/stocks")
    public Result<PageResult<StockResponse>> pageStocks(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long skuId) {

        return Result.success(queryService.pageStocks(pageNum, pageSize, skuId));
    }

    // 2. 查询单个 SKU 库存
    @Operation(summary = "库存详情")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    @GetMapping("/stocks/{skuId}")
    public Result<StockResponse> getStock(@PathVariable Long skuId) {
        return Result.success(queryService.getStock(skuId));
    }

    // 3. 调整库存
    @Operation(summary = "调整库存")
    @PreAuthorize("hasAuthority('inventory:stock:adjust')")
    @PostMapping("/stocks/adjust")
    public Result<StockResponse> adjust(
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody StockAdjustRequest request) {

        return Result.success(adminService.adjust(idempotencyKey, request));
    }

    // 4. 查询库存流水
    @Operation(summary = "库存流水分页查询")
    @PreAuthorize("hasAuthority('inventory:flow:read')")
    @GetMapping("/flows")
    public Result<PageResult<StockFlowResponse>> pageFlows(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String refBizNo) {

        return Result.success(queryService.pageFlows(
                pageNum, pageSize, skuId, bizType, bizNo, refBizNo));
    }
}