# MallAgent

MallAgent 是一个从零开发的 MVP 电商项目：Java 后端采用**模块化单体**架构（一个 Spring Boot 进程、一个启动类、一个 MySQL 库），前端使用 **Vue 3 + Vite**。后端当前为**单 Maven 模块**，根目录即启动工程；前端位于 `frontend/`。

当前进度：阶段 0–4 已通过验收；阶段 6/7 的 MVP 简化版（订单、模拟支付）已实现；Vue 前端 MVP 页面已实现并完成构建验证。后端全量测试 147 个，0 失败、0 错误、0 跳过。优惠券、物流、售后未开始。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Java 21 |
| 框架 | Spring Boot 3.5 |
| 构建 | Maven 单模块（单 JAR） |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8 |
| 缓存 | Redis |
| 消息队列 | RabbitMQ |
| 认证授权 | Spring Security + JWT |
| 数据库迁移 | Flyway |
| API 文档 | Springdoc OpenAPI |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc + Mockito |
| 本地基础设施 | Docker Compose |
| 辅助工具 | Lombok |
| 前端框架 | Vue 3 + Vite |
| 前端路由/状态 | Vue Router + Pinia |
| HTTP 客户端 | Axios |
| 前端测试 | Vitest |

## 工程结构

```text
MallAgent/
├── pom.xml                     # 唯一 pom，artifactId=mallagent，packaging=jar
├── src/main/java/com/mallagent/
│   ├── MallAgentApplication.java
│   ├── bootstrap/              # 启动配置、系统健康检查
│   ├── common/                 # 统一响应、分页、错误码、业务异常
│   ├── framework/              # Web、安全、MyBatis-Plus、Redis、RabbitMQ、OpenAPI
│   └── module/
│       ├── user/               # 用户、RBAC、JWT、收货地址
│       ├── product/            # 分类、品牌、SPU/SKU、规格、商品查询
│       ├── cart/               # 购物车
│       ├── inventory/          # 库存、库存流水、售后退补
│       ├── order/              # 订单、订单明细、取消
│       └── payment/            # 模拟支付
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/           # Flyway V1–V12
├── src/test/java/com/mallagent/
├── src/test/resources/application-test.yml
├── docker-compose.yml          # MySQL 8、Redis 7、RabbitMQ 3.13
├── docker/mysql/init/
├── frontend/                   # Vue 3 + Vite 前端
│   ├── src/api/                # Axios 封装与后端 API
│   ├── src/components/         # 通用组件
│   ├── src/views/              # 登录、商品、购物车、结算、订单、地址
│   ├── src/router/             # 路由与登录守卫
│   ├── src/stores/             # Pinia 认证状态
│   └── vite.config.js          # /api 代理到 localhost:8080
└── docs/                       # 设计、数据库、API、测试与阶段计划
```

模块是 `com.mallagent.*` 包边界，不是 Maven 子模块；跨模块只通过对方 `api` 包交互。

## 环境要求

- JDK 21
- Maven 3.10+
- Node.js 20+ 与 npm
- Docker Desktop（本地 MySQL、Redis、RabbitMQ）

## 快速开始

1. 准备本地环境变量文件（不要提交 `.env`）：

```bash
cp .env.example .env
# 按本机环境修改 .env
```

2. 启动基础设施：

```bash
docker compose up -d --wait
```

3. 设置应用运行所需环境变量（示例名称，值从本地环境注入）：

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3307
export MYSQL_DATABASE=mallagent
export MYSQL_USER=mallagent
export MYSQL_PASSWORD="$MYSQL_PASSWORD"
export REDIS_HOST=localhost
export REDIS_PORT=6379
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USER=mallagent
export RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD"
export JWT_SECRET="$JWT_SECRET"       # 生产环境至少 32 字节
```

4. 构建并启动：

```bash
mvn clean package
java -jar target/mallagent-0.0.1-SNAPSHOT.jar
```

也可以直接运行：

```bash
mvn spring-boot:run
```

默认端口 `8080`。健康检查：`GET /api/v1/system/health`；OpenAPI：`/swagger-ui.html`、`/v3/api-docs`。

5. 启动前端（新终端）：

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。Vite 已配置 `/api` 代理到 `http://localhost:8080`，本地开发无需额外 CORS 配置。生产构建：

