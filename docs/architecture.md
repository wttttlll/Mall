# MallAgent 系统架构设计

> 适用范围：阶段 0–11。本文记录包边界、依赖规则、数据所有权和横切能力；具体业务设计见 `docs/modules/`。
> 最后更新：2026-11-02（阶段 6/7 MVP 订单与模拟支付）。

## 1. 架构形态

- 模块化单体：一个 Spring Boot 进程、一个启动类、一个 MySQL 库。
- 模块间是进程内方法调用；不引入 RPC、注册中心、API 网关。
- 同一数据库内的多表写操作使用本地事务；不引入分布式事务。
- 跨模块异步协作使用 RabbitMQ，接受最终一致，必须幂等与补偿。
- 数据库是业务事实来源；Redis 只做缓存。购物车状态当前直接查询 MySQL，不使用 Redis 缓存。

## 2. 单 Maven 模块与包边界

工程已合并为**一个 Maven 模块**：根 `pom.xml` 的 `artifactId=mallagent`、`packaging=jar`，没有子模块 `pom.xml`。源码统一在根 `src/main/java`、`src/test/java`；“模块”在这里指 `com.mallagent.*` 包边界，不再是 Maven 子模块。

```text
com.mallagent.bootstrap
      ↓
com.mallagent.module.user / product / cart / inventory / ...
      ↓
com.mallagent.framework
      ↓
com.mallagent.common
```

当前包：

| 包 | 职责 | 阶段 |
| --- | --- | --- |
| `com.mallagent.common` | 统一响应、分页、错误码、业务异常 | 0 |
| `com.mallagent.framework` | Web、安全、MyBatis-Plus、Redis、RabbitMQ、OpenAPI、异常处理 | 0 |
| `com.mallagent.bootstrap` | 启动类、系统健康检查、全局配置、Flyway、打包 | 0 |
| `com.mallagent.module.user` | 用户、RBAC、JWT、收货地址 | 1 |
| `com.mallagent.module.product` | 分类、品牌、SPU/SKU、规格、状态机、商品查询 | 2 |
| `com.mallagent.module.cart` | 购物车、幂等添加、MySQL 状态查询 | 3 |
| `com.mallagent.module.inventory` | 库存锁定/扣减/释放/回补、流水、管理端调整与查询 | 4 |
| `com.mallagent.module.order` | 购物车下单、库存锁定、订单列表/详情、取消释放 | 6 |
| `com.mallagent.module.payment` | 模拟支付成功、库存扣减、订单支付状态联动 | 7 |

后续业务包按阶段加入 `com.mallagent.module.*`，不提前创建。

依赖规则：

- `common` 不依赖业务包，尽量不依赖 Spring。
- `framework` 只依赖 `common`，不反向依赖业务包。
- 业务包只能依赖 `framework`、`common` 和必要的其他业务包 `api` 子包。
- 跨包只能通过对方 `api` 包的接口与 DTO，禁止直接引用 Mapper、实体、Service 实现。
- 禁止包间循环依赖，禁止跨业务包直接读写对方数据库表。

> 历史说明：阶段 0–3 曾使用 Maven 多模块布局；阶段 4 步骤 7 前已合并为单 Maven 模块。旧 `mallagent-*` 子模块目录不再存在，历史文档中的模块名称仅表示包边界。

## 3. 业务包内结构

```text
com.mallagent.<module>
├── api/          # 跨包契约：接口 + DTO（按需）
├── controller/   # HTTP 入口，只做校验、转换、调用 service
├── service/      # 业务编排（接口 + impl，按实际需要）
├── entity/       # MyBatis-Plus 实体
├── enums/        # 枚举、状态
├── error/        # 包错误码
├── mapper/       # MyBatis-Plus Mapper
├── dto/          # 请求/响应对象
├── repository/   # Redis 等非数据库存储（按需）
├── converter/    # DTO 与实体转换（按需）
└── config/       # 包配置
```

原则：按需创建包，不建空包；不为分层而分层；业务规则不能散落在 Controller。

## 4. 跨包契约示例

购物车需要读取商品信息，但不修改商品规则：

```text
com.mallagent.module.product.api.ProductSkuQueryApi
com.mallagent.module.product.api.dto.SkuSnapshot
com.mallagent.module.product.api.dto.SkuAvailability
com.mallagent.module.product.service.ProductSkuQueryApiImpl   # 只读实现
```

- `SkuSnapshot` 包含 skuId、spuId、skuCode、spuName、imageUrl、price、specText、availability。
- 购物车只依赖接口，不感知商品 Mapper 与实体。
- 商品包现有 Service、状态机、可见性规则保持不变。

库存向订单、支付、售后提供进程内 API：

```text
com.mallagent.module.inventory.api.InventoryStockApi
com.mallagent.module.inventory.api.dto.StockCommand
com.mallagent.module.inventory.api.dto.StockRestoreCommand
com.mallagent.module.inventory.api.dto.StockSnapshot
```

