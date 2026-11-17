# 阶段 4：业务端口与模块职责收口

日期：2026-11-17。状态：源码、调用方及回归测试源码已调整；未运行构建、测试、数据库或部署验收。阶段 1B、3 的运行门禁仍独立开放。

## 1 范围与依赖方向

依据 [架构演进方案](ARCHITECTURE_EVOLUTION.md) §3.3、§8，以用例所需能力定义消费方端口。app 决定业务顺序、本地事务和结果绑定；infrastructure 负责 HTTP、凭证、提供方契约和错误翻译；bootstrap 负责装配。远端 client 仍只由基础设施模块引用，app 不新增远端依赖，不新增 Maven 模块。

本轮核对 acquire、payment、productcenter、limitcenter、reconciliation：五个 app POM 均未直接声明其他应用 client；已有本地模型隔离保留。实际调整集中在商户准入事实投影与付款额度能力隔离。接口数量不作为验收指标；完整证据、许可及快照所需字段不能为减少类型而裁剪。

```text
OrderAdmissionService → CustomerEligibilityLookup → AdmissionEligibility
                             ↑                       ├─ MerchantEligibilityFacts
CustomerEligibilityHttpLookup                       └─ 商户策略事实（不可变 Map）
  → CustomerHttpService + ProductActorProvider
  → AdmissionEligibilityConverter

PaymentSubmissionPreparation → PaymentLimitExecution（reserve / begin / verify）
PaymentLimitOutcomeDelivery → PaymentLimitSettlement（confirm / release）
RefundLimitDelivery → RefundLimitRestoration（restoreRefund）
                             ↑
                    PaymentLimitHttpClient
                      → LimitCenterHttpService + ServiceAccessTokens
```

三个额度端口由同一个适配器实现，复用原命名 HTTP 连接池、凭证源及配置，不增加 Bean 实现或外部交互次数。

## 2 商户准入：返回本地业务事实

原 `CustomerEligibilityLookup` 返回本地复制的 MerchantEligibilityDTO，app 再把 grantVersion 等字段映射成领域资格事实，同时从 MerchantProfileDTO 提取策略事实。CREATE_ORDER 命令和 MerchantAction 枚举也在 app 复制，虽不直接引用远端类型，仍让提供方报文结构扩散。

调整后：

- `app/model/AdmissionEligibility.java` 表达本地资格决定和准入所需的商户事实，事实 Map 防御性复制；不向 app 暴露 grantId、授权传输命令或提供方 DTO。
- `app/port/CustomerEligibilityLookup.java` 返回 AdmissionEligibility。
- `infrastructure/client/CustomerEligibilityHttpLookup.java` 直接构造提供方 CREATE_ORDER 请求；先核验主体、版本、grant、允许/拒绝原因和 merchant，再做语义投影。
- `infrastructure/converter/AdmissionEligibilityConverter.java` 将已验证数据投影为 MerchantEligibilityFacts 与固定商户事实键。grantVersion 缺失的合法拒绝继续映射为授权版本 0。
- `app/service/OrderAdmissionService.java` 直接使用资格事实；仍在原合同决策分支把商户字符串事实转换成 TypedValue 并加入交易金额/币种。
- `app/converter/OrderAdmissionConverter.java` 删除报文到资格事实的转换，只负责本领域对外表示。

商户事实键保持 `merchant.id/code/type/industry/country/active/province/city`；province/city 仅在非 null 时加入，空字符串继续保留。类型、长度校验留在原决策时机，避免在合同已停止、原准入重放或并发胜者回放前提前触发策略错误。`merchant.active` 继续作为 BOOLEAN，其余商户事实为 STRING；交易金额仍为 INTEGER。

删除 app 内四个传输镜像：

- `app/model/merchantcenter/command/MerchantActionAuthorizeCommand.java`
- `app/model/merchantcenter/dto/MerchantEligibilityDTO.java`
- `app/model/merchantcenter/dto/MerchantProfileDTO.java`
- `app/model/merchantcenter/enums/MerchantAction.java`

对应 `RemoteContractConverter` 请求、响应及枚举双向映射和两个响应包装方法删除。`MerchantContractFactsHttpProvider` 直接读取提供方 profile 并继续投影为本领域公开的 MerchantProfileDTO；删除其无用途的 converter 构造参数，同步测试装配。该 provider 原有非空白字段筛选规则与准入规则不同，分别保留，避免统一工具改变语义。

## 3 付款额度：按消费能力分离

删除 `app/port/PaymentLimitClient.java`，新增：

| 端口 | 消费方 | 能力与保留的验证 |
|---|---|---|
| PaymentLimitExecution | PaymentSubmissionPreparation | 预占、开始执行、核验许可；原 operation/payment/attempt/epoch/金额/币种/permit 绑定检查 |
| PaymentLimitSettlement | PaymentLimitOutcomeDelivery | 消耗确认、释放；原事实来源、状态、ACK 关联检查 |
| RefundLimitRestoration | RefundLimitDelivery | 退款恢复；原累计值、版本、重复投递和结果检查 |

