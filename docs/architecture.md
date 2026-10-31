# MallAgent 系统架构设计

> 适用范围：阶段 0–11。本文记录模块边界、依赖规则、数据所有权和横切能力；具体业务设计见 `docs/modules/`。
> 最后更新：2026-10-31（阶段 3 购物车）。

## 1. 架构形态

- 模块化单体：一个 Spring Boot 进程、一个启动类、一个 MySQL 库。
- 模块间是进程内方法调用；不引入 RPC、注册中心、API 网关。
- 同一数据库内的多表写操作使用本地事务；不引入分布式事务。
- 跨模块异步协作使用 RabbitMQ，接受最终一致，必须幂等与补偿。
- 数据库是业务事实来源；Redis 只做缓存。购物车状态当前直接查询 MySQL，不使用 Redis 缓存。

## 2. 模块与依赖

```text
mallagent-bootstrap
      ↓
mallagent-module-user / product / cart / ...
      ↓
mallagent-framework
      ↓
mallagent-common
```

当前模块：

| 模块 | 职责 | 阶段 |
| --- | --- | --- |
| `mallagent-common` | 统一响应、分页、错误码、业务异常 | 0 |
| `mallagent-framework` | Web、安全、MyBatis-Plus、Redis、RabbitMQ、OpenAPI、异常处理 | 0 |
| `mallagent-module-user` | 用户、RBAC、JWT、收货地址 | 1 |
| `mallagent-module-product` | 分类、品牌、SPU/SKU、规格、状态机、商品查询 | 2 |
| `mallagent-module-cart` | 购物车、幂等添加、MySQL 状态查询 | 3 |
| `mallagent-bootstrap` | 启动类、配置、Flyway、打包 | 0 |

后续模块按计划逐个加入，不提前创建。

依赖规则：

- `common` 不依赖业务模块，尽量不依赖 Spring。
- `framework` 只依赖 `common`，不反向依赖业务模块。
- 业务模块只能依赖 `framework`、`common` 和必要的其他模块 `api` 包。
- 跨模块只能通过对方 `api` 包的接口与 DTO，禁止直接引用 Mapper、实体、Service 实现。
- 禁止模块间循环依赖，禁止跨模块直接读写对方数据库表。

## 3. 模块内结构

```text
com.mallagent.<module>
├── api/          # 跨模块契约：接口 + DTO（按需）
├── controller/   # HTTP 入口，只做校验、转换、调用 service
├── service/      # 业务编排（接口 + impl，按实际需要）
├── entity/       # MyBatis-Plus 实体
├── enums/        # 枚举、状态
├── error/        # 模块错误码
├── mapper/       # MyBatis-Plus Mapper
├── dto/          # 请求/响应对象
├── repository/   # Redis 等非数据库存储（按需）
├── converter/    # DTO 与实体转换（按需）
└── config/       # 模块配置
```

原则：按需创建包，不建空包；不为分层而分层；业务规则不能散落在 Controller。

## 4. 跨模块契约示例

购物车需要读取商品信息，但不修改商品规则：

```text
product/api/ProductSkuQueryApi
product/api/dto/SkuSnapshot
product/api/dto/SkuAvailability
product/service/ProductSkuQueryApiImpl   # 只读实现
```

- `SkuSnapshot` 包含 skuId、spuId、skuCode、spuName、imageUrl、price、specText、availability。
- 购物车只依赖接口，不感知商品 Mapper 与实体。
- 商品模块现有 Service、状态机、可见性规则保持不变。

后续订单模块需要通过购物车清理已选条目时，再为购物车增加 `api` 契约，不提前创建。

## 5. 数据所有权

| 数据 | 事实来源 | 缓存/副本 | 说明 |
| --- | --- | --- | --- |
| 用户、角色、权限、地址 | MySQL | 无 | 普通业务数据 |
| 商品、SKU、规格 | MySQL | Redis 缓存分类树、商品详情 | 缓存失效后回源 |
| 购物车条目 | MySQL | 无 | 查询直接读 MySQL，商品信息实时读取 |
| 订单、支付、库存、优惠券 | MySQL | 仅辅助缓存 | MVP 阶段不使用 Redis 作为权威数据 |
| 幂等记录 | MySQL | 无 | 唯一索引是最终防线 |

Redis Key 统一格式：`mallagent:{module}:{biz}:{id}`。缓存必须设置 TTL。

## 6. 横切能力

- 安全：JWT 无状态认证，Spring Security 过滤链，方法级权限；用户端接口默认要求登录。
- 统一响应：`Result<T>`，业务码 0 表示成功；HTTP 状态码保留语义。
- 异常：`BusinessException` + `ErrorCode`，全局异常处理不暴露堆栈。
- 链路：`traceId` 贯穿响应与日志。
- 数据库迁移：Flyway，禁止修改已执行脚本。
- 缓存：Cache-Aside，写库提交后删除缓存，TTL 兜底。
- 消息：生产端确认、消费端手动 ACK、失败重试进入死信、消费幂等。

## 7. 部署形态

- 本地：Docker Compose 提供 MySQL 8、Redis 7、RabbitMQ 3.13。
- 应用：单个 Spring Boot 可执行 JAR；开发库与测试库隔离。
- 生产演进：可在不改业务模块边界的前提下，将 Redis/RabbitMQ 替换为托管服务；当前不设计微服务拆分。

## 8. 演进原则

- 先完成交易闭环，再考虑性能与拆分。
- 新增模块必须先定义数据所有权与依赖方向。
- 跨模块需求优先通过 `api` 契约解决；不得为了省事直接跨模块查表。
- 核心设计（状态机、事务边界、幂等、并发）先出方案，确认后编码。
