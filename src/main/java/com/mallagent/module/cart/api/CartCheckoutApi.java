package com.mallagent.module.cart.api;

import com.mallagent.module.cart.api.dto.CartCheckoutItem;

import java.util.List;

/**
 * 购物车提供给订单模块的下单契约。
 *
 * <p>两方法必须在订单事务内调用：先锁定并读取当前用户已勾选条目，订单创建成功后再清理。
 * 行锁用于串行化同一用户的并发下单，避免同一批购物车条目生成多笔订单。</p>
 */
public interface CartCheckoutApi {

    /**
     * 锁定并返回当前用户已勾选购物车条目，按 skuId 升序。
     */
    List<CartCheckoutItem> lockSelectedItems(Long userId);

    /**
     * 删除当前用户已勾选购物车条目。
     */
    void clearSelectedItems(Long userId);
}
