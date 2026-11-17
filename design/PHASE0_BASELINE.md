# 阶段 0 架构基线与例外清单

核对日期：2026-11-15。阶段：0，文档与源码静态核对完成；阶段 1～5 未实施。业务代码、POM、配置、SQL 和测试源码不在本次修改范围。

此文保留阶段 0 时点的证据与例外，下面的原行为不是持续更新的实时状态。后续[阶段 1A](PHASE1A_IMPLEMENTATION.md)已在源码中处理 E04，新增委托凭证解析器及两项直接依赖；构建与测试未运行。后续[阶段 1B](PHASE1B_IMPLEMENTATION.md)已在源码中处理 E03、统一部署范围来源并补齐原生 Scheduler 扫描入口；尚未构建或测试。后续[阶段 1C](PHASE1C_IMPLEMENTATION.md)已在源码中处理 E01，拆开 UPMS app 与默认持久化装配；构建与测试未运行。后续[阶段 2](PHASE2_IMPLEMENTATION.md)已在源码中处理 E05 的完整编译依赖，保留发布/准入总入口完整校验；未构建或测试。后续[阶段 3](PHASE3_IMPLEMENTATION.md)已调整事实/投递类型边界，E06 已进一步补齐[自包含财务证据与付款回查替代](PHASE3_FINANCIAL_EVIDENCE.md)，退款最新累计查询明确保留；运行验收仍开放，未构建或测试。下表与第 5 节保留阶段 0 历史事实，E02/E15 等其他例外不因此撤销。

入口：[五条核心流程时序](PHASE0_FLOWS.md)、[174 个 POM 实际依赖](PHASE0_DEPENDENCIES.md)、[目标架构与后续计划](ARCHITECTURE_EVOLUTION.md)。当前文档描述实现，后续计划描述目标，两者不能互相替代。

## 1 基线范围

| 仓库 | 参考 HEAD | 本轮核对范围 |
|---|---|---|
| Framework | bba2254899 | 88 个 POM；公共依赖方向、数据库/上下文/Web 自动配置、可靠性职责 |
| Modules | 81fb1fcad8 | 30 个 POM；UPMS 装配、小型 core 装配与横向消费关系 |
| Pay | 598c63a4d4 | 51 个 POM；五条交易流程、HTTP/事件契约、十应用迁移命名空间与文档 |
| Testify | e78a69a403 | 5 个 POM；测试支持与运行依赖的区别 |
| Admin 前端 | a5526c93ea | 作为外部消费方登记；本轮不改变前端结构或运行行为 |

源码基线包含各仓库原有未提交修改，HEAD 不是完整重现依据。源码位置、现有自动配置资源、application.yml 和迁移目录是本轮证据入口；历史测试报告不作为当前运行证明。

依赖扫描覆盖四个 Java 仓库全部 174 个源码 POM，以及其中 profile 的直接 dependency 声明。生产 import 规则核对覆盖这三个主仓库的 src/main/java，针对 domain 的 Spring/ORM/Servlet/本地 client DTO 引用，以及 app 对 infrastructure 的显式 import；不能将这种定向检查称为所有字节码和动态装配依赖均已证明正确。

## 2 当前所有权

| 权威数据或规则 | 唯一业务所有者 | 实现或迁移依据 |
|---|---|---|
| 商户主档、经营资格、关系、动作授权 | merchantcenter | MerchantPersistenceService、资格/授权用例及 pay/merchantcenter 迁移 |
| 产品目录、签约版本、合约经营状态、商业条款来源 | productcenter | CatalogService、MerchantContractService、ContractRuntimeResolver，pay/productcenter 迁移 |
| 稳定意图的准入决定与回放 | productcenter | OrderAdmissionService、OrderAdmissionGateway；order_admission |
| 额度策略可靠发布进度 | productcenter | PolicyPublicationService；contract_limit_publication；PUBLISHING/ACTIVE 与 Task 状态独立 |
| 应收、已付、付款占用、关单和延期 | acquire | OrderCreationService、PaymentOutcomeOperations、OrderClosureOperations；订单和结果回执迁移 |
| 付款、attempt、渠道执行身份和永久结果证据 | payment | PaymentAcceptanceService、PaymentSubmissionPreparation、PaymentResultService；payment_order/attempt/claim/receipt |
| 退款申请、计划、子退款、容量、累计退款与证据 | payment | RefundPlanning/Execution/ResultService；refund_*、payment_refund_capacity、order_refund_summary |
| 桶、预占、执行许可、结算、退款恢复 | limitcenter | LimitReservation/Execution/Settlement/RefundService；limit_* |
| 原订单费用与退款费用调整 | acquire | PaymentOutcomeOperations、FeeRefundOutcomeService；order_fee、fee_adjustment/receipt；不改订单 gross |
| 商户回调正文、目标、签名、次数与送达 ACK | acquire | MerchantNotificationEvents/Service；merchant_notification_*；不等于普通站内信 |
| 交易查询投影和消费版本 | realtime | projection 用例、trade_* 与 receipt；不裁定资金事实 |
| 封存输入、比较结果、差错与复核 | reconciliation | reconciliation_*；只读查证不自动产生金融处置 |
| 操作员账号、权限与审计 | admin 装配 UPMS/Audit | admin POM 与 framework/upms、framework/audit 迁移命名空间 |
| SERVICE/MERCHANT_APP/MERCHANT 凭证与机器令牌 | oauth2-server | OAuth2ServerConfiguration、客户端管理与 db/loadup-oauth；不代替 admin 操作员登录 |
| 站内通知及 Gotone 装配数据 | notificationcenter 装配 Notification/Gotone | framework/notification 与 pay/notificationcenter 迁移；不接管商户支付回调 |

