package com.mallagent.module.review.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品评价汇总与分页列表。
 */
public record ProductReviewListResponse(
        BigDecimal averageRating,
        long reviewCount,
        List<PublicReviewResponse> list,
        long total,
        long pageNum,
        long pageSize
) {

    public ProductReviewListResponse {
        list = list == null ? List.of() : List.copyOf(list);
    }
}
