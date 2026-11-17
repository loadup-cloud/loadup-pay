# 阶段 5：架构门禁、运维契约与部署评估

日期：2026-11-17。状态：检查器、CI 入口、回归源码与部署评估已补齐；本轮未执行检查器、构建、测试或部署，运行验收开放。阶段 1B～4 的未运行项继续独立跟踪。

## 1 架构依赖门禁

[check_architecture.py](../scripts/check_architecture.py) 无需 Maven、JDK、数据库或相邻仓库即可检查 Pay 源码。以 reactor 声明递归确定模块，读取普通依赖及 profile 依赖；非 test 的直接依赖用于有向环检测。扫描所有模块 `src/main/java`，包含未提交文件，不扫描 target 和测试 fixture。诊断携带文件、行号及稳定规则编号。

| 规则 | 阻断条件 |
|---|---|
| A5-REACTOR / POM | 缺失/损坏/重复模块、模块逃出仓库、依赖标识或 scope 未展开 |
| A5-REPOSITORY | Framework → Modules/Pay 或 Modules → Pay 的生产 POM 依赖 |
| A5-OWNER / REMOTE | 跨应用实现依赖；app/domain/client 直接依赖其他应用 client 或实现 |
| A5-LAYER / APP / CLIENT | 反向依赖实现层；公开 client 依赖本应用内部层 |
| A5-DOMAIN / TECH | domain 引入 Spring/ORM 或本应用 client 的 DTO/HTTP 等类型 |
| A5-CONTRACT | 共享事实/商业契约引用应用工件或业务内部类型 |
| A5-TEST / CYCLE | Testify 非 test scope，或非 test 的 reactor 声明依赖成环 |

源码检查覆盖 import、static import、直接全限定名及合并 service JAR 内 app/domain 包；忽略注释、字符串、字符和文本块。不是 Java AST、字节码分析、effective POM 或完整传递依赖解析，不能证明反射/运行时自动装配无逆向引用。profile 依赖保守合并检查，不代表各 profile 同时激活。BOM 管理、插件/注解处理器、父 POM 与实际已解析 classpath 仍由现有 Maven 规则和本地构建负责。

显式保留的合法边界：

- domain 复用本领域 client.error / client.enums 纯类型；不放开 DTO、HTTP 或配置类型。
- 业务领域使用支付渠道的稳定契约/API 值模型，保持现有资金语义。
- infrastructure 测试可以引用其他应用 service 作为集成 fixture，例如 ProductCenter 的 MerchantContractIT；该依赖必须是 test scope。
- merchantcenter/realtime 的 service JAR 以包边界检查；bootstrap 与三个单模块应用承担现有装配职责。
- 稳定 Framework 能力可由 app 直接使用，不为 Clock、IdGenerator、TaskTemplate 添加转发接口。

当前 CI 单 checkout 检查 Pay；可用 `--workspace <工作区>` 同时扫描 Framework、Modules、Pay 三个 reactor 的声明依赖，不静默跳过缺失仓库。Framework/Modules 自身已有的架构测试仍为各仓验证入口，本阶段不要求 Pay CI 拉取三个私有仓库。

## 2 配置与运维一致性

[check_operations.py](../scripts/check_operations.py) 只读检查仓库普通配置和 YAML，不读取 secret.env、不展开 shell、不连接 Kubernetes/数据库、不打印配置值。YAML 使用受限安全加载并拒绝重复键；异常报告不携带解析器的配置正文。PyYAML 版本固定在 [requirements-checks.txt](../scripts/requirements-checks.txt)。

检查内容包括十应用名单与调用方、Deployment/Service/NetworkPolicy、8080 命名端口和选择器、HTTP 探针、指标抓取注解、Prometheus 抓取路径/应用标签、preStop 与 Spring 停机预算、固定部署 code/schema 来源、显式 Flyway 所有权、旧租户/动态数据源残留、命名内部 HTTP 客户端的部署头开关。普通环境目录支持模板核对和显式 `--environment-dir` 核对。

配置模板的 test/prod JDBC 留空表示尚待供应，默认检查不把它当成部署就绪。显式普通配置目录还拒绝同一服务器上的跨应用账户复用；这只是配置角色名检查，不能证明角色授权正确。自定义普通配置目录的检查也不能替代运行时环境变量、Secret、ConfigMap 快照或数据库角色授权验收。相同 schema 名可存在于不同数据库；同一数据库中的不同应用必须保持独立 schema/账户边界。

