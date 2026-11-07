package com.mallagent.module.review.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.review.dto.CreateReviewRequest;
import com.mallagent.module.review.dto.MyReviewResponse;
import com.mallagent.module.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户商品评价接口。
 */
@Tag(name = "评价")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "提交商品评价",
            description = "仅已完成订单的购买用户可以评价；同一订单明细重复提交幂等返回已存在评价")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "409", description = "订单未完成或不属于当前用户")
    })
    @PostMapping
    public ResponseEntity<Result<MyReviewResponse>> create(
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(reviewService.create(currentUserId(), request)));
    }

    @Operation(summary = "我的评价")
    @GetMapping("/mine")
    public Result<PageResult<MyReviewResponse>> mine(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String orderNo) {
        return Result.success(reviewService.mine(currentUserId(), pageNum, pageSize, orderNo));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
