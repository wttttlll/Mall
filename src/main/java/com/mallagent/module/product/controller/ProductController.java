package com.mallagent.module.product.controller;

import com.mallagent.common.api.PageResult;
import com.mallagent.common.api.Result;
import com.mallagent.module.product.dto.category.CategoryResponse;
import com.mallagent.module.product.dto.product.ProductDetailResponse;
import com.mallagent.module.product.dto.product.ProductListItemResponse;
import com.mallagent.module.product.service.CategoryService;
import com.mallagent.module.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端商品查询。只返回符合展示条件的商品。
 */
@Tag(name = "商品")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @Operation(summary = "用户端分类树", description = "仅返回启用分类")
    @GetMapping("/categories/tree")
    public Result<List<CategoryResponse>> categoryTree() {
        return Result.success(categoryService.userTree());
    }

    @Operation(summary = "用户端商品分页", description = "仅返回上架且分类/品牌可用、至少有一个启用 SKU 的商品")
    @GetMapping
    public Result<PageResult<ProductListItemResponse>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.userPage(pageNum, pageSize, categoryId, brandId, keyword));
    }

    @Operation(summary = "用户端商品详情")
    @GetMapping("/{id}")
    public Result<ProductDetailResponse> detail(@PathVariable Long id) {
        return Result.success(productService.userDetail(id));
    }
}
