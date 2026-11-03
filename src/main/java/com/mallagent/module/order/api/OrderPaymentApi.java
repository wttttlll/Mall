package com.mallagent.module.order.api;

import com.mallagent.module.order.api.dto.OrderPaymentView;

import java.time.LocalDateTime;

/**
 * 订单提供给模拟支付模块的契约。
 *
 * <p>两方法必须在支付事务内调用；lockForPayment 会对订单行加锁，
 * 与用户取消订单共享同一互斥点。</p>
 */
public interface OrderPaymentApi {

    /**
     * 锁定订单并返回支付所需视图；订单不存在或不属于当前用户时抛订单不存在。
     */
    OrderPaymentView lockForPayment(String orderNo, Long userId);

    /**
     * 将待支付订单标记为已支付。状态已变化时抛状态冲突。
     */
    void markPaid(String orderNo, Long userId, LocalDateTime paidAt);
}