- 订单/支付/售后只依赖 `InventoryStockApi`，禁止直接引用库存 Mapper、实体或 Service 实现。
- 库存 API 使用 `REQUIRED` 传播，加入调用方事务；调用方负责订单状态机、商品可购买校验和有限重试。
- 管理端库存接口位于 `com.mallagent.module.inventory.controller`，通过 HTTP 暴露。

订单与支付契约（MVP 简化版已实现）：

```text
com.mallagent.module.cart.api.CartCheckoutApi          # 订单锁定/清理购物车
com.mallagent.module.user.api.UserAddressQueryApi      # 订单读取收货地址快照
com.mallagent.module.order.api.OrderPaymentApi        # 支付锁定订单、回写已支付
```

- 订单通过 `ProductSkuQueryApi` 重新读取当前价格，通过 `InventoryStockApi.lock/release` 锁定/释放库存。
- 支付通过 `InventoryStockApi.deduct` 扣减库存，通过 `OrderPaymentApi` 读取订单和更新状态。
- 购物车、用户地址、订单、支付之间不直接访问对方 Mapper/Entity/Service。

后续物流、售后模块需要通过订单状态机协作时，再增加对应 `api` 契约，不提前创建。

## 5. 数据所有权

| 数据 | 事实来源 | 缓存/副本 | 说明 |
| --- | --- | --- | --- |
| 用户、角色、权限、地址 | MySQL | 无 | 普通业务数据 |
| 商品、SKU、规格 | MySQL | Redis 缓存分类树、商品详情 | 缓存失效后回源 |
| 购物车条目 | MySQL | 无 | 查询直接读 MySQL，商品信息实时读取 |
| 库存余额与库存流水 | MySQL | 无 | `stock_sku` 为当前状态，`stock_flow` 为不可变流水；不使用 Redis |
| 订单、订单明细、模拟支付单 | MySQL | 无 | 订单与支付状态以 MySQL 为准；库存扣减/释放由订单事务驱动 |
| 优惠券（未实现） | MySQL | 仅辅助缓存 | MVP 阶段不使用 Redis 作为权威数据 |
| 幂等记录 | MySQL | 无 | 唯一索引是最终防线 |

Redis Key 统一格式：`mallagent:{module}:{biz}:{id}`。缓存必须设置 TTL。

## 6. 横切能力

- 安全：JWT 无状态认证，Spring Security 过滤链，方法级权限；商品列表/详情/公开评价允许未登录浏览，其余用户端接口默认要求登录。管理后台前端按 `/users/me` 返回的权限码显示入口和按钮，但真正权限始终由后端 `@PreAuthorize` 校验；普通用户访问 `/admin` 会被前端重定向，后端仍返回 403。
- 统一响应：`Result<T>`，业务码 0 表示成功；HTTP 状态码保留语义。
- 异常：`BusinessException` + `ErrorCode`，全局异常处理不暴露堆栈。
- 链路：`traceId` 贯穿响应与日志。
- 数据库迁移：Flyway，禁止修改已执行脚本。
- 缓存：Cache-Aside，写库提交后删除缓存，TTL 兜底。
- 消息：生产端确认、消费端手动 ACK、失败重试进入死信、消费幂等。
- 定时任务：Spring Scheduling；订单超时扫描基于数据库 `created_at`，逐单使用订单行锁和状态复核，多实例扫描不会重复释放库存。

## 7. 部署形态

- 本地：Docker Compose 提供 MySQL 8、Redis 7、RabbitMQ 3.13；Windows 脚本启动 Java JAR 和 Vite，保留 IDEA + `npm run dev` 方式。
- Docker 完整部署：Compose 增加后端多阶段镜像（Maven 构建 + JRE 运行）和前端多阶段镜像（Node 构建 + Nginx 静态资源）；宿主机默认前端 `8088`、后端 `8081`，Nginx 将 `/api/` 反向代理到 `backend:8080`，SPA 路由回落到 `index.html`。
- 后端：单个 Spring Boot 可执行 JAR；开发库与测试库隔离。
- 前端：`frontend/` 下的 Vue 3 + Vite 应用，包含用户端与管理后台（`/admin/**`）。开发时 Vite `5173` 通过 `/api` 代理到后端 `8080`；生产构建输出 `frontend/dist`，由反向代理把 `/api` 转发到后端。
- 生产演进：可在不改业务模块边界的前提下，将 Redis/RabbitMQ 替换为托管服务；当前不设计微服务拆分。

## 8. 演进原则

- 先完成交易闭环，再考虑性能与拆分。
- 新增模块必须先定义数据所有权与依赖方向。
- 跨模块需求优先通过 `api` 契约解决；不得为了省事直接跨模块查表。
- 核心设计（状态机、事务边界、幂等、并发）先出方案，确认后编码。
