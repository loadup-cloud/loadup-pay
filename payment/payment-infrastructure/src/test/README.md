# Payment 持久化集成测试

`RecoveryPersistenceIT` 使用 `@EnableTestContainers(MYSQL, reuse=false)`、Testify、实际 Flyway 迁移、生成 Mapper、真实
Gateway 和
native DelayedTask。没有 mock DataSource、内存数据库或手写替代 schema。`mysql-it` 为显式 Failsafe profile，默认单测不启动该
IT。

## 当前用例

| 用例                     | 真实记录断言                                                                    |
|--------------------------|---------------------------------------------------------------------------------|
| 首轮登记故障             | 游标和原 native 任务同时不存在                                                  |
| 后继提交故障             | 游标仍为原 generation，后继任务不存在                                           |
| 两个并发 worker          | 只有一个竞争者提交 generation+1，后继任务存在                                   |
| 只读快照与写事务并发     | 写事务在读事务结束前提交；原读事务仍看到旧游标/任务视图，下一快照看到新值       |
| native FAILED 重试与审计 | 注入故障后原任务仍 FAILED 且回执不存在；正常提交后原任务 PENDING 且回执精确匹配 |

这些用例只覆盖恢复持久化机制，不模拟真实扣款，不验证原资金聚合/Y2/退款容量/Outbox/投影。R1 的完整验证清单仍以
`design/review.md` 为准，不用这几个用例代替全项目一致性结论。

## 运行

需要 Docker 可用，并已具备当前 loadup-framework 和本项目依赖。以下命令尚未执行，遵守用户当前暂停验证要求：

```bash
mvnd clean verify -pl payment/payment-infrastructure -Pmysql-it -Dit.test=RecoveryPersistenceIT -Dskip.spotless=true -Dskip.spotbugs=true
```

这是单模块命令，不自行扩大为 full reactor。test/ci 配置均使用容器注入的数据源，不连接业务数据库；系统本地时区沿项目契约处理。
源码、编译、单测、IT 实际运行和真实环境验收需分别记录。当前状态：仅有测试源码，未运行。

## FinancialPersistenceIT：Y2 与退款容量边界

新增同一 `mysql-it` profile 下的 5 个真实 MySQL 用例源码：

| 用例             | 实际入口与断言                                                                                                                                                    |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 双 Y2 竞争       | 两个真实 PaymentSubmissionPreparation.prepare 在远端准备后同步竞争；实际门禁/付款/attempt Gateway，仅一个 ticket，数据库单一 SUBMITTING/epoch/rowVersion/原请求号 |
| Y2 ticket 丢失   | 原 attempt 已提交后重新读取并调用 prepare，得不到新 ticket，不再咨询或申请远端权限，原请求号保留                                                                  |
| attempt 更新回滚 | 实际锁与 Gateway 写后注入异常，epoch/permitHash/rowVersion 恢复原值，之后才能取得原首次 ticket                                                                    |
| 两个退款计划竞争 | 真实父门禁/容量锁、FIFO 策略与 RefundGateway.insertPlan，共享成功容量只能提交一份 7000 分计划，最终可用 3000 分                                                   |
| 退款计划提交回滚 | 实际 request/plan/child/capacity 多表写入后异常，全部业务行消失且容量版本/预占恢复                                                                                |

没有 mock 数据源、MyBatis Mapper、事务管理器或 Gateway。额度/风控/渠道 IO 使用受控测试替身；无输入 H5 的输入访问和 Y2
编排为真实实现，
不会调用 KMS。Y2 正常路径未使用的终态事件组件为测试替身，并断言没有调用；这些用例不能用来证明结果与 Outbox 原子性。

退款用例把成功款项容量作为边界前置数据，测试实际 FIFO、计划持久化与容量 CAS，不声称验证了付款成功入账生成容量、
RefundPlanningService 的全部鉴权/准入或退款渠道执行。用例中容量不足经本地事务 helper 映射的 INVALID_ARGUMENT 只用于边界测试；
商户 API 的 REFUND_AMOUNT_EXCEEDED 映射仍由实际规划服务负责，不在这里替代验证。