Framework 表结构模板属于机制提供方，应用冻结快照及迁移历史属于使用应用。同名 Task、Task、sequence 表出现在不同 schema，不表示一个共享全局交易库。原源码中显式 tenant 参数多为应用入口/事务上下文身份，不能据此声称仓储表含 tenant_id 或已具备运行时租户路由。

## 3 当前编译与运行依赖

### 3.1 仓库和业务层关系

~~~text
生产 Maven 直接声明（箭头为使用方依赖提供方）
Modules → Framework
Pay → Modules / Framework
Testify → Framework（其消费者按 test scope 使用）

业务用例和端口的典型编译关系
Pay adapter → app → domain / 本领域client
Pay infrastructure → app端口 / domain端口 / 远端client
Pay bootstrap → 本应用各层 / 实际binder
UPMS app → domain + infrastructure（当前装配例外）
~~~

默认、无 profile、排除 test 的直接内部 POM 图未发现环。这个结论不覆盖 Maven 继承、第三方传递、插件运行或动态装配；更不代表 HTTP 调用无环。原型设计图不得覆盖当前例外。

| 消费者 | 当前跨领域公开依赖 | scope 与用途 |
|---|---|---|
| acquire infrastructure | productcenter-client、payment-client、realtime-client | compile；准入、付款执行/查证、查询投影 |
| payment infrastructure | acquire-client、limitcenter-client、realtime-client、productcenter-client | compile；结果/退款交接、额度、投影及当前所需契约/适配；不能仅凭 POM 推断全部都会发网络请求 |
| limitcenter infrastructure | payment-client | compile；付款与退款权威事实回查 |
| productcenter infrastructure | merchantcenter-client、limitcenter-client | compile；资格/商户事实、额度激活 |
| productcenter infrastructure | merchantcenter-service | **test**；测试装配，不是生产跨领域实现依赖 |
| reconciliation infrastructure | payment-client、File/Transfer 等模块 | compile；受控交易证据、导入文件与任务能力 |
| admin | merchantcenter-client、productcenter-client、UPMS web/authserver、Audit | compile；管理转发及自身账号/审计，不访问 center 数据库 |

完整外部与内部依赖、provided/optional/profile、父 POM 和 BOM import 见 [依赖逐模块清单](PHASE0_DEPENDENCIES.md)。BOM 只管理版本，不自动装配全部组件。SDK binder 的 runtime 选择与 Java 编译接口依赖要分别记录。

### 3.2 当前运行往返

- acquire → productcenter → merchantcenter 是新准入的同步读取链，原准入可回放；不是三库事务。
- acquire → payment accept/close；payment → acquire 结果交接；acquire 首次接收付款/费用事件再查询 payment，形成运行往返。
- payment → limitcenter reserve/begin/verify/confirm/release/restore-refund；limitcenter 对首次结算/恢复再查 payment，形成运行往返。
- payment 与 acquire 向 realtime 投递完整快照，realtime 不同步回查源交易来重建本条负载。
- productcenter 的额度发布经原 Task 调 limitcenter，返回可信 ACK 后才写 ACTIVE。

服务 JWT 通过 ServiceAccessTokens 按目标获取/缓存续取，调用方显式传 Authorization；“逐次传递”不表示每次重新读文件或重新请求令牌。委托准入仍使用原主体令牌，是独立语义。

