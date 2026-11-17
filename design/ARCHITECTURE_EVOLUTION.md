# LoadUp 架构规范化设计与阶段实施方案

日期：2026-11-17 更新。状态：阶段 0 静态基线已完成；1A、1C、阶段 2 及旧 1B 的源码已调整但未构建/测试；1B 固定部署与数据源隔离已有源码实现，本轮修复 HTTP/schema 审查缺口并补隔离测试，运行验收未完成；阶段 3 已实施事实/投递边界及自包含财务证据源码，符合条件的付款查询已替代，运行验收仍开放；阶段 4 已调整业务端口与本地事实投影源码，待运行验收；阶段 5 已补离线门禁、运维配置检查和部署评估，待运行验收。范围：Framework、Modules、Pay，重点细化阶段 0～2。

目标是让业务规则、权威事实、协作协议和代码依赖有清晰且一致的归属。保留当前主要领域划分，先规范内部结构和协作，再按运行需求决定部署结构。应用数、JAR 数和代码行数不作为优化目标。

阶段 0 的交付包括[基线与例外](PHASE0_BASELINE.md)、[五条流程时序](PHASE0_FLOWS.md)、[依赖基线](PHASE0_DEPENDENCIES.md)及范围内既有架构文档同步。1A 当前结果见[实施记录](PHASE1A_IMPLEMENTATION.md)，其余切片保留为后续计划。代码基线采用当前工作区，包含原有未提交改动；参考 HEAD 为 Pay 598c63a4、Framework bba225489、Modules 81fb1fc，不能仅按这三个提交重现工作区。

## 1 设计范围和约束

### 1.1 保留的业务约束

- acquire 拥有订单应收、已付、在途占用和终态；payment 拥有实际付款、退款、attempt 与渠道证据；limitcenter 拥有额度桶和预占结算。
- 订单保持 0 ≤ paid + reserved ≤ amount。UNKNOWN 不构成未收款证据，不能据超时释放占用。
- 外部调用置于本地事务外。执行身份在调用前持久化，结果在独立短事务中应用。
- 业务变更、相关 Task、receipt、Task 按原业务边界同事务提交。
- 重试沿用原金融请求身份；幂等、版本、执行围栏、退款上限和已冻结条款继续有效。
- 当前按单租户部署，数据范围由部署配置决定；不新增请求级动态 schema 路由。
- Framework 提供可复用技术机制，Modules 提供可复用业务能力，Pay 拥有支付商业与交易规则。

### 1.2 阶段 0～2 不包含的工作

不合并应用、不改变领域数据所有权、不迁移数据库、不替换身份提供方、不引入消息队列或通用工作流引擎、不批量替换 ORM、不修改支付状态机和既有终态证据协议。阶段 0～2 不删除同步回查；阶段 3 按完整证据和现有语义逐条治理，见财务证据记录。

开发期实现允许同步修改调用方，不增加旧 API 别名或兼容分支。但“无需兼容旧代码”不授权重写已登记 SQL、篡改已冻结快照、改变原金融请求身份或静默重算存量哈希。

## 2 当前问题及代码依据

下表的“行为”为源码事实，“影响”为架构判断，不等同于已发现运行故障。

| 编号 | 当前行为与依据 | 架构影响 | 处理阶段 |
|---|---|---|---|
| A1 | [PaymentOutcomeEventService](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentOutcomeEventService.java) 与 [LimitSettlementService](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitSettlementService.java) 新完整证据路径已取消付款回查，旧冻结路径保留原查询；退款/费用仍读取最新累计 | 完整付款事实可离线验证并应用本地原绑定；退款最新累计查询有独立推进语义，不能批量删除端口 | 3 源码与回归源码已调整，运行待验收 |
| A2 | 阶段 3 已由 [PaymentEvents](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentEvents.java) 生产 canonical 事实，由 app.dispatch 的 PaymentFactRoutes/Publisher 分发；旧目标名仅作路由与冻结读取 | 新增投递订阅不改事实生产；完整付款证据已替代满足条件的回查，退款最新累计查询保留；真实交付待验收 | 3 边界源码已调整，未运行验收 |
| A3 | 阶段 0 的 AcquireActor 携带 productAccessToken；1A 已移除该字段，由[DelegatedCredentialResolver](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/security/DelegatedCredentialResolver.java)逐请求解析，保留原 ActorProvider 校验 | 业务身份与传输凭证已在源码中分离，构建/运行证明待补 | 1A 源码完成，未运行验证 |
| A4 | 阶段 0 的 [LimitPolicyCompiler](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/service/LimitPolicyCompiler.java) 调用完整编译器并构造 INIT 引用；阶段 2 已改为消费额度片段 | 完整总入口校验已与额度片段编译分离，运行证明待补 | 2 源码完成，未运行验证 |
| A5 | 阶段 0 的 [AcquiringTermsCompiler](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/service/AcquiringTermsCompiler.java) 同时解析多组词汇；阶段 2 已将完整闭集解析移至 domain CommercialPolicyParser，编译器仅投影条款 | 领域解析、片段约束和 DTO 投影已在源码中分开，运行证明待补 | 2 源码完成，未运行验证 |
| A6 | [RemoteContractConverter](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/converter/RemoteContractConverter.java) 第 26 行起双向映射多组同名远端与本地 Command | 部分隔离主要体现为类型复制；需要按消费方业务能力评估，不能一概删除本地模型 | 0 登记，4 调整 |
| A7 | 阶段 0 的 MultiTenantAutoConfiguration 在数据库模块注册 Servlet TenantFilter（旧 1B 已移至 WebMVC；新 1B 已删除核心 tenant 绑定，改为部署 Header 校验） | 数据库解析承担 Web 请求上下文生命周期，非 Web 入口另走一套绑定路径 | 1 |
| A8 | 阶段 0 的 [UPMS app POM](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-app/pom.xml) 依赖 infrastructure 并扫描其包；1C 已移除该依赖与扫描，由 infrastructure 独立装配默认 Gateway | UPMS 用例与默认实现的归属已在源码中分开；其他 core 例外保留，运行证明待补 | 1C 源码完成，未运行验证 |
| A9 | [GitHubOAuthProvider](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-app/src/main/java/io/github/loadup/modules/upms/app/strategy/oauth/GitHubOAuthProvider.java) 在 app 自行创建 RestTemplate 并解析第三方响应 | OAuth 传输适配与应用策略混合；阶段 0 新登记 E15，不能宣称 app 均无 HTTP 实现 | 独立适配切片，1C 不顺带调整 |

