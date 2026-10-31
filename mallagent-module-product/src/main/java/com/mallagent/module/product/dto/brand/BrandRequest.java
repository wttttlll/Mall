package com.mallagent.module.product.dto.brand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建或修改品牌。
 */
public record BrandRequest(

        @NotBlank(message = "品牌名称不能为空")
        @Size(max = 64, message = "品牌名称长度不能超过 64")
        String name,

        @Size(max = 512, message = "Logo URL 长度不能超过 512")
        String logoUrl,

        @Size(max = 255, message = "品牌描述长度不能超过 255")
        String description,

        @Min(value = 0, message = "状态只能是 0 或 1")
        @Max(value = 1, message = "状态只能是 0 或 1")
        Integer status,

        Integer sort
) {
}
