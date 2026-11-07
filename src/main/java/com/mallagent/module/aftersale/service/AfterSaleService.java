package com.mallagent.module.aftersale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.common.api.PageResult;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.aftersale.dto.AfterSaleItemResponse;
import com.mallagent.module.aftersale.dto.AfterSaleResponse;
import com.mallagent.module.aftersale.dto.AfterSaleSummaryResponse;
import com.mallagent.module.aftersale.dto.CreateAfterSaleRequest;
import com.mallagent.module.aftersale.dto.RejectAfterSaleRequest;
import com.mallagent.module.aftersale.entity.AfterSaleOrder;
import com.mallagent.module.aftersale.enums.AfterSaleStatus;
import com.mallagent.module.aftersale.error.AfterSaleErrorCode;
import com.mallagent.module.aftersale.mapper.AfterSaleOrderMapper;
import com.mallagent.module.inventory.api.InventoryStockApi;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.api.dto.StockRestoreCommand;
import com.mallagent.module.order.api.OrderAfterSaleQueryApi;
import com.mallagent.module.order.api.dto.AfterSaleOrderView;
import com.mallagent.module.payment.api.PaymentRefundApi;
import com.mallagent.module.payment.api.dto.PaymentSnapshot;
import com.mallagent.module.payment.api.dto.RefundCommand;
import com.mallagent.module.payment.api.dto.RefundResult;
import com.mallagent.module.user.api.UserQueryApi;
import com.mallagent.module.user.api.dto.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 整单退货退款售后。
 *
 * <p>加锁顺序固定为：售后单行 -> 原支付单行 -> 原订单 ORDER_LOCK 库存流水行。
 * 退款记录、库存回补和售后 COMPLETED 状态在同一事务内提交或回滚。</p>
 */
