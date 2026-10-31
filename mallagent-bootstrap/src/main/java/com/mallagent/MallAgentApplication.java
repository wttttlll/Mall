package com.mallagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * MallAgent 启动类。
 *
 * <p>排除默认内存用户自动配置：本项目使用自定义 JWT 认证。</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan("com.mallagent")
public class MallAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAgentApplication.class, args);
    }
}
