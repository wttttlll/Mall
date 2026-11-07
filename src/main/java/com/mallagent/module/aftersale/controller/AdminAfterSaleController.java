package com.mallagent.module.aftersale.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.aftersale.dto.AfterSaleResponse;
import com.mallagent.module.aftersale.dto.AfterSaleSummaryResponse;
import com.mallagent.module.aftersale.dto.RejectAfterSaleRequest;
import com.mallagent.module.aftersale.service.AfterSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端售后查询、审核和完成退款。
 */
@Tag(name = "管理端-售后")
@RestController
@RequestMapping("/api/v1/admin/after-sales")
@RequiredArgsConstructor
public class AdminAfterSaleController {

    private final AfterSaleService afterSaleService;

    @Operation(summary = "售后分页查询")
    @PreAuthorize("hasAuthority('aftersale:order:read')")
    @GetMapping
    public Result<PageResult<AfterSaleSummaryResponse>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String afterSaleNo) {
        return Result.success(afterSaleService.adminPage(pageNum, pageSize, status, afterSaleNo));
    }

    @Operation(summary = "售后详情")
    @PreAuthorize("hasAuthority('aftersale:order:read')")
    @GetMapping("/{afterSaleNo}")
    public Result<AfterSaleResponse> detail(@PathVariable String afterSaleNo) {
        return Result.success(afterSaleService.adminDetail(afterSaleNo));
    }

    @Operation(summary = "审核通过售后")
    @PreAuthorize("hasAuthority('aftersale:order:review')")
    @PostMapping("/{afterSaleNo}/approve")
    public Result<AfterSaleResponse> approve(@PathVariable String afterSaleNo) {
        return Result.success(afterSaleService.approve(currentUserId(), afterSaleNo));
    }

    @Operation(summary = "拒绝售后", description = "拒绝原因必填；拒绝后不退款、不回补库存")
    @PreAuthorize("hasAuthority('aftersale:order:review')")
    @PostMapping("/{afterSaleNo}/reject")
    public Result<AfterSaleResponse> reject(@PathVariable String afterSaleNo,
                                            @Valid @RequestBody RejectAfterSaleRequest request) {
        return Result.success(afterSaleService.reject(currentUserId(), afterSaleNo, request));
    }

    @Operation(summary = "确认收到退货并完成退款",
            description = "模拟退款、库存回补与售后完成状态同一事务；重复完成幂等")
    @PreAuthorize("hasAuthority('aftersale:order:complete')")
    @PostMapping("/{afterSaleNo}/complete")
    public Result<AfterSaleResponse> complete(@PathVariable String afterSaleNo) {
        return Result.success(afterSaleService.complete(currentUserId(), afterSaleNo));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
