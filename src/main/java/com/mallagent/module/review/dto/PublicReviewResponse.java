package com.mallagent.module.review.dto;

import java.time.LocalDateTime;

/**
 * 商品详情页公开评价。只返回脱敏昵称，不返回用户名或其他用户资料。
 */
public record PublicReviewResponse(
        Long id,
        String displayName,
        Integer rating,
        String content,
        LocalDateTime createdAt
) {
}
