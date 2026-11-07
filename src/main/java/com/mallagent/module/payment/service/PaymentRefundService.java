package com.mallagent.module.payment.service;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.payment.api.PaymentRefundApi;
import com.mallagent.module.payment.api.dto.PaymentSnapshot;
import com.mallagent.module.payment.api.dto.RefundCommand;
import com.mallagent.module.payment.api.dto.RefundResult;
import com.mallagent.module.payment.entity.PaymentOrder;
import com.mallagent.module.payment.entity.PaymentRefund;
import com.mallagent.module.payment.enums.PaymentStatus;
import com.mallagent.module.payment.error.PaymentErrorCode;
import com.mallagent.module.payment.mapper.PaymentOrderMapper;
import com.mallagent.module.payment.mapper.PaymentRefundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * 模拟退款：只写本地退款记录，不调用真实支付平台。
 *
 * <p>必须由售后完成事务调用；退款记录唯一约束保证一个售后单和原订单最多一条退款。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundService implements PaymentRefundApi {

    private static final String CHANNEL_MOCK = "MOCK";

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundMapper paymentRefundMapper;

    @Override
    public Optional<PaymentSnapshot> findSuccessfulPayment(String orderNo, Long userId) {
        if (!StringUtils.hasText(orderNo) || userId == null) {
            return Optional.empty();
        }
        PaymentOrder payment = paymentOrderMapper.selectByOrderNo(orderNo.trim());
        if (payment == null
                || !userId.equals(payment.getUserId())
                || !PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(new PaymentSnapshot(
                payment.getPaymentNo(),
                payment.getOrderNo(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt()
        ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public RefundResult refund(RefundCommand command) {
        if (command == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_CONFLICT);
        }
        String afterSaleNo = requireText(command.afterSaleNo(), "售后单号不合法");
        String orderNo = requireText(command.orderNo(), "订单号不合法");
        if (command.userId() == null || command.amount() == null
                || command.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_CONFLICT);
        }

        PaymentRefund existing = paymentRefundMapper.selectByAfterSaleNo(afterSaleNo);
        if (existing != null) {
            validateExisting(existing, orderNo, command.userId(), command.amount());
            return toResult(existing);
        }

        // 售后完成请求统一先锁售后单，再锁原支付单，最后进入库存 ORDER_LOCK 行锁；
        // 这个顺序与库存回补的锁顺序保持一致。
        PaymentOrder payment = paymentOrderMapper.selectByOrderNoForUpdate(orderNo);
        if (payment == null
                || !command.userId().equals(payment.getUserId())
                || !PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
        if (payment.getAmount() == null
                || payment.getAmount().compareTo(command.amount()) != 0) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_AMOUNT_CONFLICT);
        }

        PaymentRefund refundByOrder = paymentRefundMapper.selectByOrderNo(orderNo);
        if (refundByOrder != null) {
            // 订单已有退款记录，即使来自另一售后单也不能再次退款。
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_CONFLICT);
        }

        PaymentRefund refund = new PaymentRefund();
        refund.setRefundNo("REF" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        refund.setAfterSaleNo(afterSaleNo);
        refund.setOrderNo(orderNo);
        refund.setPaymentNo(payment.getPaymentNo());
        refund.setUserId(command.userId());
        refund.setAmount(payment.getAmount());
        refund.setChannel(CHANNEL_MOCK);
        refund.setStatus(PaymentStatus.SUCCESS.name());
        refund.setRefundedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            paymentRefundMapper.insert(refund);
        } catch (DuplicateKeyException ex) {
            PaymentRefund duplicated = paymentRefundMapper.selectByAfterSaleNo(afterSaleNo);
            if (duplicated == null) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_CONFLICT);
            }
            validateExisting(duplicated, orderNo, command.userId(), command.amount());
            return toResult(duplicated);
        }
        return toResult(refund);
    }

    @Override
    public Optional<RefundResult> findRefundByAfterSaleNo(String afterSaleNo) {
        if (!StringUtils.hasText(afterSaleNo)) {
            return Optional.empty();
        }
        PaymentRefund refund = paymentRefundMapper.selectByAfterSaleNo(afterSaleNo.trim());
        return refund == null ? Optional.empty() : Optional.of(toResult(refund));
    }

    private void validateExisting(PaymentRefund existing,
                                  String orderNo,
                                  Long userId,
                                  BigDecimal amount) {
        if (!orderNo.equals(existing.getOrderNo())
                || !userId.equals(existing.getUserId())
                || existing.getAmount() == null
                || existing.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_CONFLICT);
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value) || value.trim().length() > 64) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED, message);
        }
        return value.trim();
    }

    private RefundResult toResult(PaymentRefund refund) {
        return new RefundResult(
                refund.getRefundNo(),
                refund.getAfterSaleNo(),
                refund.getOrderNo(),
                refund.getAmount(),
                refund.getChannel(),
                refund.getStatus(),
                refund.getRefundedAt()
        );
    }
}
