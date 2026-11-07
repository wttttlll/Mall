package com.mallagent.module.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 提交商品评价。用户、商品和订单归属均由后端从订单明细校验，不能由前端指定。
 */
public record CreateReviewRequest(
        @NotBlank(message = "订单号不能为空")
        @Size(max = 64, message = "订单号不能超过 64 个字符")
        String orderNo,

        @NotNull(message = "SKU 不能为空")
        @Positive(message = "SKU 不合法")
        Long skuId,

        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分不能低于 1 星")
        @Max(value = 5, message = "评分不能高于 5 星")
        Integer rating,

        @Size(max = 1000, message = "评价内容不能超过 1000 个字符")
        String content
) {
}
