package com.mallagent.module.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 订单超时与扫描配置。
 */
@Data
@ConfigurationProperties(prefix = "mallagent.order")
public class OrderProperties {

    /**
     * 待支付订单超时时间（分钟）。
     */
    private long timeoutMinutes = 30;

    /**
     * 扫描间隔（毫秒）。
     */
    private long timeoutScanIntervalMs = 60000;

    /**
     * 应用启动后首次扫描延迟（毫秒）。
     */
    private long timeoutScanInitialDelayMs = 10000;

    /**
     * 单次扫描最多处理的订单数。
     */
    private int timeoutBatchSize = 100;
}
