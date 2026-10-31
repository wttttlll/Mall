package com.mallagent.module.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 登录保护配置。
 */
@Data
@ConfigurationProperties(prefix = "mallagent.auth")
public class AuthProperties {

    private int maxFailures = 5;

    private Duration lockDuration = Duration.ofMinutes(15);
}
