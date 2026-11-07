# MallAgent

MallAgent 是一个从零开发的 MVP 电商项目：Java 后端采用**模块化单体**架构（一个 Spring Boot 进程、一个启动类、一个 MySQL 库），前端使用 **Vue 3 + Vite**。后端当前为**单 Maven 模块**，根目录即启动工程；前端位于 `frontend/`。

当前进度：阶段 0–4 已通过验收；阶段 6/7 的 MVP 简化版（订单、模拟支付）已实现；Vue 用户端和商品/订单/售后/库存管理后台已实现；已完成订单 30 分钟超时自动取消、支付倒计时、简化发货与确认收货、个人中心、商品收藏、商品评价，以及整单售后申请、管理员审核、模拟退款与库存回补。已完成 Windows 一键启停、Docker 前后端多阶段构建、Nginx 反向代理和真实浏览器端到端联调。首页已升级为包含 Hero、六大分类、精选、新品和全部商品的现代商城展示，并内置 60 件可重复初始化的演示商品与自有 SVG 主图。后端全量测试 167 个，0 失败、0 错误、0 跳过；前端 13 个 Vitest 测试通过。优惠券、复杂物流和部分售后未开始。

> 交付定位：本项目达到**本地演示版 / 学习项目正式交付标准**，不是生产就绪系统。生产部署前必须替换演示密码和示例密钥、关闭不必要的 Swagger、配置 HTTPS、备份和监控。

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
│       ├── order/              # 订单、订单明细、发货、确认收货
│       ├── payment/            # 模拟支付与模拟退款
│       ├── aftersale/          # 整单售后、审核与退货退款
│       ├── favorite/           # 商品收藏
│       └── review/             # 商品评价
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/           # Flyway V1–V16
├── src/test/java/com/mallagent/
├── src/test/resources/application-test.yml
├── Dockerfile                  # 后端多阶段镜像
├── docker-compose.yml          # MySQL、Redis、RabbitMQ、后端、前端
├── docker/mysql/init/
├── scripts/                    # Windows PowerShell 一键启停与演示数据
│   ├── start-dev.ps1 / stop-dev.ps1
│   ├── start-docker.ps1 / stop-docker.ps1
│   ├── load-demo-data.ps1
│   └── demo-data.sql
├── frontend/                   # Vue 3 + Vite 前端
│   ├── Dockerfile / nginx.conf # 前端多阶段镜像与反向代理
│   ├── public/demo-products/   # 60 张项目自有 SVG 演示主图
│   ├── src/api/                # Axios 封装与后端 API
│   ├── src/components/         # 通用组件
│   ├── src/views/              # 登录、商品、购物车、结算、订单、售后、地址、个人中心、收藏、评价
│   ├── src/views/admin/        # 管理后台：商品、分类、SKU、库存、订单、售后
│   ├── src/router/             # 路由与登录守卫
│   ├── src/stores/             # Pinia 认证状态
│   └── vite.config.js          # /api 代理到 localhost:8080
└── docs/                       # 设计、数据库、API、测试与部署
```

模块是 `com.mallagent.*` 包边界，不是 Maven 子模块；跨模块只通过对方 `api` 包交互。

## 环境要求

- JDK 21
- Maven 3.10+
- Node.js 20+ 与 npm
- Docker Desktop（本地 MySQL、Redis、RabbitMQ）

## 快速开始

完整说明见 [`docs/deployment.md`](docs/deployment.md)。

### Windows 本地开发（一键）

```powershell
Copy-Item .env.example .env
# 按需修改 .env；不要提交 .env
powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

脚本会启动 Docker 中的 MySQL/Redis/RabbitMQ、构建并启动后端 JAR、安装缺失依赖并启动 Vite。端口被 MallAgent 自身占用时只提示已运行；被其他程序占用时直接报错，不会结束其他进程。

