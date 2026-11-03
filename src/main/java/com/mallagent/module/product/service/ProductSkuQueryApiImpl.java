package com.mallagent.module.product.service;

import com.mallagent.module.product.api.ProductSkuQueryApi;
import com.mallagent.module.product.api.dto.SkuAvailability;
import com.mallagent.module.product.api.dto.SkuSnapshot;
import com.mallagent.module.product.entity.ProductBrand;
import com.mallagent.module.product.entity.ProductCategory;
import com.mallagent.module.product.entity.ProductSku;
import com.mallagent.module.product.entity.ProductSpu;
import com.mallagent.module.product.enums.ProductStatus;
import com.mallagent.module.product.mapper.ProductBrandMapper;
import com.mallagent.module.product.mapper.ProductCategoryMapper;
import com.mallagent.module.product.mapper.ProductSkuMapper;
import com.mallagent.module.product.mapper.ProductSkuSpecMapper;
import com.mallagent.module.product.mapper.ProductSpuMapper;
import com.mallagent.module.product.mapper.SkuSpecValueRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品 SKU 只读查询实现，供购物车等模块使用。
 *
 * <p>本类只读取现有商品数据，不改变商品模块的业务规则。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductSkuQueryApiImpl implements ProductSkuQueryApi {

    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductSkuSpecMapper skuSpecMapper;

    @Override
    public Optional<SkuSnapshot> findSku(Long skuId) {
        if (skuId == null) {
            return Optional.empty();
        }
        return findSkus(List.of(skuId)).stream().findFirst();
    }

    @Override
    public List<SkuSnapshot> findSkus(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = skuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        List<ProductSku> skus = skuMapper.selectBatchIds(ids);
        if (skus.isEmpty()) {
            return List.of();
        }

        Map<Long, List<SkuSpecValueRow>> specsBySku = skuSpecMapper
                .selectRowsBySkuIds(skus.stream().map(ProductSku::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(SkuSpecValueRow::getSkuId));

        Set<Long> spuIds = skus.stream()
                .map(ProductSku::getSpuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProductSpu> spuById = spuIds.isEmpty()
                ? Map.of()
                : spuMapper.selectBatchIds(spuIds).stream()
                .collect(Collectors.toMap(ProductSpu::getId, Function.identity()));

        Set<Long> categoryIds = spuById.values().stream()
                .map(ProductSpu::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProductCategory> categoryById = categoryIds.isEmpty()
                ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));

        Set<Long> brandIds = spuById.values().stream()
                .map(ProductSpu::getBrandId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProductBrand> brandById = brandIds.isEmpty()
                ? Map.of()
                : brandMapper.selectBatchIds(brandIds).stream()
                .collect(Collectors.toMap(ProductBrand::getId, Function.identity()));

        return skus.stream()
                .map(sku -> toSnapshot(sku,
                        spuById.get(sku.getSpuId()),
                        categoryById,
                        brandById,
                        specsBySku.getOrDefault(sku.getId(), List.of())))
                .toList();
    }

    private SkuSnapshot toSnapshot(ProductSku sku,
                                   ProductSpu spu,
                                   Map<Long, ProductCategory> categoryById,
                                   Map<Long, ProductBrand> brandById,
                                   List<SkuSpecValueRow> specRows) {
        SkuAvailability availability = availabilityOf(sku, spu, categoryById, brandById);
        String imageUrl = StringUtils.hasText(sku.getImageUrl())
                ? sku.getImageUrl()
                : (spu == null ? null : spu.getMainImage());

        return new SkuSnapshot(
                sku.getId(),
                sku.getSpuId(),
                sku.getSkuCode(),
                spu == null ? null : spu.getName(),
                imageUrl,
                sku.getPrice(),
                specText(specRows),
                availability
        );
    }

    private SkuAvailability availabilityOf(ProductSku sku,
                                           ProductSpu spu,
                                           Map<Long, ProductCategory> categoryById,
                                           Map<Long, ProductBrand> brandById) {
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            return SkuAvailability.SKU_DISABLED;
        }
        if (spu == null) {
            return SkuAvailability.SPU_NOT_FOUND;
        }
        if (!ProductStatus.ON_SHELF.name().equals(spu.getStatus())) {
            return SkuAvailability.SPU_NOT_ON_SHELF;
        }

        ProductCategory category = categoryById.get(spu.getCategoryId());
        if (category == null) {
            return SkuAvailability.CATEGORY_NOT_FOUND;
        }
        if (category.getStatus() == null || category.getStatus() != 1) {
            return SkuAvailability.CATEGORY_DISABLED;
        }

        if (spu.getBrandId() == null) {
            return SkuAvailability.AVAILABLE;
        }
        ProductBrand brand = brandById.get(spu.getBrandId());
        if (brand == null) {
            return SkuAvailability.BRAND_NOT_FOUND;
        }
        if (brand.getStatus() == null || brand.getStatus() != 1) {
            return SkuAvailability.BRAND_DISABLED;
        }
        return SkuAvailability.AVAILABLE;
    }

    private String specText(List<SkuSpecValueRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.stream()
                .sorted(Comparator.comparing(SkuSpecValueRow::getSpecId))
                .map(row -> row.getSpecName() + "=" + row.getSpecValueName())
                .collect(Collectors.joining(" / "));
    }
}
