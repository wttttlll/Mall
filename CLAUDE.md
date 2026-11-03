# CLAUDE.md

本文件是 MallAgent 项目的开发规范。Claude Code 在本仓库内工作时必须遵守；与用户当次指令冲突时，以用户当次指令为准。

## 1. 项目定位

MallAgent 是从零开发的 Java 电商后端，采用模块化单体架构，只有一个 Spring Boot 启动应用。业务设计参考 [macrozheng/mall](https://github.com/macrozheng/mall)，但不复制其代码、包结构或实现。

当前范围：Java 后端 MVP（阶段 0–9）+ Vue 3 前端 MVP。前端位于 `frontend/`，已实现登录/注册、商品、购物车、结算下单、订单、模拟支付和收货地址管理。Python AI Agent 另行规划，不预埋代码。

## 2. 技术栈（固定）

| 类别 | 选型 |
| --- | --- |
| 语言 | Java 21 |
| 框架 | Spring Boot 3.x |
| 构建 | Maven 单模块（单 JAR） |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8 |
| 缓存 | Redis |
| 消息队列 | RabbitMQ |
| 认证授权 | Spring Security + JWT（JJWT 0.12.7） |
| 数据库迁移 | Flyway |
| API 文档 | Springdoc OpenAPI |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc + Mockito（随 starter-test），不使用 Testcontainers |
| 本地基础设施 | Docker Compose |
| 辅助工具 | Lombok（仅用于实体/DTO，减少样板代码） |
| 前端（MVP） | Vue 3 + Vite + Vue Router + Pinia + Axios，位于 `frontend/` |

约束：

- 不擅自升级、替换或新增核心技术组件。
- 新增依赖（如 MapStruct、Testcontainers、Redisson）必须先说明用途和影响，获得技术负责人确认；Lombok 已确认引入。
- 不引入微服务、注册中心、API 网关、Elasticsearch、Nacos 等组件。
- Vue 前端 MVP 已实现于 `frontend/`；Python AI Agent 另行设计，不预埋代码。

## 3. 架构规范

### 3.1 架构形态

- 模块化单体：一个进程、一个启动应用、一个数据库。
- 模块之间是进程内调用，不允许为了“解耦”引入 RPC 或消息中间件替代普通方法调用。
- 使用本地数据库事务保证强一致；不引入分布式事务框架。
- 跨模块异步协作使用 RabbitMQ，接受最终一致，但必须有幂等和补偿设计。

### 3.2 工程与包结构

工程为**单 Maven 模块**：根 `pom.xml` 的 `artifactId` 为 `mallagent`、`packaging` 为 `jar`，不存在子模块 `pom.xml`。源码统一在根 `src/main/java`、`src/test/java`。

本文中的“模块”指 `com.mallagent.*` 包边界，不是 Maven 子模块。按业务阶段逐步增加 `com.mallagent.module.*` 包，不提前创建后续包。

| 包 | 职责 | 引入阶段 |
| --- | --- | --- |
| `com.mallagent.common` | 通用返回、错误码、异常、常量、工具 | 阶段 0 |
| `com.mallagent.framework` | Web、安全、MyBatis-Plus、Redis、MQ、OpenAPI 等基础设施 | 阶段 0 |
| `com.mallagent.bootstrap` | 启动类、系统健康检查、全局配置、打包 | 阶段 0 |
| `com.mallagent.module.user` | 用户、RBAC、认证、收货地址 | 阶段 1 |
| `com.mallagent.module.product` | 分类、品牌、SPU/SKU、规格属性 | 阶段 2 |
| `com.mallagent.module.cart` | 购物车 | 阶段 3 |
| `com.mallagent.module.inventory` | 库存与库存流水 | 阶段 4 |
| `com.mallagent.module.coupon` | 优惠券 | 阶段 5 |
| `com.mallagent.module.order` | 订单与订单状态机 | 阶段 6 |
| `com.mallagent.module.payment` | 模拟支付 | 阶段 7 |
| `com.mallagent.module.logistics` | 物流与发货 | 阶段 8 |
| `com.mallagent.module.aftersale` | 售后与退款 | 阶段 9 |
| `com.mallagent.module.interaction` | 点赞、收藏、评论 | 阶段 10 |

依赖方向：

```text
com.mallagent.bootstrap
      ↓
com.mallagent.module.*
      ↓
com.mallagent.framework
      ↓
com.mallagent.common
```

规则：

- `common` 不依赖任何业务包，尽量不依赖 Spring。
- `framework` 只依赖 `common`，不反向依赖业务包。
- 业务包只能依赖 `framework`、`common` 和必要的其他业务包 `api` 子包。
- 跨包调用必须通过对方 `api` 包中的接口和 DTO；禁止直接引用对方的 mapper、entity、service 实现类。
- 禁止包间循环依赖。
- 禁止跨业务包直接读写对方的数据库表。

### 3.3 业务包内结构

```text
com.mallagent.<module>
├── api/          # 跨包契约：接口 + DTO（按需，仅供其他业务包调用）
├── controller/   # HTTP 入口，只做校验、参数转换、调用 service
├── service/      # 业务编排（接口 + impl）
├── entity/       # MyBatis-Plus 实体
├── enums/        # 枚举、状态定义（按需）
├── error/        # 模块错误码（按需）
├── mapper/       # MyBatis-Plus Mapper
├── dto/          # 请求/响应对象
├── converter/    # DTO 与实体转换（按需）
└── config/       # 模块专属配置（按需）
```

包结构以实际实现为准：`api`、`converter`、`enums`、`error` 只在模块确有需要时创建，不创建空包。领域规则放在 `service` 或对应的值对象中，不单独设 `domain` 包。

不为了分层而分层：简单 CRUD 不需要强行增加领域服务；但业务规则不能散落在 Controller 中。

### 3.4 命名与坐标

- groupId：`com.mallagent`
- artifactId：`mallagent`（单 Maven 模块）
- 基础包名：`com.mallagent`
- 启动类：`com.mallagent.MallAgentApplication`

## 4. 数据库规范

- MySQL 8，InnoDB，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。
- 表名格式 `{模块前缀}_{业务名}`，例如 `sys_user`、`product_spu`、`order_info`、`stock_sku`；避免使用 `order` 等保留字。
- 主键统一 `BIGINT AUTO_INCREMENT`，使用 MyBatis-Plus `IdType.AUTO`；禁止应用层手工指定主键（数据迁移等特殊场景除外）。业务单号（订单号、支付单号）单独生成，不复用主键。
- 金额统一用 `DECIMAL(18,2)` 存储，Java 使用 `BigDecimal`；禁止使用 `FLOAT`/`DOUBLE`。计算与比较使用 `BigDecimal`，四舍五入使用 `HALF_UP`，比较使用 `compareTo`；币种当前固定 CNY。
- 时间使用 `DATETIME(3)`，应用时区 `Asia/Shanghai`。
- 通用审计字段：`created_at`、`updated_at`、`created_by`、`updated_by`、`deleted`（逻辑删除）、`version`（需要的表使用乐观锁）。
- 不建物理外键，由应用层保证一致性，并用索引和唯一约束兜底。
- 业务唯一性必须落到数据库唯一索引，不能只靠代码判断。
- 逻辑删除与唯一索引的冲突必须在建表时解决：唯一索引包含 `deleted`，或约定业务键不可复用。
- 关联字段、查询条件字段、排序字段必须有索引依据，避免无索引大范围扫描。

### Flyway 规则

- 所有表结构变更必须通过 Flyway 迁移完成，禁止只改本地库不回写迁移脚本。
- 命名：`V{递增版本}__{snake_case_描述}.sql`，例如 `V1__create_user_rbac.sql`。
- 已执行的迁移脚本禁止修改；修正错误只能新增迁移。
- 每个阶段的新表必须在阶段交付中提供对应迁移脚本。
- 迁移脚本必须能在空库上从 `V1` 顺序执行成功。

## 5. API 规范

- 基础路径 `/api/v1`；管理端 `/api/v1/admin/**`，用户端 `/api/v1/**`。
- 统一响应体：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "a1b2c3d4",
  "timestamp": "2026-10-30T20:00:00+08:00"
}
```

- `code = 0` 表示成功；业务失败使用非 0 错误码。
- HTTP 状态码保留语义：400 参数错误、401 未认证、403 无权限、404 不存在、409 冲突/幂等冲突、500 服务异常。不要所有响应都返回 200。
- 错误码按模块分段，统一定义在 `common` 的 `ErrorCode`：

| 区间 | 模块 |
| --- | --- |
| `10000-10999` | 通用 |
| `11000-11999` | 用户 / 认证 / RBAC |
| `12000-12999` | 商品 |
| `13000-13999` | 购物车 |
| `14000-14999` | 库存 |
| `15000-15999` | 优惠券 |
| `16000-16999` | 订单 |
| `17000-17999` | 支付 |
| `18000-18999` | 物流 |
| `19000-19999` | 售后 |
| `20000-20999` | 互动 |

- 分页参数统一 `pageNum`（从 1 开始）、`pageSize`（默认 10，最大 100），响应使用 `PageResult<T>`。
- 请求参数使用 Jakarta Validation 校验，Controller 使用 `@Validated` / `@Valid`。
- 禁止直接返回实体对象，必须使用 DTO。
- 日期时间使用 ISO-8601；JSON 字段使用 lowerCamelCase。
- 所有 Controller 必须提供 Springdoc 注解，按模块分组，并标注主要错误响应。
- 敏感写接口必须支持幂等（见第 7 节）。

## 6. 安全规范

- 无状态 JWT：Access Token 短有效期，Refresh Token 较长有效期；登出使用 Redis 黑名单。
- 密码使用 BCrypt，禁止明文、可逆加密或自研哈希。
- RBAC 模型：用户 → 角色 → 权限；权限码格式 `module:resource:action`，例如 `product:spu:create`。
- 接口权限使用 `@PreAuthorize("hasAuthority('...')")` 或等价配置；URL 白名单保持最小。
- JWT 密钥、数据库密码、MQ 密码等通过环境变量注入，禁止提交到仓库；仓库只提供 `.env.example` 占位。
- 日志禁止输出 token、密码、完整手机号、完整地址、支付凭据。
- CORS 为未来 Vue 前端预留，但只允许明确配置的来源，禁止 `*` 与 credentials 同时使用。
- 所有 SQL 通过 MyBatis-Plus 参数绑定，禁止拼接用户输入。

## 7. 事务、幂等、并发控制原则

详细场景矩阵见 `docs/development-plan.md` 第 5 节。编码时必须遵守：

### 事务

- `@Transactional` 只加在 public 的 service 方法上，`rollbackFor = Exception.class`。
- 事务尽量短；事务内禁止调用 HTTP、发送 MQ、上传文件、执行长耗时计算。
- 同一数据库内的多表写操作使用本地事务；禁止引入分布式事务框架。
- 需要发消息的场景，在事务提交后发送（`TransactionSynchronization` 或本地消息表），不能在事务内直接发。
- 跨模块业务由发起方 service 编排；Controller 不承担事务。

### 幂等

- 客户端敏感写接口使用 `Idempotency-Key` 请求头；服务端以唯一索引或幂等表落地。
- 回调类接口使用外部业务单号（支付流水号、物流事件 ID）做唯一约束。
- 幂等判断必须与业务状态变更在同一事务内完成，不能先查后写。
- Redis 只能作为幂等辅助，不能替代数据库唯一约束。

### 并发

- 库存、优惠券额度等必须使用条件更新，例如 `UPDATE ... SET available = available - ? WHERE sku_id = ? AND available >= ?`，并检查影响行数。
- 禁止“先查询再更新”的 check-then-act 写法。
- 状态机流转使用 `WHERE status = 期望旧状态` 的条件更新，影响行数为 0 时视为冲突。
- 并发编辑使用 `version` 乐观锁；冲突时返回明确业务错误，不静默覆盖。
- 分布式锁仅在数据库约束无法解决时使用；引入 Redisson 或自研锁需先确认。
- 事务隔离级别默认 `READ_COMMITTED`；核心一致性依赖条件更新和唯一约束，而不是隔离级别。
- 死锁允许有限次数重试，重试必须有上限和日志。

### 缓存

- 数据库是唯一事实来源，Redis 不作为订单、支付、库存、优惠券的权威数据。
- 缓存采用 Cache-Aside：读缓存未命中查库回填；写库提交后删除缓存。
- 缓存必须设置 TTL；关键缓存删除失败要有日志和兜底过期。

## 8. Redis 与 RabbitMQ 规范

### Redis

- Key 格式：`mallagent:{module}:{biz}:{id}`，例如 `mallagent:product:spu:1001`。
- 缓存统一 TTL；禁止大 key、热 key 无保护、无过期时间。
- 对象使用 JSON 序列化，统一通过 `RedisTemplate` 配置，禁止各模块自定义不一致的序列化器。
- 简易分布式锁使用 `SET key value NX PX ttl`，value 必须唯一，释放时用 Lua 校验 value。

### RabbitMQ

- 命名：Exchange `mallagent.{module}.exchange`，Queue `mallagent.{module}.{event}.queue`，Routing Key `{module}.{event}`。
- 消息持久化，生产端开启确认，消费端手动 ACK。
- 消费失败进入重试，超过次数进入死信队列；禁止无限重试。
- 所有消费者必须幂等，以 `eventId` 或业务唯一键去重。
- 不依赖消息顺序；需要顺序的场景在业务层用版本号或状态机约束。
- 事件体必须包含 `eventId`、`eventType`、`occurredAt`、`version`。

## 9. 编码规则

- 使用 Java 21 特性，但以可读性优先；不可变 DTO 可用 `record`，实体类不强行使用。
- 依赖注入使用构造器注入，禁止字段注入。
- Controller 保持轻薄：参数校验、调用 service、返回结果，不写业务分支。
- 统一使用 SLF4J，禁止 `System.out`、`printStackTrace`。
- 异常使用 `BusinessException` + `ErrorCode`；禁止吞异常、禁止捕获后不处理。
- 日志必须包含可排查的上下文（业务单号、用户 ID），但不得包含敏感信息。
- 标识符使用英文；注释和 Javadoc 使用中文；公共 API 必须写 Javadoc。
- 方法保持单一职责；避免超长方法和重复代码；不为未来需求提前抽象。
- 返回集合时返回空集合而不是 `null`；边界参数必须校验。
- 代码格式：4 空格缩进，行宽 120，遵循项目统一格式化配置。
- 新增第三方依赖必须先确认。

## 10. 测试规则

- 测试框架：JUnit 5 + Spring Boot Test + MockMvc。
- 单元测试覆盖 service 与领域规则；集成测试覆盖数据库、Redis、MQ、安全链路。
- 库存、优惠券、下单、支付回调、退款必须提供并发或重复请求测试。
- 每个 bug 修复必须补充回归测试。
- 阶段完成前必须执行相关测试，并在汇报中给出真实命令和结果；禁止编造测试通过。
- 测试数据相互隔离，不依赖执行顺序；集成测试统一使用 Docker Compose 提供的 MySQL、Redis、RabbitMQ，测试库与开发库隔离，不引入 Testcontainers（如后续 CI 需要再确认）。
- 不以单一覆盖率数字作为验收标准，但核心业务分支必须有测试。

## 11. 协作与工作方式

- 用户是技术负责人，Claude 负责实际编码。
- 小任务可以直接实现；涉及核心业务设计、数据模型、状态机、事务边界的变更，先给方案再编码。
- 不擅自修改技术栈，不擅自新增中间件或依赖。
- 不提前实现后续阶段模块，不写“以后可能用到”的代码。
- 每阶段完成后执行相关测试，报告实际结果，然后停止等待确认。
- 不自动执行 `git commit`、`git push`；是否提交由技术负责人决定。
- 设计或行为变更必须同步更新 `docs/`；计划调整先更新 `docs/development-plan.md`。
- 不覆盖、不删除用户已有文件和未提交改动。

### 11.1 文档体系

`docs/README.md` 是文档索引，`CLAUDE.md`（本文件）是开发规范的唯一来源，`README.md` 存放快速开始与配置项。三者不重复维护同一份内容。

每阶段验收时，除功能验收外还必须确认：

- [ ] `docs/database.md` 已登记本阶段新增表、索引与唯一约束
- [ ] `docs/api-design.md` 已登记本阶段新增接口、错误码与权限码
- [ ] `docs/modules/<模块>.md` 已写明数据模型、状态机、事务边界、幂等键、并发策略
- [ ] `docs/test-plan.md` 已登记本阶段并发、幂等或重复请求用例
- [ ] `docs/business-flows.md` 已同步受影响的业务流程（阶段 6 起）
- [ ] `docs/learning-roadmap.md` 已补充本阶段自测问题与实际收获
- [ ] `docs/README.md` 索引状态已更新

文档尚未建立的阶段：结构类文档（`architecture.md`、`database.md`、`api-design.md`、`test-plan.md`）在阶段 3 开始前一次性补齐存量；交付类文档（`deployment.md`、`operations.md`、`retrospective.md`）在阶段 11 产出。

## 12. 当前阶段

阶段 0（工程基座）、阶段 1（用户、RBAC、JWT、收货地址）、阶段 2（商品）、阶段 3（购物车）、阶段 4（库存）已通过验收（阶段 4 于 2026-11-02 通过技术验收）。阶段 2 复核发现的 `product` 根权限、SKU 规格完整性、逻辑删除循环问题已修复（V8/V9 + 上架校验）。

阶段 4 覆盖库存锁定、支付扣减、取消释放、售后退货回补、批量库存快照、库存流水，以及管理端库存查询、调整和流水查询。库存仅使用 `stock_sku`、`stock_flow` 两张表，Flyway V10/V11；公共内部契约是 `com.mallagent.module.inventory.api.InventoryStockApi`，详见 `docs/modules/inventory.md`。

阶段 6/7 的 **MVP 简化版**已实现并验证（2026-11-02）：从购物车提交订单、服务端重新计价、订单/明细、库存锁定、我的订单列表/详情、取消未支付订单并释放库存；模拟支付成功、库存扣减、重复支付幂等、已取消不能支付、已支付不能取消、支付与取消并发仅一个成功。新增 Flyway V12 的 `order_info`、`order_item`、`payment_order` 三张表，详见 `docs/modules/order.md`、`docs/modules/payment.md`。全量 `mvn clean verify` 为 147 个测试，0 失败、0 错误、0 跳过。

Vue 3 前端 MVP 已实现于 `frontend/`：Vue Router 管理路由与登录守卫，Pinia 保存 JWT 与用户信息，Axios 统一处理统一响应、401/403 和业务错误；页面覆盖登录/注册、商品列表/详情、购物车、结算下单、订单列表/详情、模拟支付、取消订单和收货地址管理。前端通过 Vite `/api` 代理访问后端，构建和 Vitest 单测已通过。

优惠券、物流、售后、互动尚未开始，下一阶段优先级待技术负责人确认。不提前实现未确认的模块。验收记录与技术债见 `docs/development-plan.md` 第 10 节。

### 12.1 MVP 范围

- MVP 为阶段 0–9：基座、用户/RBAC、商品、购物车、库存、优惠券、订单、支付、物流、售后，构成“浏览 → 加购 → 下单 → 支付 → 发货 → 收货 → 退款”的完整闭环。
- 阶段 10（点赞、收藏、评论）为 MVP 后增量，MVP 期间不得创建其表、接口或权限码。
- 阶段 11 为交付加固，只做回归、并发验证与文档，不新增业务功能。
- 阶段 12（Vue 前端）不属于后端 MVP；前端 MVP 已实现于 `frontend/`，不新增后端业务功能。
- 功能边界以 `docs/requirements.md` 为准；扩大或缩小 MVP 范围必须先由技术负责人确认，并同步更新 `docs/development-plan.md`。

## 13. 完成定义（Definition of Done）

一个阶段只有同时满足以下条件才算完成：

- [ ] 代码可编译，`mvn clean verify` 通过
- [ ] 新增数据库变更已提供 Flyway 迁移，并能在空库执行
- [ ] 核心业务分支有测试，相关测试实际执行通过
- [ ] OpenAPI 文档已更新且可访问
- [ ] 错误码、日志、权限、幂等、事务符合本规范
- [ ] 未提前实现后续阶段功能
- [ ] `docs/` 已按第 11.1 节文档门禁更新
- [ ] 已向技术负责人如实汇报结果，并等待确认
- [ ] 未自动提交 Git
