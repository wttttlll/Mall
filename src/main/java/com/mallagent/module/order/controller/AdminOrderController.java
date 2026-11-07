package com.mallagent.module.order.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.order.dto.admin.AdminOrderResponse;
import com.mallagent.module.order.dto.admin.AdminOrderSummaryResponse;
import com.mallagent.module.order.dto.admin.ShipOrderRequest;
import com.mallagent.module.order.service.AdminOrderService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单查询与发货。
 */
@Tag(name = "管理端-订单")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @Operation(summary = "订单分页查询", description = "支持按订单状态和订单号搜索")
    @PreAuthorize("hasAuthority('order:info:read')")
    @GetMapping
    public Result<PageResult<AdminOrderSummaryResponse>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo) {
        return Result.success(adminOrderService.page(pageNum, pageSize, status, orderNo));
    }

    @Operation(summary = "订单详情")
    @PreAuthorize("hasAuthority('order:info:read')")
    @GetMapping("/{orderNo}")
    public Result<AdminOrderResponse> detail(@PathVariable String orderNo) {
        return Result.success(adminOrderService.detail(orderNo));
    }

    @Operation(summary = "订单发货",
            description = "仅已支付订单可发货；保存快递公司和物流单号；重复发货幂等")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "无发货权限"),
            @ApiResponse(responseCode = "404", description = "订单不存在"),
            @ApiResponse(responseCode = "409", description = "订单未支付或状态不允许")
    })
    @PreAuthorize("hasAuthority('order:info:ship')")
    @PostMapping("/{orderNo}/ship")
    public Result<AdminOrderResponse> ship(@PathVariable String orderNo,
                                           @Valid @RequestBody ShipOrderRequest request) {
        Long operatorId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return Result.success(adminOrderService.ship(operatorId, orderNo, request));
    }
}
