package com.mallagent.module.inventory.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 管理端库存调整请求。delta 为可售库存变化量，允许负数，但不能为 0。
 */
public record StockAdjustRequest(

        @NotNull(message = "SKU ID 不能为空")
        @Positive(message = "SKU ID 必须为正数")
        Long skuId,

        @NotNull(message = "调整数量不能为空")
        Integer delta,

        @Size(max = 255, message = "备注长度不能超过 255")
        String remark
) {
}
