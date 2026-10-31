package com.mallagent.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.product.dto.spec.SpecCreateRequest;
import com.mallagent.module.product.dto.spec.SpecResponse;
import com.mallagent.module.product.dto.spec.SpecUpdateRequest;
import com.mallagent.module.product.dto.spec.SpecValueCreateRequest;
import com.mallagent.module.product.dto.spec.SpecValueResponse;
import com.mallagent.module.product.dto.spec.SpecValueUpdateRequest;
import com.mallagent.module.product.entity.ProductSpec;
import com.mallagent.module.product.entity.ProductSpecValue;
import com.mallagent.module.product.entity.ProductSpu;
import com.mallagent.module.product.enums.ProductStatus;
import com.mallagent.module.product.error.ProductErrorCode;
import com.mallagent.module.product.mapper.ProductSpecMapper;
import com.mallagent.module.product.mapper.ProductSpecValueMapper;
import com.mallagent.module.product.mapper.ProductSpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品规格与规格值。规格结构只允许在 DRAFT 状态修改。
 */
@Service
@RequiredArgsConstructor
public class SpecService {

    private final ProductSpuMapper spuMapper;
    private final ProductSpecMapper specMapper;
    private final ProductSpecValueMapper specValueMapper;
    private final ProductCacheService cacheService;

    public List<SpecResponse> listBySpu(Long spuId) {
        List<ProductSpec> specs = specMapper.selectList(new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getSpuId, spuId)
                .orderByAsc(ProductSpec::getSort)
                .orderByAsc(ProductSpec::getId));
        if (specs.isEmpty()) {
            return List.of();
        }

        List<Long> specIds = specs.stream().map(ProductSpec::getId).toList();
        Map<Long, List<ProductSpecValue>> valuesBySpec = specValueMapper.selectList(
                        new LambdaQueryWrapper<ProductSpecValue>()
                                .in(ProductSpecValue::getSpecId, specIds)
                                .orderByAsc(ProductSpecValue::getSort)
                                .orderByAsc(ProductSpecValue::getId))
                .stream()
                .collect(Collectors.groupingBy(ProductSpecValue::getSpecId));