@Service
@RequiredArgsConstructor
public class AfterSaleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ORDER_NO_LENGTH = 64;
    private static final int MAX_AFTER_SALE_NO_LENGTH = 64;
    private static final int MAX_REASON_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final String ORDER_COMPLETED = "COMPLETED";

    private final AfterSaleOrderMapper afterSaleMapper;
    private final OrderAfterSaleQueryApi orderAfterSaleQueryApi;
    private final PaymentRefundApi paymentRefundApi;
    private final InventoryStockApi inventoryStockApi;
    private final UserQueryApi userQueryApi;

    /**
     * 已完成订单申请整单退货退款。有效售后由数据库生成列唯一约束兜底。
     */
    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AfterSaleResponse apply(Long userId, CreateAfterSaleRequest request) {
        if (request == null) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_COMMAND_INVALID);
        }
        String orderNo = requireOrderNo(request.orderNo());
        String reason = requireReason(request.reason());
        String description = normalizeDescription(request.description());

        AfterSaleOrderView order = orderAfterSaleQueryApi.findOrder(orderNo)
                .filter(value -> value.userId().equals(userId))
                .filter(value -> ORDER_COMPLETED.equals(value.status()))
                .orElseThrow(() -> new BusinessException(AfterSaleErrorCode.AFTER_SALE_NOT_ALLOWED));
        PaymentSnapshot payment = paymentRefundApi.findSuccessfulPayment(orderNo, userId)
                .orElseThrow(() -> new BusinessException(AfterSaleErrorCode.AFTER_SALE_NOT_ALLOWED));
        if (payment.amount() == null || payment.amount().signum() < 0) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_NOT_ALLOWED);
        }
        if (afterSaleMapper.selectActiveByOrderNo(orderNo) != null) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_ALREADY_EXISTS);
        }

        AfterSaleOrder afterSale = new AfterSaleOrder();
        afterSale.setAfterSaleNo("AS" + UUID.randomUUID().toString()
                .replace("-", "").toUpperCase());
        afterSale.setOrderNo(order.orderNo());
        afterSale.setUserId(userId);
        afterSale.setStatus(AfterSaleStatus.APPLIED.name());
        afterSale.setRefundAmount(payment.amount());
        afterSale.setReason(reason);
        afterSale.setDescription(description);
        try {
            afterSaleMapper.insert(afterSale);
        } catch (DuplicateKeyException ex) {
            // 并发申请由 active_order_no 唯一索引兜底。
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_ALREADY_EXISTS,
                    AfterSaleErrorCode.AFTER_SALE_ALREADY_EXISTS.message(), ex);
        }
        return toDetail(afterSale);
    }

    public PageResult<AfterSaleSummaryResponse> pageMine(Long userId,
                                                         long pageNum,
                                                         long pageSize,
                                                         String status,
                                                         String orderNo) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        AfterSaleStatus parsedStatus = parseStatus(status);
        LambdaQueryWrapper<AfterSaleOrder> wrapper = new LambdaQueryWrapper<AfterSaleOrder>()
                .eq(AfterSaleOrder::getUserId, userId)
                .orderByDesc(AfterSaleOrder::getId);
        if (parsedStatus != null) {
            wrapper.eq(AfterSaleOrder::getStatus, parsedStatus.name());
        }
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(AfterSaleOrder::getOrderNo, requireOrderNo(orderNo));
        }
        Page<AfterSaleOrder> page = afterSaleMapper.selectPage(
                new Page<>(safePageNum, safePageSize), wrapper);
        List<AfterSaleSummaryResponse> list = page.getRecords().stream()
                .map(order -> toSummary(order, null))
                .toList();
        return new PageResult<>(list, page.getTotal(), safePageNum, safePageSize);
    }

    public AfterSaleResponse detailMine(Long userId, String afterSaleNo) {
        AfterSaleOrder afterSale = requireAfterSale(afterSaleNo);
        if (!afterSale.getUserId().equals(userId)) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_NOT_FOUND);
        }
        return toDetail(afterSale);
    }

    public PageResult<AfterSaleSummaryResponse> adminPage(long pageNum,
                                                          long pageSize,
                                                          String status,
                                                          String afterSaleNo) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        AfterSaleStatus parsedStatus = parseStatus(status);
        LambdaQueryWrapper<AfterSaleOrder> wrapper = new LambdaQueryWrapper<AfterSaleOrder>()
                .orderByDesc(AfterSaleOrder::getId);
        if (parsedStatus != null) {
            wrapper.eq(AfterSaleOrder::getStatus, parsedStatus.name());
        }
        if (StringUtils.hasText(afterSaleNo)) {
            String keyword = requireAfterSaleNo(afterSaleNo);
            wrapper.like(AfterSaleOrder::getAfterSaleNo, keyword);
        }
        Page<AfterSaleOrder> page = afterSaleMapper.selectPage(
                new Page<>(safePageNum, safePageSize), wrapper);
        Map<Long, UserBrief> users = userQueryApi.findUsers(page.getRecords().stream()
                        .map(AfterSaleOrder::getUserId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserBrief::id, Function.identity(), (left, right) -> left));
        List<AfterSaleSummaryResponse> list = page.getRecords().stream()
                .map(order -> toSummary(order, users.get(order.getUserId())))
                .toList();
        return new PageResult<>(list, page.getTotal(), safePageNum, safePageSize);
    }

    public AfterSaleResponse adminDetail(String afterSaleNo) {
        return toDetail(requireAfterSale(afterSaleNo));
    }

    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AfterSaleResponse approve(Long operatorId, String afterSaleNo) {
        AfterSaleOrder afterSale = lockAfterSale(afterSaleNo);
        if (AfterSaleStatus.APPROVED.name().equals(afterSale.getStatus())) {
            return toDetail(afterSale);
        }
        if (!AfterSaleStatus.APPLIED.name().equals(afterSale.getStatus())) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        int updated = afterSaleMapper.markApproved(afterSale.getAfterSaleNo(), operatorId, now);
        if (updated != 1) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        afterSale.setStatus(AfterSaleStatus.APPROVED.name());
        afterSale.setReviewedBy(operatorId);
        afterSale.setReviewedAt(now);
        return toDetail(afterSale);
    }

    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AfterSaleResponse reject(Long operatorId,
                                    String afterSaleNo,
                                    RejectAfterSaleRequest request) {
        String rejectReason = requireReason(request == null ? null : request.rejectReason());
        AfterSaleOrder afterSale = lockAfterSale(afterSaleNo);
        if (AfterSaleStatus.REJECTED.name().equals(afterSale.getStatus())) {
            return toDetail(afterSale);
        }
        if (!AfterSaleStatus.APPLIED.name().equals(afterSale.getStatus())) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        int updated = afterSaleMapper.markRejected(afterSale.getAfterSaleNo(),
                operatorId, rejectReason, now);
        if (updated != 1) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        afterSale.setStatus(AfterSaleStatus.REJECTED.name());
        afterSale.setRejectReason(rejectReason);
        afterSale.setReviewedBy(operatorId);
        afterSale.setReviewedAt(now);
        return toDetail(afterSale);
    }

    /**
     * 管理员确认收到退货并完成模拟退款与库存回补。
     * 已 COMPLETED 时幂等返回；退款、restore 与状态更新同一事务。
     */
    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AfterSaleResponse complete(Long operatorId, String afterSaleNo) {
        AfterSaleOrder afterSale = lockAfterSale(afterSaleNo);
        if (AfterSaleStatus.COMPLETED.name().equals(afterSale.getStatus())) {
            return toDetail(afterSale);
        }
        if (!AfterSaleStatus.APPROVED.name().equals(afterSale.getStatus())) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }

        AfterSaleOrderView order = orderAfterSaleQueryApi.findOrder(afterSale.getOrderNo())
                .orElseThrow(() -> new BusinessException(AfterSaleErrorCode.AFTER_SALE_STATUS_CONFLICT));
        if (!ORDER_COMPLETED.equals(order.status())
                || !afterSale.getUserId().equals(order.userId())) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_NOT_ALLOWED);
        }

        // 退款记录和库存回补都加入当前事务；任一步失败整体回滚。
        paymentRefundApi.refund(new RefundCommand(
                afterSale.getAfterSaleNo(),
                afterSale.getOrderNo(),
                afterSale.getUserId(),
                afterSale.getRefundAmount()
        ));
        inventoryStockApi.restore(new StockRestoreCommand(
                afterSale.getAfterSaleNo(),
                afterSale.getOrderNo(),
                order.items().stream()
                        .map(item -> new StockItem(item.skuId(), item.quantity()))
                        .toList()
        ));

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        int updated = afterSaleMapper.markCompleted(afterSale.getAfterSaleNo(), operatorId, now);
        if (updated != 1) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_STATUS_CONFLICT);
        }
        afterSale.setStatus(AfterSaleStatus.COMPLETED.name());
        afterSale.setCompletedBy(operatorId);
        afterSale.setCompletedAt(now);
        return toDetail(afterSale);
    }

    private AfterSaleOrder requireAfterSale(String afterSaleNo) {
        AfterSaleOrder afterSale = afterSaleMapper.selectByAfterSaleNo(requireAfterSaleNo(afterSaleNo));
        if (afterSale == null) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_NOT_FOUND);
        }
        return afterSale;
    }

    private AfterSaleOrder lockAfterSale(String afterSaleNo) {
        AfterSaleOrder afterSale = afterSaleMapper.selectByAfterSaleNoForUpdate(
                requireAfterSaleNo(afterSaleNo));
        if (afterSale == null) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_NOT_FOUND);
        }
        return afterSale;
    }

    private AfterSaleSummaryResponse toSummary(AfterSaleOrder afterSale, UserBrief user) {
        return new AfterSaleSummaryResponse(
                afterSale.getAfterSaleNo(),
                afterSale.getOrderNo(),
                afterSale.getUserId(),
                user == null ? null : user.username(),
                user == null ? null : user.nickname(),
                afterSale.getStatus(),
                afterSale.getRefundAmount(),
                afterSale.getReason(),
                afterSale.getCreatedAt(),
                afterSale.getReviewedAt(),
                afterSale.getCompletedAt()
        );
    }

    private AfterSaleResponse toDetail(AfterSaleOrder afterSale) {
        AfterSaleOrderView order = orderAfterSaleQueryApi.findOrder(afterSale.getOrderNo())
                .orElse(null);
        UserBrief user = userQueryApi.findUser(afterSale.getUserId()).orElse(null);
        RefundResult refund = paymentRefundApi.findRefundByAfterSaleNo(afterSale.getAfterSaleNo())
                .orElse(null);
        List<AfterSaleItemResponse> items = order == null
                ? List.of()
                : order.items().stream()
                .map(item -> new AfterSaleItemResponse(
                        item.skuId(),
                        item.spuId(),
                        item.skuCode(),
                        item.spuName(),
                        item.imageUrl(),
                        item.specText(),
                        item.unitPrice(),
                        item.quantity(),
                        item.lineAmount()))
                .toList();
        return new AfterSaleResponse(
                afterSale.getAfterSaleNo(),
                afterSale.getOrderNo(),
                afterSale.getUserId(),
                user == null ? null : user.username(),
                user == null ? null : user.nickname(),
                afterSale.getStatus(),
                afterSale.getRefundAmount(),
                afterSale.getReason(),
                afterSale.getDescription(),
                afterSale.getRejectReason(),
                refundStatus(afterSale.getStatus()),
                restoreStatus(afterSale.getStatus()),
                refund == null ? null : refund.refundNo(),
                afterSale.getCreatedAt(),
                afterSale.getReviewedAt(),
                afterSale.getCompletedAt(),
                refund == null ? null : refund.refundedAt(),
                order == null ? null : order.status(),
                order == null ? null : order.totalAmount(),
                order == null ? null : order.paidAt(),
                order == null ? null : order.completedAt(),
                order == null ? null : order.receiverName(),
                order == null ? null : order.receiverPhone(),
                order == null ? null : order.receiverProvince(),
                order == null ? null : order.receiverCity(),
                order == null ? null : order.receiverDistrict(),
                order == null ? null : order.receiverDetailAddress(),
                items
        );
    }

    private String refundStatus(String status) {
        return switch (status) {
            case "COMPLETED" -> "SUCCESS";
            case "APPROVED" -> "PENDING";
            case "REJECTED" -> "NOT_APPLICABLE";
            default -> "NOT_STARTED";
        };
    }

    private String restoreStatus(String status) {
        return switch (status) {
            case "COMPLETED" -> "SUCCESS";
            case "APPROVED" -> "PENDING";
            case "REJECTED" -> "NOT_APPLICABLE";
            default -> "NOT_STARTED";
        };
    }

    private AfterSaleStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return AfterSaleStatus.from(status)
                .orElseThrow(() -> new BusinessException(AfterSaleErrorCode.AFTER_SALE_COMMAND_INVALID));
    }

    private String requireOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo) || orderNo.trim().length() > MAX_ORDER_NO_LENGTH) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_COMMAND_INVALID);
        }
        return orderNo.trim();
    }

    private String requireAfterSaleNo(String afterSaleNo) {
        if (!StringUtils.hasText(afterSaleNo)
                || afterSaleNo.trim().length() > MAX_AFTER_SALE_NO_LENGTH) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_COMMAND_INVALID);
        }
        return afterSaleNo.trim();
    }

    private String requireReason(String reason) {
        if (!StringUtils.hasText(reason) || reason.trim().length() > MAX_REASON_LENGTH) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_COMMAND_INVALID);
        }
        return reason.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(AfterSaleErrorCode.AFTER_SALE_COMMAND_INVALID);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
