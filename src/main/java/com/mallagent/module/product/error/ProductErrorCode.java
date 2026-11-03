package com.mallagent.module.product.error;

import com.mallagent.common.error.ErrorCode;

/**
 * 商品模块错误码，使用 12000-12999 区间。
 */
public enum ProductErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(12001, "商品分类不存在"),
    CATEGORY_NAME_EXISTS(12002, "同一父分类下已存在同名分类"),
    CATEGORY_LEVEL_EXCEEDED(12003, "商品分类最多支持 3 级"),
    CATEGORY_HAS_CHILDREN(12004, "分类下存在子分类，不能删除"),
    CATEGORY_IN_USE(12005, "分类已被商品使用，不能删除"),
    BRAND_NOT_FOUND(12006, "品牌不存在"),
    BRAND_NAME_EXISTS(12007, "品牌名称已存在"),
    BRAND_IN_USE(12008, "品牌已被商品使用，不能删除"),
    SPU_NOT_FOUND(12009, "商品不存在"),
    SPU_NOT_EDITABLE(12010, "当前商品状态不允许修改"),
    INVALID_STATUS_TRANSITION(12011, "非法的商品状态流转"),
    SPU_NOT_VISIBLE(12012, "商品不可见"),
    SKU_NOT_FOUND(12013, "SKU 不存在"),
    SKU_CODE_EXISTS(12014, "SKU 编码已存在"),
    SKU_SPEC_INVALID(12015, "SKU 规格值不合法"),
    SKU_SPEC_DUPLICATE(12016, "SKU 规格组合重复"),
    SPEC_NOT_FOUND(12017, "规格不存在"),
    SPEC_NAME_EXISTS(12018, "同一商品下已存在同名规格"),
    SPEC_VALUE_NOT_FOUND(12019, "规格值不存在"),
    SPEC_VALUE_EXISTS(12020, "同一规格下已存在同名规格值"),
    SPEC_IN_USE(12021, "规格已被 SKU 使用，不能删除"),
    SPEC_VALUE_IN_USE(12022, "规格值已被 SKU 使用，不能删除"),
    PRODUCT_NOT_PUBLISHABLE(12023, "商品不满足上架条件"),
    CATEGORY_DISABLED(12024, "商品分类已禁用"),
    BRAND_DISABLED(12025, "品牌已禁用"),
    INVALID_PRICE(12026, "商品价格不合法"),
    IMAGE_REQUIRED(12027, "商品主图不能为空"),
    PRODUCT_NOT_DELETABLE(12028, "当前商品状态不允许删除"),
    SKU_NOT_EDITABLE(12029, "当前商品状态不允许修改 SKU"),
    CATEGORY_PARENT_INVALID(12030, "父分类不合法"),
    INVALID_PRODUCT_STATUS(12031, "无效的商品状态"),
    SKU_SPEC_INCOMPLETE(12032, "SKU 规格不完整，无法上架");

    private final int code;
    private final String message;

    ProductErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