通知条款当前包含超时、退避倍数、最大尝试次数等参数。哪些是商业承诺、哪些是平台运行参数，需要按字段分类；阶段 2 先整理职责，不更改现有字段、默认值和存量快照解释。

## 3 目标架构

### 3.1 模块职责和事实所有权

这里的模块是逻辑职责，不对应强制的部署或 Maven 拆分。

| 逻辑模块 | 当前承载位置 | 权威事实与规则 | 不承担的职责 |
|---|---|---|---|
| 商户 | merchantcenter | 主档、经营资格、关系与逐项动作授权 | 合约解释、支付终态 |
| 产品目录与商业合约 | productcenter 内部 | 定义、签约版本、来源、生效状态、不可变条款 | 运行中的付款与额度状态 |
| 商业准入 | productcenter 内部 | 绑定稳定意图的准入决定及回放结果 | 代替额度执行许可或渠道执行 |
| 收单订单 | acquire | 应收、已付、占用、付款窗口、关单条件 | 裁定渠道是否实际收款 |
| 支付执行 | payment | 付款、退款、attempt、执行围栏、渠道证据 | 订单是否付清、额度桶累计 |
| 额度 | limitcenter | 策略运行版本、桶、预占、消耗、释放、恢复 | 推断未知付款结果 |
| 商户回调 | acquire 内部 | 报文快照、签名、期限、ACK、目标投递状态 | 普通用户消息及资金裁定 |
| 交易查询 | realtime | 可重建投影、来源版本、消费进度 | 权威交易状态写入 |
| 对账 | reconciliation | 封存输入、比较、差错和复核 | 自动据差错改写金融事实 |
| 通用通知 | Modules Notification + Pay 装配 | 用户消息、目标生命周期、外部消息编排 | 商户支付协议回调 |
| 管理入口 | Pay admin + 前端 admin | 页面聚合、管理用例入口、操作员审计关联 | 第二份商户/合约规则、跨领域直连表 |
| 身份与安全 | Framework 安全机制、UPMS、oauth2-server | 各自身份协议、凭证及验证机制 | 代替各领域的业务授权 |

### 3.2 业务协作关系

实线为命令/查询，虚线为目标状态下的可靠事实交接。阶段 0～2 保留原协议；阶段 3 已补齐完整付款/退款证据，付款新证据路径取消回查；退款最新累计读取与旧载荷查证保留。

~~~mermaid
flowchart TD
    ADMIN[管理入口] --> MER[商户]
    ADMIN --> COMM[产品目录与商业合约]
    ORDER[收单订单] --> ADMIT[商业准入]
    ADMIT --> MER
    ADMIT --> COMM
    COMM -->|策略发布| LIMIT[额度]
    ORDER --> PAY[支付执行]
    PAY -->|预占与执行许可| LIMIT
    PAY --> CHANNEL[渠道适配]
    PAY -.终态证据事实.-> LIMIT
    PAY -.付款退款事实.-> ORDER
    PAY -.交易快照.-> READ[交易查询]
    ORDER -.订单快照.-> READ
    ORDER -.回调意图.-> CALLBACK[商户回调]
    PAY -.退款回调事实.-> CALLBACK
    RECON[对账] -->|受控只读证据| PAY
~~~

事实交接的前提是：可信生产者、完整不可变载荷、原业务绑定、源版本、持久化幂等回执和本地原子应用。未满足前继续保留同步查证，不把收到事件等同于证据充分。

### 3.3 编译依赖关系

~~~text
入口适配 → app → domain
             → 本领域公开契约

infrastructure → app 的用例端口 / domain 的存储端口
infrastructure → 外部提供方公开契约、技术 SDK

bootstrap / 自动装配 → 入口适配 + app + infrastructure
~~~

