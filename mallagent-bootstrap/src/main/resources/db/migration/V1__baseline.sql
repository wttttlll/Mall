-- MallAgent Flyway 基线迁移（阶段 0）。
-- 业务表从阶段 1 开始创建；这里只建立基础设施表。

CREATE TABLE IF NOT EXISTS sys_config
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value VARCHAR(512) NOT NULL COMMENT '配置值',
    description  VARCHAR(255) DEFAULT NULL COMMENT '说明',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置';
