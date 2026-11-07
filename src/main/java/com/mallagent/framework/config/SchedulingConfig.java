package com.mallagent.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring 定时任务。当前用于订单超时扫描。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