- app 不引用 infrastructure 的具体实现；入口不访问仓储；domain 不依赖 Spring、ORM、HTTP 和远端模型。
- 存储端口可以归 domain；远程协调、事件交接等用例端口归 app。按所表达的能力选择归属，不统一塞入 domain。
- Facade 表达业务用例，内部端口表达用例所需能力，HTTP Service 表达传输协议，三者不机械复制。
- 本领域纯错误码或稳定契约枚举可以按现有约定复用；不以此放开 domain 对整个 client 技术装配的依赖。
- 小型 core JAR 内保留包级依赖规则；不要求新增通用 starter、流程引擎或 pay-common。
- 业务层可以直接使用稳定、确有语义的 Framework 能力；不为每个 IdGenerator、Clock 再造一层转发接口。

### 3.4 五类关键语义

| 概念 | 含义 | 与其他概念的区别 |
|---|---|---|
| 商业策略 | 约定允许什么、如何计费和约束 | 不等于当前执行状态 |
| 准入决定 | 对稳定意图作出的商业决定 | 不等于付款受理或渠道执行许可 |
| 业务事实 | 已收款、已退款、已消耗等权威结果 | 不由任务成败或查询投影裁定 |
| 流程进度 | 投递中、重试耗尽、等待查证 | FAILED 不等于资金失败 |
| 交付记录 | 一个目标对一个事实的处理进度 | deliveryId 不替代业务事实身份 |

## 4 阶段 0 建立可核对的架构基线

### 4.1 涉及模块与文件

阶段 0 只整理设计与现状，不改变业务行为。下列入口已按[基线报告](PHASE0_BASELINE.md)核对，并对有差异或缺少基线入口的架构文档定向同步，保留原有未提交工作。

| 范围 | 文件入口 | 应补齐的内容 |
|---|---|---|
| 总体边界 | Pay ARCHITECTURE.md、ROADMAP.md；Framework ARCHITECTURE.md；Modules ARCHITECTURE.md | 当前事实与目标分开，统一服务名、数据所有权、单租户与验收状态 |
| 依赖基线 | 三仓库根 POM/BOM；UPMS app/infrastructure/web/authserver POM；Pay 各领域 POM | 区分 compile/runtime/test/optional，不能把 test 依赖误判成生产耦合 |
| 契约基线 | Pay loadup-pay-contracts/ARCHITECTURE.md 与各 client/ARCHITECTURE.md | 请求、命令、事实、ACK、版本、幂等身份、错误语义 |
| 数据基线 | 各领域 src/main/resources/db/migration/pay 及获准组件快照 | 表和迁移所有权、唯一键、事务管理器、快照和证据归属；不执行迁移 |
| 当前交付限制 | Pay deploy/ACCEPTANCE.md、ROADMAP.md、各模块 README | 已有源码、未运行验证与未完成能力分别记录 |

相对路径以当前仓库为准；Framework/Modules 是同级独立仓库，不能假定一个根 reactor 同时构建三者。

### 4.2 五条流程核对清单

每条时序必须标出：可信身份、稳定业务键、事务开始/提交、网络位置、结果未知分支、恢复入口、重试所有者、最终完成条件。

| 流程 | 当前代码入口 | 必须回答的问题 |
|---|---|---|
| 创单与商业准入 | acquire app 的 OrderCreationService；productcenter app 的 OrderAdmissionService、PolicyPublicationService | A0/A1 如何回放；资格读取时效；策略未激活为何拒绝；合约暂停怎样阻止新单 |
| 付款执行与订单落账 | acquire 的 PaymentInitiationService、PaymentOutcomeOperations；payment 的 PaymentAcceptanceService、PaymentExecutionService、PaymentResultService | 占用、执行许可、首次渠道提交各何时持久化；UNKNOWN 谁查证；何时允许订单消费/释放 |
| 退款 | payment 的 RefundPlanningService、RefundExecutionService、RefundResultService、RefundRecoveryService | 原申请与子退款身份；额度、订单费用、回调和查询各自何时 ACK；退款不减少订单 gross 已付 |
| 关单 | acquire 的 OrderClosureService、OrderClosureOperations、OrderClosureRecoveryService；payment 的 PaymentCloseService | 父/子门禁、原 attempt 证据、迟到成功、未受理证明、CLOSING 到 CLOSED 条件 |
| 额度结算 | payment 的 PaymentLimitOutcomeDelivery；limitcenter 的 LimitSettlementService | 反向查证必要字段、桶锁序、receipt 原子提交、Release-before-Reserve、退款恢复 |

上述类位于各模块 app/src/main/java/io/github/loadup/<领域>/app/service；采用完整的 loadup-pay-<领域>-app 目录名定位，不沿用旧短目录。

### 4.3 执行顺序和交付物

1. 记录工作区已有变更、模块清单和当前版本配套关系；不得用清理工作区或恢复旧文件的方式制造基线。
2. 核对事实所有权表和生产依赖，登记明确例外及归属理由。
3. 按创单、付款、退款、关单、额度结算的顺序补齐当前时序；当前缺失的能力标为缺口，不补造步骤。
4. 为关键操作登记命令/查询/事件/ACK 契约，列出 A1～A8 的影响范围。
5. 根据核对结果同步模块现状文档；目标方案继续保留在 design，不提前把规划写成现状。

