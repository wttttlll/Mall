package com.mallagent.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.common.api.PageResult;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.order.config.OrderProperties;
import com.mallagent.module.order.dto.admin.AdminOrderResponse;
import com.mallagent.module.order.dto.admin.AdminOrderSummaryResponse;
import com.mallagent.module.order.dto.admin.ShipOrderRequest;
import com.mallagent.module.order.dto.order.OrderItemResponse;
import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.entity.OrderItem;
import com.mallagent.module.order.enums.OrderStatus;
import com.mallagent.module.order.error.OrderErrorCode;
import com.mallagent.module.order.mapper.OrderInfoMapper;
import com.mallagent.module.order.mapper.OrderItemMapper;
import com.mallagent.module.user.api.UserQueryApi;
import com.mallagent.module.user.api.dto.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端订单查询与发货。
 *
 * <p>发货只推进订单状态并保存物流信息，不再操作库存；库存已在支付时扣减。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ORDER_NO_LENGTH = 64;
    private static final ZoneId ORDER_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserQueryApi userQueryApi;
    private final OrderProperties orderProperties;

    public PageResult<AdminOrderSummaryResponse> page(long pageNum,
                                                      long pageSize,
                                                      String status,
                                                      String orderNo) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        OrderStatus parsedStatus = parseStatus(status);

        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .orderByDesc(OrderInfo::getId);
        if (parsedStatus != null) {
            wrapper.eq(OrderInfo::getStatus, parsedStatus.name());
        }
        if (StringUtils.hasText(orderNo)) {
            String keyword = orderNo.trim();
            if (keyword.length() > MAX_ORDER_NO_LENGTH) {
                throw new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID);
            }
            wrapper.like(OrderInfo::getOrderNo, keyword);
        }

        Page<OrderInfo> page = orderInfoMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        Map<Long, UserBrief> users = loadUsers(page.getRecords());
        List<AdminOrderSummaryResponse> list = page.getRecords().stream()
                .map(order -> toSummary(order, users.get(order.getUserId())))
                .toList();
        return new PageResult<>(list, page.getTotal(), safePageNum, safePageSize);
    }

    public AdminOrderResponse detail(String orderNo) {
        OrderInfo order = requireOrder(orderNo);
        return toDetail(order, orderItemMapper.selectByOrderId(order.getId()));
    }

    /**
     * 已支付订单发货。重复发货幂等返回当前订单，不重复修改状态。
     */
    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public AdminOrderResponse ship(Long operatorId, String orderNo, ShipOrderRequest request) {
        String safeOrderNo = requireOrderNo(orderNo);
        String shippingCompany = requireText(request == null ? null : request.shippingCompany(),
                "快递公司不能为空", 64);
        String trackingNo = requireText(request == null ? null : request.trackingNo(),
                "物流单号不能为空", 64);

        OrderInfo order = orderInfoMapper.selectByOrderNoForUpdate(safeOrderNo);
        if (order == null) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (OrderStatus.SHIPPED.name().equals(order.getStatus())
                || OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            boolean sameShipment = shippingCompany.equals(order.getShippingCompany())
                    && trackingNo.equals(order.getTrackingNo());
            if (sameShipment) {
                return toDetail(order, orderItemMapper.selectByOrderId(order.getId()));
            }
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_CONFLICT,
                    "订单已发货，物流信息与首次发货记录不一致");
        }
        if (!OrderStatus.PAID.name().equals(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_CONFLICT, "仅已支付订单可以发货");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        int updated = orderInfoMapper.markShipped(safeOrderNo, operatorId,
                shippingCompany, trackingNo, now);
        if (updated != 1) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_CONFLICT);
        }
        order.setStatus(OrderStatus.SHIPPED.name());
        order.setShippingCompany(shippingCompany);
        order.setTrackingNo(trackingNo);
        order.setShippedAt(now);
        return toDetail(order, orderItemMapper.selectByOrderId(order.getId()));
    }

    private OrderInfo requireOrder(String orderNo) {
        OrderInfo order = orderInfoMapper.selectByOrderNo(requireOrderNo(orderNo));
        if (order == null) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private String requireOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo) || orderNo.trim().length() > MAX_ORDER_NO_LENGTH) {
            throw new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID);
        }
        return orderNo.trim();
    }

    private String requireText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID, message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID, message);
        }
        return trimmed;
    }

    private OrderStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return OrderStatus.from(status)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID));
    }

    private Map<Long, UserBrief> loadUsers(List<OrderInfo> orders) {
        List<Long> userIds = orders.stream()
                .map(OrderInfo::getUserId)
                .distinct()
                .toList();
        return userQueryApi.findUsers(userIds).stream()
                .collect(Collectors.toMap(UserBrief::id, Function.identity(), (left, right) -> left));
    }

    private AdminOrderSummaryResponse toSummary(OrderInfo order, UserBrief user) {
        return new AdminOrderSummaryResponse(
                order.getOrderNo(),
                order.getUserId(),
                user == null ? null : user.username(),
                user == null ? null : user.nickname(),
                order.getStatus(),
                paymentStatus(order),
                order.getTotalAmount(),
                order.getItemCount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getShippingCompany(),
                order.getTrackingNo(),
                order.getShippedAt(),
                order.getCompletedAt(),
                order.getCancelledAt()
        );
    }

    private AdminOrderResponse toDetail(OrderInfo order, List<OrderItem> items) {
        UserBrief user = userQueryApi.findUser(order.getUserId()).orElse(null);
        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> new OrderItemResponse(
                        item.getSkuId(),
                        item.getSpuId(),
                        item.getSkuCode(),
                        item.getSpuName(),
                        item.getImageUrl(),
                        item.getSpecText(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getLineAmount()))
                .toList();
        return new AdminOrderResponse(
                order.getOrderNo(),
                order.getUserId(),
                user == null ? null : user.username(),
                user == null ? null : user.nickname(),
                order.getStatus(),
                paymentStatus(order),
                order.getTotalAmount(),
                order.getItemCount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverProvince(),
                order.getReceiverCity(),
                order.getReceiverDistrict(),
                order.getReceiverDetailAddress(),
                order.getReceiverPostalCode(),
                order.getShippingCompany(),
                order.getTrackingNo(),
                order.getShippedAt(),
                order.getCompletedAt(),
                itemResponses,
                order.getCreatedAt(),
                payDeadline(order),
                order.getPaidAt(),
                order.getCancelledAt()
        );
    }

    private String paymentStatus(OrderInfo order) {
        return order.getPaidAt() == null ? "UNPAID" : "PAID";
    }

    private OffsetDateTime payDeadline(OrderInfo order) {
        return order.getCreatedAt() == null
                ? null
                : order.getCreatedAt()
                .plusMinutes(orderProperties.getTimeoutMinutes())
                .atZone(ORDER_ZONE)
                .toOffsetDateTime();
    }
}