可选择两组 IT（命令尚未执行）：

```bash
mvnd clean verify -pl payment/payment-infrastructure -Pmysql-it -Dit.test=RecoveryPersistenceIT,FinancialPersistenceIT -Dskip.spotless=true -Dskip.spotbugs=true
```

当前仍是源码状态：未编译、未运行 Docker/测试。该组只覆盖上述边界，其他 R1 用例分别记录在后续章节、acquire 和 realtime
的测试说明中；全部集成源码都不能替代实际运行证据。

## PaymentResultPersistenceIT：Y3 多表与真实 Outbox

新增 10 个真实 MySQL 测试输入，使用实际 PaymentExecutionService.progress、PaymentResultService、金融锁序、Gateways、生成
Mapper、Codec、MapStruct、事务与 Outbox：

| 用例                        | 实际持久化边界                                                                                                                            |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| 5 处真实写入后注入故障      | claim、capacity、成功状态、receipt、四目标 Outbox 分别写入后抛错；本地付款/attempt 恢复提交前状态，claim/capacity/receipt/Outbox 均无残留 |
| 故障后的原请求查询恢复      | 同一权威渠道成功事实保留，下一次实际 progress 只 recover 原 identity/requestNo/epoch，不调用 submit 或重新获取提交 ticket                 |
| 同 receipt 并发成功         | 相同原 ACK，一份流水归属、一份退款容量、一份 receipt，四目标 Outbox 各一份                                                                |
| 不同 receipt 的同一成功事实 | 后续 callback receipt 为 IGNORED，资金证据与容量不重复，Outbox 不重复                                                                     |
| 原 receipt 变造金融事实     | 原状态/证据保持；冲突事实进入 anomaly，相同冲突重放不重复异常记录                                                                         |
| 相同收款账户流水跨租户别名  | 精确全局流水键不能归属第二笔付款；第二笔保留未结算状态，记录冲突回执与异常，不产生容量/Outbox                                             |
| 渠道权威关闭返回            | 父门禁关闭后实际 progress 调用 close，CLOSE_RESPONSE receipt 与 CLOSED、Outbox 提交；不生成成功流水或退款容量                             |

注入类继承实际 ResultGateway / ResultEvents，每次先调用 super 完成真实写入，再注入一次异常，没有模拟数据库回滚。
原 Y2 已提交是此组的前置条件，通过合法领域 beginSubmission + 实际 attempt 更新持久化；本组不重新证明 Y2 授权与竞争，参见
FinancialPersistenceIT。
渠道网关返回受控、已认证的合成事实，所有调用均断言位于本地事务之外。不会经过真实渠道或读取密钥。
初始 attempt 创建、首次 submission preparation、支付 action 服务为未触达分支替身，并断言零调用；本组不覆盖 JSAPI/H5 action
持久化。
ACK 丢失以调用方重放建模，不声称已运行真实 gRPC 中断、回调验签或多实例任务租约故障。
Outbox worker 在 test/ci 配置中关闭，保留实际入库而避免后台发送。计数用测试侧只读 SQL 和 Testify，业务仓储仍为生成 Mapper。

同时修复当前开发期 schema：payment_channel_receipt 的 source CHECK 包含领域已支持的 CLOSE_RESPONSE，没有引入旧 schema
兼容分支。
没有执行数据库迁移、连接业务库或重建 schema。

选择此组的命令（尚未执行）：

```bash
mvnd clean verify -pl payment/payment-infrastructure -Pmysql-it -Dit.test=PaymentResultPersistenceIT -Dskip.spotless=true -Dskip.spotbugs=true
```

仍未运行 IDEA、编译、单测、Docker 或 MySQL；R1 尚无本轮通过证据。后续仍需真实付款/退款恢复 handler 的租约、旧新 generation
并发及网络故障组合。

## PaymentResultPersistenceIT：真实付款恢复 handler 与租约交错

