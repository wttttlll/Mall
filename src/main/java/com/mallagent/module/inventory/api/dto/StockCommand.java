package com.mallagent.module.inventory.api.dto;

import java.util.List;

/**
 * 订单级库存命令。
 *
 * <p>bizNo 为订单号；items 必须覆盖该订单全部 SKU，不能只传部分条目。</p>
 */
public record StockCommand(
        String bizNo,
        List<StockItem> items
) {

    public StockCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