阶段 0 的产物已拆为三个配套文件：PHASE0_BASELINE.md、PHASE0_FLOWS.md、PHASE0_DEPENDENCIES.md。它们分别承载所有权/例外/验收、五条时序/契约及全量源码 POM 声明；没有创建空模板或 ADR。

### 4.4 阶段 0 验收标准

| 编号 | 验收条件 | 证明方式 |
|---|---|---|
| G0-1 | 每项权威数据、状态推进和规则只有明确责任模块 | 所有权表逐项指向代码、迁移或明确缺口 |
| G0-2 | 五条流程均能定位身份、事务、网络、未知结果、重试和完成条件 | 时序与实际入口对照；未覆盖处登记，不凭历史文档推断 |
| G0-3 | 依赖方向区分生产/测试/可选；app 与装配的现有例外可枚举 | POM 与实际 import/自动配置对照 |
| G0-4 | 单租户部署、数据库类型、当前服务名和未验收状态一致 | 根架构与模块文档交叉核对，无残留相互矛盾的当前描述 |
| G0-5 | 阶段 1/2 的改动范围和回归入口可定位 | 本文清单绑定现有文件；拟新增类型明确标记 |

阶段 0 以静态核对验收，不要求构建、连接数据库或发送业务请求。G0-1～G0-5 的结果及限制已记录于[验收表](PHASE0_BASELINE.md)。五条当前时序和声明依赖已核对，未定位的自动恢复入口明确列为缺口；不代表编译、运行门禁或真实金融链路通过。

## 5 阶段 1 规范分层装配与入口上下文

### 5.1 工作包 1A 分离 Actor 与委托凭证

先以 acquire → productcenter 为一个完整切片，不一次重构全部身份体系。

本工作包已完成源码和测试源码同步，详细改动、错误语义及待验收项见[实施记录](PHASE1A_IMPLEMENTATION.md)。下表为本切片已落实的职责，1B/1C 不随之标记完成。

| 模块 | 文件 | 1A 源码调整 |
|---|---|---|
| acquire-app | security/AcquireActor.java、AcquireActorProvider.java；port/OrderAdmissionClient.java | Actor 移除 productAccessToken；保持 scopeHash/permissions 的含义；业务端口不接收裸 JWT |
| acquire-adapter | security/JwtAcquireActorProvider.java | 继续从已验证身份建立稳定主体范围，不再把令牌放入 Actor |
| acquire-infrastructure | client/OrderAdmissionHttpClient.java | 从基础设施内部的委托凭证解析器获取当前请求的已验证令牌，保留禁止事务内网络调用和错误分类 |
| acquire-infrastructure | security/DelegatedCredentialResolver.java，已新增 | 先复用当前 ActorProvider 校验，再从当前已认证 Jwt credentials 解析原 token；不持久化、不缓存、不扩大 audience、不回退机器身份 |
| acquire 各层测试 | JwtAcquireActorProviderTest.java、JwtAcquireServiceActorProviderTest.java、OrderAdmissionHttpClientTest.java，以及创建 AcquireActor 的用例测试 | 同步构造器、轮换、拒绝、日志与所有权断言 |

表中 Java 文件相对各自 src/main/java/io/github/loadup/acquire，测试相对对应 src/test/java/io/github/loadup/acquire。解析器归 infrastructure，不依赖 acquire-adapter；POM 已直接声明使用的 spring-security-core 与 spring-security-oauth2-jose。

调整顺序：先确认当前 SecurityContext 中 JWT 的存活方式和下游受众校验；再集中迁移 Actor、提供者、出站适配器及调用方；最后检查错误与日志语义。未建立委托凭证时拒绝，不自动换身份。后台恢复继续使用其既有、显式授权的 SERVICE 流程，不复用请求令牌，也不由本工作包重新设计。

### 5.2 工作包 1B 固定部署与数据源隔离（源码已调整，待运行验收）

2026-11-16 按单租户部署约束替换原“统一 tenant 上下文”目标。旧 DeploymentDataScope、DatabaseTenant 等已删除，当前采用固定部署与数据源；2026-11-17 修复显式内部 HTTP 头、schema 搜索路径校验并补双 schema/角色测试。权威设计见[1B 重设计](PHASE1B_IMPLEMENTATION.md)，明确现有路径与动作见[逐文件影响清单](PHASE1B_CHANGE_INVENTORY.md)。不考虑旧 API/配置兼容或历史数据。

目标不变量：一个应用一个固定业务 DataSource、账号和 schema；Task、消费回执与需要原子登记的任务同库同事务；X-Tenant-Code 仅匹配部署 code，不能驱动数据库切换；业务 Actor/用例没有当前租户；认证、商户归属、金融证据、原子应用与幂等机制保持。

