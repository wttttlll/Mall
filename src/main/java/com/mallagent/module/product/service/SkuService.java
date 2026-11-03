package com.mallagent.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.product.dto.sku.SkuCreateRequest;
import com.mallagent.module.product.dto.sku.SkuResponse;
import com.mallagent.module.product.dto.sku.SkuSpecValueResponse;
import com.mallagent.module.product.dto.sku.SkuUpdateRequest;
import com.mallagent.module.product.entity.ProductSku;
import com.mallagent.module.product.entity.ProductSkuSpec;
import com.mallagent.module.product.entity.ProductSpec;
import com.mallagent.module.product.entity.ProductSpecValue;
import com.mallagent.module.product.entity.ProductSpu;
import com.mallagent.module.product.enums.ProductStatus;
import com.mallagent.module.product.error.ProductErrorCode;
import com.mallagent.module.product.mapper.ProductSkuMapper;
import com.mallagent.module.product.mapper.ProductSkuSpecMapper;
import com.mallagent.module.product.mapper.ProductSpecMapper;
import com.mallagent.module.product.mapper.ProductSpecValueMapper;
import com.mallagent.module.product.mapper.ProductSpuMapper;
import com.mallagent.module.product.mapper.SkuSpecValueRow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SKU 管理。SKU 规格值必须覆盖该 SPU 的全部规格，且组合签名唯一。
 */
@Service
@RequiredArgsConstructor
public class SkuService {

    private static final String DEFAULT_SIGNATURE = "DEFAULT";

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductSkuSpecMapper skuSpecMapper;
    private final ProductSpecMapper specMapper;
    private final ProductSpecValueMapper specValueMapper;
    private final ProductCacheService cacheService;