## 4 例外登记和处理决定

以下列出实际规则例外及容易误判的合法依赖。登记不是豁免安全检查，也不是本轮授权修改源码。

| 编号 | 当前例外或事实 | 依据 | 当前处理与后续归属 |
|---|---|---|---|
| E01 | UPMS app 生产依赖 infrastructure，自动配置扫描两个包 | UPMS app POM、UpmsAutoConfiguration | 当前保留；阶段 1C 拆开装配，不宣称 app 已与实现隔离 |
| E02 | audit/dictionary/file/notification/transfer 五个 core 的 app 自动配置显式引用对应 PersistenceAutoConfiguration | 五个 core 的 app/autoconfigure 源码 | 是同 JAR 的装配引用，未发现业务用例类因此直接依赖 GatewayImpl；作为装配例外保留，后续可移动装配包，无需强制拆 JAR |
| E03 | database 注册 Servlet TenantFilter，Web 依赖 optional | database POM、MultiTenantAutoConfiguration/TenantFilter | 当前 Servlet 条件/过滤器顺序保留；阶段 1B 移出 Web 生命周期职责 |
| E04 | Actor 含 productAccessToken，准入基础设施读取并委托 | AcquireActor、JwtAcquireActorProvider、OrderAdmissionHttpClient | 当前凭证诊断已隐藏；阶段 1A 隔离凭证，不改身份权限 |
| E05 | 额度编译器调用完整条款编译并构造 INIT 激活绑定 | LimitPolicyCompiler 第 134 行 | 当前作为完整词汇校验，不是激活证据；阶段 2 分离解析/片段编译 |
| E06 | payment 事件类型与 acquire/limit/risk 等目标绑定；消费方存在反查 | PaymentEvents、PaymentOutcomeEventService、LimitSettlementService | 阶段 3 才治理；阶段 0～2 保留当前证据防线 |
| E07 | Pay app 使用 Task、Task、IdGenerator、Spring tx；payment app 另有 Scheduler/Micrometer | 各 app POM 与事务/事件用例 | 当前技术编排依赖，不等于 domain 依赖基础设施；不新增一层无语义转发接口 |
| E08 | Pay domain 复用本领域错误枚举、共享生命周期/渠道契约、Money/PageResult 和诊断 JSON | domain POM、实际 import | “纯领域”表示无 Spring/ORM/HTTP 行为，不表示零依赖；不允许借此引入 client DTO 或整个远端模型 |
| E09 | Pay client 内含 Spring HTTP 声明与 OpenAPI/校验注解 | client POM、client.http 源码 | client 不是零技术依赖 JAR；业务 Facade 与 HTTP 声明角色分开说明，不据目标原则误删现有公开契约 |
| E10 | productcenter infrastructure 的 merchantcenter-service 为 test | 对应 POM scope | 合法测试依赖；不能写成生产跨服务数据库调用 |
| E11 | Transfer core 直接使用 File core；UPMS web 可选使用 audit-core | 两个 POM；对应业务集成 | 前者复用文件权限/生命周期，后者装配敏感读取审计桥；当前不能描述成 UPMS web 只依赖 audit-client |
| E12 | UPMS authserver 实际依赖 oauth-server-core | authserver POM | 当前含 SAS/资源端实现装配，不能写成只依赖 AuthServer API；不在阶段 0 调整 |
| E13 | database processor 为 provided/optional；MapStruct processor 等为编译期 provided/处理器路径 | POM 与 parent compiler 配置 | 编译期生成依赖，不是运行服务；不把其边算成部署应用或运行 binder |
| E14 | app/源码显式 tenant 参数和 DatabaseTenant.enabled 分支仍在，但默认配置关闭动态多租户 | 各 app application.yml、DatabaseTenant | 当前单租户上下文约定；schema 字符串计算不等于切换连接，不能宣称启用开关就隔离请求 |
| E15 | UPMS app 的 GitHubOAuthProvider 自行创建 RestTemplate 并处理 token/userinfo HTTP 与 JSON | [GitHubOAuthProvider](../../loadup-modules/loadup-modules-upms/loadup-modules-upms-app/src/main/java/io/github/loadup/modules/upms/app/strategy/oauth/GitHubOAuthProvider.java) 第 46、88、114 行 | 当前传输适配位于 app，不能宣称所有 app 已无 HTTP 实现依赖；登记为后续适配职责切片，阶段 1C 仅装配，不顺带重写第三方登录 |

