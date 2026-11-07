package com.mallagent.module.order.api;

import com.mallagent.module.order.api.dto.AfterSaleOrderView;

import java.util.Optional;

/**
 * 售后模块读取原订单和订单明细的只读契约。
 */
public interface OrderAfterSaleQueryApi {

    Optional<AfterSaleOrderView> findOrder(String orderNo);
}
