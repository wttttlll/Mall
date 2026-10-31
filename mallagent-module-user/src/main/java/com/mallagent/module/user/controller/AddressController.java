package com.mallagent.module.user.controller;

import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.framework.security.SecurityUtils;
import com.mallagent.module.user.dto.address.AddressRequest;
import com.mallagent.module.user.dto.address.AddressResponse;
import com.mallagent.module.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * 当前用户的收货地址接口。
 */
@Tag(name = "收货地址")
@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "地址列表")
    @GetMapping
    public Result<List<AddressResponse>> list() {
        return Result.success(addressService.list(currentUserId()));
    }

    @Operation(summary = "地址详情")
    @GetMapping("/{id}")
    public Result<AddressResponse> get(@PathVariable Long id) {
        return Result.success(addressService.get(currentUserId(), id));
    }

    @Operation(summary = "新增地址")
    @PostMapping
    public ResponseEntity<Result<AddressResponse>> create(@Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success(addressService.create(currentUserId(), request)));
    }

    @Operation(summary = "修改地址")
    @PutMapping("/{id}")
    public Result<AddressResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.update(currentUserId(), id, request));
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(currentUserId(), id);
        return Result.success();
    }

    @Operation(summary = "设为默认地址")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(currentUserId(), id);
        return Result.success();
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
