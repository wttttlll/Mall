# MallAgent 文档索引

> 本文件是 `docs/` 的唯一入口。新增文档必须在此登记；文档状态与实际代码不一致时，以代码和 `development-plan.md` 为准，并立即修正文档。

## 1. 单一事实来源

同一份事实只维护一处，其余文档只做引用，避免多份副本各自漂移。

| 内容 | 位置 | 说明 |
| --- | --- | --- |
| 开发规范 | [`../CLAUDE.md`](../CLAUDE.md) | 规范唯一来源。`docs/` 不复制第二份，Claude Code 也会自动加载根目录该文件 |
| 快速开始与配置项 | [`../README.md`](../README.md) | 环境要求、启动方式、环境变量、接口速查 |
| 阶段计划与验收标准 | [`development-plan.md`](development-plan.md) | 阶段划分、交付物、事务/幂等/并发矩阵、已确认决策 |
| 学习路线 | [`learning-roadmap.md`](learning-roadmap.md) | 每阶段该掌握什么、如何自测 |
| 需求范围 | [`requirements.md`](requirements.md) | 角色、用例、MVP 边界、非功能需求 |

## 2. 文档清单

| 文档 | 内容 | 状态 | 产出 / 更新时点 |
| --- | --- | --- | --- |
| [`development-plan.md`](development-plan.md) | 阶段划分、各阶段交付与验收、事务幂等矩阵、设计决策、验收记录与技术债 | ✅ 已维护 | 阶段调整时 |
| [`requirements.md`](requirements.md) | 需求规格：角色、功能范围、非功能需求、非目标 | ✅ 已建档 | 范围变化时 |
| [`learning-roadmap.md`](learning-roadmap.md) | 六条技术主线、阶段学习产出、自测问题 | ✅ 已建档 | 每阶段完成后补充 |
| [`product-module.md`](product-module.md) | 阶段 2 商品模块设计：数据模型、状态机、缓存、API | ✅ 已维护 | 模块变更时 |
| [`architecture.md`](architecture.md) | 模块边界、依赖规则、分层职责、横切能力、部署形态 | ✅ 已维护 | 阶段 3 起持续更新 |
| [`database.md`](database.md) | 表清单、索引与唯一约束、审计字段约定 | ✅ 已维护 | 每阶段增量 |
| [`api-design.md`](api-design.md) | 路径与命名规约、错误码清单、分页、幂等头、鉴权约定 | ✅ 已维护 | 每阶段增量 |
| [`test-plan.md`](test-plan.md) | 测试分层、并发与幂等用例、验收命令 | ✅ 已维护 | 每阶段增量 |
| [`modules/cart.md`](modules/cart.md) | 阶段 3 购物车设计：数据模型、幂等、并发、直接查 MySQL | ✅ 已维护 | 模块变更时 |
| `business-flows.md` | 下单、支付、取消、超时关闭、退款等流程与状态机 | 📝 待补 | 阶段 6 前出草案，6–9 补全 |
| `modules/<模块>.md` | 后续模块设计（库存、优惠券、订单、支付、物流、售后） | ⏳ 随阶段 | 各阶段设计确认时 |
| `deployment.md` | 部署手册：Compose 编排、profile、环境变量、启动顺序、验证步骤 | ⏳ 计划中 | 阶段 11 |
| `operations.md` | 运维手册：日志与 traceId、常见故障排查、备份与恢复 | ⏳ 计划中 | 阶段 11 |
| `retrospective.md` | 项目复盘：设计得失、踩坑记录、可改进项 | ⏳ 计划中 | 阶段 11 |

状态说明：✅ 已有并维护；📝 已确定但未编写；⏳ 待对应阶段产出。

## 3. 每阶段文档门禁

阶段验收时，除 `development-plan.md` 第 4 节的功能验收项外，还必须逐项确认：

- [ ] `database.md` 已登记本阶段新增表、索引与唯一约束
- [ ] `api-design.md` 已登记本阶段新增接口、错误码与权限码
- [ ] `modules/<模块>.md` 已写明数据模型、状态机、事务边界、幂等键、并发策略
- [ ] `test-plan.md` 已登记本阶段新增的并发、幂等或重复请求用例
- [ ] `business-flows.md` 已同步受影响的业务流程（阶段 6 起生效）
- [ ] `learning-roadmap.md` 已补充本阶段的自测问题与实际收获
- [ ] 本索引的状态列已更新

## 4. 编写约定

- 全部使用 Markdown，中文书写，文件名为小写英文短横线或下划线连接。
- 每份文档开头注明适用范围与对应阶段；设计变更时同步修改“最后更新”说明。
- 文档中的表名、接口路径、错误码必须与代码一致；不确定时先查代码再写。
- 计划性调整（阶段范围、顺序、验收标准）先改 [`development-plan.md`](development-plan.md)，再改本索引。
- 不在 `docs/` 中记录密钥、密码、Token 或完整连接串。
