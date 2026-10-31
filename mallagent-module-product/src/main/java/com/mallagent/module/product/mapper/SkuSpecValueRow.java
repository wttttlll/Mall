package com.mallagent.module.product.mapper;

import lombok.Data;

/**
 * SKU 规格值联表查询结果。
 */
@Data
public class SkuSpecValueRow {

    private Long skuId;

    private Long specId;

    private String specName;

    private Long specValueId;

    private String specValueName;
}
