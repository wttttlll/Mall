package com.mallagent.module.product.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.module.product.dto.spu.SpuCreateRequest;
import com.mallagent.module.product.dto.spu.SpuResponse;
import com.mallagent.module.product.dto.spu.SpuSummaryResponse;
import com.mallagent.module.product.dto.spu.SpuUpdateRequest;
import com.mallagent.module.product.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 SPU 与状态流转。
 */
@Tag(name = "管理端-商品 SPU")
@RestController
@RequestMapping("/api/v1/admin/product/spus")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @Operation(summary = "商品分页查询")
    @PreAuthorize("hasAuthority('product:spu:read')")
    @GetMapping
    public Result<PageResult<SpuSummaryResponse>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.adminPage(pageNum, pageSize, status, categoryId, brandId, keyword));
    }

    @Operation(summary = "商品详情")
    @PreAuthorize("hasAuthority('product:spu:read')")
    @GetMapping("/{id}")
    public Result<SpuResponse> detail(@PathVariable Long id) {
        return Result.success(productService.adminDetail(id));
    }

    @Operation(summary = "创建商品（草稿）")
    @PreAuthorize("hasAuthority('product:spu:create')")
    @PostMapping
    public ResponseEntity<Result<SpuResponse>> create(@Valid @RequestBody SpuCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(productService.create(request)));
    }

    @Operation(summary = "修改商品基础信息", description = "仅草稿或下架状态可修改")
    @PreAuthorize("hasAuthority('product:spu:update')")
    @PutMapping("/{id}")
    public Result<SpuResponse> update(@PathVariable Long id,
                                      @Valid @RequestBody SpuUpdateRequest request) {
        return Result.success(productService.update(id, request));
    }

    @Operation(summary = "删除商品", description = "逻辑删除；上架商品必须先下架")
    @PreAuthorize("hasAuthority('product:spu:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

    @Operation(summary = "商品上架")
    @PreAuthorize("hasAuthority('product:spu:on-shelf')")
    @PutMapping("/{id}/on-shelf")
    public Result<Void> onShelf(@PathVariable Long id) {
        productService.onShelf(id);
        return Result.success();
    }

    @Operation(summary = "商品下架")
    @PreAuthorize("hasAuthority('product:spu:off-shelf')")
    @PutMapping("/{id}/off-shelf")
    public Result<Void> offShelf(@PathVariable Long id) {
        productService.offShelf(id);
        return Result.success();
    }

    @Operation(summary = "商品转为草稿", description = "仅下架状态可转草稿，用于修改规格结构")
    @PreAuthorize("hasAuthority('product:spu:update')")
    @PutMapping("/{id}/draft")
    public Result<Void> toDraft(@PathVariable Long id) {
        productService.toDraft(id);
        return Result.success();
    }
}
