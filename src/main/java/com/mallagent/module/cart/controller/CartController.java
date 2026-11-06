package com.mallagent.module.cart.controller;

import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.cart.dto.cart.AddCartItemRequest;
import com.mallagent.module.cart.dto.cart.CartResponse;
import com.mallagent.module.cart.dto.cart.UpdateCartItemRequest;
import com.mallagent.module.cart.dto.cart.UpdateSelectedRequest;
import com.mallagent.module.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户购物车接口。所有接口只操作当前登录用户的数据。
 */
@Tag(name = "购物车")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "添加购物车",
            description = "重复添加同一 SKU 合并数量；同一 Idempotency-Key 相同请求不会重复累加")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "参数错误或幂等键缺失"),
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "404", description = "SKU 不存在"),
            @ApiResponse(responseCode = "409", description = "商品不可购买、数量超限或幂等键冲突")
    })

    //增
    @PostMapping("/items")
    public Result<CartResponse> add(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AddCartItemRequest request) {
        Long userId = currentUserId();
        cartService.addItem(userId, idempotencyKey, request);
        return Result.success(cartService.getCart(userId));
    }

    //改
    @Operation(summary = "修改购买数量", description = "数量为绝对值，范围 1-99")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "数量不合法"),
            @ApiResponse(responseCode = "404", description = "购物车条目不存在")
    })
    @PutMapping("/items/{skuId}")
    public Result<CartResponse> updateQuantity(@PathVariable Long skuId,
                                               @Valid @RequestBody UpdateCartItemRequest request) {
        Long userId = currentUserId();
        cartService.updateQuantity(userId, skuId, request);
        return Result.success(cartService.getCart(userId));
    }

    //删
    @Operation(summary = "删除购物车条目", description = "条目不存在时视为成功")
    @DeleteMapping("/items/{skuId}")
    public Result<CartResponse> delete(@PathVariable Long skuId) {
        Long userId = currentUserId();
        cartService.deleteItem(userId, skuId);
        return Result.success(cartService.getCart(userId));
    }


    @Operation(summary = "勾选或取消勾选单个条目")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "购物车条目不存在")
    })
    @PutMapping("/items/{skuId}/selected")
    public Result<CartResponse> setSelected(@PathVariable Long skuId,
                                            @Valid @RequestBody UpdateSelectedRequest request) {
        Long userId = currentUserId();
        cartService.setSelected(userId, skuId, request);
        return Result.success(cartService.getCart(userId));
    }

    @Operation(summary = "全选或取消全选")
    @PutMapping("/selected")
    public Result<CartResponse> selectAll(@Valid @RequestBody UpdateSelectedRequest request) {
        Long userId = currentUserId();
        cartService.selectAll(userId, request);
        return Result.success(cartService.getCart(userId));
    }

    //查
    @Operation(summary = "查询购物车",
            description = "价格与可购买状态来自商品模块当前数据；不可购买条目保留并标记")
    @GetMapping
    public Result<CartResponse> get() {
        return Result.success(cartService.getCart(currentUserId()));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
