package com.mallagent.module.product.dto.category;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改商品分类。父分类创建后不可修改。
 */
public record CategoryUpdateRequest(

        @NotBlank(message = "分类名称不能为空")
        @Size(max = 64, message = "分类名称长度不能超过 64")
        String name,

        Integer sort,

        @Min(value = 0, message = "状态只能是 0 或 1")
        @Max(value = 1, message = "状态只能是 0 或 1")
        Integer status,

        @Size(max = 512, message = "图标 URL 长度不能超过 512")
        String iconUrl
) {
}
