package com.mallagent.module.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 初始管理员配置。
 */
@Data
@ConfigurationProperties(prefix = "mallagent.admin")
public class AdminProperties {

    private boolean initEnabled = true;

    private String username = "admin";

    private String initPassword = "Admin@123456";
}
