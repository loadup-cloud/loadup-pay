# LoadUp Pay 后续工作

## 架构规范化阶段

- [x] 阶段 0：完成当前工作区的[所有权及例外核对](design/PHASE0_BASELINE.md)、[五条流程时序](design/PHASE0_FLOWS.md)、[174 个 POM 声明清单](design/PHASE0_DEPENDENCIES.md)与范围内架构文档同步；仅静态验收。
- [ ] 阶段 1：按[实施方案](design/ARCHITECTURE_EVOLUTION.md)隔离 Actor/凭证、固定部署/数据源与 UPMS 装配。
- [x] 阶段 1A 源码与调用方/回归源码同步：[AcquireActor 与委托凭证分离](design/PHASE1A_IMPLEMENTATION.md)；构建、测试、装配与运行验证尚未执行，旧 1B 与 1C 已有源码调整，不能视为运行验收。
- [ ] 阶段 1B 重设计：[固定部署与数据源隔离](design/PHASE1B_IMPLEMENTATION.md)及[逐文件清单](design/PHASE1B_CHANGE_INVENTORY.md)已有源码调整；2026-11-17 修复 HTTP/schema 审查缺口、补双 schema/角色测试并同步文档，R6 运行验收未完成；不保留旧租户兼容模型。
- [ ] 阶段 2：分离完整商业配置解析、类型化策略和条款/额度编译；保持现有快照与哈希语义。
- [ ] 阶段 3～5：阶段 3 事实/投递边界及[财务证据、付款回查替代](design/PHASE3_FINANCIAL_EVIDENCE.md)源码已调整，退款最新累计读取保留；运行验收未执行，阶段 4～5 未开始。按独立验收推进。

本文只维护当前决策及待完成事项。当前源码边界见 [ARCHITECTURE](ARCHITECTURE.md)，历史环境记录见 [本地发布记录](deploy/LOCAL_DEVELOPMENT.md)。历史镜像就绪、旧测试结果均不能证明当前工作区通过验收。

## 已确定的边界

- 当前按单租户部署，不实现运行时 schema 路由；业务服务独立持有数据库及迁移历史。
- admin 为 UPMS 登录/RBAC/审计与商户、产品、OAuth 客户端管理 BFF；原 merchantmng/productmng 已合并。
- oauth2-server 只负责机器/商户应用 client_credentials，操作员登录由 admin/UPMS 负责。内部服务鉴权保留，按当前权限配置发短期令牌。
- 请求响应归提供方 client；消费方通过基础设施转换为本地模型。共享 commercial/events 契约归独立工件。本阶段同步修改所有调用方，不保留历史兼容层。
- 内部调用使用 Spring HTTP Service Clients + RestClient、Kubernetes Service DNS。写请求不自动重试，未知结果由既有幂等与恢复流程处理。
- 普通配置与 Secret 版本化随滚动发布生效；Gateway API / Envoy Gateway 为默认入口；虚拟线程保留为压测候选，默认平台线程。
- 组件 SQL 是模板，应用持有冻结快照；业务模块按显式命名空间参与应用迁移。已应用历史只追加，禁止生成器覆盖旧版本。

## 当前源码收口

- [ ] 修复构建脚本的目录适配：`scripts/common.py` 的 bootstrap_project 及 build.py 仍拼接旧短目录名；admin/notificationcenter 已是单模块，不能按旧 bootstrap 路径打包。完成前不将 build/up/deploy 视为可用的一键入口。
- [ ] 同步迁移生成器的目录映射与冻结锁路径，适配 `loadup-pay-*` 及两个单模块应用；保留已登记 SQL 的版本与内容。
- [ ] 网关 merchantmng/productmng 后端改为 admin；同步 NetworkPolicy 调用方及 Admin/center 的 issuer、audience 信任配置。
- [ ] 本地数据库准备补齐 admin；复核十应用发布清单、镜像位置、各自签名 KeyStore 与环境变量。
- [ ] 验证 admin 操作员登录、中心管理接口与 OAuth 客户端管理的权限矩阵、原子审计、secret 单次交付及响应丢失处理。
- [ ] 核对 admin 工作台需求：当前装配 UPMS/audit，dictionary/file/transfer 是否纳入须按实际需求补齐，不将规划写成已接入。

## 运行验收

按 [最终验收](deploy/ACCEPTANCE.md) 与 [验收记录模板](deploy/acceptance-report.template.md) 留存证据：

- [ ] 当前源码的窄模块 clean 编译及相关回归；与真实环境验证分开报告。
- [ ] PostgreSQL 空库初始化、既有库增量升级、迁移命名空间和冻结快照完整性。
- [ ] 付款、退款、额度、通知、投影、对账和 UNKNOWN 恢复；覆盖重复、并发、围栏及事务回滚。
- [ ] OAuth/JWKS、错误 issuer/audience/权限拒绝、缓存续取、凭证/签名密钥轮换及已签发 JWT 到期语义。
- [ ] 网关回调原文透传、CNI 允许/拒绝、滚动排空、扩缩容、配置及镜像组合回退。
- [ ] 平台线程与虚拟线程 A/B 容量、P95/P99、错误率、连接池等待及上下文传播；满足门槛后再调整默认值。

## 后续能力

- [ ] 生产 KMS 使用 OpenBao Transit；Secret Manager/ESO 负责秘密分发，二者职责独立。验证签名、加解密及轮换后再替换本地后端。
- [ ] 仓库签名材料清理、部署期注入、镜像定版、SBOM/签名与漏洞扫描。
- [ ] 完成 tracing/logging 后端及告警送达；Prometheus 规则存在不等于已上线。
- 商户入驻、SMS/第三方渠道真实发送暂不排期；动态多租户另行设计与验收。

文档整理只做静态核对，没有执行上述构建、测试、迁移、部署或金融调用。
