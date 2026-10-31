package com.mallagent.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.common.api.PageResult;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.product.dto.product.ProductDetailResponse;
import com.mallagent.module.product.dto.product.ProductListItemResponse;
import com.mallagent.module.product.dto.sku.SkuResponse;
import com.mallagent.module.product.dto.spu.SpuCreateRequest;
import com.mallagent.module.product.dto.spu.SpuResponse;
import com.mallagent.module.product.dto.spu.SpuSummaryResponse;
import com.mallagent.module.product.dto.spu.SpuUpdateRequest;
import com.mallagent.module.product.entity.ProductBrand;
import com.mallagent.module.product.entity.ProductCategory;
import com.mallagent.module.product.entity.ProductSku;
import com.mallagent.module.product.entity.ProductSkuSpec;
import com.mallagent.module.product.entity.ProductSpec;
import com.mallagent.module.product.entity.ProductSpecValue;
import com.mallagent.module.product.entity.ProductSpu;
import com.mallagent.module.product.entity.ProductSpuImage;
import com.mallagent.module.product.enums.ProductStatus;
import com.mallagent.module.product.error.ProductErrorCode;
import com.mallagent.module.product.mapper.ProductBrandMapper;
import com.mallagent.module.product.mapper.ProductCategoryMapper;
import com.mallagent.module.product.mapper.ProductSkuMapper;
import com.mallagent.module.product.mapper.ProductSkuSpecMapper;
import com.mallagent.module.product.mapper.ProductSpecMapper;
import com.mallagent.module.product.mapper.ProductSpecValueMapper;
import com.mallagent.module.product.mapper.ProductSpuImageMapper;
import com.mallagent.module.product.mapper.ProductSpuMapper;
import com.mallagent.module.product.mapper.SkuSpecValueRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SPU 管理、状态机与用户端商品查询。
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductSpuMapper spuMapper;
    private final ProductSpuImageMapper spuImageMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductSkuSpecMapper skuSpecMapper;
    private final ProductSpecMapper specMapper;
    private final ProductSpecValueMapper specValueMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;
    private final CategoryService categoryService;
    private final SpecService specService;
    private final SkuService skuService;
    private final ProductCacheService cacheService;

    public PageResult<SpuSummaryResponse> adminPage(long pageNum,
                                                    long pageSize,
                                                    String status,
                                                    Long categoryId,
                                                    Long brandId,
                                                    String keyword) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        LambdaQueryWrapper<ProductSpu> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductSpu::getStatus, ProductStatus.from(status).name());
        }
        if (categoryId != null) {
            wrapper.eq(ProductSpu::getCategoryId, categoryId);
        }
        if (brandId != null) {
            wrapper.eq(ProductSpu::getBrandId, brandId);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query
                    .like(ProductSpu::getName, value)
                    .or()
                    .like(ProductSpu::getSubtitle, value));
        }
        wrapper.orderByDesc(ProductSpu::getId);

        Page<ProductSpu> page = spuMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        Map<Long, String> categoryNames = loadCategoryNames(page.getRecords().stream()
                .map(ProductSpu::getCategoryId).toList());
        Map<Long, String> brandNames = loadBrandNames(page.getRecords().stream()
                .map(ProductSpu::getBrandId).toList());

        List<SpuSummaryResponse> list = page.getRecords().stream()
                .map(spu -> new SpuSummaryResponse(
                        spu.getId(),
                        spu.getName(),
                        spu.getSubtitle(),
                        spu.getCategoryId(),
                        categoryNames.get(spu.getCategoryId()),
                        spu.getBrandId(),
                        spu.getBrandId() == null ? null : brandNames.get(spu.getBrandId()),
                        spu.getMainImage(),
                        spu.getStatus(),
                        spu.getSort(),
                        spu.getUpdatedAt()))
                .toList();

        return new PageResult<>(list, page.getTotal(), safePageNum, safePageSize);
    }

    public SpuResponse adminDetail(Long id) {
        return toSpuResponse(requireSpu(id));
    }

    @Transactional
    public SpuResponse create(SpuCreateRequest request) {
        validateCategory(request.categoryId(), false);
        validateBrand(request.brandId(), false);

        ProductSpu spu = new ProductSpu();
        spu.setCategoryId(request.categoryId());
        spu.setBrandId(request.brandId());
        spu.setName(request.name().trim());
        spu.setSubtitle(normalize(request.subtitle()));
        spu.setMainImage(normalize(request.mainImage()));
        spu.setDescription(normalize(request.description()));
        spu.setStatus(ProductStatus.DRAFT.name());
        spu.setSort(request.sort() == null ? 0 : request.sort());
        spuMapper.insert(spu);

        replaceImages(spu.getId(), request.images());
        return toSpuResponse(spu);
    }

    @Transactional
    public SpuResponse update(Long id, SpuUpdateRequest request) {
        ProductSpu spu = requireSpu(id);
        if (!ProductStatus.from(spu.getStatus()).editable()) {
            throw new BusinessException(ProductErrorCode.SPU_NOT_EDITABLE);
        }
        validateCategory(request.categoryId(), false);
        validateBrand(request.brandId(), false);

        spu.setCategoryId(request.categoryId());
        spu.setBrandId(request.brandId());
        spu.setName(request.name().trim());
        spu.setSubtitle(normalize(request.subtitle()));
        spu.setMainImage(normalize(request.mainImage()));
        spu.setDescription(normalize(request.description()));
        if (request.sort() != null) {
            spu.setSort(request.sort());
        }

        int updated = spuMapper.updateById(spu);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "商品已被修改，请刷新后重试");
        }
        replaceImages(spu.getId(), request.images());
        cacheService.evictSpuDetailAfterCommit(spu.getId());
        return toSpuResponse(spu);
    }

    @Transactional
    public void delete(Long id) {
        ProductSpu spu = requireSpu(id);
        if (!ProductStatus.from(spu.getStatus()).deletable()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_DELETABLE);
        }

        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, id));
        if (!skus.isEmpty()) {
            List<Long> skuIds = skus.stream().map(ProductSku::getId).toList();
            skuSpecMapper.delete(new LambdaQueryWrapper<ProductSkuSpec>()
                    .in(ProductSkuSpec::getSkuId, skuIds));
            skuMapper.delete(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getSpuId, id));
        }

        List<ProductSpec> specs = specMapper.selectList(new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getSpuId, id));
        if (!specs.isEmpty()) {
            List<Long> specIds = specs.stream().map(ProductSpec::getId).toList();
            specValueMapper.delete(new LambdaQueryWrapper<ProductSpecValue>()
                    .in(ProductSpecValue::getSpecId, specIds));
            specMapper.delete(new LambdaQueryWrapper<ProductSpec>()
                    .eq(ProductSpec::getSpuId, id));
        }

        spuImageMapper.delete(new LambdaQueryWrapper<ProductSpuImage>()
                .eq(ProductSpuImage::getSpuId, id));
        spuMapper.deleteById(id);
        cacheService.evictSpuDetailAfterCommit(id);
    }

    @Transactional
    public void onShelf(Long id) {
        ProductSpu spu = requireSpu(id);
        ProductStatus current = ProductStatus.from(spu.getStatus());
        if (!current.canTransitionTo(ProductStatus.ON_SHELF)) {
            throw new BusinessException(ProductErrorCode.INVALID_STATUS_TRANSITION);
        }
        validatePublishable(spu);

        spu.setStatus(ProductStatus.ON_SHELF.name());
        updateStatus(spu);
        cacheService.evictSpuDetailAfterCommit(id);
    }

    @Transactional
    public void offShelf(Long id) {
        ProductSpu spu = requireSpu(id);
        ProductStatus current = ProductStatus.from(spu.getStatus());
        if (!current.canTransitionTo(ProductStatus.OFF_SHELF)) {
            throw new BusinessException(ProductErrorCode.INVALID_STATUS_TRANSITION);
        }

        spu.setStatus(ProductStatus.OFF_SHELF.name());
        updateStatus(spu);
        cacheService.evictSpuDetailAfterCommit(id);
    }

    @Transactional
    public void toDraft(Long id) {
        ProductSpu spu = requireSpu(id);
        ProductStatus current = ProductStatus.from(spu.getStatus());
        if (!current.canTransitionTo(ProductStatus.DRAFT)) {
            throw new BusinessException(ProductErrorCode.INVALID_STATUS_TRANSITION);
        }

        spu.setStatus(ProductStatus.DRAFT.name());
        updateStatus(spu);
        cacheService.evictSpuDetailAfterCommit(id);
    }

    public PageResult<ProductListItemResponse> userPage(long pageNum,
                                                        long pageSize,
                                                        Long categoryId,
                                                        Long brandId,
                                                        String keyword) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        List<Long> categoryIds = categoryId == null
                ? null
                : categoryService.collectSelfAndDescendantIds(categoryId);
        String value = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<ProductSpu> page = new Page<>(safePageNum, safePageSize);
        IPage<ProductSpu> result = spuMapper.selectVisiblePage(page, categoryIds, brandId, value);
        List<ProductSpu> records = result.getRecords();

        Map<Long, String> categoryNames = loadCategoryNames(records.stream()
                .map(ProductSpu::getCategoryId).toList());
        Map<Long, String> brandNames = loadBrandNames(records.stream()
                .map(ProductSpu::getBrandId).toList());
        Map<Long, PriceRange> priceRanges = loadPriceRanges(records.stream()
                .map(ProductSpu::getId).toList());

        List<ProductListItemResponse> list = records.stream()
                .map(spu -> {
                    PriceRange priceRange = priceRanges.getOrDefault(spu.getId(), PriceRange.EMPTY);
                    return new ProductListItemResponse(
                            spu.getId(),
                            spu.getName(),
                            spu.getSubtitle(),
                            spu.getMainImage(),
                            spu.getCategoryId(),
                            categoryNames.get(spu.getCategoryId()),
                            spu.getBrandId(),
                            spu.getBrandId() == null ? null : brandNames.get(spu.getBrandId()),
                            priceRange.minPrice(),
                            priceRange.maxPrice());
                })
                .toList();

        return new PageResult<>(list, result.getTotal(), safePageNum, safePageSize);
    }

    public ProductDetailResponse userDetail(Long id) {
        Optional<ProductDetailResponse> cached = cacheService.getSpuDetail(id);
        if (cached.isPresent()) {
            return cached.get();
        }

        ProductDetailResponse detail = buildUserDetail(id);
        cacheService.putSpuDetail(id, detail);
        return detail;
    }

    private ProductDetailResponse buildUserDetail(Long id) {
        ProductSpu spu = requireSpu(id);
        if (!ProductStatus.from(spu.getStatus()).onShelf()) {
            throw new BusinessException(ProductErrorCode.SPU_NOT_VISIBLE);
        }

        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        if (category == null || category.getStatus() == null || category.getStatus() != 1) {
            throw new BusinessException(ProductErrorCode.SPU_NOT_VISIBLE);
        }

        String brandName = null;
        if (spu.getBrandId() != null) {
            ProductBrand brand = brandMapper.selectById(spu.getBrandId());
            if (brand == null || brand.getStatus() == null || brand.getStatus() != 1) {
                throw new BusinessException(ProductErrorCode.SPU_NOT_VISIBLE);
            }
            brandName = brand.getName();
        }

        List<SkuResponse> skus = skuService.listBySpu(id, true);
        if (skus.isEmpty()) {
            throw new BusinessException(ProductErrorCode.SPU_NOT_VISIBLE);
        }

        return new ProductDetailResponse(
                spu.getId(),
                spu.getName(),
                spu.getSubtitle(),
                spu.getDescription(),
                spu.getMainImage(),
                loadImages(id),
                category.getId(),
                category.getName(),
                spu.getBrandId(),
                brandName,
                specService.listBySpu(id),
                skus
        );
    }

    private SpuResponse toSpuResponse(ProductSpu spu) {
        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());

        return new SpuResponse(
                spu.getId(),
                spu.getName(),
                spu.getSubtitle(),
                spu.getCategoryId(),
                category == null ? null : category.getName(),
                spu.getBrandId(),
                brand == null ? null : brand.getName(),
                spu.getMainImage(),
                loadImages(spu.getId()),
                spu.getDescription(),
                spu.getStatus(),
                spu.getSort(),
                spu.getCreatedAt(),
                spu.getUpdatedAt(),
                specService.listBySpu(spu.getId()),
                skuService.listBySpu(spu.getId(), false)
        );
    }

    private void validatePublishable(ProductSpu spu) {
        if (!StringUtils.hasText(spu.getMainImage())) {
            throw new BusinessException(ProductErrorCode.IMAGE_REQUIRED);
        }
        validateCategory(spu.getCategoryId(), true);
        validateBrand(spu.getBrandId(), true);

        List<ProductSku> enabledSkus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spu.getId())
                .eq(ProductSku::getStatus, 1));
        if (enabledSkus.isEmpty()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_PUBLISHABLE);
        }
        validateSkuSpecCompleteness(spu.getId(), enabledSkus);
    }

    /**
     * 每个启用 SKU 必须完整覆盖当前 SPU 的全部规格，且每个规格值属于对应规格。
     */
    private void validateSkuSpecCompleteness(Long spuId, List<ProductSku> enabledSkus) {
        List<ProductSpec> specs = specMapper.selectList(new LambdaQueryWrapper<ProductSpec>()
                .eq(ProductSpec::getSpuId, spuId));
        List<Long> skuIds = enabledSkus.stream().map(ProductSku::getId).toList();
        Map<Long, List<SkuSpecValueRow>> rowsBySku = skuSpecMapper.selectRowsBySkuIds(skuIds)
                .stream()
                .collect(Collectors.groupingBy(SkuSpecValueRow::getSkuId));

        if (specs.isEmpty()) {
            if (!rowsBySku.isEmpty()) {
                throw new BusinessException(ProductErrorCode.SKU_SPEC_INCOMPLETE);
            }
            return;
        }

        List<Long> specIds = specs.stream().map(ProductSpec::getId).toList();
        Map<Long, Long> valueToSpec = specValueMapper.selectList(
                        new LambdaQueryWrapper<ProductSpecValue>()
                                .in(ProductSpecValue::getSpecId, specIds))
                .stream()
                .collect(Collectors.toMap(ProductSpecValue::getId, ProductSpecValue::getSpecId));
        Set<Long> expectedSpecIds = new HashSet<>(specIds);

        for (ProductSku sku : enabledSkus) {
            List<SkuSpecValueRow> rows = rowsBySku.getOrDefault(sku.getId(), List.of());
            if (rows.size() != expectedSpecIds.size()) {
                throw new BusinessException(ProductErrorCode.SKU_SPEC_INCOMPLETE);
            }

            Set<Long> seenSpecIds = new HashSet<>();
            for (SkuSpecValueRow row : rows) {
                Long valueSpecId = valueToSpec.get(row.getSpecValueId());
                if (!expectedSpecIds.contains(row.getSpecId())
                        || valueSpecId == null
                        || !valueSpecId.equals(row.getSpecId())
                        || !seenSpecIds.add(row.getSpecId())) {
                    throw new BusinessException(ProductErrorCode.SKU_SPEC_INCOMPLETE);
                }
            }
            if (!seenSpecIds.equals(expectedSpecIds)) {
                throw new BusinessException(ProductErrorCode.SKU_SPEC_INCOMPLETE);
            }
        }
    }

    private void updateStatus(ProductSpu spu) {
        int updated = spuMapper.updateById(spu);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "商品状态已被修改，请刷新后重试");
        }
    }

    private ProductCategory validateCategory(Long categoryId, boolean requireEnabled) {
        ProductCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        if (requireEnabled && (category.getStatus() == null || category.getStatus() != 1)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_DISABLED);
        }
        return category;
    }

    private void validateBrand(Long brandId, boolean requireEnabled) {
        if (brandId == null) {
            return;
        }
        ProductBrand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BusinessException(ProductErrorCode.BRAND_NOT_FOUND);
        }
        if (requireEnabled && (brand.getStatus() == null || brand.getStatus() != 1)) {
            throw new BusinessException(ProductErrorCode.BRAND_DISABLED);
        }
    }

    private ProductSpu requireSpu(Long id) {
        ProductSpu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BusinessException(ProductErrorCode.SPU_NOT_FOUND);
        }
        return spu;
    }

    private void replaceImages(Long spuId, List<String> imageUrls) {
        spuImageMapper.delete(new LambdaQueryWrapper<ProductSpuImage>()
                .eq(ProductSpuImage::getSpuId, spuId));
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        List<String> normalized = imageUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(20)
                .toList();
        for (int i = 0; i < normalized.size(); i++) {
            ProductSpuImage image = new ProductSpuImage();
            image.setSpuId(spuId);
            image.setImageUrl(normalized.get(i));
            image.setSort(i);
            spuImageMapper.insert(image);
        }
    }

    private List<String> loadImages(Long spuId) {
        return spuImageMapper.selectList(new LambdaQueryWrapper<ProductSpuImage>()
                        .eq(ProductSpuImage::getSpuId, spuId)
                        .orderByAsc(ProductSpuImage::getSort)
                        .orderByAsc(ProductSpuImage::getId))
                .stream()
                .map(ProductSpuImage::getImageUrl)
                .toList();
    }

    private Map<Long, String> loadCategoryNames(List<Long> categoryIds) {
        Set<Long> ids = categoryIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
    }

    private Map<Long, String> loadBrandNames(List<Long> brandIds) {
        Set<Long> ids = brandIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return brandMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ProductBrand::getId, ProductBrand::getName));
    }

    private Map<Long, PriceRange> loadPriceRanges(List<Long> spuIds) {
        if (spuIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<ProductSku>> skusBySpu = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .in(ProductSku::getSpuId, spuIds)
                        .eq(ProductSku::getStatus, 1))
                .stream()
                .collect(Collectors.groupingBy(ProductSku::getSpuId));

        Map<Long, PriceRange> result = new LinkedHashMap<>();
        skusBySpu.forEach((spuId, skus) -> {
            BigDecimal min = null;
            BigDecimal max = null;
            for (ProductSku sku : skus) {
                BigDecimal price = sku.getPrice();
                if (price == null) {
                    continue;
                }
                if (min == null || price.compareTo(min) < 0) {
                    min = price;
                }
                if (max == null || price.compareTo(max) > 0) {
                    max = price;
                }
            }
            result.put(spuId, new PriceRange(min, max));
        });
        return result;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        private static final PriceRange EMPTY = new PriceRange(null, null);
    }
}