本轮定向 import 检查：Pay 和 Framework 未发现 domain 引用 Spring/MyBatis-Flex/Servlet/本地 client DTO 或 app 显式 import infrastructure 的匹配；Modules 的五个匹配均为 E02 的自动配置类。UPMS 的包扫描依赖是字符串装配，因此另外通过 POM 和自动配置核对，不会被 import 检查自动发现。

## 5 数据范围与阶段 1B 的设计收口

### 5.1 源码现状

DatabaseProperties.MultiTenant 默认 enabled=false。DatabaseTenant.currentTenantId 在 disabled 时返回配置默认 tenant；currentSchema 在 disabled 时校验并返回 default-schema。enabled 时，currentSchema 对非空上下文 tenant 直接校验其作为 schema 名，不专门把“默认 tenant”映射到 default-schema；没有上下文且 required=true 才拒绝。无论返回什么字符串，这个 helper 都不负责切换连接。

TenantFilter 在 Servlet 环境将配置默认 tenant 写入已有 ExecutionContext，并在链退出时恢复作用域；不是从请求 Header 选择 schema。现有顺序为 ExecutionContextFilter 的 HIGHEST_PRECEDENCE，随后 TenantFilter 的 +10，均覆盖 REQUEST/ASYNC/ERROR。

本次据此修正文档中“默认 tenant 总是映射 default-schema”的过度概括；不改 helper 实现，也不把 enabled=true 当作可部署的多租户隔离能力。

依据：[DatabaseTenant](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/tenant/DatabaseTenant.java) currentTenantId/currentSchema、[DatabaseProperties](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/config/DatabaseProperties.java)、迁移前 TenantFilter（已移除；现为 [DeploymentScopeFilter](../../loadup-framework/loadup-components/web/loadup-components-webmvc/src/main/java/io/github/loadup/components/webmvc/DeploymentScopeFilter.java)）。

### 5.2 后续实施决定

阶段 1B 采用轻量不可变 DeploymentDataScope（拟新增至 commons-context，仅包含受信部署 tenantId，不含 schema/连接/事务/凭证），由 runtime 的拟新增 DeploymentScopeAutoConfiguration 解析当前默认 tenant 配置并装配；WebMVC 的入口使用该值建立上下文。database 已依赖 runtime，可消费同一值，schema 校验和当前 default-schema 仍归 database。

单一配置来源继续为现有 loadup.database.multi-tenant.default-tenant-id，默认值复用当前共享常量；阶段 1 不同时重命名配置键。部署范围不是请求租户路由，enabled/required 的现有行为不能因移动 Filter 被悄悄放宽。runtime 需要显式声明 commons-context；不能依赖 database，避免反向边。此处为后续类型/装配归属决定，尚无新 Bean 或源码。

不引入第二套 ContextHolder、不让 WebMVC 强制依赖 JDBC、不改变原 ASYNC/ERROR dispatch 与已有字段继承。任务入口继续校验原工作记录和可信部署范围；移动 Web Filter 不替代后台入口核验。

## 6 文档核对与同步记录

| 文档 | 核对出的现状或差异 | 本轮同步 |
|---|---|---|
| Pay 总体架构、ROADMAP | 已使用十应用、PostgreSQL、单租户；阶段 0 产物尚无入口 | 加入基线/时序/依赖入口，区分阶段 0 完成与后续未实施 |
| acquire/payment/limitcenter/productcenter 架构 | 当前恢复任务和大部分事实约束已准确；不应回退到旧批次待接入描述 | 补当前流程证据入口、事务/交付完成语义、已登记缺口和编译职责现状 |
| productcenter client 架构 | 准入路径误为 /product/admissions，且“跨服务复用原 Java DTO”容易误解 app 直接引用 | 改为 /productcenter/admissions；明确仅基础设施使用提供方契约并映射本地模型 |
| acquire client 架构 | 错写事件 DTO 归 payment-client | 修为 contracts-events；明确首次回查与 receipt 重放区别 |
| limitcenter client 架构 | “令牌逐次读取”与当前 OAuth2 ServiceAccessTokens 获取/缓存不一致 | 改为每次显式传递目标凭证，缓存续取归服务认证模块 |
| payment client 架构 | 事件复用未明确独立工件及各类完成语义 | 明确共享事件归属，不用受理 ACK 代表资金/投影/通知全完成 |
| Framework 总体及 database 文档 | 默认租户/schema 映射的描述超出当前 enabled 分支行为 | 按真实分支修正，登记 Servlet optional 与无连接路由事实 |
| Modules 总体及 UPMS 文档 | Spring Data JDBC、AuthServer API、audit-client、未来 HTTP 组件等描述与当前依赖不符 | 修正实际依赖与装配例外；不宣称阶段 1 已实施 |

