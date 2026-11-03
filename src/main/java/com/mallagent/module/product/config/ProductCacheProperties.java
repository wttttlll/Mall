package com.mallagent.module.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 商品缓存 TTL 配置。
 */
@Data
@ConfigurationProperties(prefix = "mallagent.product.cache")
public class ProductCacheProperties {

    /**
     * 用户端分类树缓存 TTL。
     */
    private Duration categoryTreeTtl = Duration.ofHours(1);

    /**
     * 用户端商品详情缓存 TTL。
     */
    private Duration spuDetailTtl = Duration.ofMinutes(30);
}
