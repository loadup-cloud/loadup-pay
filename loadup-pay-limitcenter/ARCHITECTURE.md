# limitcenter 架构

阶段 0 的[额度结算与退款恢复时序](../design/PHASE0_FLOWS.md)已按当前实现核对。Confirm/Release 首次及重复调用均可能在本地 settlement receipt 检查前查询 payment；restore-refund 则先按 refundId 查本地回执，命中直接校验重放。两者的源端可用性依赖不能混同；后续事实契约调整尚未实施。

接入及配置见 [README](README.md)，跨服务协议与数据库基线见 [总体架构](../ARCHITECTURE.md)。

## 激活协议

LimitPolicyActivateCommand 包含 policyId、policyVersion、policyHash、payeeMerchantId、refundAmountRestore、refundCountRestore
和完整 rules。规则最多64条，AMOUNT 单位为分，PAYMENT_COUNT 单位为笔；CNY、DAY/WEEK/MONTH/LIFETIME，weekStart 按 ISO
1至7。meteringEnabled 必须为 true；enforcementEnabled=false 仍持续累计，仅跳过新预占的阈值检查。退款配置值为
NO_RESTORE/ORIGINAL_BUCKETS 和 NO_RESTORE/FULL_PAYMENT_REFUNDED_ONCE，由退款恢复流程按原策略和可信退款证据执行。

发布方使用 client 的 LimitPolicyHash.compute(command) 计算规范化 SHA-256，再构造携带该 hash 的命令。hash
自身不进入输入；算法 v1 包含协议名、policyId、version、payee、两项退款策略、规则数量和按稳定身份排序后的全部规则字段。每字段为
UTF-8，前置四字节大端字节长度；整数使用十进制字符串、布尔使用 true/false。JSON 格式或规则输入顺序不影响结果，long 不经过
double。服务端重新计算，读库存量解释也重新验证 hash、行身份和字段约束。

- policyId 和 ruleScopeId 由可信发布方保持稳定；不得以换 ID 规避累计额度。新版本必须从1开始逐次递增。
- policy_head 行锁序列化同一策略的激活与新预占。相同版本同 hash 返回原 activatedAt；不同 hash 冲突。重放旧 ACK 不会回写旧
  runtime 阈值。
- 同一策略后续版本只能调整阈值、是否执行阈值门禁及退款恢复解释；不得改收款商户、增删/改名规则、改变指标/币种/窗口/周起点。runtime
  稳定键归属于原策略，其他策略不得接管。此保守约束避免把规则迁移伪装成累计清零；新规则/改历法需要后续显式迁移流程。
- 当前只接受规则 merchantId 等于 policy 的 payeeMerchantId。父商户共享桶和规则迁移需要单独可靠发布协议，暂不支持；遇到父级规则明确拒绝。
- 不可变 revision 保存原规则；runtime 保存最新阈值。原 pending 订单使用旧 policy 引用仍可预占，但按当前 runtime 阈值判断，既有
  used/held 不清零。降阈值允许历史 used+held 超过新阈值，此时后续新预占拒绝。

## 预占与事务

原始 initiatedAt 按业务 Clock 的本地时区转换，要求微秒精度、年份1970至9998且首次预占不能晚于当前时间。DAY/WEEK/MONTH 使用该原始时间归桶，LIFETIME
使用固定窗口。桶键不含 policyVersion。每个 AMOUNT 桶占本笔 amountMinor，每个 PAYMENT_COUNT 桶占1；一次请求命中的全部规则一起成功或一起回滚。

写操作使用主库 READ_COMMITTED 独立事务，拒绝已有本地外层事务。预占锁顺序为 operation_gate → policy_head → runtime
按稳定身份 → bucket 按稳定身份与窗口 → reservation/items；激活为 policy_head →
runtime。纯领域模型先生成全部桶变更提案，再以行版本条件写回所有桶、reservation/items 和 OPEN
gate，同一事务提交。缺行插入由唯一约束兜底；唯一键竞争或瞬时数据库冲突整事务回滚后重试，最多3次，耗尽返回
LIMIT_CONCURRENT_UPDATE，调用方以相同意图重试。

同一 operationId/paymentId 唯一；重复请求逐项核对付款、订单、商户、金额/币种、policyId/version/hash、原
initiatedAt、requestDigest 与调用服务。相同请求返回已存 reservation，不重复占额或刷新过期时间。预占有效期默认首次成功建单时间加120秒，可配置1至86400秒；该值用于
BeginExecution 门禁；当前提供显式 expire 入口，自动扫描器尚待接入。FENCED gate 不得被迟到 Reserve 重新打开。

