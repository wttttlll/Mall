package com.mallagent.module.product.service;

import com.mallagent.module.product.api.ProductSpuQueryApi;
import com.mallagent.module.product.api.dto.SpuSnapshot;
import com.mallagent.module.product.entity.ProductSpu;
import com.mallagent.module.product.mapper.ProductSpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 商品 SPU 只读查询实现。
 */
@Service
@RequiredArgsConstructor
public class ProductSpuQueryApiImpl implements ProductSpuQueryApi {

    private final ProductSpuMapper spuMapper;

    @Override
    public Optional<SpuSnapshot> findSpu(Long spuId) {
        if (spuId == null) {
            return Optional.empty();
        }
        ProductSpu spu = spuMapper.selectById(spuId);
        return spu == null ? Optional.empty() : Optional.of(toSnapshot(spu));
    }

    @Override
    public List<SpuSnapshot> findSpus(Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = spuIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return spuMapper.selectBatchIds(ids).stream()
                .map(this::toSnapshot)
                .toList();
    }

    private SpuSnapshot toSnapshot(ProductSpu spu) {
        return new SpuSnapshot(
                spu.getId(),
                spu.getName(),
                spu.getSubtitle(),
                spu.getMainImage(),
                spu.getStatus()
        );
    }
}
