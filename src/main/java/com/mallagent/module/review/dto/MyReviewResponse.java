package com.mallagent.module.review.dto;

import java.time.LocalDateTime;

/**
 * 当前用户自己的评价。
 */
public record MyReviewResponse(
        Long id,
        String orderNo,
        Long spuId,
        Long skuId,
        String spuName,
        String skuCode,
        String imageUrl,
        Integer rating,
        String content,
        LocalDateTime createdAt
) {
}
