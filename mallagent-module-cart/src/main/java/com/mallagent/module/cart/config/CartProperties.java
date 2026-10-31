package com.mallagent.module.cart.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 购物车配置。
 */
@Data
@ConfigurationProperties(prefix = "mallagent.cart")
public class CartProperties {

    /**
     * 单个 SKU 最大购买数量。
     */
    private int maxQuantityPerItem = 99;

    /**
     * 购物车最大 SKU 种类数，应用层软上限。
     */
    private int maxItems = 100;
}
