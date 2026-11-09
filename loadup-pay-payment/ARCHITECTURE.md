# 恢复任务的运营语义


## 当前业务轮询

付款与退款使用同一个纯领域 RecoveryPollingPolicy，配置由 payment-app 的 RecoveryPollingConfiguration 注入。
只有正常执行后仍未终结的分支计算新 dueAt，游标更新与 native successor 仍在原本地事务一起提交。
状态机、锁序、原请求身份、Y2 提交权、金额及占用不因该策略变动。

年龄分别取付款 initiatedAt / 退款 plannedAt 到本次锚点，不因 native attempts、generation 或重启重置。
这是原业务存续年龄；首次 UNKNOWN 时间、占用年龄指标仍需另外实现，不能把它们混为一谈。
锚点使用 now 与 previousDueAt 的较晚者，避免时钟回拨把任务排回旧截止之前。

| 参数（loadup.modules.payment.recovery-polling） | 默认 | 范围           |
|-------------------------------------------------|------|----------------|
| initial-delay-seconds                           | 30   | 1..86400       |
| maximum-delay-seconds                           | 1800 | initial..86400 |
| backoff-every-seconds                           | 900  | 1..604800      |
| jitter-percent                                  | 20   | 0..50          |

年龄每增加 backoff-every，名义等待翻倍，在 maximum 饱和；先检查上限再乘倍，不以溢出回到短间隔。
偏移由租户、任务类型、原业务 ID 与当前 generation 确定；相同输入稳定，不消耗额外状态或更换金融幂等键。
窗口为 [max (initial, nominal-spread), min (maximum, nominal+spread)]，spread=nominal*jitter/100，整数秒。
默认新业务 30..36 秒；饱和后 1440..1800 秒。策略只约束该业务的下一次正常唤醒，不声称提供全渠道并发/总请求速率上限。

长期饱和且持续返回未知时，理论正常后继生成量由间隔约束到每笔每天约 48..60 次量级；这只是 86400/间隔 的推算，不是实测存储增长或渠道请求量。
每次推进可包含不止一次外部协议操作，native 失败重试/运营重试另计；并发限制与保留策略不能省略。

配置修改只影响之后登记的后继，不重写既有 executeAt。删除旧 payment/refund-recovery.poll-delay-seconds 及其环境变量，不提供开发期兼容分支。
所有时间沿用系统本地时区，数据库业务时间为 timestamp(6) without time zone；不更改机器时区或绝对协议时间。

## 失败重试与资金事实

- 传输/依赖失败继续由原 native 任务 attempts/lease/retry-delay/max-attempts 管理；不是一次新的业务付款/退款。
- 正常查询但结果不确定，保留原状态及占用，提交当前业务 generation 的唯一后继。
- FAILED 表示任务处理失败，不等于付款未收款或退款未发生；运营重试原任务仍须复核原身份/attempts/nextExecuteAt，并与审计同事务。
- 终态依原付款/退款权威证据完成游标；退避策略不能调用 submit、创建 attempt、解封门禁、释放额度或改写金融终态。

R2 已分离运营只读快照与加锁 retry；这不授予绕过预检或人工注入成功事实的能力。
此轮没有新增手工扣款/退款、成功记账、查询结果伪造或无证据释放入口。

## 当前渠道恢复并发源码

统一 ChannelPaymentFacade 在原路由/租户/绑定/能力检查后，以标准 Bulkhead 保护
query/recover/close/queryRefund/recoverRefund。
LoadUp ResilienceRegistries 由 payment 业务服务的 runtime core binder 提供，
`loadup-components-payment-channel-core` 仅依赖 `loadup-components-payment-channel-api`。
每个 Channel 共用一个 payment-channel-recovery-<channel> 实例，部署配置默认各 16、等待 0ms；非零等待在装配时拒绝。
使用原生许可/释放，满额在 Provider 前抛 CHANNEL_RECOVERY_BUSY，finally 归还名额，不创建线程池或任何新的金融请求。

