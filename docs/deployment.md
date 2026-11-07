# MallAgent 运行与部署说明

本文覆盖 Windows 本地开发、Docker 完整部署、端口冲突处理、日志排查和浏览器验收。所有命令默认在项目根目录执行。

## 1. 两种运行模式

| 模式 | 后端 | 前端 | 数据库 / 中间件 | 适用场景 |
| --- | --- | --- | --- | --- |
| Windows 本地开发 | Java 进程 `localhost:8080` | Vite `localhost:5173` | Docker MySQL `3307`、Redis `6379`、RabbitMQ `5672` | 日常开发、IDEA 调试 |
| Docker 完整部署 | `mallagent-backend`，宿主机 `8081` | Nginx + 静态资源，宿主机 `8088` | Compose 内部 `mysql:3306`、`redis:6379`、`rabbitmq:5672` | 演示、交付验收 |

Docker 完整部署故意不占用 `8080` 和 `5173`，因此可以和本地开发模式同时运行。浏览器访问 Docker 前端 `8088` 时，Nginx 自动把 `/api/` 转发到 Compose 内部后端。

## 2. 环境要求

- Windows 10/11 + PowerShell 5.1 或 PowerShell 7
- JDK 21（本地模式）
- Maven 3.10+（本地模式）
- Node.js 20+ 与 npm（本地模式）
- Docker Desktop（两种模式都需要 MySQL/Redis/RabbitMQ）

## 3. Windows 本地开发

### 3.1 一键启动

```powershell
Copy-Item .env.example .env
# 按需修改 .env；不要提交 .env
powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

脚本会：

1. 检查 `8080` 和 `5173`。
2. 如果端口已被 MallAgent 自身占用，会提示“已在运行”，不会结束进程。
3. 如果端口被其他程序占用，会明确报错并退出，不会强制结束其他程序。
4. 启动 Docker 中的 MySQL、Redis、RabbitMQ。
5. 构建并启动后端 JAR。
6. 安装缺失的前端依赖并启动 Vite。
7. 等待后端健康检查和前端页面可访问。

启动后可访问：

- 商城前端：`http://localhost:5173`
- 后端健康检查：`http://localhost:8080/api/v1/system/health`
- Swagger：`http://localhost:8080/swagger-ui.html`

日志：

```text
.run\backend.log
.run\backend.err.log
.run\backend-build.log
.run\frontend.log
.run\frontend.err.log
```

停止本地开发进程：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

同时停止 Docker 基础设施（不删除容器和数据卷）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1 -StopInfrastructure
```

### 3.2 保留 IDEA + npm run dev 的开发方式

后端可直接在 IDEA 中运行 `MallAgentApplication`。需要设置：

```text
MYSQL_HOST=localhost
MYSQL_PORT=3307
MYSQL_DATABASE=mallagent
MYSQL_USER=mallagent
MYSQL_PASSWORD=<.env 中的值>
REDIS_HOST=localhost
REDIS_PORT=6379
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=mallagent
RABBITMQ_PASSWORD=<.env 中的值>
JWT_SECRET=<.env 中的值>
ADMIN_INIT_PASSWORD=<.env 中的值>
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

IDEA 和 `npm run dev` 启动的服务不会被 `stop-dev.ps1` 误杀，因为脚本只处理自己写入 `.run\*.pid` 的进程。

## 4. Docker 完整部署

### 4.1 准备环境变量

```powershell
Copy-Item .env.example .env
```

至少确认：

```dotenv
MYSQL_ROOT_PASSWORD=...
MYSQL_PASSWORD=...
RABBITMQ_PASSWORD=...
JWT_SECRET=至少32字节的随机值
ADMIN_INIT_PASSWORD=...
DOCKER_BACKEND_PORT=8081
FRONTEND_PORT=8088
```

`.env` 已被 `.gitignore` 忽略。镜像不包含这些值，全部在运行时注入。

### 4.2 一键构建并启动

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-docker.ps1
```

等价命令：

```powershell
docker compose up -d --build --wait
```

首次启动会构建后端和前端多阶段镜像：

- 后端构建阶段使用 Maven + JDK 21，运行阶段只保留 JRE、curl 和 JAR。
- 前端构建阶段使用 Node 22，运行阶段只保留 Nginx 和 `dist/` 静态资源。
- 最终镜像不包含源代码、Maven 仓库、Node 依赖或开发工具。

访问地址：

- 商城：`http://127.0.0.1:8088`
- 管理后台：`http://127.0.0.1:8088/admin/products`
- 后端健康检查：`http://127.0.0.1:8081/api/v1/system/health`
- Swagger：`http://127.0.0.1:8081/swagger-ui.html`

所有 Compose 发布端口默认只绑定 `127.0.0.1`，避免数据库、Redis、RabbitMQ 管理页和后端 Swagger 暴露到局域网。需要局域网访问时，明确修改 `docker-compose.yml` 的端口绑定，并同时收紧防火墙和密码策略。

Nginx 配置位于 `frontend/nginx.conf`：

- SPA 路由全部回落到 `index.html`，刷新 `/admin/after-sales` 等深链接不会 404。
- `/api/` 反向代理到 Compose 内部 `backend:8080`。

### 4.3 停止、重启和日志

