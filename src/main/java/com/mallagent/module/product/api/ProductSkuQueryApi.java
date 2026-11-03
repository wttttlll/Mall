package com.mallagent.module.product.api;

import com.mallagent.module.product.api.dto.SkuSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 商品 SKU 只读查询契约。
 *
 * <p>其他模块只能通过本接口读取 SKU 信息，不得直接使用商品模块的 Mapper、实体或 Service 实现。</p>
 */
public interface ProductSkuQueryApi {

    /**
     * 查询单个 SKU 快照。
     */
    Optional<SkuSnapshot> findSku(Long skuId);

    /**
     * 批量查询 SKU 快照。不存在的 SKU 不会出现在返回列表中。
     */
    List<SkuSnapshot> findSkus(Collection<Long> skuIds);
}