    public List<SkuResponse> listBySpu(Long spuId, boolean onlyEnabled) {
        requireSpu(spuId);

        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spuId)
                .orderByAsc(ProductSku::getId);
        if (onlyEnabled) {
            wrapper.eq(ProductSku::getStatus, 1);
        }
        return toResponses(skuMapper.selectList(wrapper));
    }

    @Transactional
    public SkuResponse create(Long spuId, SkuCreateRequest request) {
        ProductSpu spu = requireSpu(spuId);
        assertEditable(spu);

        String skuCode = request.skuCode().trim();
        ensureSkuCodeUnique(skuCode, null);
        SpecSelection selection = validateSpecValues(spuId, request.specValueIds());
        ensureSignatureUnique(spuId, selection.signature(), null);

        ProductSku sku = new ProductSku();
        sku.setSpuId(spuId);
        sku.setSkuCode(skuCode);
        sku.setPrice(normalizePrice(request.price()));
        sku.setMarketPrice(normalizeNullablePrice(request.marketPrice()));
        sku.setImageUrl(normalize(request.imageUrl()));
        sku.setStatus(request.status() == null ? 1 : request.status());
        sku.setSpecSignature(selection.signature());

        try {
            skuMapper.insert(sku);
        } catch (DuplicateKeyException ex) {
            throw mapDuplicateKey(ex);
        }
        insertSkuSpecs(sku.getId(), selection.specs());
        cacheService.evictSpuDetailAfterCommit(spuId);
        return toResponses(List.of(sku)).get(0);
    }

    @Transactional
    public SkuResponse update(Long spuId, Long skuId, SkuUpdateRequest request) {
        ProductSpu spu = requireSpu(spuId);
        assertEditable(spu);
        ProductSku sku = requireSku(spuId, skuId);

        SpecSelection selection = validateSpecValues(spuId, request.specValueIds());
        ensureSignatureUnique(spuId, selection.signature(), skuId);

        sku.setPrice(normalizePrice(request.price()));
        sku.setMarketPrice(normalizeNullablePrice(request.marketPrice()));
        sku.setImageUrl(normalize(request.imageUrl()));
        if (request.status() != null) {
            sku.setStatus(request.status());
        }
        sku.setSpecSignature(selection.signature());

        int updated = skuMapper.updateById(sku);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "SKU 已被修改，请刷新后重试");
        }

        skuSpecMapper.delete(new LambdaQueryWrapper<ProductSkuSpec>()
                .eq(ProductSkuSpec::getSkuId, skuId));
        insertSkuSpecs(skuId, selection.specs());
        cacheService.evictSpuDetailAfterCommit(spuId);
        return toResponses(List.of(sku)).get(0);
    }

    @Transactional
    public void delete(Long spuId, Long skuId) {
        ProductSpu spu = requireSpu(spuId);
        assertEditable(spu);
        requireSku(spuId, skuId);

        skuSpecMapper.delete(new LambdaQueryWrapper<ProductSkuSpec>()
                .eq(ProductSkuSpec::getSkuId, skuId));
        skuMapper.deleteById(skuId);
        cacheService.evictSpuDetailAfterCommit(spuId);
    }

    private SpecSelection validateSpecValues(Long spuId, List<Long> requestedValueIds) {
        List<ProductSpec> specs = specMapper.selectList(new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getSpuId, spuId)
                .orderByAsc(ProductSpec::getSort)
                .orderByAsc(ProductSpec::getId));

        List<Long> valueIds = requestedValueIds == null
                ? List.of()
                : requestedValueIds.stream().filter(Objects::nonNull).toList();

        if (specs.isEmpty()) {
            if (!valueIds.isEmpty()) {
                throw new BusinessException(ProductErrorCode.SKU_SPEC_INVALID);
            }
            return new SpecSelection(List.of(), DEFAULT_SIGNATURE);
        }

        if (valueIds.size() != specs.size()) {
            throw new BusinessException(ProductErrorCode.SKU_SPEC_INVALID);
        }

        List<Long> specIds = specs.stream().map(ProductSpec::getId).toList();
        Map<Long, ProductSpecValue> valueById = specValueMapper.selectList(
                        new LambdaQueryWrapper<ProductSpecValue>()
                                .in(ProductSpecValue::getSpecId, specIds))
                .stream()
                .collect(Collectors.toMap(ProductSpecValue::getId, Function.identity(), (left, right) -> left));

        Map<Long, ProductSpecValue> valueBySpec = new LinkedHashMap<>();
        for (Long valueId : valueIds) {
            ProductSpecValue value = valueById.get(valueId);
            if (value == null) {
                throw new BusinessException(ProductErrorCode.SKU_SPEC_INVALID);
            }
            if (valueBySpec.putIfAbsent(value.getSpecId(), value) != null) {
                throw new BusinessException(ProductErrorCode.SKU_SPEC_INVALID);
            }
        }

        if (valueBySpec.size() != specs.size()) {
            throw new BusinessException(ProductErrorCode.SKU_SPEC_INVALID);
        }

        List<SelectedSpec> selected = valueBySpec.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SelectedSpec(entry.getKey(), entry.getValue().getId()))
                .toList();

        String signature = selected.stream()
                .map(spec -> spec.specId() + ":" + spec.specValueId())
                .collect(Collectors.joining(","));

        return new SpecSelection(selected, signature);
    }

    private List<SkuResponse> toResponses(List<ProductSku> skus) {
        if (skus.isEmpty()) {
            return List.of();
        }

        List<Long> skuIds = skus.stream().map(ProductSku::getId).toList();
        Map<Long, List<SkuSpecValueRow>> specRows = skuSpecMapper.selectRowsBySkuIds(skuIds)
                .stream()
                .collect(Collectors.groupingBy(
                        SkuSpecValueRow::getSkuId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return skus.stream()
                .map(sku -> new SkuResponse(
                        sku.getId(),
                        sku.getSkuCode(),
                        sku.getPrice(),
                        sku.getMarketPrice(),
                        sku.getImageUrl(),
                        sku.getStatus(),
                        specRows.getOrDefault(sku.getId(), List.of()).stream()
                                .map(row -> new SkuSpecValueResponse(
                                        row.getSpecId(),
                                        row.getSpecName(),
                                        row.getSpecValueId(),
                                        row.getSpecValueName()))
                                .toList()))
                .toList();
    }

    private void insertSkuSpecs(Long skuId, List<SelectedSpec> selectedSpecs) {
        for (SelectedSpec selected : selectedSpecs) {
            ProductSkuSpec skuSpec = new ProductSkuSpec();
            skuSpec.setSkuId(skuId);
            skuSpec.setSpecId(selected.specId());
            skuSpec.setSpecValueId(selected.specValueId());
            skuSpecMapper.insert(skuSpec);
        }
    }

    private ProductSpu requireSpu(Long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BusinessException(ProductErrorCode.SPU_NOT_FOUND);
        }
        return spu;
    }

    private void assertEditable(ProductSpu spu) {
        if (!ProductStatus.from(spu.getStatus()).editable()) {
            throw new BusinessException(ProductErrorCode.SKU_NOT_EDITABLE);
        }
    }

    private ProductSku requireSku(Long spuId, Long skuId) {
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getId, skuId)
                .eq(ProductSku::getSpuId, spuId));
        if (sku == null) {
            throw new BusinessException(ProductErrorCode.SKU_NOT_FOUND);
        }
        return sku;
    }

    private void ensureSkuCodeUnique(String skuCode, Long excludeSkuId) {
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSkuCode, skuCode);
        if (excludeSkuId != null) {
            wrapper.ne(ProductSku::getId, excludeSkuId);
        }
        if (skuMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ProductErrorCode.SKU_CODE_EXISTS);
        }
    }

    private void ensureSignatureUnique(Long spuId, String signature, Long excludeSkuId) {
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spuId)
                .eq(ProductSku::getSpecSignature, signature);
        if (excludeSkuId != null) {
            wrapper.ne(ProductSku::getId, excludeSkuId);
        }
        if (skuMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ProductErrorCode.SKU_SPEC_DUPLICATE);
        }
    }

    private BusinessException mapDuplicateKey(DuplicateKeyException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("uk_product_sku_code") || message.contains("uk_product_sku_active_code")) {
            return new BusinessException(ProductErrorCode.SKU_CODE_EXISTS);
        }
        return new BusinessException(ProductErrorCode.SKU_SPEC_DUPLICATE);
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeNullablePrice(BigDecimal price) {
        return price == null ? null : price.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SelectedSpec(Long specId, Long specValueId) {
    }

    private record SpecSelection(List<SelectedSpec> specs, String signature) {
    }
}
