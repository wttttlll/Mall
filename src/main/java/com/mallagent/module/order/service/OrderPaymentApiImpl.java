package com.mallagent.module.order.service;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.order.api.OrderPaymentApi;
import com.mallagent.module.order.api.dto.OrderPaymentItem;
import com.mallagent.module.order.api.dto.OrderPaymentView;
import com.mallagent.module.order.config.OrderProperties;
import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.error.OrderErrorCode;
import com.mallagent.module.order.mapper.OrderInfoMapper;
import com.mallagent.module.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单支付契约实现。必须在支付模块事务内执行。
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentApiImpl implements OrderPaymentApi {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderProperties orderProperties;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderPaymentView lockForPayment(String orderNo, Long userId) {
        OrderInfo order = orderInfoMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        List<OrderPaymentItem> items = orderItemMapper.selectByOrderId(order.getId()).stream()
                .map(item -> new OrderPaymentItem(item.getSkuId(), item.getQuantity()))
                .toList();
        return new OrderPaymentView(
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt() == null
                        ? null
                        : order.getCreatedAt().plusMinutes(orderProperties.getTimeoutMinutes()),
                items
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaid(String orderNo, Long userId, LocalDateTime paidAt) {
        int updated = orderInfoMapper.markPaid(orderNo, userId, paidAt);
        if (updated != 1) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_CONFLICT);
        }
    }
}
