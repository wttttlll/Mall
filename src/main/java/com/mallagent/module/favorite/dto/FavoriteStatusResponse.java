package com.mallagent.module.favorite.dto;

import java.util.List;

/**
 * 指定商品集合中当前用户已收藏的 SPU ID。
 */
public record FavoriteStatusResponse(
        List<Long> favoriteSpuIds
) {

    public FavoriteStatusResponse {
        favoriteSpuIds = favoriteSpuIds == null ? List.of() : List.copyOf(favoriteSpuIds);
    }
}