在上述 10 个 Y3 输入之外，同一 IT 新增 3 个恢复 handler 用例源码，共 13 个输入。
实际调用 PaymentRecoveryTaskHandler → PaymentRecoveryService → PaymentExecutionService → Y3，使用原生
DelayedTaskStore.claim/finish、实际游标、实际任务表和金融持久化。
此组不模拟 native 状态、不直接改任务表，也不把 claim 当成新的渠道提交授权。

| 用例                                     | 预期约束                                                                                                            |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| 租约已过期及已重新 claim                 | 旧快照均被拒绝且没有渠道 IO；当前 claim 可正常查询并完成，旧 claim ACK 失败                                         |
| 旧查询暂停、新 worker 接管、当前后继收敛 | 第二个 worker 只提交一个后继；当前 generation 成功后，原旧查询回来不能覆盖成功或额外注册后继，原过期 token 不能 ACK |
| 未来 generation 故障任务                 | 未来任务被拒绝，不产生渠道 IO，也不能跳过仍有效的当前任务；原当前任务可正常完成                                     |

测试在实际 INIT 付款与首任务同事务登记后，持久化合法的已提交 Y2 前置状态。
只有渠道查询返回和未触达的首提/action 分支为替身；首提 preparation、初始 attempt 创建和 submit 都断言零调用。
并发屏障只暂停一笔原渠道查询，已捕获的 UNKNOWN 返回保留到旧 worker 恢复；新 worker 可获得后续成功事实。
业务与 native 使用同一个可推进测试 Clock（系统本地 ZoneId），不 sleep、不改变机器时区，也不替换审计时间监听器。
该 IT 的原生任务配置限定为 lease=5 秒、max-attempts=3、batch-size=1，recovery-polling.jitter-percent=0；不改变部署配置。
用例结束取消尚未完成的测试原生任务，保留业务、回执和成功任务记录，不删除金融证据。

这些用例尚未编译或执行。它们直接编排实际 claim/handler/finish，不代表实际 Scheduler、Dispatcher 扫描、跨进程停机或真实渠道网络已演练。
退款对应组合源码见下方 RefundRecoveryHandlerPersistenceIT；R1 仍需实际 MySQL 运行结果，不能以源码完成替代验收。

## RefundRecoveryHandlerPersistenceIT：退款结果、Outbox 与真实恢复 handler

新增 8 个真实 MySQL 测试输入。复用 PaymentResultPersistenceIT 的实际付款存储/结果装配、受控 Clock 与渠道端口，
额外接入实际
RefundGateway、ResultGateway、SummaryGateway、SnapshotReader、ProjectionEvents、RefundExecutionService、ResultService、RecoveryService、TaskHandler。
原付款通过真实 Y3 生成成功流水和退款容量，再通过实际 FIFO/insertPlan 预占、实际 projections.planned 及 recovery.register
同事务登记退款原任务。
原退款首提已经持久化是恢复前置条件，用合法 beginSubmission + 实际 update/projections 建立，没有 mock 金融仓储或合成成功容量。

| 用例                                    | 真实边界断言                                                                            |
|-----------------------------------------|-----------------------------------------------------------------------------------------|
| claim 后故障                            | 流水认领回滚，退款请求/子单/容量/汇总/receipt/Outbox 保持原提交状态                     |
| request/child/capacity 写后故障         | 多表状态与金额回滚，原退款占用仍在                                                      |
| 投影/汇总/Outbox 写后故障               | 实际 changed 写入后抛错，汇总及各目标 Outbox 一起回滚                                   |
| receipt 写后故障                        | 原 receipt 及全部前序金融/事件写入一起回滚；真实原任务 handler 可查询并重新收敛         |
| 租约过期及重新 claim                    | 旧快照拒绝且没有渠道 IO，当前 claim 可完成，旧 token ACK 失败                           |
| 旧查询在途、新 worker 及后继 generation | 只登记唯一后继；当前 generation 成功后旧 UNKNOWN 返回不能重复退款、回退状态或再次发事件 |
| 未来 generation                         | 未来任务拒绝，不能跳过当前原任务；原当前任务仍可恢复                                    |
| 同事实不同 receipt                      | 新 callback receipt 为 IGNORED；原容量、退款汇总、商户/额度/费用/交易投影事件均不重复   |

