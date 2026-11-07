package com.mallagent.module.favorite.dto;

import java.time.LocalDateTime;

/**
 * 收藏列表项。商品下架或删除时保留收藏记录，并返回明确的不可用原因。
 */
public record FavoriteResponse(
        Long spuId,
        String spuName,
        String subtitle,
        String mainImage,
        String status,
        boolean available,
        String unavailableReason,
        LocalDateTime createdAt
) {
}