- 商城：`http://localhost:5173`
- 后端健康检查：`http://localhost:8080/api/v1/system/health`
- Swagger：`http://localhost:8080/swagger-ui.html`
- 日志：`.run\backend.log`、`.run\frontend.log`

停止：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
# 同时停止 Docker 基础设施（保留容器和数据卷）
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1 -StopInfrastructure
```

仍然可以在 IDEA 中运行 `MallAgentApplication`，并在 `frontend` 目录执行 `npm run dev`；所需环境变量见 `docs/deployment.md`。

### Docker 完整部署

```powershell
Copy-Item .env.example .env
# 修改 MySQL/RabbitMQ 密码、JWT_SECRET、ADMIN_INIT_PASSWORD
powershell -ExecutionPolicy Bypass -File .\scripts\start-docker.ps1
```

等价命令：

```powershell
docker compose up -d --build --wait
```

- 商城前端：`http://127.0.0.1:8088`
- 管理后台：`http://127.0.0.1:8088/admin/products`
- 后端（仅用于调试）：`http://127.0.0.1:8081/api/v1/system/health`
- Swagger：`http://127.0.0.1:8081/swagger-ui.html`
- 前端 Nginx 将 `/api/` 反向代理到 Compose 内部后端 `backend:8080`。
- MySQL 数据使用 `mallagent-mysql-data` 持久化卷；不要执行 `docker compose down -v`。

停止、日志和镜像站配置：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-docker.ps1
docker compose ps
docker compose logs -f backend frontend mysql
```

Docker Hub 不可达时，在 `.env` 中把 `MAVEN_IMAGE`、`JRE_IMAGE`、`NODE_IMAGE`、`NGINX_IMAGE` 改为可用镜像站地址后重新构建。

前端页面：

- 登录、注册
- 商城首页：Hero Banner、六大分类、精选好物、新品推荐、全部商品与完整页脚
- 商品列表、商品详情、SKU 选择、立即购买、收藏、加入购物车
- 购物车：修改数量、删除、勾选、全选
- 结算：选择收货地址、查看后端计算的商品与金额、提交订单
- 我的订单（待支付、待发货、待收货、已完成、已取消）、订单详情
- 模拟支付、取消未支付订单、已发货订单确认收货
- 收货地址查看、新增、编辑、删除、设为默认
- 个人中心：查看和修改昵称、手机号、邮箱，快捷进入订单、地址、收藏与评价
- 商品收藏：列表/详情收藏按钮、我的收藏、取消收藏，下架/删除商品保留不可用提示
- 商品评价：已完成订单按 SKU 评价、1～5 星、商品详情评分汇总与评价列表、我的评价
- 售后：已完成订单申请整单退货退款、我的售后列表/详情、拒绝后重新申请
- 管理后台：商品分页/搜索/上下架、新增/编辑商品、规格与 SKU 维护、分类管理、订单查询/详情/发货、售后查询/详情/审核/确认退货退款、库存查询/调整、库存流水查询

管理后台入口：使用具有商品、订单、售后或库存权限的账号（SUPER_ADMIN / OPERATOR）登录后，页面顶部出现“管理后台”，或直接访问 `http://localhost:5173/admin/products`。SUPER_ADMIN 可执行有权限的写操作；OPERATOR 为只读；普通用户访问后台会被重定向，所有接口最终仍由后端 `@PreAuthorize` 校验。

## 演示商品数据

项目自带 6 个分类、60 件商城演示商品和 60 张本地 SVG 演示主图。初始化脚本不进入 Flyway，生产环境不会自动执行。

```powershell
# 默认写入运行中的 mallagent-mysql 容器
powershell -ExecutionPolicy Bypass -File .\scripts\load-demo-data.ps1

# 使用本机 mysql 客户端，连接 .env 中的 MYSQL_HOST/MYSQL_PORT
powershell -ExecutionPolicy Bypass -File .\scripts\load-demo-data.ps1 -Local
```

