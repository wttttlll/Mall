package com.mallagent.module.order.api;

import com.mallagent.module.order.api.dto.ReviewableOrderItem;

import java.util.Optional;

/**
 * 评价模块读取订单明细的只读契约。
 *
 * <p>只有订单属于当前用户且状态为 COMPLETED 时才返回明细，其他情况一律返回空。</p>
 */
public interface OrderReviewQueryApi {

    Optional<ReviewableOrderItem> findCompletedOrderItem(Long userId, String orderNo, Long skuId);
}
