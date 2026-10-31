package com.mallagent.module.product.dto.spu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 修改 SPU 基础信息与图片。
 */
public record SpuUpdateRequest(

        @NotBlank(message = "商品名称不能为空")
        @Size(max = 128, message = "商品名称长度不能超过 128")
        String name,

        @Size(max = 255, message = "副标题长度不能超过 255")
        String subtitle,

        @NotNull(message = "商品分类不能为空")
        Long categoryId,

        Long brandId,

        @Size(max = 512, message = "主图 URL 长度不能超过 512")
        String mainImage,

        @Size(max = 20, message = "商品图片最多 20 张")
        List<@Size(max = 512, message = "图片 URL 长度不能超过 512") String> images,

        @Size(max = 5000, message = "商品描述长度不能超过 5000")
        String description,

        Integer sort
) {
}
