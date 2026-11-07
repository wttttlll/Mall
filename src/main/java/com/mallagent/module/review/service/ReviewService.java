package com.mallagent.module.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.common.api.PageResult;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.order.api.OrderReviewQueryApi;
import com.mallagent.module.order.api.dto.ReviewableOrderItem;
import com.mallagent.module.review.dto.CreateReviewRequest;
import com.mallagent.module.review.dto.MyReviewResponse;
import com.mallagent.module.review.dto.ProductReviewListResponse;
import com.mallagent.module.review.dto.PublicReviewResponse;
import com.mallagent.module.review.entity.ProductReview;
import com.mallagent.module.review.error.ReviewErrorCode;
import com.mallagent.module.review.mapper.ProductReviewMapper;
import com.mallagent.module.user.api.UserQueryApi;
import com.mallagent.module.user.api.dto.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品评价：只有已完成订单的购买用户可以评价，同一订单明细只允许一条评价。
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ORDER_NO_LENGTH = 64;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final ProductReviewMapper reviewMapper;
    private final OrderReviewQueryApi orderReviewQueryApi;
    private final UserQueryApi userQueryApi;

    @Transactional(propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public MyReviewResponse create(Long userId, CreateReviewRequest request) {
        if (request == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_COMMAND_INVALID);
        }
        String orderNo = requireOrderNo(request.orderNo());
        Long skuId = request.skuId();
        if (skuId == null || skuId <= 0) {
            throw new BusinessException(ReviewErrorCode.REVIEW_COMMAND_INVALID);
        }
        int rating = request.rating() == null ? 0 : request.rating();
        if (rating < 1 || rating > 5) {
            throw new BusinessException(ReviewErrorCode.REVIEW_COMMAND_INVALID);
        }
        String content = normalizeContent(request.content());

        ReviewableOrderItem item = orderReviewQueryApi
                .findCompletedOrderItem(userId, orderNo, skuId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_ALLOWED));
        if (item.orderItemId() == null || item.spuId() == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_ALLOWED);
        }

        ProductReview existing = reviewMapper.selectByOrderItemId(item.orderItemId());
        if (existing != null) {
            return toMyResponse(existing);
        }

        ProductReview review = new ProductReview();
        review.setUserId(userId);
        review.setOrderNo(item.orderNo());
        review.setOrderItemId(item.orderItemId());
        review.setSpuId(item.spuId());
        review.setSkuId(item.skuId());
        review.setSpuName(item.spuName());
        review.setSkuCode(item.skuCode());
        review.setImageUrl(item.imageUrl());
        review.setRating(rating);
        review.setContent(content);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException ex) {
            // 并发重复提交由 UNIQUE(order_item_id) 兜底，返回已存在的评价。
            ProductReview duplicated = reviewMapper.selectByOrderItemId(item.orderItemId());
            if (duplicated == null) {
                throw new BusinessException(ReviewErrorCode.REVIEW_COMMAND_INVALID);
            }
            return toMyResponse(duplicated);
        }
        return toMyResponse(review);
    }

    public ProductReviewListResponse productReviews(Long spuId, long pageNum, long pageSize) {
        if (spuId == null || spuId <= 0) {
            throw new BusinessException(ReviewErrorCode.REVIEW_COMMAND_INVALID);
        }
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        Page<ProductReview> page = reviewMapper.selectPage(
                new Page<>(safePageNum, safePageSize),
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getSpuId, spuId)
                        .orderByDesc(ProductReview::getId));

        Map<Long, UserBrief> users = userQueryApi.findUsers(page.getRecords().stream()
                        .map(ProductReview::getUserId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserBrief::id, Function.identity(), (left, right) -> left));

        List<PublicReviewResponse> list = page.getRecords().stream()
                .map(review -> new PublicReviewResponse(
                        review.getId(),
                        displayName(users.get(review.getUserId())),
                        review.getRating(),
                        review.getContent(),
                        review.getCreatedAt()))
                .toList();
        return new ProductReviewListResponse(
                averageRating(reviewMapper.selectAverageRating(spuId)),
                page.getTotal(),
                list,
                page.getTotal(),
                safePageNum,
                safePageSize
        );
    }

    public PageResult<MyReviewResponse> mine(Long userId,
                                             long pageNum,
                                             long pageSize,
                                             String orderNo) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getUserId, userId)
                .orderByDesc(ProductReview::getId);
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(ProductReview::getOrderNo, requireOrderNo(orderNo));
        }
        Page<ProductReview> page = reviewMapper.selectPage(
                new Page<>(safePageNum, safePageSize), wrapper);
        List<MyReviewResponse> list = page.getRecords().stream()
                .map(this::toMyResponse)
                .toList();
        return new PageResult<>(list, page.getTotal(), safePageNum, safePageSize);
    }

    private MyReviewResponse toMyResponse(ProductReview review) {
        return new MyReviewResponse(
                review.getId(),
                review.getOrderNo(),
                review.getSpuId(),
                review.getSkuId(),
                review.getSpuName(),
                review.getSkuCode(),
                review.getImageUrl(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }

    private String requireOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo) || orderNo.trim().length() > MAX_ORDER_NO_LENGTH) {
            throw new BusinessException(ReviewErrorCode.REVIEW_COMMAND_INVALID);
        }
        return orderNo.trim();
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ReviewErrorCode.REVIEW_COMMAND_INVALID);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal averageRating(Double average) {
        if (average == null) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }

    private String displayName(UserBrief user) {
        String nickname = user == null ? null : user.nickname();
        if (!StringUtils.hasText(nickname)) {
            return "匿名用户";
        }
        String trimmed = nickname.trim();
        return trimmed.substring(0, 1) + "**";
    }
}
