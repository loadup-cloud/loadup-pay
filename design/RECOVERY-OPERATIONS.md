# 恢复任务的运营语义

更新：2026-10-27。对应 review R3；只将下述已写源码标为实现，其余明确保留待办。没有运行验证。

## 当前业务轮询

付款与退款使用同一个纯领域 RecoveryPollingPolicy，配置由 payment-app 的 RecoveryPollingConfiguration 注入。
只有正常执行后仍未终结的分支计算新 dueAt，游标更新与 native successor 仍在原本地事务一起提交。
状态机、锁序、原请求身份、Y2 提交权、金额及占用不因该策略变动。

年龄分别取付款 initiatedAt / 退款 plannedAt 到本次锚点，不因 native attempts、generation 或重启重置。
这是原业务存续年龄；首次 UNKNOWN 时间、占用年龄指标仍需另外实现，不能把它们混为一谈。
锚点使用 now 与 previousDueAt 的较晚者，避免时钟回拨把任务排回旧截止之前。

| 参数（loadup.modules.payment.recovery-polling） | 默认 | 范围 |
|---|---|---|
| initial-delay-seconds | 30 | 1..86400 |
| maximum-delay-seconds | 1800 | initial..86400 |
| backoff-every-seconds | 900 | 1..604800 |
| jitter-percent | 20 | 0..50 |

年龄每增加 backoff-every，名义等待翻倍，在 maximum 饱和；先检查上限再乘倍，不以溢出回到短间隔。
偏移由租户、任务类型、原业务 ID 与当前 generation 确定；相同输入稳定，不消耗额外状态或更换金融幂等键。
窗口为 [max(initial, nominal-spread), min(maximum, nominal+spread)]，spread=nominal*jitter/100，整数秒。
默认新业务 30..36 秒；饱和后 1440..1800 秒。策略只约束该业务的下一次正常唤醒，不声称提供全渠道并发/总请求速率上限。

长期饱和且持续返回未知时，理论正常后继生成量由间隔约束到每笔每天约 48..60 次量级；这只是 86400/间隔 的推算，不是实测存储增长或渠道请求量。
每次推进可包含不止一次外部协议操作，native 失败重试/运营重试另计；并发限制与保留策略不能省略。

配置修改只影响之后登记的后继，不重写既有 executeAt。删除旧 payment/refund-recovery.poll-delay-seconds 及其环境变量，不提供开发期兼容分支。
所有时间沿用系统本地时区，数据库业务时间为现有 local DATETIME；不更改机器时区或绝对协议时间。

## 失败重试与资金事实

- 传输/依赖失败继续由原 native 任务 attempts/lease/retry-delay/max-attempts 管理；不是一次新的业务付款/退款。
- 正常查询但结果不确定，保留原状态及占用，提交当前业务 generation 的唯一后继。
- FAILED 表示任务处理失败，不等于付款未收款或退款未发生；运营重试原任务仍须复核原身份/attempts/nextExecuteAt，并与审计同事务。
- 终态依原付款/退款权威证据完成游标；退避策略不能调用 submit、创建 attempt、解封门禁、释放额度或改写金融终态。

R2 已分离运营只读快照与加锁 retry；这不授予绕过预检或人工注入成功事实的能力。
此轮没有新增手工扣款/退款、成功记账、查询结果伪造或无证据释放入口。

## 当前渠道恢复并发源码

统一 ChannelPaymentFacade 在原路由/租户/绑定/能力检查后，以标准 Bulkhead 保护 query/recover/close/queryRefund/recoverRefund。
LoadUp ResilienceRegistries 由 payment-business 的 runtime core binder 提供，channel-core 仅依赖 API。
每个 Channel 共用一个 pay-channel-recovery-<channel> 实例，部署配置默认各 16、等待 0ms；非零等待在装配时拒绝。
使用原生许可/释放，满额在 Provider 前抛 CHANNEL_RECOVERY_BUSY，finally 归还名额，不创建线程池或任何新的金融请求。

该限制覆盖单次 Provider 恢复操作，其中可能有多步外部 IO；不是逐 HTTP 请求 QPS 限制。只有同 JVM 的调用共享；多个副本的预算不能当作一个集群配额。
错误继续走原任务 retry/FAILED；付款保留原状态，退款依赖故障可以转 UNKNOWN，但原金额/容量保持。
Core binder 提供原生 Bulkhead 指标绑定源码，尚无运行观测，不能当作 UNKNOWN 金额/年龄等业务指标已完成。

