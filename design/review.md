# LoadUp Pay 架构与设计 Review

更新日期：2026-10-27。替代 2026-10-26 基于 020cd42 的审查结论；旧版可从 Git 历史查看。

审查基线：上轮 020cd42 → 本轮 702ac1c，另纳入 PaymentCaptureRpcMapper、ReconciliationLedgerService、RECONCILIATION.md
的调整；这些调整在收尾时已提交为 bc23872。关联框架读取基线为 loadup-framework/c00b8f48。审查期间代码持续推进，下文结论针对已读取源码，不覆盖之后新增改动。

本轮按 review-agent 的缺陷优先规则复核上轮问题，重点追踪恢复协议、事务边界、付款生命周期事件、支付宝
H5/微信付款码，以及新增对账采集和导入链路；不是这一区间全部文件的逐行审计。仅静态阅读源码、差异、相关测试与文档；未运行
IDEA、编译、测试、DDL、数据库操作或外部支付调用。报告修改不包含业务修复。

**用户已确认：没有生产交易数据，仍处于开发阶段，不考虑兼容性和存量迁移。**
以下数据库评估采用单一目标数据库、重新建立开发库的方案，不计双写、CDC、历史数据搬迁或混合版本运行。

## 1. 本轮发现

### [P2] 补齐对账比较任务耗尽重试后的恢复入口 — ReconciliationService.java:99

