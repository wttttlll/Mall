package com.mallagent.module.favorite.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.favorite.dto.FavoriteResponse;
import com.mallagent.module.favorite.dto.FavoriteStateResponse;
import com.mallagent.module.favorite.dto.FavoriteStatusResponse;
import com.mallagent.module.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户商品收藏接口。
 */
@Tag(name = "收藏")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "我的收藏分页")
    @GetMapping
    public Result<PageResult<FavoriteResponse>> list(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(favoriteService.list(currentUserId(), pageNum, pageSize));
    }

    @Operation(summary = "批量查询收藏状态")
    @GetMapping("/status")
    public Result<FavoriteStatusResponse> status(@RequestParam(required = false) List<Long> spuIds) {
        return Result.success(favoriteService.status(currentUserId(), spuIds));
    }

    @Operation(summary = "收藏商品", description = "重复收藏幂等成功")
    @PostMapping("/{spuId}")
    public Result<FavoriteStateResponse> add(@PathVariable Long spuId) {
        return Result.success(favoriteService.add(currentUserId(), spuId));
    }

    @Operation(summary = "取消收藏", description = "未收藏时幂等成功")
    @DeleteMapping("/{spuId}")
    public Result<FavoriteStateResponse> remove(@PathVariable Long spuId) {
        return Result.success(favoriteService.remove(currentUserId(), spuId));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