## 7 阶段 0 验收记录

| 标准 | 本轮结论 | 证据与限制 |
|---|---|---|
| G0-1 所有权 | 静态核对完成 | 本文第 2 节及迁移附录；框架表快照、业务事实、派生投影分别登记 |
| G0-2 五条时序 | 静态核对完成 | 五张时序图、身份/稳定键/事务/网络/恢复/完成条件；A0 自动续办和额度自动 expire 缺口明确保留 |
| G0-3 实际依赖和例外 | 静态核对完成 | 全部 174 POM 声明和 15 类例外；不把 test/optional/BOM 当生产边，不声称完整 effective classpath |
| G0-4 文档一致性 | 核对范围内同步完成 | 第 6 节；部署和运行结果继续标为待验收，不把目标设计写成现状 |
| G0-5 后续范围可定位 | 完成设计定位 | 演进方案阶段 1/2 文件、顺序与回归入口；阶段 1B 类型/配置归属在第 5 节收口 |

验收方式是源码/POM/配置/SQL/文档的静态交叉核对与文档链接检查。未执行编译、单测、数据库迁移、真实并发、渠道请求、部署或性能验证。未将未定位的恢复入口写成已实现，也没有修改业务代码以消除登记的例外。

## 附录 迁移声明与数据所有权

下表从源码迁移目录提取 CREATE TABLE 声明，包含应用冻结的技术表；不是线上已建表清单，也不代表同名组件表全局共享。应用实际 classpath 迁移集合另由其配置选择。

| 应用及本地迁移目录 | CREATE TABLE 声明 | 表名 |
|---|---:|---|
| [loadup-pay-acquire](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/resources/db/migration/pay/acquire) | 23 | loadup_task, loadup_task_operation, loadup_task_inbox, loadup_task, loadup_sequence_allocation, order_create_intent, acquire_order, order_contract_snapshot, order_fee, checkout_session, order_payment_allocation, order_payment_outcome, order_close_intent, order_extension_receipt, acquire_payment_event_receipt, fee_adjustment, fee_refund_receipt, merchant_notification_delivery, refund_notification_receipt, merchant_notification_wake_receipt, checkout_session_revocation_receipt, allocation_recovery_cursor, acquire_recovery_retry_receipt |
| [loadup-pay-admin](../loadup-pay-admin/src/main/resources/db/migration/pay/admin) | 1 | loadup_sequence_allocation |
| [loadup-pay-limitcenter](../loadup-pay-limitcenter/loadup-pay-limitcenter-infrastructure/src/main/resources/db/migration/pay/limitcenter) | 11 | loadup_sequence_allocation, limit_policy_head, limit_policy_revision, limit_rule_runtime, limit_bucket, limit_operation_gate, limit_reservation, limit_reservation_item, limit_execution_grant, limit_settlement_receipt, limit_refund_receipt |
| [loadup-pay-merchantcenter](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-service/src/main/resources/db/migration/pay/merchantcenter) | 5 | global_unique, loadup_sequence_allocation, merchant_profile, merchant_relationship, merchant_action_grant |
| [loadup-pay-notificationcenter](../loadup-pay-notificationcenter/src/main/resources/db/migration/pay/notificationcenter) | 3 | gotone_notification_service, gotone_service_channel, gotone_notification_record |
| [loadup-pay-payment](../loadup-pay-payment/loadup-pay-payment-infrastructure/src/main/resources/db/migration/pay/payment) | 34 | loadup_task, loadup_task_operation, loadup_task_inbox, loadup_task, loadup_sequence_allocation, payment_parent_gate, payment_gate, payment_order, payment_control_receipt, payment_input, channel_merchant_binding, channel_binding_revision, channel_binding_change_receipt, payment_attempt, routing_profile, payment_channel_receipt, payment_result_anomaly, channel_transaction_claim, payment_refund_capacity, refund_request, refund_allocation_plan, refund_order, refund_channel_receipt, refund_result_anomaly, refund_transaction_claim, order_refund_summary, refund_recovery_cursor, payment_action, payment_recovery_cursor, payment_recovery_retry_receipt, refund_acceptance_evidence, payment_reconciliation_capture, payment_reconciliation_line, payment_close_evidence |
| [loadup-pay-productcenter](../loadup-pay-productcenter/loadup-pay-productcenter-infrastructure/src/main/resources/db/migration/pay/productcenter) | 9 | loadup_task, loadup_task_operation, loadup_task_inbox, loadup_sequence_allocation, contract_catalog_version, merchant_contract, merchant_contract_revision, order_admission, contract_limit_publication |
| [loadup-pay-realtime](../loadup-pay-realtime/loadup-pay-realtime-service/src/main/resources/db/migration/pay/realtime) | 6 | loadup_sequence_allocation, trade_order_projection, trade_payment_projection, trade_refund_projection, trade_refund_summary, trade_projection_receipt |
| [loadup-pay-reconciliation](../loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure/src/main/resources/db/migration/pay/reconciliation) | 15 | loadup_task, loadup_task_operation, loadup_task_inbox, loadup_task, loadup_sequence_allocation, reconciliation_batch, reconciliation_input, reconciliation_entry, reconciliation_difference, reconciliation_import, reconciliation_statement_request, reconciliation_review, reconciliation_review_proposal, reconciliation_review_event, reconciliation_verification_request |

