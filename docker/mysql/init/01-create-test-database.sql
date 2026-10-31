-- 首次初始化 MySQL 数据卷时执行。
-- 创建集成测试专用数据库，避免测试数据污染开发库。
CREATE DATABASE IF NOT EXISTS `mallagent_test`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON `mallagent_test`.* TO 'mallagent'@'%';
FLUSH PRIVILEGES;
