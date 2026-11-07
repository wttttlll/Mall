package com.mallagent.module.payment.api;

import com.mallagent.module.payment.api.dto.PaymentSnapshot;
import com.mallagent.module.payment.api.dto.RefundCommand;
import com.mallagent.module.payment.api.dto.RefundResult;

import java.util.Optional;

/**
 * 模拟退款内部契约。
 *
 * <p>refund 使用 MANDATORY 传播，必须加入售后完成事务；退款记录、库存回补和售后状态
 * 必须由同一个最外层事务提交或回滚。</p>
 */
public interface PaymentRefundApi {

    Optional<PaymentSnapshot> findSuccessfulPayment(String orderNo, Long userId);

    RefundResult refund(RefundCommand command);

    Optional<RefundResult> findRefundByAfterSaleNo(String afterSaleNo);
}