停止服务但保留容器与数据卷：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-docker.ps1
# 等价：docker compose stop
```

重新启动：

```powershell
docker compose start
# 或重新执行 start-docker.ps1
```

查看日志：

```powershell
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
docker compose ps
```

禁止使用 `docker compose down -v` 删除 MySQL 数据卷。

### 4.4 Docker 镜像网络问题

如果 Docker Hub 无法访问，可在 `.env` 中指定镜像站：

```dotenv
MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21
JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-jammy
NODE_IMAGE=docker.m.daocloud.io/library/node:22-alpine
NGINX_IMAGE=docker.m.daocloud.io/library/nginx:1.27-alpine
```

然后重新执行：

```powershell
docker compose up -d --build --wait
```

## 5. 演示商品初始化

演示数据包含 6 个分类、60 件商品、60 个启用 SKU、初始库存和 `ADMIN_ADJUST` 库存流水。脚本不是 Flyway 迁移，不会在生产启动时自动运行。

```powershell
# Docker MySQL（默认）
powershell -ExecutionPolicy Bypass -File .\scripts\load-demo-data.ps1

# 本机 mysql 客户端（连接 .env 的 MYSQL_HOST/MYSQL_PORT）
powershell -ExecutionPolicy Bypass -File .\scripts\load-demo-data.ps1 -Local
```

幂等规则：

- 使用固定 `DEMO-DIGI-*`、`DEMO-HOME-*` 等 SKU 码判断商品是否已存在。
- 已存在商品不会重复创建，不会重复初始化库存或流水。
- 分类按根分类名称幂等创建。
- 演示图片位于 `frontend/public/demo-products/`，是项目自有 SVG 资源，不依赖第三方图床。
- 脚本执行后会清理 Redis 分类树缓存。

## 6. 初始化数据库和管理员

- Flyway 在后端启动时自动执行 V1–V16。
- Docker MySQL 数据保存在 `mallagent-mysql-data` 卷；`docker compose stop`、`restart` 和重新部署都不会删除。
- 首次启动时 `AdminAccountInitializer` 使用 `ADMIN_INIT_PASSWORD` 创建 `admin` 账号。
- 已存在的 `admin` 账号不会因再次启动而被重置密码。
- `.env.example` 中的 `admin / Admin@123456` 和示例 JWT_SECRET 仅用于本地演示；生产环境必须替换。
- 生产或局域网部署建议设置 `SPRINGDOC_API_DOCS_ENABLED=false`、`SPRINGDOC_SWAGGER_UI_ENABLED=false`，并收紧防火墙。发布端口默认只绑定 `127.0.0.1`。

## 7. 端口冲突

| 端口 | 用途 |
| --- | --- |
| 8080 | Windows 本地后端 |
| 5173 | Windows 本地 Vite |
| 8081 | Docker 后端映射到宿主机 |
| 8088 | Docker 前端 Nginx 映射到宿主机 |
| 3307 | Docker MySQL 映射到宿主机 |
| 6379 | Redis |
| 5672 / 15672 | RabbitMQ / 管理页面 |

Docker 端口默认绑定到 `127.0.0.1`，不会监听局域网地址。

`start-dev.ps1` 和 `start-docker.ps1` 都会检查相关端口：

- 端口已被 MallAgent 自身占用：提示已运行或继续使用，不结束进程。
- 端口被其他程序占用：报错并停止脚本，不执行 `taskkill` 或 `Stop-Process`。

手工排查：

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,5173,8081,8088
Get-Process -Id <PID>
```

## 8. 浏览器验收步骤

建议使用 Edge/Chrome 实际操作，不以 HTTP 200 代替页面验收。

1. 打开首页，检查 Hero Banner、六大分类入口、精选好物、新品推荐和全部商品区域；桌面与移动端均无横向滚动。
2. 未登录直接打开商品列表和商品详情，可以浏览商品与公开评价；点击收藏、立即购买或加入购物车会跳转登录页。
3. 注册新用户，登录后进入商城。
4. 搜索商品，点击商品卡片收藏按钮；刷新页面后收藏状态仍为已收藏。
5. 打开商品详情，加入购物车或立即购买；在地址页新增地址。
6. 购物车勾选商品，进入结算，提交订单。
7. 订单详情点击“模拟支付”，状态变为待发货。
8. 退出用户，使用 admin 登录，进入 `/admin/products`。
9. 新增商品并填写初始 SKU；编辑副标题；在列表上架；在库存页调整库存。
10. 在 `/admin/orders` 搜索订单号，进入详情填写快递公司和物流单号并确认发货。
11. 退出管理员，重新登录用户；刷新订单详情，物流信息仍存在；点击确认收货。
12. 已完成订单中点击“评价商品”，选择星级并提交；商品详情评价汇总增加。
13. 已完成订单点击“申请售后”，填写原因提交；退出后管理员在 `/admin/after-sales` 审核通过并确认退货退款。
14. 用户刷新售后详情和订单详情，确认售后已完成、退款单号存在；普通用户直接访问 `/admin/products` 会被重定向，管理 API 返回 403。

## 9. 当前联调限制

- 无真实支付、真实物流、优惠券、部分退款、换货和 AI Agent。
- 浏览器自动化使用独立 Headless Edge + CDP 执行，不依赖用户日常浏览器窗口。
- 手工验收时如果浏览器缓存了旧前端，`Ctrl+F5` 强制刷新即可。
