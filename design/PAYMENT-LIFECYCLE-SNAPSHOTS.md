# 付款生命周期快照

更新：2026-10-27。新增生产与测试源码均未构建、运行或进行环境验收。

## 生产边界

payment 的原终态事件组件重命名为 `PaymentEvents`，不保留旧内部类兼容。
现有 wire 类型 `pay.payment.snapshot.realtime.v1`、PaymentSnapshotEventDTO、RPC、Outbox handler 和投影表沿用。
快照本来允许版本 0 和非终态，本次补齐实际生产入口。

| 持久变更                     | 生产位置                                        | 本次提交的快照                                         |
|------------------------------|-------------------------------------------------|--------------------------------------------------------|
| 首次受理                     | PaymentAcceptanceService.accept                 | INIT / version 0，与付款、门禁、恢复任务同事务         |
| 创建首个 attempt             | PaymentInitialAttemptService.create             | PROCESSING / 新付款版本，与冻结路由和 attempt 同事务   |
| 首次不明结果进入 UNKNOWN     | PaymentResultService.apply                      | UNKNOWN / 新付款版本，与 attempt、付款和观察回执同事务 |
| 受控关闭或后台截止进入关闭中 | PaymentControlService / PaymentExecutionService | CLOSING / 新付款版本，与原门禁和付款更新同事务         |
| 成功、最终失败、已关闭       | 原终态 publish 路径                             | 最终快照及 acquire/limit/risk 各独立投递               |

只生产事务最终保存的付款版本；同事务内 INIT → CLOSING → CLOSED 等未单独提交的过渡不伪造一份独立 CLOSING 快照。
Y2 只改变 attempt 的提交元数据、JSAPI 受理但付款根版本未变、本地 H5 动作保存、相同状态查证、
重复回执或重放 accept/close 都不再次生产同一付款版本。

## 快照事实与来源

快照保持原 payment/order/payeeMerchant、固定金额/CNY、initiatedAt 和付款 sourceVersion。
INIT 尚未选路时 channel/accessMode 为空；选路后取原 active attempt 的冻结 channel/accessMode。
发布前复用纯领域付款/attempt 绑定，核验原金额、商户、调用方、额度操作和 requestDigest。
UNKNOWN 还要求原 attempt 已越过 Y2；不存在 attempt 的 PROCESSING/UNKNOWN 不生成快照。

SUCCEEDED 的 succeededAt 只能来自同租户、原 attempt/金额、原终态 receipt 对应的永久成功 claim；
不使用本地发布时间或通知到达时间。FAILED/CLOSED 有 attempt 时核对相同的未收款证据。
非终态没有 terminalEvidenceId，不能以中间态调用终态财务发布入口。

每个非终态快照生成自己的 businessEventId 和 deliveryId；它们只是事件关联标识，不是扣款成功或未收款证据。
终态沿用原 terminalEvidenceId 关联各目标，但 deliveryId 独立。投递重试沿 Outbox 已保存的完整 envelope，
不重新读取付款、重新生成 ID 或把当前状态塞回旧消息。

## 事务、重放与主链路

所有写入要求实际可写本地事务以及匹配的可信 TenantUtil 租户；readonly、错误租户或损坏的原绑定拒绝。
事件 JSON 与原业务事务一起入库。Outbox 入库失败使该本地业务提交回滚；远端 realtime 暂不可用由独立投递重试处理，
主链路不连接 realtime、不等待远端 ACK，也不调用远端查询。

这不表示数据库 Outbox 本身永不耗时，也没有引入分布式事务。
付款实际版本不变时不发新快照；版本提交后在相同请求重试中不会重复申请业务提交权。
UNKNOWN 付款的 accept 重放也改用原动作展示路径，保留有效 H5 地址，并且不新增快照。

## 消费与查询

realtime 继续消费四类独立完整快照。付款 INIT/PROCESSING/UNKNOWN/CLOSING 不依赖订单投影存在，
可以先保存为付款读模型。相同版本/内容幂等，较旧版本返回 STALE；已成功付款不能被更高版本的处理中状态重开。

消费者只推进自己数据库中的投影及 receipt，没有对 payment/acquire 的同步回查。
快照发布完成不等于前台马上可见；查询新鲜度 SLA、流级检查点、重建、数据保留和导出仍是后续事项。

## 测试源码与证据范围

- PaymentEventsTest：INIT 到 CLOSING 的完整版本/身份/金额快照、只投 realtime、只读/租户/缺失 attempt 拒绝，终态继续四个目标。
- 受理/attempt/结果/关闭服务测试：首次提交事件、重复不增发、UNKNOWN 不改变资金副作用，初始 Outbox 异常的事务传播；单元事务计数不能证明真实
  SQL 回滚。
- PaymentResultPersistenceIT：真实 UNKNOWN 付款/attempt/首未知时间、回执与 Outbox 写后故障一起回滚；重试和同状态新回执不重复快照；后续真实成功独立四目标。只是
  IT 源码，未运行。
- ProjectionPersistenceIT：独立数据库 INIT version 0 到成功、无父订单投影、迟到 INIT 为 STALE、终态新 delivery 幂等、拒绝高版本重开。只是
  IT 源码，未运行。

仍缺实际构建、MySQL 回滚与并发执行、跨进程 Outbox/gRPC/JWT/TLS、宕机重启和积压恢复证明；不把源码计作通过证据。
