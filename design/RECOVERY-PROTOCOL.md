# payment 内部恢复协议

更新：2026-10-27，对应 review R7。只有源码与测试输入，尚未运行验证。

## 复用范围

不合并付款/退款状态机，不新增 Saga、泛型 runner、跨库事务或新的框架组件。共享部分仅包含纯值计算/比较：

| 类型 | 层次 | 责任 |
|---|---|---|
| RecoveryTaskKey | payment-domain | subjectId/generation 的规范 bizId 编解码 |
| RecoveryPosition | payment-domain | 代次/原 executeAt 当前性、单调后继与溢出保护 |
| RecoveryTaskProtocol | payment-app | 原生任务元数据与可信租户校验、本模块错误映射 |
| RecoveryPollingPolicy | payment-domain，已有 R3 | 原付款/退款年龄的退避/抖动时间计算 |

纯领域类型没有 DelayedTask/Spring/ORM。app 协议只接收显式值，不查询 native store、不读取线程租户/Clock、不执行数据库或渠道，也不注册/ACK 任务。
PaymentRecoveryKey/RefundRecoveryKey 继续保留各自 typed paymentId/requestId；公共规范规则移到 RecoveryTaskKey，bizId 字节仍为原 subjectId.generation，没有任务迁移或兼容格式。
PaymentRecoveryCursor/RefundRecoveryCursor 仍属于各自业务入口，增加 position 视图，schedule 委托共用规则，finish 与业务终态判断继续由原服务持有。

## 元数据预检

服务入口仍先拒绝外层真实事务。receive 核对任务 kind、显式可信 tenant，并解析唯一规范 key；错误映射 SNAPSHOT_INVALID。
每个服务继续自己执行 tasks.find，缺失持久登记仍为 SNAPSHOT_INVALID。随后 requireClaim 比较收到与当前登记的 id/tenant/type/bizId/executeAt/attempts/maxAttempts：

- 收到和登记的任务必须 RUNNING，收到的 attempts/maxAttempts 必须为正。
- 登记 leaseUntil 非空且严格晚于当前时点，nextExecuteAt 非空且已经到期。
- 任务原 executeAt 不变；允许 heartbeat 延长登记的 leaseUntil，不要求旧快照与续租时间完全相等。
- 不比较错误文本，不将错误日志或重试次数解释为未收款/未退款。

这是当前任务元数据的新鲜度/一致性检查，不能替代 claim token 归属。token、条件领取、续租、过期重领、ACK/finish fencing 仍由原 DelayedTask Store/Dispatcher 负责；业务协议不直接完成或重领 native task。
原始 DTO 没有在此协议暴露新的 token 或渠道许可，也不提供 exactly-once 外部副作用保证。

## 两阶段的代次检查

各服务仍分别执行：锁内业务预检 → 事务外原业务推进 → 锁内完成/后继登记。
两个阶段都调用同一个 position/current 规则：

1. subject 必须相同，future generation 必须拒绝，即使游标已经 completed，也不能让未来任务跳过当前代。
2. completed 或旧代返回非当前，原任务跳过，不注册后继、不推进金融状态。
3. 当前代必须有与原任务 executeAt 完全一致的 dueAt，才可继续。

旧代不需要拿自己的 executeAt 匹配更新后的 dueAt；它仅安全跳过。
本轮同时补齐原推进后阶段缺少的同代 dueAt 核对：若同一代时间被异常改变，报 SNAPSHOT_INVALID，不在错误游标上完成或登记后继；不回滚已独立提交的远端/本地资金事实，也不自动修复或释放占用。

非终态后继继续用原 RecoveryPollingPolicy 计算时间；共用 schedule 要求尚未完成、expectedGeneration 相等、nextAt 严格晚于原 dueAt，并通过 Math.incrementExact 增加一代。
游标更新和独立 successor 的 native register 仍在同一原 writeLocal 中提交，异常保留给 native retry/FAILED；不是对 RUNNING 任务原地改期。

## 保留的业务差异

付款依旧先父门禁/付款门禁/付款，再游标；是否终态与 Y2 唯一首次提交权由付款流程决定。退款继续原 request/付款容量/子退款锁及固定计划，全部子退款终态才结束请求游标。
execute/progress 的准备、原号查询/关闭、claim/receipt、容量、Outbox、风险/额度等没有移到共享协议。
不在协议中注入 execution callback、ChannelPaymentFacade 或 submit 方法；UNKNOWN、传输失败、任务失败不能成为释放/重扣依据。
原 TYPE、bizId、任务行/游标 schema、租户和 successor 原子边界不变；新增正数/status/maxAttempts 校验仅拒绝不一致领取快照。

## 源码测试与证据

- RecoveryPositionTest：两个 typed key 同样的规范字节/非法格式、current/旧代/完成/future/错误主体/时间，两个 cursor 相同后继、完成语义、非前进时间与 long 溢出。
- RecoveryTaskProtocolTest：相同用例对付款/退款执行，覆盖可信 envelope、身份/类型/原时间/attempts/maxAttempts/status、到期与租约边界、heartbeat 延长，以及 phase 的错误映射。
- 两个 RecoveryServiceTest：推进后同一代 dueAt 被异常改变时，游标/后继不写入，原生失败由原外层处理。
- 已有业务差异测试及真实 MySQL lease/generation handler IT 保留，既有后继回滚/原号恢复/终态处理仍须独立验证。

本轮没有执行 IDEA/编译/测试、数据库、native dispatcher、渠道或部署。纯协议/SPI 替身测试源码不能证明实际锁、token fencing、回滚、多实例或外部副作用；R1/R2/R7 完整运行验收仍待完成。