`PaymentLimitHttpClient` 实现三个接口，六个方法及 exchange/credential 行为保持。PaymentAttemptService 未调用额度端口，移除该字段和构造依赖，并同步所有构造调用方；尝试状态推进仍由原执行锁和事务负责。

以下内容保留：本地 Limit*Command/DTO、PaymentLimitConverter 的命令构造、许可与 ACK 全字段。它们当前承载稳定操作键、财务证据与消费者逐字段验证；不能仅为缩短类型名而换成 boolean 或 void。后续只有明确减少业务耦合且有独立期望值证明时才继续投影。

## 4 其余边界核对结果

| 边界 | 本轮决定 | 理由 |
|---|---|---|
| Acquire：OrderAdmissionClient、付款受理/关闭/结果端口 | 保留 | 已按用例隔离；准入来源、父栅栏及权威付款事实均需完整验证 |
| ProductCenter：PolicyActivationClient / PolicyPublicationCodec | 保留 | 冻结策略、规则、hash、可信 ACK 与原发布回放是同一完整协议 |
| LimitCenter：PaymentEvidenceClient | 保留 | 阶段 3 已区分自包含证据与同步权威查询；不能删除仍需的兜底查证 |
| Reconciliation：captures/statements/verification 等端口 | 保留 | 已按对账能力分开，源证据及人工复核语义不能折叠 |
| Acquire/Payment → Realtime | 保留 | 不可变目标投递及 ACK 已隔离，阶段 3 的可靠交付机制继续生效 |

不删除有实际调用的 client Maven 依赖，不增加 pay-common 或通用远程服务入口。Framework、Modules 的公开接口无需调整。

## 5 实施顺序与验收矩阵

调整顺序：核对实际消费者和构造调用 → 定义本地事实/窄端口 → 修改基础设施实现 → 同步服务和测试装配 → 删除失去用途的模型/映射 → 静态检查 → 文档同步。真实运行证明独立执行。

| 编号 | 验收条件 | 测试/检查入口 | 本轮状态 |
|---|---|---|---|
| G4-1 | app 不依赖提供方 client/HTTP 类型，反向依赖仍为 infrastructure → app | 五个 app POM、imports、原有依赖方向 | 静态核对；未编译 |
| G4-2 | 只有传输验证通过的数据进入准入，主体错配、缺凭证、事务内调用均拒绝 | CustomerEligibilityHttpLookupTest | 已同步源码，未运行 |
| G4-3 | 资格事实版本、商户键和值完整；可选位置字段与不可变性符合原语义 | CustomerEligibilityHttpLookupTest | 已增加独立期望值，未运行 |
| G4-4 | 合法拒绝与 NOT_FOUND 仍为原业务拒绝；其他远端错误保持 DEPENDENCY_UNAVAILABLE，不泄漏描述 | CustomerEligibilityHttpLookupTest | 已同步源码，未运行 |
| G4-5 | 原准入回放不重新查资格；合同停止、决策持久化、竞争回放和错误不变 | OrderAdmissionServiceTest、ContractPersistenceIT | 已同步相关 fixture；未运行 |
| G4-6 | 合同事实 provider 的凭证、主体校验、空白过滤与错误不变 | MerchantContractFactsHttpProviderTest、MerchantContractIT | 已同步装配；未运行 |
| G4-7 | 执行/结算/退款消费者只依赖所需能力；不存在 PaymentLimitClient 残留 | 服务 imports/构造器、PaymentLimitHttpClient | 静态核对；未编译 |
| G4-8 | 额度调用事务隔离、凭证、错误码以及许可/ACK 绑定不变 | PaymentLimitHttpClientTest、PaymentSubmissionPreparationTest、PaymentLimitOutcomeDeliveryTest、RefundLimitDeliveryTest | 测试源码保持或同步；未运行 |
| G4-9 | 本地事务、任务提交与事实投递可靠性、幂等和失败恢复不变 | FinancialPersistenceIT、阶段 3 财务证据门禁 | 已同步执行端口装配；未运行 |

静态检查不能证明 Spring 装配、MapStruct 生成代码、PostgreSQL 锁/事务或真实资金链路通过。后续按本地构建纪律验证受影响模块，阶段 5 后续已补门禁源码与部署组合评估，见[阶段 5 记录](PHASE5_IMPLEMENTATION.md)；运行验收仍开放。

## 6 本轮静态核对记录

- 删除类型的生产/测试 Java 引用已清理；构造调用方和测试 Bean 已同步。
- `git diff --check` 通过；本轮四份架构/进度文档的相对链接均可解析。
- IDEA 检查已覆盖调整后的服务、适配器和回归源码；修正了删除 PaymentAttemptService 额度依赖后测试中遗留的两条 mock 断言。最后复查该测试无 Java 错误。
- IDEA 仍报告工作区 Spring 聚合模型中接口与实现同时作为候选的装配诊断，以及 FinancialPersistenceIT 缺失数据库表元数据；这些不作为运行装配通过或 SQL 通过的证据。MapStruct 生成和实际应用上下文仍待本地构建/测试确认。
- 未执行构建、单测、集成测试、迁移或部署；未改动工作区既有 NotificationCenter 暂存变化。