- SQL：[`scripts/demo-data.sql`](scripts/demo-data.sql)
- 商品图片：[`frontend/public/demo-products/`](frontend/public/demo-products/)
- 固定 SKU 码作为幂等锚点；重复执行不会重复创建商品，也不会重复增加库存。
- 初始库存同时写入 `stock_sku` 和 `ADMIN_ADJUST` 库存流水，保持库存与流水一致。
- 脚本会清理商品分类树 Redis 缓存，执行后首页分类立即生效。
- 已有浏览器测试商品已下架但未删除，历史订单/评价/收藏仍保留。

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
| `ADMIN_INIT_PASSWORD` | 首次启动的管理员初始密码；已有 admin 不会重置 |
| `DOCKER_BACKEND_PORT` | Docker 后端映射端口，默认 8081，避免与本地 8080 冲突 |
| `FRONTEND_PORT` | Docker 前端映射端口，默认 8088，避免与本地 5173 冲突 |
| `MAVEN_IMAGE` / `JRE_IMAGE` | 后端多阶段构建镜像，可替换为镜像站地址 |
| `NODE_IMAGE` / `NGINX_IMAGE` | 前端多阶段构建镜像，可替换为镜像站地址 |

数据库结构由 Flyway 在后端启动时自动执行 V1–V16。Docker MySQL 使用 `mallagent-mysql-data` 卷；管理员默认用户名为 `admin`，密码来自 `ADMIN_INIT_PASSWORD`，已有账号不会因重启重置。

订单超时配置位于 `application.yml` 的 `mallagent.order`：`timeout-minutes`（默认 30）、`timeout-scan-interval-ms`（默认 60000）和 `timeout-batch-size`（默认 100）。

其余默认值见 `src/main/resources/application.yml`；不要在生产环境使用本地开发默认值。

### 安全提示

- `.env.example` 中的 `Admin@123456`、示例 JWT_SECRET 和 MySQL/RabbitMQ 密码仅用于本地演示，生产环境必须替换。
- Docker Compose 强制要求 `.env` 提供数据库、RabbitMQ、JWT 和管理员密码；不会把密码写进镜像。
- Docker 发布端口默认只绑定 `127.0.0.1`，不会把 MySQL、Redis、RabbitMQ 管理页和 Swagger 暴露到局域网。
- 生产或局域网部署建议设置 `SPRINGDOC_API_DOCS_ENABLED=false`、`SPRINGDOC_SWAGGER_UI_ENABLED=false`。
- 首次使用管理员账号后应立即修改密码；已有 admin 账号不会因重启重新初始化。
- JWT 存放在浏览器 localStorage，属于单页应用常见方案，但仍需防范 XSS。

## 测试

```bash
docker compose up -d --wait

# 已有测试库；密码由本地环境注入
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD="$MYSQL_PASSWORD" mvn clean verify

# 空库迁移与全量测试；测试库需提前创建并授权
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD="$MYSQL_PASSWORD" \
MYSQL_TEST_DATABASE=mallagent_admin_verify mvn clean verify
```

前端：

```bash
cd frontend
npm test
npm run build
```

测试库与开发库隔离，测试 profile 使用 Redis database `1`。测试框架不使用 Testcontainers。

最近一次验收结果：

```text
后端：Tests run: 167, Failures: 0, Errors: 0, Skipped: 0
前端：4 个测试文件、13 个 Vitest 测试通过
前端：npm run build 成功
Docker：5 个服务全部 healthy，Flyway V16
浏览器：Headless Edge + CDP 完成 19 项真实页面链路
```

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
| 优化 | 订单超时自动取消、支付倒计时、库存并发稳定性 | ✅ 第一轮优化（2026-11-05） |
| 管理后台 | 商品/分类/SKU/库存管理，JWT + RBAC 权限控制 | ✅ 第二轮（2026-11-05） |
| 8 | 简化履约：管理端发货、用户确认收货、物流信息展示 | ✅ 第三轮（2026-11-06） |
| 10 | 个人中心、商品收藏、商品评价 | ✅ 第四轮（2026-11-06，点赞等未实现） |
| 9 | 简化售后：整单退货退款、管理员审核、模拟退款、库存回补 | ✅ 第五轮（2026-11-06） |
| 5 | 优惠券、部分退款/换货等复杂售后 | ⬜ 未开始 |
| 11 | 收尾加固 | ⬜ 计划中 |
| 12 | Vue 前端 MVP | ✅ 已实现并完成构建验证（2026-11-02） |

