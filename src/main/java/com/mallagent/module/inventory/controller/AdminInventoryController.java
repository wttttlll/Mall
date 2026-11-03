package com.mallagent.module.inventory.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.module.inventory.dto.admin.StockAdjustRequest;
import com.mallagent.module.inventory.dto.admin.StockFlowResponse;
import com.mallagent.module.inventory.dto.admin.StockResponse;
import com.mallagent.module.inventory.service.InventoryAdminService;
import com.mallagent.module.inventory.service.InventoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端库存查询、调整与流水查询。
 */
@Tag(name = "管理端-库存")
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryAdminService inventoryAdminService;
    private final InventoryQueryService inventoryQueryService;

    @Operation(summary = "库存分页查询")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    @GetMapping("/stocks")
    public Result<PageResult<StockResponse>> pageStocks(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long skuId) {
        return Result.success(inventoryQueryService.pageStocks(pageNum, pageSize, skuId));
    }

    @Operation(summary = "库存详情")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "库存记录不存在"),
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    @GetMapping("/stocks/{skuId}")
    public Result<StockResponse> getStock(@PathVariable Long skuId) {
        return Result.success(inventoryQueryService.getStock(skuId));
    }

    @Operation(summary = "调整库存",
            description = "单 SKU 调整可售库存；必须携带 Idempotency-Key；不直接修改锁定库存")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "参数错误、幂等键缺失或调整后库存为负"),
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "403", description = "无权限"),
            @ApiResponse(responseCode = "404", description = "SKU 不存在"),
            @ApiResponse(responseCode = "409", description = "库存未初始化或幂等键冲突")
    })
    @PreAuthorize("hasAuthority('inventory:stock:adjust')")
    @PostMapping("/stocks/adjust")
    public Result<StockResponse> adjust(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StockAdjustRequest request) {
        return Result.success(inventoryAdminService.adjust(idempotencyKey, request));
    }

    @Operation(summary = "库存流水分页查询")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PreAuthorize("hasAuthority('inventory:flow:read')")
    @GetMapping("/flows")
    public Result<PageResult<StockFlowResponse>> pageFlows(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String refBizNo) {
        return Result.success(inventoryQueryService.pageFlows(
                pageNum, pageSize, skuId, bizType, bizNo, refBizNo));
    }
}