首提 submit/submitRefund 不纳入该恢复预算，避免在 Y2 已赢得且落库后再抛并发拒绝而消耗唯一票据。
首次提交限流必须在其持久化边界前管理名额；不能用通用 Retry 重建票据。validate/canRefund 仍是本地预检。

## 当前恢复执行指标源码

RecoveryExecutionMetrics 使用共享 MeterRegistry，不建立私有 SimpleMeterRegistry/backend；没有共享 registry 时业务照常执行。
实际付款/退款任务 handler 外层记录：

| 名称 | 类型与标签 | 口径 |
|---|---|---|
| loadup.payment.recovery.execution | Timer，kind=payment/refund，outcome=continued/completed/skipped/failed | 每次 handler 调用一次，耗时包括校验、本地事务、外部推进与后继提交 |
| loadup.payment.recovery.inflight | Gauge，kind=payment/refund | 当前进程正在执行的 handler 数量 |

业务恢复服务返回已提交的 RecoveryHandlingOutcome：继续、推进后完成或无需推进/失去当前代。
标签结果来自固定枚举；异常记 failed 并原样抛出给 native 重试。计数在事务外层，不对每次本地写重试单独加数。
预检发现已有终态或旧代记 skipped，虽可顺便完成游标；这不是资金失败或成功计数。
服务执行结束不等于原生 ACK 成功；原生 lease_lost 等仍由框架自己的 dispatcher 观测。

类型与结果只形成 8 个 Timer 和 2 个 Gauge 身份，不使用任何租户、业务/任务 ID、账户、金额、generation、URL 或异常文本标签。
指标写入的常规 RuntimeException 不覆盖原业务异常，诊断只含固定 kind/outcome 与异常类型；不据指标改写资金或重试。
这些指标是调用流量、耗时及本进程在途量，不是 UNKNOWN 库存、最老年龄、真实 held 金额、FAILED 任务存量或 Outbox 新鲜度的替代品。

## R3 尚未完成

- 首次提交边界前的并发准入、集群/渠道总请求配额、QPS 与公平性，以及扫描/原生重试/运营唤醒在真实副本数下的验证。单 JVM 恢复舱壁源码已补，不代表这些都已实现。
- 未收敛真实额度 held、FAILED 任务库存、Outbox 最老未交付和投影新鲜度。UNKNOWN 行数/名义金额/首次 UNKNOWN 最老年龄的独立只读库存源码已补（默认未启用），见 [库存说明](RECOVERY-INVENTORY.md)；不把它们等同于真实资金占用。
- 长期未知分级阈值、受控原身份查证列表与告警规则源码已补，见 [分级查证](RECOVERY-ESCALATION.md)。实际规则/通知路由、人工工单认领/确认及升级运行证据仍待补；超阈值不能自动失败、释放或重扣。
- 调度历史、幂等回执、资金证据分别定义保留/归档；不能统一按 TTL 删除。现阶段没有实现清理金融证据的作业。
- 实际 Scheduler/Dispatcher、多实例故障、查询负载、任务生成量及长期运行演练。

## 源码及证据

RecoveryPollingPolicyTest 覆盖年龄档位、上限、长龄不溢出、固定偏移及窗口、配置非法和时钟回拨。
RecoveryPollingConfigurationTest 覆盖默认/显式配置和错误装配。
PaymentRecoveryServiceTest / RefundRecoveryServiceTest 补长期未终结保持原业务并登记上限后继；租约交错 IT 显式关闭抖动，以免混淆时序与策略测试。
渠道 Facade 另补共享预算、真实线程交错、跨渠道隔离、名额释放、鉴权优先及首提/本地能力边界测试源码；依赖门禁补 API/binder runtime 归属。
RecoveryExecutionMetricsTest / RecoveryTaskHandlerMetricsTest 另补固定标签、调用结束后计数、异常原样传播、并发归零、无共享 registry 仍执行及两类实际 adapter 的指标接线源码；恢复服务单测同步了已提交决策。
以上只有源码，没有 IDE/编译/测试/数据库/部署运行证据。R1/R2 验收继续独立保留，不用本切片替代前序证明或宣称 review 已完成。