compiled_rules 使用 TEXT 保存并校验完整 payload，诊断中隐藏；表、约束及组件快照以本应用迁移为准。

## 执行门禁与过期

BeginExecution 首次要求原记录为 RESERVED，服务端当前时间严格早于 reserveExpiresAt，且无 previousAttemptEvidenceId。它与
expire 争用同一 operation_gate 行锁；先读取定位并校验服务归属，取得门禁锁后重读状态，再锁 policy_head。成功时在同一事务写
immutable execution_grant（operation/payment/attempt/epoch/requestKey/keyId/permitHash）并把 reservation 转为
EXECUTING，epoch从1开始。依赖签名失败、唯一键冲突、CAS或提交失败均不会部分提交。

已进入 EXECUTING 的同 attempt + requestKey 重试用原 keyId 重算同一许可，即使预占期限已过也不刷新额度或增加 epoch。更改
requestKey/先前证据字段拒绝；不同 attempt 返回 LIMIT_OUTCOME_UNKNOWN，即使传入任意 evidenceId 也不启用换尝试。可信渠道查证和封禁旧执行的接入后续实施。

许可复用框架
commons-util.DigestUtils.hmacSha256，输出64位小写十六进制；协议v1以UTF-8字节长度加冒号分帧，绑定租户、原调用服务、reservation/operation/payment/order/payee、金额/币种、原policy/version/hash、意图摘要、原initiatedAt、attempt、epoch、requestKey和keyId。数据库只保存
SHA-256 (permit) 与 keyId；不存原许可或HMAC密钥。诊断隐藏输入permit、持久摘要和配置密钥。此为 limitcenter 内部许可，不是 JWT
或渠道扣款凭证，HMAC密钥不得下发给商户/payment/浏览器。

verifyExecution 使用原服务权限，核对当前 EXECUTING 状态、payment/attempt/epoch、金额/币种与许可摘要（恒定时间比较）。返回
valid 与 VALID/NOT_EXECUTING/PERMIT_MISMATCH；认证/资源归属/存储损坏仍为规范业务失败。它是锁内某时刻的观察，不能与跨服务
payment 数据库形成原子事务，也不证明渠道只提交了一次；payment 的本地 epoch/attempt CAS、重试和查证仍承担渠道执行约束；资源端许可检查不能替代该边界。

expire 只在 RESERVED 且当前时间达到期限时，按原不可变policy/hash/受理时间核对完整明细，锁原桶并同时归还本次 held、保留其他
used/held，再CAS变更为 EXPIRED。重复调用不重复释放；早于期限返回 RESERVED；EXECUTING/CONFIRMED
等状态返回当前状态且绝不按TTL释放。缺桶/缺明细或数量不一致拒绝并回滚，不新建空桶。EXPIRED 后不能 BeginExecution，同operation
Reserve只返回原终态；新付款须使用新的权威payment/operation。

Begin 在过期边界返回 LIMIT_RESERVATION_EXPIRED，不在抛异常的事务中偷偷释放；调用方可显式 expire 并确认
EXPIRED。不存在的operation返回 NOT_FOUND，不等于持久无扣款证明，也不替代后续 Release-before-Reserve 的 FENCED tombstone
协议。自动过期扫描、到期事件/历史与 payment 释放订单占用联动仍待后续实施。

## 付款终态确认与安全释放

LimitSettlementFacade 提供 及内部 POST `/api/internal/v1/limitcenter/operations/confirm`、`/api/internal/v1/limitcenter/operations/release`，分别要求指定 payment SERVICE 身份和
pay:limit:confirm/release。confirm 接 LimitConfirmCommand 返回 LimitReservationDTO；release 接 LimitReleaseCommand，现返回
LimitReleaseDTO，含 fenced、付款版本/证据及可空 reservation。释放早于预占到达时只有持久 gate/回执，不构造假 reservation。

应用先在本地事务外使用标准 OAuth2 客户端取得的 SERVICE JWT 请求 payment `/api/internal/v1/payment/operations/facts`，核对
operation/payment/order/商户/金额/币种/原 initiatedAt 及终结版本/证据。confirm 还须为 SUCCEEDED 且成功 attempt
等于原活动尝试；release 必须为 CLOSED/FAILED 并持久执行封禁。HTTP 超时、空记录或仅提供 evidenceId 不修改额度。

