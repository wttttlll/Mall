package com.mallagent.module.product.controller;

import com.mallagent.common.api.Result;
import com.mallagent.module.product.dto.sku.SkuCreateRequest;
import com.mallagent.module.product.dto.sku.SkuResponse;
import com.mallagent.module.product.dto.sku.SkuUpdateRequest;
import com.mallagent.module.product.service.SkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端 SKU。
 */
@Tag(name = "管理端-商品 SKU")
@RestController
@RequestMapping("/api/v1/admin/product/spus/{spuId}/skus")
@RequiredArgsConstructor
public class AdminSkuController {

    private final SkuService skuService;

    @Operation(summary = "SKU 列表")
    @PreAuthorize("hasAuthority('product:sku:read')")
    @GetMapping
    public Result<List<SkuResponse>> list(@PathVariable Long spuId) {
        return Result.success(skuService.listBySpu(spuId, false));
    }

    @Operation(summary = "创建 SKU")
    @PreAuthorize("hasAuthority('product:sku:create')")
    @PostMapping
    public ResponseEntity<Result<SkuResponse>> create(@PathVariable Long spuId,
                                                      @Valid @RequestBody SkuCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(skuService.create(spuId, request)));
    }

    @Operation(summary = "修改 SKU")
    @PreAuthorize("hasAuthority('product:sku:update')")
    @PutMapping("/{skuId}")
    public Result<SkuResponse> update(@PathVariable Long spuId,
                                      @PathVariable Long skuId,
                                      @Valid @RequestBody SkuUpdateRequest request) {
        return Result.success(skuService.update(spuId, skuId, request));
    }

    @Operation(summary = "删除 SKU")
    @PreAuthorize("hasAuthority('product:sku:delete')")
    @DeleteMapping("/{skuId}")
    public Result<Void> delete(@PathVariable Long spuId, @PathVariable Long skuId) {
        skuService.delete(spuId, skuId);
        return Result.success();
    }
}