| 顺序 | 工作内容 | 退出条件 |
|---|---|---|
| 1B-R0 | code/schema/账号/issuer、Header 例外与 tenant 用途分类 | 没有未分类的安全/存储用途 |
| 1B-R1 | 固定 DataSource/schema、迁移目标与不可变部署标识 | 无动态选择或 public fallback，错配不就绪 |
| 1B-R2 | Header 校验、内部出站、客户端、协议例外与凭证边界 | 错 code 拒绝，第三方无泛化头注入，跨部署 token 拒绝 |
| 1B-R3 | Actor/事务/业务参数链与渠道/签名/摘要/文件用途 | 去掉当前 tenant，原业务归属和安全用途完整 |
| 1B-R4 | Task/Scheduler/恢复扫描 | 无上下文运行，业务与可靠性数据同源原子提交 |
| 1B-R5 | 删除旧类/配置/imports、收敛 POM/SQL/调用方 | 生产无旧租户残留或兼容重载；依赖显式且无环 |
| 1B-R6 | 集中源码/装配及授权后的定向运行验收 | 1B 主文 G1B-01～16 均有证据；未运行不得标完成 |

当前原 Outbox/DelayedTask 已整合为 Task；1B 不新建 TenantProvider、路由 DataSource 或另一套重试引擎。DeploymentIdentity 归 runtime，数据库仅消费固定 schema 配置；Web/HTTP 依赖部署标识，业务 app/domain 不因该常量新增 runtime 依赖。具体配置、路径、依赖删除条件、例外和验收标准以 1B 主文为准。

### 5.3 工作包 1C 明确 UPMS 的装配归属

1C 已完成源码、消费者、测试源码与文档同步；运行验收待执行，详见[1C 实施记录](PHASE1C_IMPLEMENTATION.md)。以下保留设计约束与迁移来源。

以 UPMS 为阶段 0 的 app → infrastructure 例外治理对象；Pay 现有 infrastructure → app 方向作为参照，不把全仓分层改造塞入本工作包。

| 模块 | 文件或资源 | 后续调整 |
|---|---|---|
| UPMS app | pom.xml；app/autoconfigure/UpmsAutoConfiguration.java；自身 AutoConfiguration.imports | 删除对具体 infrastructure 的编译依赖及扫描；只注册应用服务，以 gateway 接口为输入 |
| UPMS infrastructure | pom.xml；infrastructure 包及新增 UpmsPersistenceAutoConfiguration | 注册默认持久化适配，保留替换点；不能依赖 app 以制造反向循环 |
| UPMS infrastructure | 新增 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports | 独立登记持久化自动配置，明确先于 app；不依赖名称偶然排序 |
| UPMS web/authserver | 各自 pom.xml、自动配置及装配入口 | 接入默认实现的消费者显式获得 infrastructure；不依赖 app 的隐式传递引入 |
| Pay admin | pom.xml、AdminApplication.java 及装配测试 | 验证实际消费组合能装配 UPMS；不增加对其他 Pay 领域表的访问 |
| UPMS test | TestApplication.java、已有登录/用户权限相关测试 | 覆盖默认持久化与自定义 Gateway 两类装配 |

原 app 已通过组件扫描注册的服务和基础设施 Bean，要分清新自动配置的唯一注册路径。避免扫描与 @Bean 双注册；按服务实际依赖设置条件，不使用“缺任意一个 Gateway 就禁用全部 UPMS”的粗粒度条件。

迁移前 [UpmsAutoConfiguration](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-app/src/main/java/io/github/loadup/modules/upms/app/autoconfigure/UpmsAutoConfiguration.java) 只扫描 app 和 infrastructure 两个包，并显式注册 PasswordEncoder、UserPermissionService、AccessDecisionService；并非直接构造全部 GatewayImpl。迁移应逐项保留这些 Bean 的语义和替换条件。现有 [app 导入资源](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-app/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) 只登记该自动配置。阶段 0 没有直接装配测试；1C 已补齐独立 app、默认持久化、逐接口替换和 Admin 消费组合测试源码，均未运行。

已有回归入口包括 [ServiceInputContractTest](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-test/src/test/java/io/github/loadup/modules/upms/ServiceInputContractTest.java) 和 [SchemaIsolationIT](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-test/src/test/java/io/github/loadup/modules/upms/SchemaIsolationIT.java)；后者的存在不代表动态多租户路由已实现或通过验收。UPMS infrastructure 的 Servlet API 当前为 provided，需按实际使用点核对，不能随数据库 Filter 的移动一并删除。

调整顺序：先梳理现有 Bean 和消费 POM；为 infrastructure 建立独立装配；在同一切片切换 app 及默认消费者依赖；删除旧扫描；最后核对自定义 Gateway 覆盖与自动配置顺序。不得从“移除 app 依赖”直接推断默认装配仍然完整。

### 5.4 阶段 1 验收矩阵

