package com.mallagent.module.product.enums;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.product.error.ProductErrorCode;

import java.util.Locale;

/**
 * 商品状态机：
 * DRAFT -> ON_SHELF
 * ON_SHELF -> OFF_SHELF
 * OFF_SHELF -> ON_SHELF / DRAFT
 */
public enum ProductStatus {

    DRAFT,
    ON_SHELF,
    OFF_SHELF;

    public static ProductStatus from(String value) {
        if (value == null) {
            throw new BusinessException(ProductErrorCode.INVALID_PRODUCT_STATUS);
        }
        try {
            return ProductStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ProductErrorCode.INVALID_PRODUCT_STATUS);
        }
    }

    public boolean canTransitionTo(ProductStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == ON_SHELF;
            case ON_SHELF -> target == OFF_SHELF;
            case OFF_SHELF -> target == ON_SHELF || target == DRAFT;
        };
    }

    public boolean editable() {
        return this == DRAFT || this == OFF_SHELF;
    }

    public boolean deletable() {
        return this == DRAFT || this == OFF_SHELF;
    }

    public boolean onShelf() {
        return this == ON_SHELF;
    }
}
