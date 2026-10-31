package com.mallagent.module.product.dto.spec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建规格值。
 */
public record SpecValueCreateRequest(
        @NotBlank(message = "规格值不能为空")
        @Size(max = 64, message = "规格值长度不能超过 64")
        String value,

        Integer sort
) {
}
