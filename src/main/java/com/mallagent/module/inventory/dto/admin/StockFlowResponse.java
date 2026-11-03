package com.mallagent.module.inventory.dto.admin;

import java.time.LocalDateTime;

/**
 * 管理端库存流水响应。
 */
public record StockFlowResponse(
        Long id,
        String bizType,
        String bizNo,
        String refBizNo,
        Long skuId,
        Integer quantity,
        Integer changeAvailable,
        Integer changeLocked,
        Integer availableBefore,
        Integer availableAfter,
        Integer lockedBefore,
        Integer lockedAfter,
        String remark,
        LocalDateTime createdAt,
        Long createdBy
) {
}