锁序为 operation_gate→policy_head→原桶稳定顺序→reservation/items。使用原 policy/version/hash 和原 initiatedAt
恢复全部窗口，确认将所有桶 held→used 并确认全部明细，释放只减少原 held。策略升级不改变原归桶或重置终生额度；EXPIRED
已释放不重复扣减，EXECUTING 永不依赖 TTL 回收。

不可变 limit_settlement_receipt：同 operation 固定 CONFIRM/RELEASE、源版本和证据，业务摘要不含投递 eventId，因此相同语义换投递
ID 不二次记账；改参数或成功/未收款冲突拒绝。release 同事务写 FENCED，迟到 Reserve/BeginExecution
无法再开门禁。原桶、明细、预占状态、门禁和回执原子提交。


## 成功退款恢复与权威事实

内部 `restore-refund` HTTP 操作 实现 `LimitRefundFacade.restoreRefund`，只接受配置的 payment SERVICE 和
`pay:limit:refund:restore`。原 `LimitRefundRestoreCommand` 绑定
operation/payment/refund/version/businessEvent、单笔成功退款金额、原消息付款累计成功退款、币种和成功证据。业务 Facade
继续分切片装配，MerchantLimitFacade 聚合继承该切片。

首次调用在本地事务外调用 payment 的 refund-facts HTTP 操作
，验证真实成功子退款与原付款、operation/商户/订单/币种/金额/版本/证据/首次受理时间。查询专供 limitcenter SERVICE 和
`pay:payment:refund-facts`，按 payment 的 parent→request→capacity→child 锁顺序复核原付款永久 claim、成功退款永久 claim
与渠道成功记录，返回该付款最新累计已退及 capacity 版本。旧消息的累计可小于最新权威累计，使用已验证的最新累计推进高水位。

本地按 operation gate→原策略 head/revision→原 buckets 加锁；必须先完成付款 Confirm，且存在匹配原成功版本/证据/attempt
的确认回执。原预占尚为 RESERVED/EXECUTING 时返回 DEPENDENCY_NOT_READY，保持出站重试，不提前写退款回执。原已确认但缺确认回执属于损坏。当前合约是否活跃、当前
revision 如何改恢复规则，均不覆盖原 reservation 冻结解释。

根据原 initiatedAt、原规则的 DAY/WEEK/MONTH/LIFETIME 键读取原 items，只减原 buckets 的 used，保留 held
和窗口身份，不创建退款发生日/月的新桶，不改订单可付款额。金额按累计成功退款高水位计算各 item
的正差额；FULL_PAYMENT_REFUNDED_ONCE 只有累计达到原付款全额才恢复该付款的笔数，每 item 最多一次。乱序/重复不再次恢复，NO_RESTORE
明确保存 NO_ACTION。

bucket CAS、item restored 高水位和 `limit_refund_receipt` 同事务保存；失败整体回滚。refundId 唯一，完整命令按 UTF-8
长度分帧 SHA-256 绑定；同退款改参拒绝，原参数直接返回首次结果，不再次依赖 payment
可用性。回执保存原累计、权威累计/版本、首次恢复金额/笔数、来源服务、acceptedAt。返回的 restoredAmountMinor
是逻辑付款本次恢复额，不把相同金额的日/月/终生规则恢复量相加。


## 能力限制与待验收

不支持父商户共享桶、规则身份/历法迁移、跨 attempt 安全替换或自动过期扫描；显式 expire 不释放 EXECUTING。confirm 的 succeededAt 只保留来源元数据，归桶取原 initiatedAt。

待验收：激活/预占竞争、全桶原子回滚、许可签名及旧键重放、Release-before-Reserve 围栏、权威事实查询与退款高水位幂等；不能用内存事务夹具替代真实数据库证明。

## 阶段 3 事件边界

仍消费原 confirm/release/restore 命令；payment 侧规范事实与投递，不新增绕过额度用例的 EventEnvelope 入口。confirm/release 命令新增完整 PaymentFinancialEvidence，有完整证据时从其构造本地 facts 并保留原绑定/attempt/原桶/policy/gate/receipt 校验，无证据的旧路径仍先查证后查回执。退款先回放回执，再读取最新累计；新增 RefundFinancialEvidence 校验冻结来源与容量版本，不用冻结累计替代最新累计。详细映射、回查保留原因和待运行验收见[实施记录](../design/PHASE3_IMPLEMENTATION.md)。本轮未构建或测试。

完整契约、逐条查询决策及未运行验证见[财务证据记录](../design/PHASE3_FINANCIAL_EVIDENCE.md)。