冻结迁移继续由 `gen-migrations.py --check` 检查已登记文件及哈希；不会生成、改写或执行 SQL。既有 configmap/deploy 工具继续负责不可变快照、配置引用、镜像与回退引用检查。在线 `verify.py` 仍只证明 rollout 和 ready endpoints，不证明身份、权限、资金事实或恢复成功。

### 本轮修正的工具路径

`common.bootstrap_project` 原先为除 OAuth 外的应用拼接旧短目录和 bootstrap，导致 Admin/NotificationCenter 及其他重命名模块选错路径。现在统一使用 `loadup-pay-*`，三个单模块应用直接选本模块，其余选择实际 bootstrap；build.py 复用这个选择结果定位 JAR，不再维护第二套路径规则。未知应用拒绝选择路径。没有执行任何打包、镜像构建或部署。

## 3 CI 与测试源码

现有 [.github/workflows/ci.yml](../.github/workflows/ci.yml) 的 tooling 任务原本只有 checkout。本轮增加 Python 环境、固定 YAML 解析依赖、两个离线检查器、迁移冻结检查和 scripts/tests 的 unittest 发现。quality-gate 已依赖 tooling，检查失败将阻断质量门禁。CI 配置已写入，未触发或证明远端运行。

| 测试文件 | 独立反例/期望 |
|---|---|
| [test_check_architecture.py](../scripts/tests/test_check_architecture.py) | 直接全限定名、外层引用、合并 service 包、domain 纯枚举例外、注释/文本块、跨仓反向依赖、Testify scope、fixture scope、环、缺模块及生产源码扫描 |
| [test_check_operations.py](../scripts/tests/test_check_operations.py) | 合法合成仓库及配置、重复 YAML 键、应用名单漂移、错误配置/迁移来源、数据目标冲突、探针/端口/停机预算 |
| [test_application_layout.py](../scripts/tests/test_application_layout.py) | 十应用实际可执行模块 POM、artifactId、Boot 插件、三个单模块和未知应用拒绝 |
| 既有 scripts/tests | 保留渲染、不可变发布、冻结迁移等回归；不以合成 fixture 代替 Kubernetes 验收 |

为供开发者后续执行，入口如下；本轮没有运行这些命令：

```sh
python3 -m pip install -r scripts/requirements-checks.txt
python3 scripts/check_architecture.py
python3 scripts/check_operations.py
python3 scripts/gen-migrations.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
```

## 4 部署组合评估

当前十个应用各有独立身份、配置、数据源、schema/数据库所有权与迁移历史。单部署仅一个客户 code，HTTP 头校验部署路由，后台任务使用固定数据源。Task、业务回执和本地业务写入共享所属应用事务，不能把队列集中到另一业务数据库。

| 应用 | 事实/责任 | 部署判断 |
|---|---|---|
| acquire | 订单意图、关闭流程、商户通知投递 | 保留独立进程，承担外部请求及订单恢复 |
| payment | 付款/退款渠道执行、权威结果、UNKNOWN 查证 | 独立故障与权限边界；恢复积压不能被管理流量挤占 |
| limitcenter | 额度计数、许可、消耗及退款恢复 | 保留独立所有权，优先保障锁竞争与执行栅栏语义 |
| productcenter | 商业配置、合同、准入、策略发布 | 保留独立发布与迁移；准入仍有在线依赖，不能当作纯离线管理 |
| merchantcenter | 商户状态、应用授权与可信资格 | 保留身份及资格边界；故障影响准入，需独立容量预算 |
| realtime | 查询投影 | 适合独立扩缩容；投影延迟不能推进或裁定资金事实 |
| reconciliation | 源文件、对账差异、审核与权威查证 | 批处理资源与交易入口隔离，保留独立执行预算 |
| admin | UPMS、RBAC、审计和管理 BFF | 独立操作员权限/凭证边界；不能与资金执行进程混装 |
| oauth2-server | 应用凭证、令牌与签名密钥 | 保留独立安全与密钥边界；JWKS 与令牌可用性单独验收 |
| notificationcenter | 通用通知任务/发送渠道 | 保留独立重试与外部渠道故障隔离；不接管收单商户资金通知事实 |