### 应用显式选择的迁移命名空间

| 应用配置 | loadup.flyway.locations |
|---|---|
| [acquire](../loadup-pay-acquire/loadup-pay-acquire-bootstrap/src/main/resources/application.yml) | classpath:db/migration/pay/acquire |
| [payment](../loadup-pay-payment/loadup-pay-payment-bootstrap/src/main/resources/application.yml) | classpath:db/migration/pay/payment |
| [productcenter](../loadup-pay-productcenter/loadup-pay-productcenter-bootstrap/src/main/resources/application.yml) | classpath:db/migration/pay/productcenter |
| [limitcenter](../loadup-pay-limitcenter/loadup-pay-limitcenter-bootstrap/src/main/resources/application.yml) | classpath:db/migration/pay/limitcenter |
| [merchantcenter](../loadup-pay-merchantcenter/loadup-pay-merchantcenter-bootstrap/src/main/resources/application.yml) | classpath:db/migration/pay/merchantcenter |
| [realtime](../loadup-pay-realtime/loadup-pay-realtime-bootstrap/src/main/resources/application.yml) | classpath:db/migration/pay/realtime |
| [reconciliation](../loadup-pay-reconciliation/loadup-pay-reconciliation-bootstrap/src/main/resources/application.yml) | classpath:db/migration/pay/reconciliation, classpath:db/migration/framework/file, classpath:db/migration/framework/transfer |
| [admin](../loadup-pay-admin/src/main/resources/application.yml) | classpath:db/migration/pay/admin, classpath:db/migration/framework/upms, classpath:db/migration/framework/audit |
| [notificationcenter](../loadup-pay-notificationcenter/src/main/resources/application.yml) | classpath:db/migration/pay/notificationcenter, classpath:db/migration/framework/notification |
| [oauth2-server](../loadup-pay-oauth2-server/src/main/resources/application.yml) | classpath:db/loadup-oauth, classpath:db/migration/pay/oauth2-server, classpath:db/migration/framework/audit |

oauth2-server 的身份存储来自 db/loadup-oauth，不能因为上表未在 pay 命名空间提取到 CREATE TABLE 就认为没有数据库表。admin 另装配 UPMS/Audit，notificationcenter 另装配 Notification，reconciliation 另装配 File/Transfer。所有位置归同应用数据源及迁移历史，不赋予跨应用读表权限。

### 核心唯一约束核对

- acquire 创单意图：payee_merchant_id + merchant_order_no；orderId 独立唯一。付款动作：order_id + actor_scope + action_key；paymentId 唯一；资金结果以 allocationId 唯一。
- payment：allocation_id、limit_operation_id 唯一；attempt 的 payment_id + attempt_no/request_key、渠道作用域 + channel_request_no 受唯一约束。退款申请及渠道退款 claim 保留各自业务唯一性。
- limitcenter：operation_id/payment_id 唯一；policyId + version 唯一；execution_grant 的 operation + epoch/requestKey 唯一；settlement_receipt 按 operation 唯一，refund_receipt 按 refundId 唯一。
- productcenter：准入按 orderId 回放；额度发布关联固定 contract/revision、merchant、policy 与 Task；实际约束与来源校验共同成立，不能只依赖进程内先查后写。

本地业务更改、回执与可靠记录的共同事务要求来自用例和实际 Spring 数据源装配；序列分配独立 REQUIRES_NEW，业务回滚不回收已分配号码，不应把序列行视为本地业务原子回滚的一部分。