原付款始终保持 SUCCEEDED、10000 分、原版本；测试退款为 5000 分。成功后容量 refunded=5000/reserved=0，独立退款汇总同样只结算一次。
实际事件覆盖子退款投影、退款汇总、退费、商户子单/请求通知与额度结果，不只检查事件方法被调用。
故障注入均先执行实际 super 写入，再抛一次测试异常。Outbox worker 仍关闭，不发生真实发送。

渠道 recoverRefund 返回受控的已认证事实，断言位于本地事务外，并始终使用完全相同的
PreparedChannelRefund（原账户/付款请求号/退款号/金额/渠道流水）。
canRefund 仅作为本地能力检查，断言位于持锁事务内；不会载入密钥或发网络请求。
未触达的首次 submitRefund 和付款首提分支均断言零调用。真实渠道的 recoverRefund 可按原幂等退款号重试，本用例不宣称证明了渠道端的退款幂等。
用可推进 Clock 与屏障模拟租约，不 sleep、不手改任务表；测试尾部仅取消未完成 native 任务，不删除金融证据。

选择此组的命令（尚未执行）：

```bash
mvnd clean verify -pl payment/payment-infrastructure -Pmysql-it -Dit.test=RefundRecoveryHandlerPersistenceIT -Dskip.spotless=true -Dskip.spotbugs=true
```

当前只有源码，未运行 IDEA、编译、单测、Docker、MySQL、DDL 或 IT。R1 的真实运行证据仍未满足。
本组没有覆盖 RefundPlanningService 全部鉴权/合约准入、多付款 FIFO 串行退款、实际 Dispatcher/Scheduler 扫描、真实渠道验签或跨进程停机。
这些范围与 R3–R8 按 review 继续推进，不能以此宣称全业务或生产验收完成。

## Review R3 后的装配说明

PaymentResultPersistenceIT 显式导入 RecoveryPollingConfiguration；付款/退款租约交错 IT 统一关闭抖动，保持原 30 秒短龄时序。
生产 profile 使用有界退避/抖动的新配置，不保留旧固定 poll-delay-seconds 属性。
策略测试覆盖于 payment-domain/RecoveryPollingPolicyTest，配置默认/覆盖/拒绝非法上限及长龄业务后继测试覆盖于 payment-app。
这些变更都未编译或运行；没有以零抖动 IT 来证明生产请求速率或任务生成量。

## Review R3 后的 handler 指标装配

付款基础 IT 装配显式导入 RecoveryExecutionMetrics，退款 IT 复用同一基础装配。
它只使用容器已有的共享 MeterRegistry，缺省不创建测试外的私有 registry；这些金融 IT 仍按原 MySQL/任务/回执/Outbox
边界断言，不把指标状态当金融成功证据。
指标单位测试另在 payment-app 的 RecoveryExecutionMetricsTest 以及 infrastructure 的 RecoveryTaskHandlerMetricsTest，用测试专属
SimpleMeterRegistry。
本轮仍未编译或运行，未观测实际 exporter，也没有 UNKNOWN 库存/金额/最老年龄的数据库快照证明。

## Review R3：实际 UNKNOWN 聚合输入

PaymentResultPersistenceIT 追加 1 个输入（该类当前 14 个）：真实 Y3 将两个不同租户的付款置 UNKNOWN，实际
RecoveryInventoryReaderImpl 聚合只返回原租户 1 笔/10000 分与空退款，并拒绝无事务或可写事务。
RefundRecoveryHandlerPersistenceIT 追加 1 个输入（该类当前 9 个）：实际退款结果置 UNKNOWN 后读 1 笔/5000
分，原付款不计入未知，真实预占保持不变。
新增 reader 使用既有生成 Mapper/TableDef、QueryMethods 聚合与 MapStruct；这些输入也覆盖 COUNT=0/SUM/MIN=NULL 的空 cell 映射。
仅 import reader，不启动周期 collector；没有自动查询业务库、DDL、构建或测试运行。新 SQL 生成、映射、只读隔离与性能均待真实运行证明。

