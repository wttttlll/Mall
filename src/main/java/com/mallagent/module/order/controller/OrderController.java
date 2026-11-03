package com.mallagent.module.order.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.order.dto.order.CreateOrderRequest;
import com.mallagent.module.order.dto.order.OrderResponse;
import com.mallagent.module.order.dto.order.OrderSummaryResponse;
import com.mallagent.module.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户订单接口。所有接口只操作当前登录用户的数据。
 */
@Tag(name = "订单")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "从购物车提交订单",
            description = "服务端重新读取商品价格；必须携带 Idempotency-Key；下单与库存锁定同一事务")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "参数错误或幂等键缺失"),
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "404", description = "收货地址不存在"),
            @ApiResponse(responseCode = "409", description = "购物车为空、商品不可购买或库存不足")
    })
    @PostMapping
    public ResponseEntity<Result<OrderResponse>> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(orderService.create(currentUserId(), idempotencyKey, request)));
    }

    @Operation(summary = "我的订单列表")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "状态参数不合法"),
            @ApiResponse(responseCode = "401", description = "未认证")
    })
    @GetMapping
    public Result<PageResult<OrderSummaryResponse>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(orderService.list(currentUserId(), pageNum, pageSize, status));
    }

    @Operation(summary = "订单详情")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "404", description = "订单不存在")
    })
    @GetMapping("/{orderNo}")
    public Result<OrderResponse> detail(@PathVariable String orderNo) {
        return Result.success(orderService.detail(currentUserId(), orderNo));
    }

    @Operation(summary = "取消待支付订单",
            description = "取消时释放库存；已取消订单重复取消幂等成功")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "404", description = "订单不存在"),
            @ApiResponse(responseCode = "409", description = "已支付订单不能取消")
    })
    @PostMapping("/{orderNo}/cancel")
    public Result<OrderResponse> cancel(@PathVariable String orderNo) {
        return Result.success(orderService.cancel(currentUserId(), orderNo));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
