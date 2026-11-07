package com.mallagent.module.product.api;

import com.mallagent.module.product.api.dto.SpuSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 商品 SPU 只读查询契约。
 *
 * <p>其他模块只能通过本接口读取商品基础信息，不得直接使用商品模块的 Mapper、实体或 Service 实现。</p>
 */
public interface ProductSpuQueryApi {

    Optional<SpuSnapshot> findSpu(Long spuId);

    List<SpuSnapshot> findSpus(Collection<Long> spuIds);
}
