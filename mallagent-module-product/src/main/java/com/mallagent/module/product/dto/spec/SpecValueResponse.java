package com.mallagent.module.product.dto.spec;

/**
 * 规格值。
 */
public record SpecValueResponse(
        Long id,
        String value,
        Integer sort
) {
}