库存模块提供 `InventoryStockApi`（lock / deduct / release / restore / findStocks）及管理端查询、调整、流水接口，详见 [`docs/modules/inventory.md`](docs/modules/inventory.md)。

订单与模拟支付已实现完整 MVP 主链路：`POST /api/v1/orders` 从购物车下单并锁定库存，`POST /api/v1/payments/orders/{orderNo}/pay` 模拟支付并扣减库存，`POST /api/v1/orders/{orderNo}/cancel` 取消待支付订单并释放库存。

订单状态为 `PENDING_PAYMENT → PAID → SHIPPED → COMPLETED`，并保留 `PENDING_PAYMENT → CANCELLED`。管理端通过 `POST /api/v1/admin/orders/{orderNo}/ship` 保存快递公司和物流单号，用户通过 `POST /api/v1/orders/{orderNo}/confirm-receipt` 确认收货；发货与确认收货不再修改库存。相同物流信息重复发货、重复确认收货幂等；不同物流信息重复发货返回 409，不覆盖首次记录。

简化售后支持已完成订单整单退货退款：用户提交申请，管理员审核通过并确认收到退货后，在同一事务内写入模拟退款记录、调用 `InventoryStockApi.restore` 按售后单号回补库存并标记 `COMPLETED`。拒绝售后不退款、不回补库存；重复完成幂等，不同售后单不会重复回补同一订单。原订单保持 `COMPLETED`，履约记录不被改写。

待支付订单超过 30 分钟会由后端定时任务自动取消并释放库存；支付接口在订单行锁内校验支付截止时间，截止后返回 409 `17005`，不会扣减库存。前端订单列表/详情显示剩余支付时间，超时后自动刷新状态。

## 已知限制与后续扩展

### 已知限制

- 支付、物流和售后为本地模拟：不接真实支付平台、真实快递轨迹；售后仅支持已完成订单整单退货退款。
- 未实现优惠券、运费、发票、部分退款、部分退货、换货、图片评价、评价回复和评论审核。
- 管理后台未实现品牌、用户、角色权限管理页面；品牌仅在商品表单中选择。
- 商品图片使用 URL，不提供文件上传和对象存储。
- JWT 存放在 localStorage，需通过 CSP、XSS 防护和前端依赖治理降低风险。
- 默认管理员密码和示例 JWT_SECRET 仅用于本地演示；生产必须替换，并建议关闭 Swagger。
- Docker 发布端口默认只绑定 `127.0.0.1`；没有 HTTPS、域名、WAF、日志轮转和自动备份。
- 浏览器 E2E 为独立 Headless Edge 手工执行脚本，尚未接入 CI。
- 当前环境 Docker Hub 直连不稳定，重建镜像可能需要镜像站参数。

### 后续扩展建议

1. 优惠券、运费规则和订单价格明细。
2. 真实支付/退款适配、对账和支付回调幂等。
3. 物流轨迹事件、部分发货、确认收货和售后物流。
4. 部分退款、换货、售后审核工作流和凭证图片。
5. 图片上传、对象存储、CDN 和搜索服务。
6. CI/CD、HTTPS、可观测性、日志轮转、数据库备份和恢复演练。
7. 刷新令牌轮换、CSP、安全响应头和更细粒度权限审计。

## 文档

- [`CLAUDE.md`](CLAUDE.md)：开发规范
- [`docs/README.md`](docs/README.md)：文档索引
- [`docs/deployment.md`](docs/deployment.md)：Windows 一键启停、Docker 部署与浏览器验收
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
