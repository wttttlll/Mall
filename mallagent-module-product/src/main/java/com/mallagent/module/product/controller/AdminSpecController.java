package com.mallagent.module.product.controller;

import com.mallagent.common.api.Result;
import com.mallagent.module.product.dto.spec.SpecCreateRequest;
import com.mallagent.module.product.dto.spec.SpecResponse;
import com.mallagent.module.product.dto.spec.SpecUpdateRequest;
import com.mallagent.module.product.dto.spec.SpecValueCreateRequest;
import com.mallagent.module.product.dto.spec.SpecValueResponse;
import com.mallagent.module.product.dto.spec.SpecValueUpdateRequest;
import com.mallagent.module.product.service.SpecService;
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
 * 管理端商品规格。
 */
@Tag(name = "管理端-商品规格")
@RestController
@RequestMapping("/api/v1/admin/product/spus/{spuId}/specs")
@RequiredArgsConstructor
public class AdminSpecController {

    private final SpecService specService;

    @Operation(summary = "规格列表")
    @PreAuthorize("hasAuthority('product:spec:read')")
    @GetMapping
    public Result<List<SpecResponse>> list(@PathVariable Long spuId) {
        return Result.success(specService.listBySpu(spuId));
    }

    @Operation(summary = "创建规格")
    @PreAuthorize("hasAuthority('product:spec:create')")
    @PostMapping
    public ResponseEntity<Result<SpecResponse>> create(@PathVariable Long spuId,
                                                       @Valid @RequestBody SpecCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(specService.createSpec(spuId, request)));
    }

    @Operation(summary = "修改规格")
    @PreAuthorize("hasAuthority('product:spec:update')")
    @PutMapping("/{specId}")
    public Result<SpecResponse> update(@PathVariable Long spuId,
                                       @PathVariable Long specId,
                                       @Valid @RequestBody SpecUpdateRequest request) {
        return Result.success(specService.updateSpec(spuId, specId, request));
    }

    @Operation(summary = "删除规格")
    @PreAuthorize("hasAuthority('product:spec:delete')")
    @DeleteMapping("/{specId}")
    public Result<Void> delete(@PathVariable Long spuId, @PathVariable Long specId) {
        specService.deleteSpec(spuId, specId);
        return Result.success();
    }

    @Operation(summary = "创建规格值")
    @PreAuthorize("hasAuthority('product:spec:create')")
    @PostMapping("/{specId}/values")
    public ResponseEntity<Result<SpecValueResponse>> createValue(
            @PathVariable Long spuId,
            @PathVariable Long specId,
            @Valid @RequestBody SpecValueCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(specService.createValue(spuId, specId, request)));
    }

    @Operation(summary = "修改规格值")
    @PreAuthorize("hasAuthority('product:spec:update')")
    @PutMapping("/{specId}/values/{valueId}")
    public Result<SpecValueResponse> updateValue(
            @PathVariable Long spuId,
            @PathVariable Long specId,
            @PathVariable Long valueId,
            @Valid @RequestBody SpecValueUpdateRequest request) {
        return Result.success(specService.updateValue(spuId, specId, valueId, request));
    }

    @Operation(summary = "删除规格值")
    @PreAuthorize("hasAuthority('product:spec:delete')")
    @DeleteMapping("/{specId}/values/{valueId}")
    public Result<Void> deleteValue(@PathVariable Long spuId,
                                    @PathVariable Long specId,
                                    @PathVariable Long valueId) {
        specService.deleteValue(spuId, specId, valueId);
        return Result.success();
    }
}