        return specs.stream()
                .map(spec -> new SpecResponse(
                        spec.getId(),
                        spec.getName(),
                        spec.getSort(),
                        valuesBySpec.getOrDefault(spec.getId(), List.of()).stream()
                                .map(value -> new SpecValueResponse(value.getId(), value.getSpecValue(), value.getSort()))
                                .toList()))
                .toList();
    }

    @Transactional
    public SpecResponse createSpec(Long spuId, SpecCreateRequest request) {
        requireDraftSpu(spuId);
        String name = request.name().trim();
        ensureSpecNameUnique(spuId, name, null);

        ProductSpec spec = new ProductSpec();
        spec.setSpuId(spuId);
        spec.setName(name);
        spec.setSort(request.sort() == null ? 0 : request.sort());

        try {
            specMapper.insert(spec);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ProductErrorCode.SPEC_NAME_EXISTS);
        }
        cacheService.evictSpuDetailAfterCommit(spuId);
        return new SpecResponse(spec.getId(), spec.getName(), spec.getSort(), List.of());
    }

    @Transactional
    public SpecResponse updateSpec(Long spuId, Long specId, SpecUpdateRequest request) {
        requireDraftSpu(spuId);
        ProductSpec spec = requireSpec(spuId, specId);
        String name = request.name().trim();
        ensureSpecNameUnique(spuId, name, specId);

        spec.setName(name);
        if (request.sort() != null) {
            spec.setSort(request.sort());
        }
        int updated = specMapper.updateById(spec);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "规格信息已被修改，请刷新后重试");
        }
        cacheService.evictSpuDetailAfterCommit(spuId);
        return new SpecResponse(spec.getId(), spec.getName(), spec.getSort(), List.of());
    }

    @Transactional
    public void deleteSpec(Long spuId, Long specId) {
        requireDraftSpu(spuId);
        requireSpec(spuId, specId);
        if (specMapper.countSkuUsage(specId) > 0) {
            throw new BusinessException(ProductErrorCode.SPEC_IN_USE);
        }
        specValueMapper.delete(new LambdaQueryWrapper<ProductSpecValue>()
                .eq(ProductSpecValue::getSpecId, specId));
        specMapper.deleteById(specId);
        cacheService.evictSpuDetailAfterCommit(spuId);
    }

    @Transactional
    public SpecValueResponse createValue(Long spuId, Long specId, SpecValueCreateRequest request) {
        requireDraftSpu(spuId);
        requireSpec(spuId, specId);
        String value = request.value().trim();
        ensureSpecValueUnique(specId, value, null);

        ProductSpecValue specValue = new ProductSpecValue();
        specValue.setSpecId(specId);
        specValue.setSpecValue(value);
        specValue.setSort(request.sort() == null ? 0 : request.sort());

        try {
            specValueMapper.insert(specValue);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ProductErrorCode.SPEC_VALUE_EXISTS);
        }
        cacheService.evictSpuDetailAfterCommit(spuId);
        return new SpecValueResponse(specValue.getId(), specValue.getSpecValue(), specValue.getSort());
    }

    @Transactional
    public SpecValueResponse updateValue(Long spuId,
                                         Long specId,
                                         Long valueId,
                                         SpecValueUpdateRequest request) {
        requireDraftSpu(spuId);
        requireSpec(spuId, specId);
        ProductSpecValue specValue = requireSpecValue(specId, valueId);
        String value = request.value().trim();
        ensureSpecValueUnique(specId, value, valueId);

        specValue.setSpecValue(value);
        if (request.sort() != null) {
            specValue.setSort(request.sort());
        }
        int updated = specValueMapper.updateById(specValue);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "规格值已被修改，请刷新后重试");
        }
        cacheService.evictSpuDetailAfterCommit(spuId);
        return new SpecValueResponse(specValue.getId(), specValue.getSpecValue(), specValue.getSort());
    }

    @Transactional
    public void deleteValue(Long spuId, Long specId, Long valueId) {
        requireDraftSpu(spuId);
        requireSpec(spuId, specId);
        requireSpecValue(specId, valueId);
        if (specValueMapper.countSkuUsage(valueId) > 0) {
            throw new BusinessException(ProductErrorCode.SPEC_VALUE_IN_USE);
        }
        specValueMapper.deleteById(valueId);
        cacheService.evictSpuDetailAfterCommit(spuId);
    }

    private ProductSpu requireDraftSpu(Long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BusinessException(ProductErrorCode.SPU_NOT_FOUND);
        }
        if (ProductStatus.from(spu.getStatus()) != ProductStatus.DRAFT) {
            throw new BusinessException(ProductErrorCode.SPU_NOT_EDITABLE);
        }
        return spu;
    }

    private ProductSpec requireSpec(Long spuId, Long specId) {
        ProductSpec spec = specMapper.selectOne(new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getId, specId)
                .eq(ProductSpec::getSpuId, spuId));
        if (spec == null) {
            throw new BusinessException(ProductErrorCode.SPEC_NOT_FOUND);
        }
        return spec;
    }

    private ProductSpecValue requireSpecValue(Long specId, Long valueId) {
        ProductSpecValue specValue = specValueMapper.selectOne(new LambdaQueryWrapper<ProductSpecValue>()
                .eq(ProductSpecValue::getId, valueId)
                .eq(ProductSpecValue::getSpecId, specId));
        if (specValue == null) {
            throw new BusinessException(ProductErrorCode.SPEC_VALUE_NOT_FOUND);
        }
        return specValue;
    }

    private void ensureSpecNameUnique(Long spuId, String name, Long excludeId) {
        LambdaQueryWrapper<ProductSpec> wrapper = new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getSpuId, spuId)
                .eq(ProductSpec::getName, name);
        if (excludeId != null) {
            wrapper.ne(ProductSpec::getId, excludeId);
        }
        if (specMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ProductErrorCode.SPEC_NAME_EXISTS);
        }
    }

    private void ensureSpecValueUnique(Long specId, String value, Long excludeId) {
        LambdaQueryWrapper<ProductSpecValue> wrapper = new LambdaQueryWrapper<ProductSpecValue>()
                .eq(ProductSpecValue::getSpecId, specId)
                .eq(ProductSpecValue::getSpecValue, value);
        if (excludeId != null) {
            wrapper.ne(ProductSpecValue::getId, excludeId);
        }
        if (specValueMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ProductErrorCode.SPEC_VALUE_EXISTS);
        }
    }
}
