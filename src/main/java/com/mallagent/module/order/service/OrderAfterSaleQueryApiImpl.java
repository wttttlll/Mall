package com.mallagent.module.order.service;

import com.mallagent.module.order.api.OrderAfterSaleQueryApi;
import com.mallagent.module.order.api.dto.AfterSaleOrderItem;
import com.mallagent.module.order.api.dto.AfterSaleOrderView;
import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.mapper.OrderInfoMapper;
import com.mallagent.module.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 售后模块订单只读查询实现。
 */
@Service
@RequiredArgsConstructor
public class OrderAfterSaleQueryApiImpl implements OrderAfterSaleQueryApi {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public Optional<AfterSaleOrderView> findOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return Optional.empty();
        }
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            return Optional.empty();
        }
        return Optional.of(new AfterSaleOrderView(
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getPaidAt(),
                order.getCompletedAt(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverProvince(),
                order.getReceiverCity(),
                order.getReceiverDistrict(),
                order.getReceiverDetailAddress(),
                orderItemMapper.selectByOrderId(order.getId()).stream()
                        .map(item -> new AfterSaleOrderItem(
                                item.getSkuId(),
                                item.getSpuId(),
                                item.getSkuCode(),
                                item.getSpuName(),
                                item.getImageUrl(),
                                item.getSpecText(),
                                item.getUnitPrice(),
                                item.getQuantity(),
                                item.getLineAmount()))
                        .toList()
        ));
    }
}