[ReconciliationService.start:99](../reconciliation/reconciliation-app/src/main/java/io/github/loadup/pay/reconciliation/app/service/ReconciliationService.java#L99)
遇到 RUNNING 批次直接返回。若比较阶段读取输入或写结果持续失败，默认 5 次后原生 DelayedTask 进入 FAILED，批次事务回滚后仍为
RUNNING；框架不再领取 FAILED，重复 start 也不会重排原任务。当前批次 Facade/HTTP/gRPC 没有比较任务 retry 入口，已有
import/retry 和 ledger/retry 分别操作文件导入及 payment 采集任务，无法恢复这个
run。故障消除后，原批次会永久表现为“运行中”，只能额外开发维护手段或另建批次绕过。

证据链：

- [默认任务配置](../reconciliation/reconciliation-bootstrap/src/main/resources/application-reconciliation-business.yml#L56)
  限制最大尝试次数；[DelayedTaskStore](../../loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/main/java/io/github/loadup/components/delayedtask/DelayedTaskStore.java#L78)
  的领取条件排除 FAILED，完成失败处理会写入 FAILED。
- [ReconciliationFacade](../reconciliation/reconciliation-client/src/main/java/io/github/loadup/pay/reconciliation/client/facade/ReconciliationFacade.java#L29)
  只有 create/attach/start/detail/differences；未找到比较任务的 retryFailed 调用。
- [现有失败测试](../reconciliation/reconciliation-app/src/test/java/io/github/loadup/pay/reconciliation/app/service/ReconciliationServiceTest.java#L113)
  明确验证结果持久化失败后批次留在 RUNNING，但未覆盖原生任务耗尽后的恢复。

建议增加受权的原比较任务重试命令，核对 tenant、原 caller、当前账户权限、runId、expectedTaskId 和实际 FAILED 状态，调用原生
retryFailed；保留两份 manifest 和原 runId。详情同时暴露 nativeStatus，区分业务执行中与调度已失败。不要用重复 start 无条件创建新
run 或修改已封存输入。

验收：使原比较任务耗尽重试，确认查询能看到 FAILED；修复故障后重试同一 task/run，最终只提交一份结果。补充过时 taskId、跨租户、已
RUNNING、已完成与并发重试场景。这里是源码可追踪的恢复缺口，并未声称已运行复现。

本轮未确认新的 P0/P1 回归。以下架构建议与 PG 适配阻断项分别记录，不把尚未执行的迁移风险当成现有 MySQL 缺陷。

## 2. 上轮 R1–R9 复核

| 编号                    | 本轮状态                   | 源码证据与剩余工作                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|-------------------------|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 真实数据库证明       | 已补测试源码；运行证明仍缺 | 当前有 8 个 *IT.java，包括 [AcquiringFinancialPersistenceIT](../acquire/acquire-infrastructure/src/test/java/io/github/loadup/pay/acquire/infrastructure/repository/AcquiringFinancialPersistenceIT.java)、[PaymentResultPersistenceIT](../payment/payment-infrastructure/src/test/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentResultPersistenceIT.java)、[PaymentCapturePersistenceIT](../payment/payment-infrastructure/src/test/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentCapturePersistenceIT.java) 和 [ProjectionPersistenceIT](../realtime/realtime-infrastructure/src/test/java/io/github/loadup/pay/realtime/infrastructure/repository/ProjectionPersistenceIT.java)。撤销“仓库没有数据库 IT”的旧描述；不能将源码等同于通过。 |
| R2 运营查询占写锁       | 已在源码修复               | [AcquireRecoveryOperationsService](../acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/AcquireRecoveryOperationsService.java#L83) 与 [PaymentRecoveryOperationsService](../payment/payment-app/src/main/java/io/github/loadup/pay/payment/app/service/PaymentRecoveryOperationsService.java#L82) 使用 readSnapshot/readInspection；retry 仍经 lockForRetry。保留并发运行验收。                                                                                                                                                                                                                                                                                                                                                                              |
| R3 恢复生命周期         | 部分完成                   | 已有 [RecoveryPollingPolicy](../payment/payment-domain/src/main/java/io/github/loadup/pay/payment/domain/policy/RecoveryPollingPolicy.java)、渠道恢复并发限制、UNKNOWN 快照指标和分级查证。[指标契约](RECOVERY-INVENTORY.md) 明确仅统计 UNKNOWN 名义金额，不代表实际 held。任务/证据保留、人工处置闭环及容量验收仍待完成。旧的固定 30 秒生成量估算不再适用于当前策略。                                                                                                                                                                                                                                                                                                                                                                                                                |
| R4 绑定不变量散落       | 主要收敛已落源码           | [PaymentExecutionBindings](../payment/payment-app/src/main/java/io/github/loadup/pay/payment/app/service/PaymentExecutionBindings.java)、[PaymentExecutionContext](../payment/payment-domain/src/main/java/io/github/loadup/pay/payment/domain/model/PaymentExecutionContext.java) 与 PaymentAttemptBinding/PaymentGateBinding 集中共同关系。继续保留提交、观察、关单的不同前置条件，不再建议增加万能 Validator。                                                                                                                                                                                                                                                                                                                                                                     |
| R5 read 语义混淆        | 已在源码修复               | [PaymentAcceptanceTransactions](../payment/payment-app/src/main/java/io/github/loadup/pay/payment/app/service/PaymentAcceptanceTransactions.java#L47) 等明确区分 writeLocal、readSnapshot、withTenantContext；写 RC、读 RR、外层事务限制均有表达。不要再以“同名 read 不一致”列问题。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| R6 持久快照复用 Command | 已在源码修复               | [payment 快照](../payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/model/PaymentIntentSnapshot.java) 与 [acquire 快照](../acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/model/PaymentIntentSnapshot.java) 由本服务拥有，codec/mapper 转换公开 Command；当前开发期不需要旧格式兼容器。                                                                                                                                                                                                                                                                                                                                                                                                              |
| R7 恢复机械流程重复     | 已完成一轮合理提取         | [RecoveryTaskProtocol](../payment/payment-app/src/main/java/io/github/loadup/pay/payment/app/service/RecoveryTaskProtocol.java)、RecoveryPosition/RecoveryTaskKey 收敛纯协议。付款/退款锁序和金融决策仍分别持有，符合原建议。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| R8 文档与边界门禁       | 主要改善已落源码           | README/ARCHITECTURE 已更新，历史材料迁出当前能力说明；[PayModuleBoundaryTest](../channel/channel-core/src/test/java/io/github/loadup/pay/channel/core/PayModuleBoundaryTest.java) 增加跨服务/分层约束。当前能力与未运行验收仍需持续同步。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| R9 深分页成本           | 保留为条件性建议           | [TradeProjectionGatewayImpl](../realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/repository/TradeProjectionGatewayImpl.java#L305) 仍 count + offset。没有数据量/执行计划证明已超预算；不要为此抢先引入搜索集群。导出和大范围遍历再考虑游标。                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |

## 3. 架构判断与后续重点

**保留现有领域边界：六个交易相关服务、独立 reconciliation，以及渠道插件。当前更需要完成故障恢复与数据库证明，不需要增加服务层级或通用
Saga 平台。**

### 3.1 付款事实、展示快照和对账证据分开持有

[PaymentEvents](../payment/payment-app/src/main/java/io/github/loadup/pay/payment/app/service/PaymentEvents.java#L69)
将终态资金投递和完整生命周期快照分开：非终态只更新 realtime，终态继续向 acquire/limit/risk 投递；同一付款事务内冻结快照并写
Outbox。这使 realtime 可以展示 INIT/PROCESSING/UNKNOWN/CLOSING，同时避免非终态被下游误当资金结果。

[PaymentCaptureReaderImpl](../payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentCaptureReaderImpl.java#L63)
直接读取 payment 权威证据，在 RR
中检查账户、未决收款、退款受理日期，再形成完整有界集合；[ReconciliationLedgerService](../reconciliation/reconciliation-app/src/main/java/io/github/loadup/pay/reconciliation/app/service/ReconciliationLedgerService.java#L94)
在事务外取完整分页并验证摘要，之后本地提交文件引用、封存输入和批次引用。应保留这一方向，不用 realtime 投影代替资金事实源。

采集时间不是全局提交水位；MATCHED 也只是两份封存输入相符。[采集契约](PAYMENT-RECONCILIATION-CAPTURE.md)
已说明迟到事实需新快照/新批次复核，以及早期未决记录可能阻断后续账期。这些是明确的保守边界，本轮不列为回归缺陷。

### 3.2 完善恢复能力时同时覆盖“金融任务”和“非金融任务”

付款/退款已有恢复、运营查询和受控重试，但新增比较任务遗漏了耗尽后的恢复入口（见
P2）。以后增加持久任务时，应在同一用例设计中列出正常完成、重放、尝试耗尽、执行者崩溃、业务已提交但 ACK 丢失的行为。业务状态和
native 状态分别呈现，复用已有 DelayedTask API 即可。

UNKNOWN 指标不能替代所有未收敛金额统计，尤其 PROCESSING/CLOSING；是否需要额度
held、投影新鲜度、失败任务积压，应按运营目标分别定义，不能相加推导可释放资金。恢复变慢或人工升级只改变查询/处置节奏，不授予新扣款权。

### 3.3 不把主动限制当作已经实现的闭环

支付宝 H5 本地签名 URL 与渠道受理观察已分离；微信付款码成功响应后查询原交易取得证据。服务商 H5、丢失本地 action
后重新生成、微信撤销补偿、官方账单自动来源、Transfer 遗留 RUNNING 的受控恢复均有明确未实现说明。继续按现有契约补齐，不以同名普通
close/refund 代替特殊协议。

下一轮优先级：先修复比较任务恢复，再在目标数据库证明事务/幂等/租约，随后补容量与证据保留。R9
的分页优化由实际查询计划决定。无需为了代码行数减少而把不同业务状态机合并。

## 4. MySQL → PostgreSQL：开发期成本与收益

### 4.1 决策建议

**若项目长期打算采用 PostgreSQL，建议在当前无生产数据阶段完成统一切换；先做共享组件验证，再重建七个服务的开发库。**
不建设长期双数据库兼容层，不搬运历史测试数据，不保留旧 DDL 的运行兼容分支。Git 保留历史即可；本评审不执行重建或任何数据库写入。

从当前支付核心看，没有只能靠 PG 解决的资金一致性问题。换库的直接收益主要是约束表达、后续查询能力和统一技术栈；不能承诺吞吐提升、延迟下降或数据库费用下降。若现有团队/部署平台只擅长
MySQL，又没有 PG 查询需求，继续用 MySQL 同样合理，应优先完成业务验证。

### 4.2 改动范围：业务 DDL 之外还有共享依赖

以下为本轮源码盘点，不是实际部署数据库表数；统计 src/main/resources/db/migration 下的 SQL，排除 design/sql 草案。

| 服务           | 迁移脚本数 | CREATE TABLE 声明数 |
|----------------|-----------:|--------------------:|
| acquire        |         16 |                  18 |
| payment        |         18 |                  28 |
| limitcenter    |          4 |                  10 |
| customer       |          1 |                   2 |
| product        |          2 |                   2 |
| realtime       |          1 |                   5 |
| reconciliation |          2 |                   5 |
| 合计           |     **44** |              **70** |

41 份脚本含 DATETIME、40 份含 TINYINT、40 份含 COLLATE、14 份含 LONGTEXT、1 份含 MEDIUMTEXT、1 份含 GENERATED
ALWAYS。脚本数不等于需要保留的版本数：开发库可重建时，可以按服务整理为清晰的 PG 初始基线，但必须保留累计后的约束和索引。

| 层次         | 必须处理的范围                                                                                   | 判断                                                                                                                                                                                                                                                                             |
|--------------|--------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| pay 启动装配 | 七个 bootstrap 的 business profile、JDBC 驱动/URL、Flyway 数据库支持、开发与部署配置             | [payment POM](../payment/payment-bootstrap/pom.xml#L79) 与 [reconciliation POM](../reconciliation/reconciliation-bootstrap/pom.xml#L67) 明确依赖 flyway-mysql/mysql-connector-j，不能只改 URL。                                                                                  |
| pay 存储适配 | 上述 DDL、MyBatis-Flex 生成 SQL/类型映射、enum/时间/金额、锁与版本 CAS                           | domain/公开 DTO 原则上不因换库改变；变动集中在基础设施。                                                                                                                                                                                                                         |
| 可靠性组件   | database 序列分配、Outbox/Inbox、DelayedTask、GlobalUnique                                       | 原子性与幂等热路径，必须先证明支持 PG。                                                                                                                                                                                                                                          |
| 业务依赖闭包 | customer 引用 merchant，product 引用 contract；reconciliation 的 File/Transfer 与文件引用/任务表 | 这些共享模块的迁移和同库事务必须一起适配；不能只处理 pay 目录内 70 个表声明。                                                                                                                                                                                                    |
| 测试与规范   | 8 个 pay IT、相关框架 IT、测试 fixture/原生 SQL、AGENTS 数据库类型约定、README                   | [SharedPostgreSQLContainer](../../loadup-framework/loadup-components/platform/loadup-components-testcontainers/src/main/java/io/github/loadup/components/testcontainers/database/SharedPostgreSQLContainer.java#L55) 已有实现，不必新造测试平台；其存在不等于其他组件已支持 PG。 |

框架 database POM 的 MySQL 驱动/Flyway 是 **test scope**，不是业务运行时硬依赖；应分别改测试后端与业务启动依赖。File 的 S3
字节存储可保持不变，但其本地元数据和 Transfer 存储仍需适配。评估不包含与 pay 无关的整个框架所有模块换库。

### 4.3 最重要的语义改造

**A. 修复事务内重复键后的继续执行。**

以下组件使用 insert → catch DuplicateKeyException → 同一事务 select 的模式：

- [DefaultGlobalUniqueTemplate.claim](../../loadup-framework/loadup-components/reliability/loadup-components-globalunique/src/main/java/io/github/loadup/components/globalunique/DefaultGlobalUniqueTemplate.java#L65)。
- [OutboxInbox.consume](../../loadup-framework/loadup-components/reliability/loadup-components-outbox/src/main/java/io/github/loadup/components/outbox/OutboxInbox.java#L50)。
- [DelayedTaskTemplate.register](../../loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/main/java/io/github/loadup/components/delayedtask/DelayedTaskTemplate.java#L71)。

PG 中约束错误会使当前事务进入失败状态，Java catch 不会恢复事务；继续查询无法正常完成幂等处理。优先使用针对正确业务唯一键的
ON CONFLICT DO NOTHING，并在未插入时校验已有记录；或明确使用 savepoint/整笔事务回滚后重试。不能用无差别“忽略所有冲突”掩盖其他唯一约束错误，也不能换成会覆盖原证据的无条件
upsert。[PG 事务错误恢复](https://www.postgresql.org/docs/current/tutorial-transactions.html)、[PG INSERT](https://www.postgresql.org/docs/current/sql-insert.html)、[ROLLBACK TO SAVEPOINT](https://www.postgresql.org/docs/current/sql-rollback-to.html)。

[PaymentAcceptanceTransactions.writeLocal](../payment/payment-app/src/main/java/io/github/loadup/pay/payment/app/service/PaymentAcceptanceTransactions.java#L58)
和 [DatabaseSequenceRangeAllocator.reserve](../../loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/sequence/DatabaseSequenceRangeAllocator.java#L53)
在 transaction.execute 外捕获异常，已经具备整事务回滚后重试的结构；它们与上述问题不同。仍需验证 PG 异常转换及并发行为，不应机械重写为全局
SERIALIZABLE。

**B. 替换 SQL 方言，并明确“当前时间”口径。**

[JdbcOutboxRepository](../../loadup-framework/loadup-components/reliability/loadup-components-outbox/src/main/java/io/github/loadup/components/outbox/JdbcOutboxRepository.java#L109)
使用 TIMESTAMPADD，指标查询使用 TIMESTAMPDIFF；需要改成 PG 时间运算。PG CURRENT_TIMESTAMP
固定为事务开始时间，租约判断应明确采用语句时间或实时钟，不能只做字符串替换；本地无时区时间列还需统一会话时区。[PG 时间函数](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT)。

Outbox 已采用领取锁，DelayedTask 采用候选查询 + 条件 UPDATE/CAS；两者不是同一个调度实现。先保留 claim token、lease、attempt
的现有语义，验证后再考虑 PG 专用优化。

**C. 重建 DDL 时保留领域含义，不机械替换类型。**

| 当前表示                        | 建议 PG 表示/处理                         | 必须验证                                                                                                              |
|---------------------------------|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| 金额分、版本、generation        | BIGINT，保留 CHECK                        | Long 上限、SUM 映射、CAS 受影响行数；不改浮点金额。                                                                   |
| DATETIME(6) / LocalDateTime     | 本轮先保持 timestamp(6) without time zone | JVM/Clock/JDBC/DB 会话一致；微信 +08:00 账单日界、微秒与 RPC instant 往返。若改 timestamptz，需另列跨层时间模型重构。 |
| TINYINT 0/1                     | SMALLINT + CHECK，保留当前整数映射        | BaseDO.deleted、查询 eq(0) 及更新。若统一 boolean，应同时修改所有映射/规范，不能局部替换。                            |
| LONGTEXT/MEDIUMTEXT             | TEXT                                      | 长度上限由当前 codec/约束保持。                                                                                       |
| VARCHAR 主键与业务编号          | 保留原字符串 ID、原编号算法               | 租户唯一键和分段分配并发；不因 PG 有 sequence 就换业务编号体系。                                                      |
| ENGINE/CHARSET/KEY/列内 COMMENT | 删除 MySQL 选项，重写索引/COMMENT ON 等   | 外键、CHECK、NOT NULL、唯一键及默认值无遗漏；空库能完整初始化。                                                       |
| utf8mb4_bin、可空唯一键         | 明确 PG collation 与 NULL 语义            | 大小写、尾空格、非 ASCII 身份、排序/分页。不要假定默认 locale 或 C 与旧排序规则完全相同。                             |

**D. 带摘要的原始载荷先用 TEXT。**

[PaymentAcceptanceCodecImpl](../payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/codec/PaymentAcceptanceCodecImpl.java#L63)
对原 JSON 的 UTF-8 字节计算 SHA-256，读取时验证。JSONB 会改变空白/对象键顺序等表示；若仍沿用原载荷摘要，读取会被判损坏。因此这些证据载荷保留
TEXT；确有检索需求时另建结构化字段/投影，不为了使用 PG 特性批量改
JSONB。[PG JSON 类型](https://www.postgresql.org/docs/current/datatype-json.html)。这与旧数据兼容无关，新写入记录也要满足同一摘要契约。

**E. Flyway 只加载选定后端的脚本。**

当前 business 配置使用 classpath:db/migration，共享组件也从此路径提供迁移，GlobalUnique 还有 mysql 子目录。采用 PG
单后端时应替换相关资源并核查依赖 JAR，不要把两种方言同时留在扫描范围，也不要对所有环境自动执行 Flyway
clean/repair。先在隔离的新开发库验证完整依赖闭包，再由开发者按需清理可丢弃环境。

### 4.4 收益与代价

| 维度           | 对本项目的收益                                                         | 限制/代价                                                                         |
|----------------|------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| 约束表达       | customer 的 active_slot 生成列可用带状态谓词的唯一索引表达，减少辅助列 | 必须保留 INIT/ACTIVE 的准确谓词；deleted 是否加入谓词是业务规则变化，应单独决定。 |
| 待处理集合索引 | 对 Outbox/任务的少量活跃状态可评估 partial index，减少不必要的索引内容 | 查询条件必须能匹配谓词，参数化 SQL 下需检查真实计划；不是所有索引都应改 partial。 |
| 查询与分析     | JSONB/表达式索引可用于新建的运营检索投影，对账和统计有后续发展空间     | 当前核心数据已经结构化，这部分是潜在收益；证据 JSON 保持原字节。                  |
| 幂等 SQL       | ON CONFLICT 能直接表达有条件插入，减少以异常处理正常重复请求的路径     | 必须限定冲突目标、核验原内容；不能替代业务幂等约束。                              |
| 性能/资金安全  | 两种数据库都能支撑当前本地事务 + Outbox + CAS 架构                     | 换库不会自动消除重扣风险、分布式一致性或深分页成本，没有证据可给性能提升比例。    |
| 运维           | 若团队统一 PG，可减少后续数据库栈分裂                                  | 需掌握 autovacuum、死元组、长快照、WAL 与备份恢复；高更新任务表尤应关注。         |

customer
当前约束见 [授权 DDL](../customer/customer-infrastructure/src/main/resources/db/migration/V20261021160000__create_customer_authorization.sql#L18)
。Partial index 的用途和谓词限制依据 [PG 部分索引文档](https://www.postgresql.org/docs/current/indexes-partial.html)
；更新/删除产生的旧行回收与统计维护依据 [PG vacuum 文档](https://www.postgresql.org/docs/current/routine-vacuuming.html)
。表中的项目收益是结合源码的评估，不是压测结果。

### 4.5 工作量估算：16–28 人日

这是粗粒度工程估算，假设熟悉 Java/Spring/MyBatis-Flex，目标为 pay 七服务及其必要共享依赖的 PG
单后端可运行版本；包含针对性验证，不包含业务新功能或无关框架模块改造。人日是总投入，不是自然日承诺。

| 工作包                   |      人日 | 交付物                                                                                              |
|--------------------------|----------:|-----------------------------------------------------------------------------------------------------|
| 共享依赖最小验证         |       1–2 | 确认 PG/JDBC/Flyway 版本组合；重复键、事务失败恢复、Outbox/任务/序列最小运行证据。                  |
| 共享组件与依赖闭包适配   |       5–8 | Outbox/Inbox、DelayedTask、GlobalUnique、序列及 merchant/contract/File/Transfer 所需 DDL/映射修正。 |
| 业务数据库基线重建       |       3–5 | 七服务 PG DDL、约束/索引、空库初始化；可以合并开发期历史迁移。                                      |
| 应用与配置切换           |       1–3 | bootstrap/BOM/驱动/Flyway、数据源、生成 SQL、类型及异常映射。                                       |
| 集成与并发验证           |       4–7 | 转换现有 pay IT 并补 PG 特有用例；框架事务契约、恢复及对账封存运行结果。                            |
| 开发环境、规范与文档收尾 |       2–3 | 统一测试镜像/目标版本、配置与数据库约定、开发初始化和常规备份恢复说明。                             |
| 合计                     | **16–28** | 完成范围内 PG 验证，不只做到连接数据库。                                                            |

单人约 3–6 个工作周；两人可并行推进 DDL/配置与组件测试，但共享事务语义验证仍在关键路径。若仅做
DDL/驱动替换会更快，却不能称为迁移完成。精度有限：尚未实际运行 PG、核验全部生成 SQL或测量数据规模，首个工作包结束后应更新估算。

费用可按“16–28 × 团队综合人日成本 + 开发/CI 数据库资源”计算；没有实例规格、流量和平台报价，不给出虚假的云费用节省数字。无需支付存量迁移、双写平台或在线切换的工程成本。

### 4.6 实施顺序与完成条件

1. **先建立共享组件的 PG 证明。** 固定目标版本和测试镜像；现有 PG 容器默认镜像为 15-alpine，不能将其默认值直接当成部署版本决策。验证
   Inbox/GlobalUnique/DelayedTask 重复键、Outbox 租约、序列并发。此阶段失败先修框架，不继续机械批量换 DDL。
2. **建立 PG 单一开发基线。** 同步七服务及实际引入的共享迁移、驱动、Flyway 和类型规范；新库初始化，移除这些运行路径中的 MySQL
   方言依赖。保留每服务数据所有权，不趁换库合并七个服务。
3. **按业务不变量验收。** 相同请求并发只生效一次；Y2 首提资格唯一；状态/receipt/Outbox/任务同事务回滚；旧
   lease/token/generation 不取得新权限；退款不超额；投影旧版本不覆盖新版本；对账 RR、完整封存及失败比较任务重试均通过。
4. **给出明确验证记录。** 列出数据库版本、schema、测试范围、结果及关键 SQL 计划；真实 PG 驱动的错误分类/超时和 RR 行为需要实测。PG
   的 RR/Serializable 发生序列化错误时需要整事务重试，不能仅重放最后一条
   SQL。[PG 事务隔离](https://www.postgresql.org/docs/current/transaction-iso.html)。

停止条件是目标数据库上关键契约可证明且开发配置一致；不要求先增加分区、读写分离、JSONB 全量改造或数据库抽象平台。本轮只完成评估与报告，尚未实施上述工作。

## 5. 本轮验证边界

- 新增一项确认的 P2 恢复缺口；上轮问题已按当前源码更新状态，未把有意后置能力重复列为缺陷。
- 8 个 pay 数据库 IT 的存在经源码盘点确认；本轮没有运行，无法证明当前 MySQL 或未来 PG 已通过集成验收。
- 未做真实渠道产品验收、数据库压测、部署检查；跨服务 API/安全实现仅针对所追踪链路抽查，不构成全面安全审计。
- 仅更新本报告与 ROADMAP；保留工作区已有业务改动，不进行业务修复、换库、构建、提交或推送。
