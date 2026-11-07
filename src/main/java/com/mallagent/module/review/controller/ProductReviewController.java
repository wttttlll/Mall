package com.mallagent.module.review.controller;

import com.mallagent.common.api.Result;
import com.mallagent.module.review.dto.ProductReviewListResponse;
import com.mallagent.module.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品详情公开评价。
 */
@Tag(name = "商品评价")
@RestController
@RequestMapping("/api/v1/products/{spuId}/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "商品评价列表与评分汇总",
            description = "只返回公开字段和脱敏昵称")
    @GetMapping
    public Result<ProductReviewListResponse> list(
            @PathVariable Long spuId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(reviewService.productReviews(spuId, pageNum, pageSize));
    }
}
