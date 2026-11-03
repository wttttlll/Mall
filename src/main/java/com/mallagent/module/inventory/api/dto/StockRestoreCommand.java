package com.mallagent.module.inventory.api.dto;

import java.util.List;

/**
 * 售后回补命令。
 *
 * @param bizNo     售后单号
 * @param refBizNo  原订单号，用于定位 ORDER_LOCK/ORDER_DEDUCT 流水
 * @param items     回补条目；阶段 4 必须与原始扣减的 SKU 集合和数量一致
 */
public record StockRestoreCommand(
        String bizNo,
        String refBizNo,
        List<StockItem> items
) {

    public StockRestoreCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
