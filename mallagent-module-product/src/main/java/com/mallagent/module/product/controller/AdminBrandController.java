package com.mallagent.module.product.controller;

import com.mallagent.common.api.Result;
import com.mallagent.module.product.dto.brand.BrandRequest;
import com.mallagent.module.product.dto.brand.BrandResponse;
import com.mallagent.module.product.service.BrandService;
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
 * 管理端商品品牌。
 */
@Tag(name = "管理端-商品品牌")
@RestController
@RequestMapping("/api/v1/admin/product/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @Operation(summary = "品牌列表")
    @PreAuthorize("hasAuthority('product:brand:read')")
    @GetMapping
    public Result<List<BrandResponse>> list() {
        return Result.success(brandService.list());
    }

    @Operation(summary = "品牌详情")
    @PreAuthorize("hasAuthority('product:brand:read')")
    @GetMapping("/{id}")
    public Result<BrandResponse> detail(@PathVariable Long id) {
        return Result.success(brandService.get(id));
    }

    @Operation(summary = "创建品牌")
    @PreAuthorize("hasAuthority('product:brand:create')")
    @PostMapping
    public ResponseEntity<Result<BrandResponse>> create(@Valid @RequestBody BrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(brandService.create(request)));
    }

    @Operation(summary = "修改品牌")
    @PreAuthorize("hasAuthority('product:brand:update')")
    @PutMapping("/{id}")
    public Result<BrandResponse> update(@PathVariable Long id,
                                        @Valid @RequestBody BrandRequest request) {
        return Result.success(brandService.update(id, request));
    }

    @Operation(summary = "删除品牌")
    @PreAuthorize("hasAuthority('product:brand:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return Result.success();
    }
}
