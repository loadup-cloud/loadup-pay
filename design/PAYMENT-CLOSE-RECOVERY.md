# 关单确认的持久恢复

状态：2026-10-28。本稿对应 P1 的有效关单 ACK 后 Y3 失败窗口。完成源码、迁移和测试源码，未运行构建、测试、DDL 或真实渠道调用。

## 要解决的问题

原流程在本地持久 CLOSING 后，在事务外执行原渠道 close，再由独立 Y3 事务应用观察。渠道关闭已成功但 Y3 失败时，后续查询可能只返回含义不明确的
CLOSED。例如支付宝 TRADE_CLOSED 也可能来自收款后全额退款，不能据此释放未收款占用。不能用任务失败、超时或本地 CLOSING
代替渠道资金证据。

现在将已验证的 NO_COLLECTION/CLOSE_RESPONSE 作为独立、不可变的关单确认保存。它与 Y3 金融事务分开提交，后续恢复优先应用原确认，无需重新解释渠道的模糊终态。

## 执行顺序与职责

1. PaymentExecutionService 继续持久化关闭意图、围栏和原 attempt 快照；未提交的 attempt 仍走原本地关闭证明，不进入渠道关单。
2. PaymentCloseService 以只读 RR 检查原付款/attempt、已提交 executionEpoch 与绑定，并读取原确认。没有原确认时，仅 CLOSING
   付款可开始渠道关闭；OPEN 或已经终结的付款不会在此重新开始关闭操作。
3. 没有确认时，事务外调用原 PaymentChannelGateway.close。必须精确匹配原 identity/金额，响应来源只允许
   QUERY_RESPONSE/CLOSE_RESPONSE。UNKNOWN、真实成功等观察继续由原 Y3 处理，不制造关单确认。
4. 仅 NO_COLLECTION 且 CLOSE_RESPONSE 可进入独立本地写事务，按父门禁→付款门禁→付款→attempt 原锁序重新核验原提交上下文。首份有效确认写入
   payment_close_evidence；并发后来的响应不能覆盖它。
5. 关单确认提交完成后，事务外调用 PaymentResultService.record，由原 Y3 再核验身份、金额、当前状态及
   receipt/claim，应用状态、终结证据和原目标 Outbox。
6. Y3 回滚时确认仍存在；原恢复任务再次执行时直接重放这份确认。Y3 已成功而回复丢失时，执行服务原终态门禁停止工作，或由原
   receipt 幂等返回；不重复产生资金结果。

不新增队列、调度器、付款票据或渠道重提交权限。仍复用原生 DelayedTask 恢复游标、原号重试及已有 FAILED 运维入口。

## 存储及不变量

新表 payment_close_evidence 主键为原 attemptId；每个原 attempt 仅一份确认，无更新或删除 Gateway。DO 继承 BaseDO，数据库处理器生成
Mapper/TableDef，Spring MapStruct 维护表示转换。

字段保留完整 ChannelPaymentIdentity（payment/attempt/channel/bindingRevision/accountScopeHash/channelRequestNo）、CNY
原金额、可空渠道交易号、原 receiptKey、原 sourceDigest、正 executionEpoch 和本地微秒 recordedAt。时间来自注入的
paymentClock，不改变系统时区；recordedAt 是本地记录时间，绝不是渠道成功或关闭的发生时间。

类型隐含已确认未收款关闭，不另存可由调用方指定的状态。原 observation 重建固定
NO_COLLECTION/CLOSE_RESPONSE，成功时间为空。私有渠道交易号在诊断输出隐藏；不保存浏览器输入、授权 token 或凭证材料。

这份记录是已由插件验证/协议确认的类型化事实及原来源摘要，不是原始签名报文档案，也不提供离线重新验签的能力。原退款/付款聚合、claim
和容量仍只由 Y3 决定；关单确认事务自身不释放额度或增加退款容量。

迁移：payment/payment-infrastructure/src/main/resources/db/migration/V20261028040000__payment_close_evidence.sql。当前没有执行。

## 仍存在的窗口

渠道响应尚未收到、验签失败、进程在确认落库前崩溃，或确认事务自身失败时，无法保证保存成功 ACK；仍需原渠道后续证据，无法证明时保持
UNKNOWN/CLOSING。保存确认不能让远端调用和数据库具备原子性。

本批不把任意查询 CLOSED、NOT_FOUND 或退款后的关闭态转换为未收款，也不解决微信付款码撤销/mTLS 补偿。已有成功收款与关单确认发生矛盾时仍交由
Y3 冲突/异常逻辑处理，不能让保存的确认越过资金事实或重开终态。

未决确认不设自动 TTL 或清理任务，避免删除唯一恢复证据；完整金融证据保留及受控清理策略仍待统一设计。

## 测试源码

- PaymentCloseEvidenceTest：原提交 epoch、正金额、摘要和精确微秒时间不变量。
- PaymentCloseServiceTest：确认先落库、Y3 失败后原证明重放、并发首份保持、模糊查询/实际成功不生成确认、网络失败/伪造来源/目标不符拒绝、未提交/OPEN/终态与外层事务门禁。
- PaymentCloseEvidenceStorageConverterTest：原身份、可空交易号、超过 JavaScript 安全整数的 int64 金额/epoch、微秒时间及诊断隐藏。
- PaymentExecutionServiceTest：原已提交 attempt 委托给持久关单流程，终态不再访问渠道。
- PaymentResultPersistenceIT：真实 MySQL 确认已提交后注入 Y3 receipt 写后失败，确认保留而金融结果回滚；原渠道之后只提供
  UNKNOWN 时仍消费此前确认，且不重复 close、不创建退款容量、不重复目标投递。

以上仅为源码覆盖。按用户暂停验证要求，没有运行 IDEA、编译、单测、集成测试、数据库、容器或真实渠道。
