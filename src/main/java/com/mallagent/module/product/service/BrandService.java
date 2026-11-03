package com.mallagent.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.product.dto.brand.BrandRequest;
import com.mallagent.module.product.dto.brand.BrandResponse;
import com.mallagent.module.product.entity.ProductBrand;
import com.mallagent.module.product.error.ProductErrorCode;
import com.mallagent.module.product.mapper.ProductBrandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品品牌管理。
 */
@Service
@RequiredArgsConstructor
public class BrandService {

    private final ProductBrandMapper brandMapper;
    private final ProductCacheService cacheService;

    public List<BrandResponse> list() {
        return brandMapper.selectList(new LambdaQueryWrapper<ProductBrand>()
                        .orderByAsc(ProductBrand::getSort)
                        .orderByAsc(ProductBrand::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public BrandResponse get(Long id) {
        return toResponse(requireBrand(id));
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        String name = request.name().trim();
        ensureNameUnique(name, null);

        ProductBrand brand = new ProductBrand();
        brand.setName(name);
        brand.setLogoUrl(normalize(request.logoUrl()));
        brand.setDescription(normalize(request.description()));
        brand.setStatus(request.status() == null ? 1 : request.status());
        brand.setSort(request.sort() == null ? 0 : request.sort());

        try {
            brandMapper.insert(brand);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ProductErrorCode.BRAND_NAME_EXISTS);
        }
        return toResponse(brand);
    }

    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        ProductBrand brand = requireBrand(id);
        String name = request.name().trim();
        ensureNameUnique(name, id);

        brand.setName(name);
        brand.setLogoUrl(normalize(request.logoUrl()));
        brand.setDescription(normalize(request.description()));
        if (request.status() != null) {
            brand.setStatus(request.status());
        }
        if (request.sort() != null) {
            brand.setSort(request.sort());
        }

        int updated = brandMapper.updateById(brand);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "品牌信息已被修改，请刷新后重试");
        }
        cacheService.evictSpuDetailsAfterCommit(brandMapper.selectActiveSpuIds(id));
        return toResponse(brand);
    }

    @Transactional
    public void delete(Long id) {
        requireBrand(id);
        if (brandMapper.countActiveSpus(id) > 0) {
            throw new BusinessException(ProductErrorCode.BRAND_IN_USE);
        }
        brandMapper.deleteById(id);
    }

    private void ensureNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<ProductBrand> wrapper = new LambdaQueryWrapper<ProductBrand>()
                .eq(ProductBrand::getName, name);
        if (excludeId != null) {
            wrapper.ne(ProductBrand::getId, excludeId);
        }
        if (brandMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ProductErrorCode.BRAND_NAME_EXISTS);
        }
    }

    private ProductBrand requireBrand(Long id) {
        ProductBrand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BusinessException(ProductErrorCode.BRAND_NOT_FOUND);
        }
        return brand;
    }

    private BrandResponse toResponse(ProductBrand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getLogoUrl(),
                brand.getDescription(),
                brand.getStatus(),
                brand.getSort()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
