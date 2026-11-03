-- 阶段 1：用户、RBAC 与收货地址表结构。

CREATE TABLE sys_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    nickname      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    phone         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    email         VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    avatar        VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    token_version INT          NOT NULL DEFAULT 0 COMMENT '令牌版本，用于强制旧令牌失效',
    last_login_at DATETIME(3)  DEFAULT NULL COMMENT '最后登录时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by    BIGINT       DEFAULT NULL COMMENT '创建人',
    updated_by    BIGINT       DEFAULT NULL COMMENT '更新人',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_phone (phone),
    UNIQUE KEY uk_sys_user_email (email),
    KEY idx_sys_user_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户';

CREATE TABLE sys_role
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色编码',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by  BIGINT       DEFAULT NULL COMMENT '创建人',
    updated_by  BIGINT       DEFAULT NULL COMMENT '更新人',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code),
    KEY idx_sys_role_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

CREATE TABLE sys_permission
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '父权限 ID，0 为根',
    permission_code VARCHAR(128) NOT NULL COMMENT '权限码',
    permission_name VARCHAR(64)  NOT NULL COMMENT '权限名称',
    type            TINYINT      NOT NULL DEFAULT 3 COMMENT '1 菜单，2 按钮，3 接口',
    path            VARCHAR(255) DEFAULT NULL COMMENT '前端路由或接口路径',
    method          VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP 方法',
    sort            INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by      BIGINT       DEFAULT NULL COMMENT '创建人',
    updated_by      BIGINT       DEFAULT NULL COMMENT '更新人',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code),
    KEY idx_sys_permission_parent (parent_id),
    KEY idx_sys_permission_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '权限项';

CREATE TABLE sys_user_role
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL COMMENT '用户 ID',
    role_id    BIGINT      NOT NULL COMMENT '角色 ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色关联';

CREATE TABLE sys_role_permission
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id       BIGINT      NOT NULL COMMENT '角色 ID',
    permission_id BIGINT      NOT NULL COMMENT '权限 ID',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色权限关联';

CREATE TABLE user_address
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL COMMENT '用户 ID',
    receiver_name  VARCHAR(64)  NOT NULL COMMENT '收货人',
    receiver_phone VARCHAR(20)  NOT NULL COMMENT '收货人手机号',
    province       VARCHAR(64)  NOT NULL COMMENT '省份',
    city           VARCHAR(64)  NOT NULL COMMENT '城市',
    district       VARCHAR(64)  NOT NULL COMMENT '区县',
    detail_address VARCHAR(255) NOT NULL COMMENT '详细地址',
    postal_code    VARCHAR(16)  DEFAULT NULL COMMENT '邮编',
    is_default     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 默认地址，0 否',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by     BIGINT       DEFAULT NULL COMMENT '创建人',
    updated_by     BIGINT       DEFAULT NULL COMMENT '更新人',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_user_address_user (user_id),
    KEY idx_user_address_default (user_id, is_default)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户收货地址';
