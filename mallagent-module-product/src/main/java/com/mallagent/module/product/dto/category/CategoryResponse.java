package com.mallagent.module.product.dto.category;

import java.util.List;

/**
 * 商品分类树节点。
 */
public record CategoryResponse(
        Long id,
        Long parentId,
        String name,
        Integer level,
        Integer sort,
        Integer status,
        String iconUrl,
        List<CategoryResponse> children
) {
}
