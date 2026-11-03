package com.mallagent.module.product.dto.spec;

import java.util.List;

/**
 * 规格及其规格值。
 */
public record SpecResponse(
        Long id,
        String name,
        Integer sort,
        List<SpecValueResponse> values
) {
}