| 编号 | 必须满足的行为 | 现有入口及待补验证 |
|---|---|---|
| G1-1 | Actor 无原始 token、Authorization 或下游专用凭证字段；凭证轮换不改变原订单所有权 | JwtAcquireActorProviderTest；用例构造器和 scopeHash 固定样例 |
| G1-2 | 出站委托使用原已验证身份；无凭证/错误 audience/权限不足拒绝，不换 SERVICE 身份；日志不含凭证 | OrderAdmissionHttpClientTest；拟补解析器与真实安全链验证 |
| G1-3 | 无外层事务时允许请求；事务内网络调用仍被拒绝；业务拒绝和依赖失败分类不被混淆 | 现有 HTTP 客户端与事务用例回归 |
| G1-4 | 固定数据源/schema、实际数据库权限隔离，Header 不选择数据；无上下文后台执行 | 新 1B 的 G1B-01～04、G1B-12～14；旧 DatabaseTenantTest 不再作为目标行为 |
| G1-5 | 部署 Header 检查与协议例外明确；保留其他执行上下文；无数据库 Web 不装配 JDBC | 新 1B 的 G1B-05～08、G1B-15～16；tenant 绑定测试替换 |
| G1-6 | UPMS app 无 infrastructure 生产依赖；默认实现、替换 Gateway、web、authserver、Pay admin 均无缺 Bean/重复 Bean | POM/import 静态核对；UpmsAutoConfigurationTest、UpmsPersistenceAutoConfigurationTest、Admin UpmsAssemblyTest 已补源码，待运行 |
| G1-7 | 业务路径、权限、幂等、业务错误分类、事务与金融身份保持；1B 部署头、tenant 字段/配置/摘要变化以新设计为准，不保留兼容入口 | 相关 Controller/Facade/集成回归，不能只看单个配置测试 |

### 5.5 跨仓顺序

原阶段顺序为 1A → 旧 1B → 1C。当前新 1B 按 R0～R6 跨 Framework、Modules、Pay 与客户端完整收口；各开发步骤不是可独立部署的兼容版本，不能只发布提供方而遗漏消费方。已完成源码调整的 1A/1C 继续保留其职责边界。

需要验证装配时，先获得匹配的 Framework 制品，再获得匹配的 Modules 制品，最后验证 Pay；BOM 固定配套版本。开发期直接迁移当前调用方，不引入永久兼容层。每个切片独立保留验证结果，不以一个模块通过推断整个系统通过。

## 6 阶段 2 分离商业配置解析与策略编译

阶段 2 已完成源码、调用方、测试源码和文档同步；完整校验、错误边界、通知分类及 G2 待运行验收见[实施记录](PHASE2_IMPLEMENTATION.md)。下文保留目标职责与验收约束，不代表运行门禁已通过。

### 6.1 目标流水线

~~~text
ResolvedConfiguration（值与来源）
    ↓ 完整闭集解析、类型/范围/跨策略校验
ValidatedCommercialPolicy（内部不可变类型，已新增）
    ├─ 订单/付款/费用/退款/通知/渠道策略片段
    └─ 额度策略片段

额度发布：额度片段 → 绑定稳定商户范围 → 激活命令/哈希 → 原可靠发布工作流
订单准入：各片段 + 真实 ACTIVE 额度引用 → 条款契约 → 原不可变快照/哈希
~~~

完整校验保留在发布或准入的总入口。额度片段编译不依赖其他片段，但这不意味着允许在原本要求完整配置的发布入口提交残缺合约。禁止删除 requireAllConsumed 后静默接受未知字段。

### 6.2 模块与文件清单

以下 Java 路径以 productcenter 相应模块的 src/main/java/io/github/loadup/productcenter 为根。

| 模块 | 现有文件 | 后续责任 |
|---|---|---|
| domain | model/ResolvedConfiguration.java、TypedValue.java、ValueType.java | 保持解析输入及类型语义，保留来源信息；不依赖 Spring/Jakarta Validator |
| domain | policy/CommercialPolicyParser.java；policy/ValidatedCommercialPolicy.java 及片段值类型，已新增 | 完整词汇解析、精确数值、局部与跨片段约束；不生成 DTO、不请求数据库、不读平台配置 |
| app | service/AcquiringTermsCompiler.java | 改为从已验证策略和真实激活引用生成条款；不再承担原始字符串词汇解析 |
| app | service/LimitPolicyCompiler.java | 只消费额度片段及受信商户范围；移除对 AcquiringTermsCompiler 的依赖和虚构 INIT 激活绑定 |
| app | service/PolicyPublicationService.java | 在原合约锁与来源约束内调用统一解析，再编译额度命令；保留原 Task 与 ACK 流程 |
| app | service/OrderAdmissionService.java | 调用统一解析和条款生成；保持资格网络读取在事务外，合约锁、时钟及原准入回放规则不变 |
| app | model/CompiledAcquiringTerms.java、EncodedAcquiringTerms.java；port/CompiledTermsCodec.java | 保持快照边界；解析内部类型不进入公开快照 |
| app | converter/OrderAdmissionConverter.java、PolicyPublicationConverter.java；新增 CommercialPolicyConverter | 遵循 Spring 管理的 MapStruct 转换；字段校验不混入表示转换 |
| infrastructure | codec/CompiledTermsCodecImpl.java、PolicyPublicationCodecImpl.java；converter/PolicyPublicationStorageConverter.java | 保留规范化、哈希、严格读回和持久化映射；不因抽取类型改变现有字节语义 |
| client | dto/terms 下各类型及 OrderAdmissionDTO、LimitPolicyPublicationDTO | 阶段 2 保持对外结构；检查注解与实际约束一致 |
| 消费方 | acquire app/model/productcenter；payment client/dto/terms；相应 RemoteContractConverter | 本阶段作为契约回归对象，不开展批量模型删改 |

