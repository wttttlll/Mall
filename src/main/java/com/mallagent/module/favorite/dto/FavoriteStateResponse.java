package com.mallagent.module.favorite.dto;

/**
 * 收藏状态变更结果。
 */
public record FavoriteStateResponse(
        Long spuId,
        boolean favorite
) {
}