```bash
cd frontend
npm run build
# 产物在 frontend/dist；部署时由反向代理把 /api 转发到后端
```

前端页面：

- 登录、注册
- 商品列表、商品详情、SKU 选择、加入购物车
- 购物车：修改数量、删除、勾选、全选
- 结算：选择收货地址、查看后端计算的商品与金额、提交订单
- 我的订单、订单详情
- 模拟支付、取消未支付订单
- 收货地址查看、新增、编辑、删除、设为默认

## 配置项

敏感配置（数据库密码、JWT 密钥、管理员初始密码等）必须通过环境变量注入，仓库只保留 `.env.example` 占位。

| 环境变量 | 说明 |
| --- | --- |
| `SERVER_PORT` | 应用端口，默认 8080 |
| `MYSQL_HOST` / `MYSQL_PORT` | MySQL 地址与端口 |
| `MYSQL_DATABASE` | 数据库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` | Redis 连接 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | RabbitMQ 连接 |
| `JWT_SECRET` | JWT 签名密钥；生产必须使用足够长的随机值 |
| `ADMIN_INIT_PASSWORD` | 首次启动的管理员初始密码 |

其余默认值见 `src/main/resources/application.yml`；不要在生产环境使用本地开发默认值。

## 测试

```bash
docker compose up -d --wait

# 已有测试库；密码由本地环境注入
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD="$MYSQL_PASSWORD" mvn clean verify

# 空库迁移与全量测试；测试库需提前创建并授权
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD="$MYSQL_PASSWORD" \
MYSQL_TEST_DATABASE=mallagent_order_verify mvn clean verify
```

前端：

```bash
cd frontend
npm test
npm run build
```

测试库与开发库隔离，测试 profile 使用 Redis database `1`。测试框架不使用 Testcontainers。

## 当前已实现

| 阶段 | 模块 | 状态 |
| --- | --- | --- |
| 0 | 工程基座 | ✅ 已完成 |
| 1 | 用户、RBAC、JWT、收货地址 | ✅ 已通过验收 |
| 2 | 商品 | ✅ 已通过验收 |
| 3 | 购物车 | ✅ 已通过验收 |
| 4 | 库存 | ✅ 已通过技术验收（2026-11-02） |
| 6 | 订单（MVP 简化版） | ✅ 已实现并验证（2026-11-02） |
| 7 | 模拟支付（MVP 简化版） | ✅ 已实现并验证（2026-11-02） |
| 5、8、9 | 优惠券、物流、售后 | ⬜ 未开始 |
| 10 | 互动 | ⬜ MVP 后增量 |
| 11 | 收尾加固 | ⬜ 计划中 |
| 12 | Vue 前端 MVP | ✅ 已实现并完成构建验证（2026-11-02） |

库存模块提供 `InventoryStockApi`（lock / deduct / release / restore / findStocks）及管理端查询、调整、流水接口，详见 [`docs/modules/inventory.md`](docs/modules/inventory.md)。

订单与模拟支付已实现完整 MVP 主链路：`POST /api/v1/orders` 从购物车下单并锁定库存，`POST /api/v1/payments/orders/{orderNo}/pay` 模拟支付并扣减库存，`POST /api/v1/orders/{orderNo}/cancel` 取消待支付订单并释放库存。

## 文档

- [`CLAUDE.md`](CLAUDE.md)：开发规范
- [`docs/README.md`](docs/README.md)：文档索引
- [`docs/development-plan.md`](docs/development-plan.md)：阶段计划与验收记录
- [`docs/architecture.md`](docs/architecture.md)：工程结构与模块边界
- [`docs/database.md`](docs/database.md)：表、索引与 Flyway
- [`docs/api-design.md`](docs/api-design.md)：接口、错误码与权限
- [`docs/test-plan.md`](docs/test-plan.md)：测试矩阵与执行记录
- [`docs/modules/inventory.md`](docs/modules/inventory.md)：库存模块设计
- [`docs/modules/order.md`](docs/modules/order.md)：订单模块设计
- [`docs/modules/payment.md`](docs/modules/payment.md)：模拟支付模块设计

## 约定

- 不提交 `.env`、密钥、密码、Token 或本地环境配置。
- 数据库变更必须通过 Flyway；已执行迁移禁止修改。
- 未通过验收的后续阶段功能不提前实现。
