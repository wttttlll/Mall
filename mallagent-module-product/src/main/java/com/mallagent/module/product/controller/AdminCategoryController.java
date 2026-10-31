package com.mallagent.module.product.controller;

import com.mallagent.common.api.Result;
import com.mallagent.module.product.dto.category.CategoryCreateRequest;
import com.mallagent.module.product.dto.category.CategoryResponse;
import com.mallagent.module.product.dto.category.CategoryUpdateRequest;
import com.mallagent.module.product.service.CategoryService;
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
 * 管理端商品分类。
 */
@Tag(name = "管理端-商品分类")
@RestController
@RequestMapping("/api/v1/admin/product/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类树")
    @PreAuthorize("hasAuthority('product:category:read')")
    @GetMapping
    public Result<List<CategoryResponse>> tree() {
        return Result.success(categoryService.adminTree());
    }

    @Operation(summary = "分类详情")
    @PreAuthorize("hasAuthority('product:category:read')")
    @GetMapping("/{id}")
    public Result<CategoryResponse> detail(@PathVariable Long id) {
        return Result.success(categoryService.get(id));
    }

    @Operation(summary = "创建分类")
    @PreAuthorize("hasAuthority('product:category:create')")
    @PostMapping
    public ResponseEntity<Result<CategoryResponse>> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(categoryService.create(request)));
    }

    @Operation(summary = "修改分类")
    @PreAuthorize("hasAuthority('product:category:update')")
    @PutMapping("/{id}")
    public Result<CategoryResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody CategoryUpdateRequest request) {
        return Result.success(categoryService.update(id, request));
    }

    @Operation(summary = "删除分类")
    @PreAuthorize("hasAuthority('product:category:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
