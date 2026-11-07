package com.mallagent.module.order.api.dto;

/**
 * 可评价的已完成订单明细。评价模块用它校验归属并保存商品快照。
 */
public record ReviewableOrderItem(
        Long orderItemId,
        String orderNo,
        Long spuId,
        Long skuId,
        String spuName,
        String skuCode,
        String imageUrl,
        String specText
) {
}