实现采用 domain/policy 下的领域片段与解析器，详细清单见[实施记录](PHASE2_IMPLEMENTATION.md)；不额外发布一组 policy-api/binder Maven 制品。完整策略解析属于商业领域，Bean Validation 保留为应用边界的补充校验，不能成为 domain 的运行依赖。

### 6.3 内部片段的责任

| 片段 | 主要内容 | 不应包含 |
|---|---|---|
| OrderPolicy | 等待、延期、未付款处理、会话约束 | 当前订单状态和数据库锁 |
| PaymentPolicy / RefundPolicy | 金额、笔数、部分退款、期限和分配规则 | attempt、渠道响应和当前额度 |
| FeePolicy | 费率、固定额、舍入、上下限与退款费用规则 | 已确认费用流水 |
| LimitPolicy | 稳定规则范围、窗口、阈值、计量与恢复解释 | 虚构激活 ACK、当前桶累计 |
| NotificationPolicy | 启用、受控目标/签名引用及现有冻结参数 | 明文密钥、临时令牌 |
| ChannelPolicy | 允许能力、商业路由引用 | 厂商凭证、实际执行证据 |

来源 provenance 与原 ResolvedConfiguration 关联保留。不能把内部片段对象直接序列化进 CompiledAcquiringTerms，使纯内部重构改变原编译哈希。

### 6.4 调整顺序

1. 固定现有合法/非法配置样例、条款输出、策略激活命令、规范化哈希和来源记录；优先复用已有测试样例，补充独立的固定期望值。
2. 提取完整解析与领域值类型，集中处理缺失/未知字段、精确整数、小数、枚举及跨片段约束。
3. 将 AcquiringTermsCompiler 切换到类型化输入；通过转换器维持公开 DTO、字段默认值和 capabilities 排序语义。
4. 将 LimitPolicyCompiler 切换到额度片段；删除其完整条款编译调用，保留统一入口的完整配置校验。
5. 在同一业务切片迁移 PolicyPublicationService、OrderAdmissionService 和调用方测试，不长期保留两套运行解析器。
6. 核对 CompiledTermsCodecImpl、PolicyPublicationCodecImpl、持久化转换与消费方契约；不更改 compilerVersion/schemaVersion 或哈希算法来掩盖差异。
7. 同步 productcenter README/ARCHITECTURE 和契约说明，列出阶段 2 仍保留的限制与后续决策。

### 6.5 通知参数分类决策

阶段 2 交付字段分类表，但不直接迁移通知协议。

| 字段类别 | 建议归属 | 阶段 2 处理 |
|---|---|---|
| notify.enabled、受控目标/签名引用 | 商业策略与商户回调配置 | 保持现有归属与安全约束 |
| 业务承诺的通知有效窗口 | 商业交付规则 | 保留冻结解释；确认当前合同是否明确承诺 |
| HTTP 超时、退避倍数、最大间隔/尝试数 | 默认应归平台执行策略，合同有明确承诺时另论 | 登记当前来源、覆盖顺序和快照用途，不改现有行为 |

后续若调整归属，应在新投递创建时解析出完整有效配置并冻结；旧投递继续使用原快照。该协议或数据演进需独立设计，不混入本轮内部编译重构。

### 6.6 阶段 2 验收矩阵

| 编号 | 验收条件 | 主要验证入口 |
|---|---|---|
| G2-1 | 同一合法配置产生相同条款、来源及哈希；capabilities/集合顺序不影响既有语义 | AcquiringTermsCompilerTest、CompiledTermsCodecImplTest；固定输出样例 |
| G2-2 | 缺失字段、未知键、错误类型、越界和不支持策略仍拒绝；完整入口不接受残缺配置 | 已新增 CommercialPolicyParserTest（app 测试模块）；既有非法配置回归 |
| G2-3 | long 超出浮点精确范围、小数、金额和阈值仍精确处理；不经 double | 编译器与 codec 的边界样例 |
| G2-4 | LimitPolicyCompiler 不依赖完整收单编译器；额度片段不读取通知/费用字段 | LimitPolicyCompilerTest；源码/依赖核对；统一入口另测完整配置 |
| G2-5 | 稳定 ruleScope、policyId/version/hash、八组计数规则及退款恢复解释不变 | LimitPolicyCompilerTest、PolicyPublicationCodecImplTest |
| G2-6 | INIT/PUBLISHING/ACTIVE、可信 ACK、重放及事务内 Task 语义不变；不能用解析结果伪造激活 | PolicyPublicationServiceTest、PublicationTransactionsTest、PolicyActivationTaskHandlerTest |
| G2-7 | 原准入回放不重新判定当前合约；新单仍检查合约状态、资格和激活；拒绝与到期结果稳定 | OrderAdmissionServiceTest、ContractPersistenceIT、MerchantContractIT |
| G2-8 | 编译快照读回拒绝身份/版本/哈希错配；原数据不被重算或静默覆盖 | CompiledTermsCodecImplTest、PolicyPublicationStorageConverterTest；持久化回归 |
| G2-9 | 消费方公开条款、HTTP JSON、MapStruct 映射和错误码无非预期变化 | client 的 AcquiringTermsTest；acquire/payment 相关契约回归 |

