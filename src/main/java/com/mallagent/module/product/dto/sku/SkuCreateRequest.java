package com.mallagent.module.product.dto.sku;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建 SKU。
 */
public record SkuCreateRequest(

        @NotBlank(message = "SKU 编码不能为空")
        @Size(max = 64, message = "SKU 编码长度不能超过 64")
        String skuCode,

        @NotNull(message = "销售价不能为空")
        @DecimalMin(value = "0.01", message = "销售价必须大于 0")
        @Digits(integer = 16, fraction = 2, message = "销售价最多保留两位小数")
        BigDecimal price,

        @DecimalMin(value = "0.00", message = "市场价不能为负数")
        @Digits(integer = 16, fraction = 2, message = "市场价最多保留两位小数")
        BigDecimal marketPrice,

        @Size(max = 512, message = "SKU 图片 URL 长度不能超过 512")
        String imageUrl,

        List<Long> specValueIds,

        @Min(value = 0, message = "状态只能是 0 或 1")
        @Max(value = 1, message = "状态只能是 0 或 1")
        Integer status
) {
}
