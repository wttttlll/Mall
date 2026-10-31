package com.mallagent.module.product.api.dto;

/**
 * SKU 可购买状态。购物车等调用方根据该状态决定是否允许加入。
 */
public enum SkuAvailability {

    AVAILABLE,
    SKU_DISABLED,
    SPU_NOT_FOUND,
    SPU_NOT_ON_SHELF,
    CATEGORY_NOT_FOUND,
    CATEGORY_DISABLED,
    BRAND_NOT_FOUND,
    BRAND_DISABLED
}
