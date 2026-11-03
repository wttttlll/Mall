package com.mallagent.module.payment.service;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.inventory.api.InventoryStockApi;
import com.mallagent.module.inventory.api.dto.StockCommand;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.order.api.OrderPaymentApi;
import com.mallagent.module.order.api.dto.OrderPaymentView;
import com.mallagent.module.payment.dto.payment.PaymentResponse;
import com.mallagent.module.payment.entity.PaymentOrder;
import com.mallagent.module.payment.enums.PaymentStatus;
import com.mallagent.module.payment.error.PaymentErrorCode;
import com.mallagent.module.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 模拟支付：订单行锁 + 库存扣减 + 支付单 + 订单状态在同一事务内完成。
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String ORDER_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String ORDER_PAID = "PAID";
    private static final String ORDER_CANCELLED = "CANCELLED";
    private static final String CHANNEL_MOCK = "MOCK";
    private static final int MAX_ORDER_NO_LENGTH = 64;

    private final OrderPaymentApi orderPaymentApi;
    private final InventoryStockApi inventoryStockApi;
    private final PaymentOrderMapper paymentOrderMapper;

    /**
     * 模拟支付成功。重复支付已支付订单幂等返回，不重复扣减库存。
     */
    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PaymentResponse pay(Long userId, String orderNo) {
        String safeOrderNo = requireOrderNo(orderNo);
        OrderPaymentView order = orderPaymentApi.lockForPayment(safeOrderNo, userId);

        if (ORDER_PAID.equals(order.status())) {
            PaymentOrder existing = paymentOrderMapper.selectByOrderNo(safeOrderNo);
            if (existing == null) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_RECORD_CONFLICT);
            }
            return toResponse(existing);
        }
        if (ORDER_CANCELLED.equals(order.status())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_CONFLICT, "已取消订单不能支付");
        }
        if (!ORDER_PENDING_PAYMENT.equals(order.status())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_CONFLICT);
        }
        if (order.items().isEmpty()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECORD_CONFLICT);
        }

        LocalDateTime paidAt = LocalDateTime.now();

        // 支付扣减库存与订单状态变更在同一事务；失败全部回滚。
        inventoryStockApi.deduct(new StockCommand(order.orderNo(), order.items().stream()
                .map(item -> new StockItem(item.skuId(), item.quantity()))
                .toList()));

        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo("PAY" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        payment.setOrderNo(order.orderNo());
        payment.setUserId(userId);
        payment.setAmount(order.totalAmount());
        payment.setChannel(CHANNEL_MOCK);
        payment.setStatus(PaymentStatus.SUCCESS.name());
        payment.setPaidAt(paidAt);
        try {
            paymentOrderMapper.insert(payment);
        } catch (DuplicateKeyException ex) {
            // 订单行锁下不应发生；出现说明订单与支付记录状态不一致，整体回滚。
            throw new BusinessException(PaymentErrorCode.PAYMENT_RECORD_CONFLICT,
                    PaymentErrorCode.PAYMENT_RECORD_CONFLICT.message(), ex);
        }

        orderPaymentApi.markPaid(order.orderNo(), userId, paidAt);
        return toResponse(payment);
    }

    private String requireOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo) || orderNo.trim().length() > MAX_ORDER_NO_LENGTH) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_COMMAND_INVALID);
        }
        return orderNo.trim();
    }

    private PaymentResponse toResponse(PaymentOrder payment) {
        return new PaymentResponse(
                payment.getPaymentNo(),
                payment.getOrderNo(),
                payment.getAmount(),
                payment.getChannel(),
                payment.getStatus(),
                payment.getPaidAt()
        );
    }
}
