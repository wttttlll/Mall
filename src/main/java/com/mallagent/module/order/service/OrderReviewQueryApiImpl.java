package com.mallagent.module.order.service;

import com.mallagent.module.order.api.OrderReviewQueryApi;
import com.mallagent.module.order.api.dto.ReviewableOrderItem;
import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.entity.OrderItem;
import com.mallagent.module.order.enums.OrderStatus;
import com.mallagent.module.order.mapper.OrderInfoMapper;
import com.mallagent.module.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 评价模块订单只读查询实现。
 */
@Service
@RequiredArgsConstructor
public class OrderReviewQueryApiImpl implements OrderReviewQueryApi {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public Optional<ReviewableOrderItem> findCompletedOrderItem(Long userId, String orderNo, Long skuId) {
        if (userId == null || orderNo == null || skuId == null) {
            return Optional.empty();
        }
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        if (order == null
                || !order.getUserId().equals(userId)
                || !OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            return Optional.empty();
        }
        OrderItem item = orderItemMapper.selectByOrderIdAndSkuId(order.getId(), skuId);
        if (item == null) {
            return Optional.empty();
        }
        return Optional.of(new ReviewableOrderItem(
                item.getId(),
                order.getOrderNo(),
                item.getSpuId(),
                item.getSkuId(),
                item.getSpuName(),
                item.getSkuCode(),
                item.getImageUrl(),
                item.getSpecText()
        ));
    }
}
