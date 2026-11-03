package com.mallagent.module.inventory.api;

import com.mallagent.module.inventory.api.dto.StockCommand;
import com.mallagent.module.inventory.api.dto.StockRestoreCommand;
import com.mallagent.module.inventory.api.dto.StockSnapshot;

import java.util.Collection;
import java.util.List;

/**
 * 库存内部 API，供订单、支付、售后模块调用。
 *
 * <p>事务边界：所有写方法使用 REQUIRED 传播，加入调用方已有事务；由最外层事务发起方
 * 负责隔离级别（推荐 READ_COMMITTED）。库存 API 不修改调用方已存在事务的隔离级别。</p>
 *
 * <p>库存 API 不负责订单状态机。调用方必须在同一本地事务内完成订单/支付/售后状态变更，
 * 再调用对应库存方法；如果调用方在事务提交后才调用库存，无法保证一致性。</p>
 */
public interface InventoryStockApi {

    /**
     * 下单锁定。items 必须覆盖订单全部 SKU，并按 skuId 升序处理。
     */
    void lock(StockCommand command);

    /**
     * 支付确认扣减。必须匹配订单全部 SKU 与原始锁定数量，不能只传部分条目。
     */
    void deduct(StockCommand command);

    /**
     * 取消/超时释放。必须匹配订单全部 SKU 与原始锁定数量。
     */
    void release(StockCommand command);

    /**
     * 售后退货回补。阶段 4 只支持整单 SKU/数量匹配；累计回补不得超过原始扣减数量。
     */
    void restore(StockRestoreCommand command);

    /**
     * 批量查询库存快照。不存在的库存记录返回 initialized=false。
     */
    List<StockSnapshot> findStocks(Collection<Long> skuIds);
}