| 组合候选 | 收益 | 代价与决定 |
|---|---|---|
| 十进程，开发/低负载环境共享集群节点和 PostgreSQL 实例 | 复用资源，保留业务/事务/身份边界 | 当前建议；独立数据库角色、schema、连接与资源预算仍必需 |
| productcenter + merchantcenter 同进程 | 可能减少进程开销及部分资格调用 | 在线准入/身份资格与商业配置相互扩大故障、发布及事务影响；缺容量与团队证据，不实施 |
| acquire + payment + limitcenter 同进程 | 可能减少网络跳数 | 权威结果、执行许可与订单流程强耦合，改变故障隔离及权限边界；当前不实施 |
| realtime + reconciliation 共用工作进程 | 少一个运行单元 | 查询延迟与批处理内存/IO争抢、发布节奏不同；当前不实施 |
| admin + oauth2-server 同进程 | 可能简化运维入口 | 合并操作员与应用令牌/签名密钥边界，收益不抵权限影响；不实施 |

本轮选择继续十应用，允许基础设施资源共置；没有新增合并 launcher、共享业务数据源或跨应用本地事务。推荐依据是现有责任与风险边界，尚无真实容量、延迟、故障或团队成本测量，不能宣称十应用数量最优。

### 容量与发布判据

按当前 pay.yml 十个单副本、每 Pod request=100m/256Mi、limit=512Mi：稳态合计 request=1 CPU/2560Mi，内存 limit 合计5120Mi。若十个 Deployment 同时达到 maxSurge=1，最多二十个 Pod，对应 request=2 CPU/5120Mi、内存 limit=10240Mi；不含数据库、网关、监控、sidecar、节点余量，不能用这些配置值替代压测数据。

dev common.env 的 Hikari 上限5：十个单副本理论池上限50连接；十应用同时滚动可达100。真实容量还需累计迁移/管理连接、旧 Pod 排空期与其他客户端；其他环境必须按实际 pool/replica/surge 分别计算。源码 dev 复用 admin 账户仅是本地示例，不满足生产最小权限证明，不能随共享 PostgreSQL 实例方案一起复制。

后续考虑合并/拆分前，至少采集每应用峰值与持续 CPU/内存、DB 活跃连接/锁等待、p95/p99、额度许可延迟、Task 最老待处理/失败/租约恢复、投影延迟、渠道 UNKNOWN 与重放结果、发布频率、故障扩散及实际维护责任。用同一负载比较资源/成本和资金恢复时限，再单独审核组合决策。

## 5 验收及剩余项

| 编号 | 验收条件 | 本轮状态 |
|---|---|---|
| G5-1 | 源码/POM 正向依赖与登记例外可被离线门禁识别 | 检查器/反例源码已补，未执行 |
| G5-2 | 配置、迁移、数据目标、内部头及部署资源漂移可被报告 | 检查器/反例源码已补，未执行 |
| G5-3 | 构建选择与实际十应用模块一致 | 路径调整/测试源码已补，未构建 |
| G5-4 | tooling 失败使 CI 质量门禁失败 | workflow 已接入，未触发 CI |
| G5-5 | 组合决策包含责任、容量、故障、发布与替代方案 | 文档评估已完成；测量与部署证明仍开放 |
| G5-6 | 身份、权限、业务校验、幂等、可靠交付与错误回归无变化 | 未修改业务 Java/SQL；原业务回归未执行，不能记通过 |

本轮静态审查与文档链接检查单独记录；未运行门禁、测试、构建、迁移、镜像、集群或金融请求。生产角色与跨 schema 拒绝、配置快照/回退、停机与租约恢复、多副本竞争、JWKS/权限、告警实际抓取和压测继续按 [部署验收](../deploy/ACCEPTANCE.md) 留证。

## 6 本轮静态核对记录

- IDEA 已检查两个新检查器、两个门禁回归文件、构建路径回归、common.py、build.py 及 workflow，未报告错误；这不证明 Python 分支或 YAML/集群运行语义正确。
- 主代理已审查检查器及回归源码，修正 Service/NetworkPolicy 的字典访问、真实模块路径、数据库+schema 冲突判定、重复逻辑键、额外工作负载及测试 mutation 的具体目标。
- `git diff --check` 通过；本轮八份架构/脚本/部署文档相对链接检查无失效链接。
- 未执行 pip 安装、检查器、新旧测试、Maven、Docker、Kubernetes、SQL 或部署命令；未触发远端 CI。工作区原阶段 4 和 NotificationCenter 暂存变化保留。
