# MallAgent

MallAgent 是一个从零开发的 Java 电商后端，采用模块化单体架构，只有一个 Spring Boot 启动应用。业务设计参考 [macrozheng/mall](https://github.com/macrozheng/mall)，但不复制其代码、包结构或实现。

## 当前状态

阶段 0（工程基座）、阶段 1（用户、RBAC、JWT、收货地址）、阶段 2（商品）已完成；阶段 2 复核发现的 `product` 根权限、SKU 规格完整性、逻辑删除循环问题已修复（V8/V9 + 上架校验）；阶段 3（购物车）已实现，等待验收。

阶段 3 提供添加/合并数量、修改数量、删除、勾选/全选、购物车查询与金额汇总。MySQL 为事实来源，购物车状态直接查询 MySQL，商品信息实时读取商品模块，添加接口使用 `Idempotency-Key`。

订单、库存、支付等业务尚未开始。Python AI Agent 和 Vue 前端不在当前范围。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Java 21 |
| 框架 | Spring Boot 3.5.16 |
| 构建 | Maven 多模块 |
| ORM | MyBatis-Plus 3.5.18.1 |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3.13 |
| 认证授权 | Spring Security + JWT（JJWT 0.12.7） |
| 金额 | BigDecimal + DECIMAL(18,2) |
| 数据库迁移 | Flyway |
| API 文档 | Springdoc OpenAPI 2.10.0 |
| 本地基础设施 | Docker Compose |
| 辅助工具 | Lombok |

## 工程结构

```text
MallAgent/
├── mallagent-common/       # 统一响应、错误码、业务异常等通用能力
├── mallagent-framework/    # Web、安全、MyBatis-Plus、Redis、MQ、OpenAPI 配置
├── mallagent-module-user/    # 用户、RBAC、JWT 认证、收货地址
├── mallagent-module-product/ # 商品分类、品牌、SPU/SKU、规格、Redis 缓存
├── mallagent-module-cart/    # 购物车、幂等添加、MySQL 状态查询
├── mallagent-bootstrap/      # 启动类、健康检查、配置文件、Flyway 迁移
├── docker/mysql/init/      # MySQL 首次初始化脚本
├── docs/                   # 开发计划与设计文档
├── docker-compose.yml
├── CLAUDE.md               # 开发规范
└── pom.xml
```

业务模块按开发阶段逐步加入；当前已有 `mallagent-module-user`、`mallagent-module-product`、`mallagent-module-cart`，后续模块不提前创建。

## 环境要求

- JDK 21
- Maven 3.9+
- Docker Desktop，含 Docker Compose v2

## 快速开始

### 1. 准备基础环境

当前开发默认连接本机 MySQL 8（`localhost:3306`，`root/123456`）。首次使用需创建开发库和测试库：

```bash
mysql -uroot -p123456 -e "
CREATE DATABASE IF NOT EXISTS mallagent DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mallagent_test DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;"
```

Redis 和 RabbitMQ 仍需在 `localhost:6379`、`localhost:5672` 可用。

也可以改用 Docker Compose 启动全部基础设施：

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

PowerShell 可用 `Copy-Item .env.example .env`。Docker 方式会创建开发库 `mallagent`、测试库 `mallagent_test`、Redis 和 RabbitMQ；其 MySQL 映射在宿主机 `3307`，启动应用时需设置 `MYSQL_PORT=3307`、`MYSQL_USER=mallagent`、`MYSQL_PASSWORD=mallagent`。

### 2. 构建并执行测试

使用本机 MySQL（`localhost:3306`，`root/123456`）与本地 Redis、RabbitMQ：

```bash
mvn clean verify
```

使用 Docker Compose 的全部基础设施（MySQL 映射在宿主机 `3307`）时，需同时覆盖连接参数：

```bash
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD=mallagent mvn clean verify
```

PowerShell：

```powershell
$env:MYSQL_PORT="3307"; $env:MYSQL_USER="mallagent"; $env:MYSQL_PASSWORD="mallagent"; mvn clean verify
```

集成测试连接 `mallagent_test` 数据库（本机 `3306` 或 Docker Compose `3307`，由环境变量决定），以及 Redis database `1`、RabbitMQ。

### 3. 启动应用

```bash
mvn -pl mallagent-bootstrap -am package -DskipTests
java -jar mallagent-bootstrap/target/mallagent-bootstrap-0.0.1-SNAPSHOT.jar
```

也可以直接在 IDE 中运行 `com.mallagent.MallAgentApplication`。

### 4. 验证

- 健康检查：<http://localhost:8080/api/v1/system/health>
- OpenAPI 文档：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- RabbitMQ 管理台：<http://localhost:15672>（默认 `mallagent` / `mallagent`）

健康检查正常时返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "status": "UP",
    "application": "mallagent",
    "components": {
      "database": "UP",
      "redis": "UP",
      "rabbitmq": "UP"
    }
  },
  "traceId": "4f8c2c9e7b1a4d6c9e0f3a2b1c8d7e6f",
  "timestamp": "2026-10-30T20:00:00+08:00"
}
```

### 5. 初始管理员

首次启动且用户表中不存在 `admin` 时，系统会自动创建管理员：

- 用户名：`admin`
- 初始密码：`ADMIN_INIT_PASSWORD` 环境变量，未设置时为 `Admin@123456`

请首次登录后立即通过管理端重置密码。生产环境必须显式设置 `ADMIN_INIT_PASSWORD` 和 `JWT_SECRET`。

## 核心接口

| 接口 | 说明 | 认证 |
| --- | --- | --- |
| `POST /api/v1/auth/register` | 注册并返回令牌 | 公开 |
| `POST /api/v1/auth/login` | 登录 | 公开 |
| `POST /api/v1/auth/refresh` | 刷新令牌（旧 Refresh Token 一次性使用） | 公开 |
| `POST /api/v1/auth/logout` | 登出并撤销令牌 | Bearer |
| `GET/PUT /api/v1/users/me` | 当前用户信息与资料修改 | Bearer |
| `/api/v1/users/me/addresses/**` | 收货地址增删改查、设默认 | Bearer |
| `/api/v1/admin/users/**` | 用户分页、状态、重置密码、分配角色 | 对应权限码 |
| `/api/v1/admin/roles/**` | 角色 CRUD、角色权限分配 | 对应权限码 |
| `GET /api/v1/admin/permissions/tree` | 权限树 | `system:permission:read` |
| `/api/v1/admin/product/categories/**` | 商品分类管理 | `product:category:*` |
| `/api/v1/admin/product/brands/**` | 商品品牌管理 | `product:brand:*` |
| `/api/v1/admin/product/spus/**` | SPU CRUD、上下架、转草稿 | `product:spu:*` |
| `/api/v1/admin/product/spus/{spuId}/specs/**` | 规格与规格值 | `product:spec:*` |
| `/api/v1/admin/product/spus/{spuId}/skus/**` | SKU 管理 | `product:sku:*` |
| `GET /api/v1/products/categories/tree` | 用户端分类树 | 登录用户 |
| `GET /api/v1/products` | 用户端商品分页 | 登录用户 |
| `GET /api/v1/products/{id}` | 用户端商品详情 | 登录用户 |
| `POST /api/v1/cart/items` | 添加购物车（合并数量） | Bearer + `Idempotency-Key` |
| `PUT /api/v1/cart/items/{skuId}` | 修改购买数量 | Bearer |
| `DELETE /api/v1/cart/items/{skuId}` | 删除购物车条目 | Bearer |
| `PUT /api/v1/cart/items/{skuId}/selected` | 单条勾选/取消勾选 | Bearer |
| `PUT /api/v1/cart/selected` | 全选/取消全选 | Bearer |
| `GET /api/v1/cart` | 查询购物车与汇总 | Bearer |

完整接口与参数说明见 Swagger UI，商品模块设计详见 [docs/product-module.md](docs/product-module.md)。

## 商品模块摘要

- 商品状态：`DRAFT` → `ON_SHELF` → `OFF_SHELF`；`OFF_SHELF` 可重新上架或转回 `DRAFT`。
- 只有上架、分类启用、品牌可用且至少有一个启用 SKU 的商品对用户可见。
- 下架与删除不同：下架保留记录可编辑；删除为逻辑删除，查询不可见。
- 商品、SKU、规格使用关联表；SKU 规格组合由 `(spu_id, spec_signature, deleted)` 唯一约束防重。
- 金额使用 `BigDecimal`，数据库 `DECIMAL(18,2)`。
- 用户端分类树和商品详情使用 Redis 缓存，写库提交后失效；Key 与 TTL 见商品模块文档。
- 商品分页最大 `pageSize = 100`，搜索使用 MySQL `LIKE`。

## 购物车模块摘要

- MySQL `cart_item` 是事实来源；`(user_id, sku_id)` 唯一，重复添加由原子 upsert 合并数量。
- `cart_idempotency` 记录添加请求；`POST /api/v1/cart/items` 必须携带 `Idempotency-Key`，同一 key 相同请求不会重复累加。
- 购物车状态不缓存，查询直接读 MySQL；商品价格、名称、规格、上下架状态实时读取商品模块。
- 不可购买条目保留并标记；客户端不能传入价格或金额。
- 单 SKU 数量 1–99；购物车种类上限 100 为应用层软上限。
- 详细设计见 [docs/modules/cart.md](docs/modules/cart.md)。

## 本地服务

| 服务 | 地址 | 默认账号 |
| --- | --- | --- |
| MySQL（本机） | `localhost:3306` | `root` / `123456` |
| MySQL（Docker Compose） | `localhost:3307` | `mallagent` / `mallagent`；`root` / `root123456` |
| Redis | `localhost:6379` | 无密码 |
| RabbitMQ | `localhost:5672` | `mallagent` / `mallagent` |
| RabbitMQ 管理台 | `localhost:15672` | `mallagent` / `mallagent` |

默认值仅用于本地开发。`.env` 只被 Docker Compose 读取，不应提交到 Git。

当前应用默认使用本机 MySQL 3306。若改用 Docker Compose 的 MySQL（宿主机 3307），需同时设置应用环境变量 `MYSQL_PORT=3307`、`MYSQL_USER=mallagent`、`MYSQL_PASSWORD=mallagent`。

## 应用配置

应用读取环境变量，未设置时使用 `application.yml` 中的本地默认值：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 应用端口 |
| `MYSQL_HOST` | `localhost` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | MySQL 端口；使用 Docker Compose 时为 `3307` |
| `MYSQL_DATABASE` | `mallagent` | 开发库 |
| `MYSQL_USER` | `root` | MySQL 用户；使用 Docker Compose 时为 `mallagent` |
| `MYSQL_PASSWORD` | `123456` | MySQL 密码；使用 Docker Compose 时为 `mallagent` |
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `REDIS_DATABASE` | `0` | Redis 库 |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ 主机 |
| `RABBITMQ_PORT` | `5672` | RabbitMQ 端口 |
| `RABBITMQ_USER` | `mallagent` | RabbitMQ 用户 |
| `RABBITMQ_PASSWORD` | `mallagent` | RabbitMQ 密码 |
| `RABBITMQ_VHOST` | `/` | RabbitMQ 虚拟主机 |
| `JWT_SECRET` | 本地开发占位值 | JWT 签名密钥，至少 32 字节；生产必须设置 |
| `ADMIN_INIT_PASSWORD` | `Admin@123456` | 初始管理员密码，仅首次创建时使用 |

注意：`.env` 只被 Docker Compose 读取。修改应用连接信息时，需要同时给应用进程设置对应环境变量。

购物车配置位于 `application.yml`：

```yaml
mallagent:
  cart:
    max-quantity-per-item: 99
    max-items: 100              # 应用层软上限
```

## 数据库迁移

- 迁移脚本位置：`mallagent-bootstrap/src/main/resources/db/migration`
- Flyway 在应用启动时自动执行
- `V1__baseline.sql` 只创建基础设施表 `sys_config`
- `V2__create_user_rbac.sql` 创建用户、角色、权限、关联表和收货地址表
- `V3__seed_user_rbac.sql` 写入内置角色与权限；管理员账号由启动初始化器创建，密码不进入迁移脚本
- `V4__create_product.sql` 创建商品分类、品牌、SPU、SKU、规格及关联表
- `V5__seed_product_permissions.sql` 写入 `product:*` 权限并授予超级管理员/运营只读
- `V6__fix_product_sku_unique_indexes.sql` 将 SKU 唯一索引纳入 `deleted`，支持逻辑删除后重建
- `V7__create_cart.sql` 创建购物车条目表和添加幂等记录表
- `V8__grant_super_admin_product_root_permission.sql` 补授 SUPER_ADMIN 的 `product` 根权限
- `V9__fix_product_logical_delete_unique_indexes.sql` 使用生成列支持多次删除与同业务键重建
- 已执行的迁移脚本禁止修改，修正只能新增版本
- 每个阶段的业务表必须提供新的迁移脚本

## 测试

```bash
# 本机 MySQL / Redis / RabbitMQ
mvn clean verify

# Docker Compose 基础设施（MySQL 宿主机端口 3307）
MYSQL_PORT=3307 MYSQL_USER=mallagent MYSQL_PASSWORD=mallagent mvn clean verify
```

- 单元测试与集成测试均在 `mallagent-bootstrap` 中执行
- 集成测试依赖 MySQL、Redis、RabbitMQ，可使用本机服务或 Docker Compose
- 测试库为 `mallagent_test`（本机 `3306` 或 Docker Compose `3307`），Redis database `1`，与开发环境隔离
- 当前共 60 个测试，覆盖健康检查、注册登录、令牌刷新/登出、登录失败锁定、RBAC、权限控制、地址归属与默认地址
- 商品测试覆盖分类层级/删除约束、品牌、SPU/SKU/规格、状态机、可见性、缓存失效、分页上限、`product:*` 权限，以及根权限、规格完整性和逻辑删除循环
- 购物车测试覆盖幂等重试、重复添加合并、数量上限、勾选/全选、改价、下架、SKU 删除、用户隔离、客户端伪造价格和并发添加

如果测试库不存在，可手动创建（本机 MySQL）：

```bash
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS mallagent_test DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;"
```

使用 Docker Compose 时改为：

```bash
docker compose exec mysql mysql -uroot -proot123456 -e "CREATE DATABASE IF NOT EXISTS mallagent_test DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON mallagent_test.* TO 'mallagent'@'%'; FLUSH PRIVILEGES;"
```

## 日志与链路

- 每个请求生成或透传 `X-Trace-Id`
- 响应体和日志均带 `traceId`
- 统一异常处理不会向客户端暴露堆栈

## 开发约定

详见 [CLAUDE.md](CLAUDE.md) 和 [docs/development-plan.md](docs/development-plan.md)。

- 用户是技术负责人，Claude 负责编码
- 不擅自修改技术栈、不提前实现后续模块
- 每阶段完成后执行测试并如实汇报
- 不自动提交 Git
