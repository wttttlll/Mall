package com.mallagent.module.aftersale.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.aftersale.dto.AfterSaleResponse;
import com.mallagent.module.aftersale.dto.AfterSaleSummaryResponse;
import com.mallagent.module.aftersale.dto.CreateAfterSaleRequest;
import com.mallagent.module.aftersale.service.AfterSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户整单售后接口。
 */
@Tag(name = "售后")
@RestController
@RequestMapping("/api/v1/after-sales")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    @Operation(summary = "申请整单退货退款",
            description = "仅当前用户已完成订单可申请；退款金额取原支付快照")
    @PostMapping
    public ResponseEntity<Result<AfterSaleResponse>> apply(
            @Valid @RequestBody CreateAfterSaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(afterSaleService.apply(currentUserId(), request)));
    }

    @Operation(summary = "我的售后列表")
    @GetMapping
    public Result<PageResult<AfterSaleSummaryResponse>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo) {
        return Result.success(afterSaleService.pageMine(
                currentUserId(), pageNum, pageSize, status, orderNo));
    }

    @Operation(summary = "我的售后详情")
    @GetMapping("/{afterSaleNo}")
    public Result<AfterSaleResponse> detail(@PathVariable String afterSaleNo) {
        return Result.success(afterSaleService.detailMine(currentUserId(), afterSaleNo));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