“编译器输出相同”不能只比较新旧两段同源实现；关键 hash、金额与固定策略应有独立期望值。持久化、锁竞争和事务原子性需要实际 PostgreSQL 验证，源码或纯单测不替代它们。

## 7 阶段间顺序与验收记录

~~~text
0 事实/依赖/流程基线
    ↓
1A Actor 与凭证 → 1B 固定部署/数据源隔离（重设计） → 1C UPMS 装配
    ↓ 每个切片独立验证并同步配套制品
2 类型化策略解析 → 条款投影 → 额度编译 → 发布/准入及快照回归
    ↓
3 事实与目标投递分离、证据契约和同步回查治理
    ↓
4 消费方业务端口深化 → 5 自动约束及部署评估
~~~

阶段 1 与 2 在职责上可分离，但首次实施建议顺序进行，减少装配和业务编译变化叠加。阶段 0～2 结束不代表 A1/A2/A6 已解决；阶段 3 已按完整证据逐条替代付款回查，退款最新累计与旧冻结载荷查证保留；其证据及恢复路径不得批量删除。

验收记录至少包含：工作区/提交标识、受影响模块、验证用例、静态核对结果、编译结果、测试结果、真实环境结果、未解决项。上述状态分别记录，不以 IDE 无错误、已有测试文件或历史通过结果替代本轮运行结果。

构建与测试遵守仓库纪律：本次不执行；后续由开发者本地运行，或在明确授权后执行窄模块 mvnd clean。必须携带 clean，按需跳过无关检查，不自行启动全 reactor。多仓依赖按 Framework → Modules → Pay 顺序准备，Testify 只以 test scope 使用。

## 8 后续阶段的进入条件

阶段 3 首批见[实施记录](PHASE3_IMPLEMENTATION.md)，当前完整契约、查询逐条决策及 G3-7 待运行门禁见[财务证据记录](PHASE3_FINANCIAL_EVIDENCE.md)。源码已调整不等同于运行验收通过。

| 阶段 | 范围 | 进入条件 |
|---|---|---|
| 3 | 不可变事实契约与目标级交付；逐条减少反向查证 | 证据字段和信任模型明确；原绑定/幂等/版本/冲突/回滚均有验收；先付款，再额度，再退款 |
| 4 | 消费方业务端口、本地模型与转换精简 | 先按新 1B 确定并收口身份/事务/隔离边界；按用例证明语义投影收益；不让 app 直接依赖远端 HTTP DTO，不批量删隔离层 |
| 5 | 依赖门禁、配置与运维一致性、部署组合 | 根据容量、故障隔离、发布节奏和团队责任决定；不预设五应用或任何数量目标 |

本方案不新增全局交易协调中心。订单、付款、额度继续各自拥有状态推进与恢复，公共可靠性机制只提供持久化、调度、租约与观测，不裁定资金事实。

阶段 4 的模块/文件、保留边界及 G4-1～G4-9 验收见[实施记录](PHASE4_IMPLEMENTATION.md)。

阶段 5 的检查范围、CI 入口、合法例外和部署组合评估见[实施记录](PHASE5_IMPLEMENTATION.md)。

## 9 本次交付状态

- 已完成：目标职责及阶段 1～2 的实施设计；阶段 0 的五条现状时序、核心契约、174 个 POM 声明、实际例外和范围内文档一致性收口。
- 阶段 0 验收：G0-1～G0-5 静态核对结果见 PHASE0_BASELINE.md；已登记自动续办、同步回查等缺口，没有以改代码的方式消除它们。
- 已调整源码：阶段 1A 的 Actor/凭证分离及 旧 1B 的 Web/数据库入口分离、统一部署范围（已被固定部署/数据源模型替代，旧核心类已删除）及 1C 的 UPMS 装配归属，以及阶段 2 的商业策略解析/片段编译、调用方与测试源码；静态审阅完成，构建和测试未运行，不能视作 G1/G2 运行验收通过。
- 阶段 3 已调整源码：目标无关事实、目标级交付、冻结读取及调用方/回归源码，详见 PHASE3_IMPLEMENTATION.md；自包含付款/退款证据及消费验证已补齐，付款完整证据路径已替代查询；退款最新累计查询保留，运行验收尚未完成。
- 1B 新目标：固定部署与数据源隔离已有源码调整；HTTP/schema 审查问题已修复、隔离测试已补源码、清单已同步，运行验收仍开放。
- 尚待完成：新 1B 的 R6 运行验收及其余部署证明；阶段 4 源码调整见 PHASE4_IMPLEMENTATION.md，运行验收开放；阶段 5 已补离线门禁、运维配置检查和部署评估，待运行验收。
- 尚未运行：编译、单测、集成测试、数据库迁移、部署与真实金融链路验收。
