package com.mallagent.module.favorite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.common.api.PageResult;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.favorite.dto.FavoriteResponse;
import com.mallagent.module.favorite.dto.FavoriteStateResponse;
import com.mallagent.module.favorite.dto.FavoriteStatusResponse;
import com.mallagent.module.favorite.entity.UserFavorite;
import com.mallagent.module.favorite.error.FavoriteErrorCode;
import com.mallagent.module.favorite.mapper.UserFavoriteMapper;
import com.mallagent.module.product.api.ProductSpuQueryApi;
import com.mallagent.module.product.api.dto.SpuSnapshot;
import com.mallagent.module.product.enums.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 当前用户商品收藏。收藏关系只属于当前用户，重复收藏由数据库唯一约束兜底。
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_STATUS_LOOKUP_IDS = 100;

    private final UserFavoriteMapper favoriteMapper;
    private final ProductSpuQueryApi productSpuQueryApi;

    public PageResult<FavoriteResponse> list(Long userId, long pageNum, long pageSize) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .orderByDesc(UserFavorite::getId);
        Page<UserFavorite> page = favoriteMapper.selectPage(
                new Page<>(safePageNum, safePageSize), wrapper);
        Map<Long, SpuSnapshot> products = productSpuQueryApi.findSpus(
                        page.getRecords().stream().map(UserFavorite::getSpuId).toList())
                .stream()
                .collect(Collectors.toMap(SpuSnapshot::id, Function.identity(), (left, right) -> left));

        List<FavoriteResponse> list = page.getRecords().stream()
                .map(favorite -> toResponse(favorite, products.get(favorite.getSpuId())))
                .toList();
        return new PageResult<>(list, page.getTotal(), safePageNum, safePageSize);
    }

    public FavoriteStatusResponse status(Long userId, Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return new FavoriteStatusResponse(List.of());
        }
        List<Long> ids = spuIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.size() > MAX_STATUS_LOOKUP_IDS) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_COMMAND_INVALID);
        }
        if (ids.isEmpty()) {
            return new FavoriteStatusResponse(List.of());
        }
        List<Long> favoriteIds = favoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .in(UserFavorite::getSpuId, ids))
                .stream()
                .map(UserFavorite::getSpuId)
                .toList();
        return new FavoriteStatusResponse(favoriteIds);
    }

    /**
     * 添加收藏。重复收藏幂等成功，不新增记录。
     */
    @Transactional
    public FavoriteStateResponse add(Long userId, Long spuId) {
        Long safeSpuId = requireSpuId(spuId);
        SpuSnapshot product = productSpuQueryApi.findSpu(safeSpuId)
                .orElseThrow(() -> new BusinessException(FavoriteErrorCode.FAVORITE_PRODUCT_UNAVAILABLE));
        if (!ProductStatus.ON_SHELF.name().equals(product.status())) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_PRODUCT_UNAVAILABLE);
        }

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setSpuId(safeSpuId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException ignored) {
            // UNIQUE(user_id, spu_id) 保证并发重复收藏只保留一条记录。
        }
        return new FavoriteStateResponse(safeSpuId, true);
    }

    /**
     * 取消收藏。未收藏时幂等成功。
     */
    @Transactional
    public FavoriteStateResponse remove(Long userId, Long spuId) {
        Long safeSpuId = requireSpuId(spuId);
        favoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getSpuId, safeSpuId));
        return new FavoriteStateResponse(safeSpuId, false);
    }

    private Long requireSpuId(Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_COMMAND_INVALID);
        }
        return spuId;
    }

    private FavoriteResponse toResponse(UserFavorite favorite, SpuSnapshot product) {
        if (product == null) {
            return new FavoriteResponse(
                    favorite.getSpuId(),
                    null,
                    null,
                    null,
                    "DELETED",
                    false,
                    "商品已删除",
                    favorite.getCreatedAt()
            );
        }
        boolean available = ProductStatus.ON_SHELF.name().equals(product.status());
        return new FavoriteResponse(
                favorite.getSpuId(),
                product.name(),
                product.subtitle(),
                product.mainImage(),
                product.status(),
                available,
                available ? null : "商品已下架",
                favorite.getCreatedAt()
        );
    }
}