该限制覆盖单次 Provider 恢复操作，其中可能有多步外部 IO；不是逐 HTTP 请求 QPS 限制。只有同 JVM 的调用共享；多个副本的预算不能当作一个集群配额。
错误继续走原任务 retry/FAILED；付款保留原状态，退款依赖故障可以转 UNKNOWN，但原金额/容量保持。
Core binder 提供原生 Bulkhead 指标绑定源码，尚无运行观测，不能当作 UNKNOWN 金额/年龄等业务指标已完成。

首提 submit/submitRefund 不纳入该恢复预算，避免在 Y2 已赢得且落库后再抛并发拒绝而消耗唯一票据。
首次提交限流必须在其持久化边界前管理名额；不能用通用 Retry 重建票据。validate/canRefund 仍是本地预检。

## 当前恢复执行指标源码

RecoveryExecutionMetrics 使用共享 MeterRegistry，不建立私有 SimpleMeterRegistry/backend；没有共享 registry 时业务照常执行。
实际付款/退款任务 handler 外层记录：

| 名称                              | 类型与标签                                                             | 口径                                                              |
|-----------------------------------|------------------------------------------------------------------------|-------------------------------------------------------------------|
| loadup.payment.recovery.execution | Timer，kind=payment/refund，outcome=continued/completed/skipped/failed | 每次 handler 调用一次，耗时包括校验、本地事务、外部推进与后继提交 |
| loadup.payment.recovery.inflight  | Gauge，kind=payment/refund                                             | 当前进程正在执行的 handler 数量                                   |

业务恢复服务返回已提交的 RecoveryHandlingOutcome：继续、推进后完成或无需推进/失去当前代。
标签结果来自固定枚举；异常记 failed 并原样抛出给 native 重试。计数在事务外层，不对每次本地写重试单独加数。
预检发现已有终态或旧代记 skipped，虽可顺便完成游标；这不是资金失败或成功计数。
服务执行结束不等于原生 ACK 成功；原生 lease_lost 等仍由框架自己的 dispatcher 观测。

类型与结果只形成 8 个 Timer 和 2 个 Gauge 身份，不使用任何租户、业务/任务 ID、账户、金额、generation、URL 或异常文本标签。
指标写入的常规 RuntimeException 不覆盖原业务异常，诊断只含固定 kind/outcome 与异常类型；不据指标改写资金或重试。
这些指标是调用流量、耗时及本进程在途量，不是 UNKNOWN 库存、最老年龄、真实 held 金额、FAILED 任务存量或 Outbox 新鲜度的替代品。


## 存量观测与升级查证

UNKNOWN 库存只统计当前未知付款/子退款的笔数、名义金额与首次未知时间，不等于额度 held 或可释放金额。采样由后台定期执行，不在 Prometheus scrape 时查库。当前适配通过参数化 Spring JDBC 聚合；以首次 UNKNOWN 时间判断 REVIEW/URGENT，缺失、过期或未初始化库存不能伪装为零。

配置入口为 `loadup.modules.payment.recovery-inventory` 与 `recovery-escalation`，默认库存采样关闭，启用时必须限定可信租户。只读查证和原 FAILED 任务重试不允许人工注入成功事实。详见 [模块接入](README.md) 与 [监控部署](../deploy/README.md)。

## 关单 ACK 独立保存

PaymentCloseService 已接原执行/恢复流程：只有已验证的 NO_COLLECTION/CLOSE_RESPONSE 才保存不可变的原 attempt 关单确认，独立提交后再交
Y3。Y3 失败后的原任务优先应用旧确认，避免把随后模糊的 CLOSED 当成新证据；没有收到或没有保存成功 ACK
时仍不制造未收款结论。独立表、MapStruct、原账户/epoch
防线与测试源码已补，未执行迁移或验证。详见 [恢复设计](../ARCHITECTURE.md)。
