package com.mallagent.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.common.api.PageResult;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.cart.api.CartCheckoutApi;
import com.mallagent.module.cart.api.dto.CartCheckoutItem;
import com.mallagent.module.inventory.api.InventoryStockApi;
import com.mallagent.module.inventory.api.dto.StockCommand;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.order.dto.order.CreateOrderRequest;
import com.mallagent.module.order.dto.order.OrderItemResponse;
import com.mallagent.module.order.dto.order.OrderResponse;
import com.mallagent.module.order.dto.order.OrderSummaryResponse;
import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.entity.OrderItem;
import com.mallagent.module.order.enums.OrderStatus;
import com.mallagent.module.order.error.OrderErrorCode;
import com.mallagent.module.order.mapper.OrderInfoMapper;
import com.mallagent.module.order.mapper.OrderItemMapper;
import com.mallagent.module.product.api.ProductSkuQueryApi;
import com.mallagent.module.product.api.dto.SkuSnapshot;
import com.mallagent.module.user.api.UserAddressQueryApi;
import com.mallagent.module.user.api.dto.AddressSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 订单业务：从购物车下单、查询、取消。
 *
 * <p>下单事务内完成订单、明细、库存锁定和购物车清理；库存 API 使用 REQUIRED 传播加入本事务。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ORDER_NO_LENGTH = 64;

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderIdempotencyLookupService idempotencyLookupService;
    private final CartCheckoutApi cartCheckoutApi;
    private final ProductSkuQueryApi productSkuQueryApi;
    private final InventoryStockApi inventoryStockApi;
    private final UserAddressQueryApi userAddressQueryApi;

    /**
     * 从购物车已勾选条目创建订单并锁定库存。
     */
    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public OrderResponse create(Long userId, String idempotencyKey, CreateOrderRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        OrderInfo existing = orderInfoMapper.selectByUserIdAndIdempotencyKey(userId, key);
        if (existing != null) {
            return toResponse(existing, orderItemMapper.selectByOrderId(existing.getId()));
        }

        Long addressId = requireAddressId(request);
        AddressSnapshot address = userAddressQueryApi.findForUser(userId, addressId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_ADDRESS_NOT_FOUND));

        // 先插入订单占位行，唯一键 (user_id, idempotency_key) 是并发下单的幂等锚点。
        OrderInfo order = buildOrder(userId, key, address);
        try {
            orderInfoMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            // 唯一键可能是 (user_id, idempotency_key) 或 order_no；独立事务确认幂等订单。
            existing = idempotencyLookupService.findByIdempotencyKey(userId, key);
            if (existing == null) {
                throw new BusinessException(OrderErrorCode.ORDER_OPERATION_CONFLICT,
                        OrderErrorCode.ORDER_OPERATION_CONFLICT.message(), ex);
            }
            return toResponse(existing, orderItemMapper.selectByOrderId(existing.getId()));
        }

        List<CartCheckoutItem> cartItems = cartCheckoutApi.lockSelectedItems(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORDER_CART_EMPTY);
        }

        List<OrderLine> lines = buildOrderLines(cartItems);
        BigDecimal totalAmount = lines.stream()
                .map(line -> line.item().getLineAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int totalsUpdated = orderInfoMapper.updateTotals(order.getId(), totalAmount, lines.size(), userId);
        if (totalsUpdated != 1) {
            throw new BusinessException(OrderErrorCode.ORDER_OPERATION_CONFLICT);
        }
        order.setTotalAmount(totalAmount);
        order.setItemCount(lines.size());

        for (OrderLine line : lines) {
            line.item().setOrderId(order.getId());
            orderItemMapper.insert(line.item());
        }

        // 与订单、明细同一事务；库存不足或其他冲突会整体回滚。
        inventoryStockApi.lock(new StockCommand(order.getOrderNo(), lines.stream()
                .map(line -> new StockItem(line.item().getSkuId(), line.item().getQuantity()))
                .toList()));

        cartCheckoutApi.clearSelectedItems(userId);

        return toResponse(order, lines.stream().map(OrderLine::item).toList());
    }

    /**
     * 当前用户订单分页查询。
     */
    public PageResult<OrderSummaryResponse> list(Long userId,
                                                 long pageNum,
                                                 long pageSize,
                                                 String status) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        OrderStatus parsedStatus = parseStatus(status);

        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .orderByDesc(OrderInfo::getId);
        if (parsedStatus != null) {
            wrapper.eq(OrderInfo::getStatus, parsedStatus.name());
        }

        Page<OrderInfo> page = orderInfoMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        List<OrderSummaryResponse> list = page.getRecords().stream()
                .map(this::toSummary)
                .toList();
        return new PageResult<>(list, page.getTotal(), safePageNum, safePageSize);
    }

    /**
     * 当前用户订单详情。
     */
    public OrderResponse detail(Long userId, String orderNo) {
        OrderInfo order = requireOwnedOrder(userId, requireOrderNo(orderNo));
        return toResponse(order, orderItemMapper.selectByOrderId(order.getId()));
    }

    /**
     * 取消待支付订单并释放库存。已取消订单重复取消幂等成功。
     */
    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public OrderResponse cancel(Long userId, String orderNo) {
        String safeOrderNo = requireOrderNo(orderNo);
        OrderInfo order = orderInfoMapper.selectByOrderNoForUpdate(safeOrderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
            return toResponse(order, orderItemMapper.selectByOrderId(order.getId()));
        }
        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_CONFLICT, "已支付订单不能取消");
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_CONFLICT);
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        inventoryStockApi.release(new StockCommand(order.getOrderNo(), items.stream()
                .map(item -> new StockItem(item.getSkuId(), item.getQuantity()))
                .toList()));

        LocalDateTime now = LocalDateTime.now();
        int updated = orderInfoMapper.markCancelled(order.getOrderNo(), userId, now);
        if (updated != 1) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_CONFLICT);
        }
        order.setStatus(OrderStatus.CANCELLED.name());
        order.setCancelledAt(now);
        return toResponse(order, items);
    }

    private List<OrderLine> buildOrderLines(List<CartCheckoutItem> cartItems) {
        List<CartCheckoutItem> sorted = cartItems.stream()
                .sorted(Comparator.comparing(CartCheckoutItem::skuId))
                .toList();
        List<Long> skuIds = sorted.stream().map(CartCheckoutItem::skuId).toList();
        Map<Long, SkuSnapshot> skuById = productSkuQueryApi.findSkus(skuIds).stream()
                .collect(Collectors.toMap(SkuSnapshot::skuId, Function.identity(), (left, right) -> left));

        List<OrderLine> lines = new ArrayList<>(sorted.size());
        for (CartCheckoutItem cartItem : sorted) {
            if (cartItem.skuId() == null || cartItem.skuId() <= 0) {
                throw new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID);
            }
            if (cartItem.quantity() == null || cartItem.quantity() <= 0) {
                throw new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID);
            }
            SkuSnapshot sku = skuById.get(cartItem.skuId());
            if (sku == null || !sku.purchasable()) {
                throw new BusinessException(OrderErrorCode.ORDER_PRODUCT_UNAVAILABLE);
            }
            if (sku.price() == null || sku.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(OrderErrorCode.ORDER_PRODUCT_UNAVAILABLE);
            }

            BigDecimal unitPrice = sku.price().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(cartItem.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            OrderItem item = new OrderItem();
            item.setSkuId(sku.skuId());
            item.setSpuId(sku.spuId());
            item.setSkuCode(sku.skuCode());
            item.setSpuName(sku.spuName());
            item.setImageUrl(sku.imageUrl());
            item.setSpecText(sku.specText());
            item.setUnitPrice(unitPrice);
            item.setQuantity(cartItem.quantity());
            item.setLineAmount(lineAmount);
            lines.add(new OrderLine(item));
        }
        return lines;
    }

    private OrderInfo buildOrder(Long userId,
                                 String idempotencyKey,
                                 AddressSnapshot address) {
        OrderInfo order = new OrderInfo();
        order.setOrderNo("ORD" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        // 占位值；取到购物车和当前价格后在同一事务内回填。
        order.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setItemCount(1);
        order.setReceiverName(address.receiverName());
        order.setReceiverPhone(address.receiverPhone());
        order.setReceiverProvince(address.province());
        order.setReceiverCity(address.city());
        order.setReceiverDistrict(address.district());
        order.setReceiverDetailAddress(address.detailAddress());
        order.setReceiverPostalCode(address.postalCode());
        order.setIdempotencyKey(idempotencyKey);
        order.setVersion(0);
        return order;
    }

    private OrderInfo requireOwnedOrder(Long userId, String orderNo) {
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
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

    private String requireIdempotencyKey(String key) {
        if (!StringUtils.hasText(key) || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(OrderErrorCode.ORDER_IDEMPOTENCY_KEY_REQUIRED);
        }
        return key;
    }

    private Long requireAddressId(CreateOrderRequest request) {
        if (request == null || request.addressId() == null || request.addressId() <= 0) {
            throw new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID);
        }
        return request.addressId();
    }

    private OrderStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return OrderStatus.from(status)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_COMMAND_INVALID));
    }

    private OrderResponse toResponse(OrderInfo order, List<OrderItem> items) {
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
        return new OrderResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItemCount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverProvince(),
                order.getReceiverCity(),
                order.getReceiverDistrict(),
                order.getReceiverDetailAddress(),
                order.getReceiverPostalCode(),
                itemResponses,
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getCancelledAt()
        );
    }

    private OrderSummaryResponse toSummary(OrderInfo order) {
        return new OrderSummaryResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItemCount(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getCancelledAt()
        );
    }

    private record OrderLine(OrderItem item) {
    }
}
