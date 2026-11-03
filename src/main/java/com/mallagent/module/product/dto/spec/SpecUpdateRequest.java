package com.mallagent.module.product.dto.spec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改规格。
 */
public record SpecUpdateRequest(
        @NotBlank(message = "规格名称不能为空")
        @Size(max = 64, message = "规格名称长度不能超过 64")
        String name,

        Integer sort
) {
}
