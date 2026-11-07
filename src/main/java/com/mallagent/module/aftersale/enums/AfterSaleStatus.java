package com.mallagent.module.aftersale.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * 整单售后状态：APPLIED -> APPROVED -> COMPLETED，或 APPLIED -> REJECTED。
 */
public enum AfterSaleStatus {

    APPLIED,
    APPROVED,
    REJECTED,
    COMPLETED;

    public static Optional<AfterSaleStatus> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