## Review R3：首次 UNKNOWN 时间输入

上述两个库存输入保持原测试数量，扩充为实际 gateway 在第一次 UNKNOWN 状态写入后的故障回滚，数据库 first_unknown_at
仍为空；推进可控时钟后成功提交才开始计时。
重复未知回执不重置；成功终态保留原时间且实际库存返回空。付款仍验证租户隔离，退款仍验证 UNKNOWN 时容量不变。
初始化迁移增加 first_unknown_at 及 CHECK，实际 gateway 使用同一状态 CAS 写入，需由用户后续 clean 构建生成
TableDef；不手工维护生成类。
本轮未运行任何构建、测试、迁移或真实数据库操作。

## Review R3：UNKNOWN 受控列表输入

PaymentResultPersistenceIT 新增 1 个输入（当前 15 个），实际 UnknownRecoveryReaderImpl 联 cursor：同租户两个同时进入 UNKNOWN
的付款使用原 ID 继续分页，另一租户排除；截止时间和只读上下文受检。
同一个 RR 快照持有期间，并行真实 Y3 成功提交须在超时内返回，旧快照仍见原行、新快照见终态退出；不以 mock 锁证明无阻塞。
RefundRecoveryHandlerPersistenceIT 扩充原库存输入（仍 9 个），实际联父申请及 cursor，区分子退款 recordId 与请求
subjectId，验证时间游标和终态退出。
基础 IT Application 显式导入新 Reader；映射仍走生成 Mapper/TableDef/MapStruct。所有输入未运行，不代表 SQL、插件、锁或性能验收。

## 2026-10-27 UNKNOWN 生命周期快照

PaymentResultPersistenceIT 新增一个真实 MySQL 用例源码：UNKNOWN 更新与 realtime Outbox
写后抛错，付款/attempt/first_unknown_at/receipt/Outbox 全部回滚；相同回执重放与同状态新回执不重复根版本快照，随后成功增加原四目标。已有关闭/租约交错预期同步计入
CLOSING/UNKNOWN 快照，并区分 realtime 两份与三个财务目标各一份。事件组件内部名更新为 PaymentEvents。未运行，不增加通过证据。

## P1 权威采集快照（测试源码，未运行）

新增 PaymentCapturePersistenceIT：复用真实付款提交/成功路径，覆盖源端关联 SQL、未决阻断、账户/租户/事务约束、
一个 RR 内并发提交不可见与新 RR 可见、后续金融事实不改变封存分页，以及全部 capture SQL 后失败使头/行统一回滚。
RefundRecoveryHandlerPersistenceIT 增加提交退款缺受理日期、受理跨账单日、PROCESSING不冒充SUCCEEDED、成功 claim与时间冲突阻断。
测试不使用数据库替身；外部渠道继续受控，所有新场景均尚未编译/执行。运行须遵守用户恢复验证后的窄范围 clean 约定。

## 关单确认独立提交测试源码（2026-10-28）

新增 PaymentCloseEvidenceStorageConverterTest，覆盖原绑定、金额/epoch 精确 int64、时间和私有交易号诊断。PaymentResultPersistenceIT
装配真实 PaymentCloseService/确认 Gateway，并增加“渠道成功关闭→确认独立提交→Y3 receipt 实际写后失败→后续渠道仅
UNKNOWN→消费原确认”的用例源码；断言确认保留、Y3 金融结果回滚、不重复 close、最终原 receipt/Outbox 收敛且不产生退款容量。

领域/app 同批增加不变量、先持久再应用、重复恢复、并发首份保持、错误来源/身份/epoch/网络失败及
OPEN/终态/事务门禁测试源码。所有源码未编译/运行，未执行 MySQL/DDL；真实丢包、确认写前崩溃、签名报文留档和实际渠道验收继续待办。
