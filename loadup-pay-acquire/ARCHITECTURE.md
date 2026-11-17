# Acquire 架构

阶段 0 的[当前时序与契约登记](../design/PHASE0_FLOWS.md)覆盖 A0/A1、A2/A3 和关单。当前未定位 A0 INIT 的专用后台扫描器，不能把已有 allocation/关单恢复任务描述成所有创单断点均自动续办；原调用方仍可按稳定意图重入。阶段 0 例外 E04 已按[1A 实施记录](../design/PHASE1A_IMPLEMENTATION.md)调整源码，1B/1C 尚未实施；构建与测试未运行。

## 身份与同步委托

AcquireActor 只包含本地 tenantId、稳定 scopeHash 和 permissions；scopeHash 仍按原 tenant/type/sub/merchant 元组计算。JwtAcquireActorProvider 的主体类型、商户绑定及权限来源保持原样。

基础设施 DelegatedCredentialResolver 在每次准入出站前先调用既有 ActorProvider 校验，再从当前已认证 Jwt credentials 读取原 token。它不缓存、持久化或换成机器身份；入站和目标 Resource Server 保留各自验证。OrderAdmissionHttpClient 先拒绝事务内网络调用，再解析凭证；本地缺身份/凭证仍为 ACTION_FORBIDDEN，远端认证/协议失败仍为 DEPENDENCY_UNAVAILABLE。业务用例和幂等摘要不接收裸 token。

[README](README.md) 维护接入、API 和配置；本文维护订单、付款占用、会话、费用与通知规则。跨服务身份和数据库基线遵循 [总体架构](../ARCHITECTURE.md)。

## 所有权与持久化

acquire 拥有创单意图、订单、冻结合约快照、费用及调整、付款占用/结果回执、会话、商户通知和恢复游标。payment 拥有资金执行事实，productcenter 拥有准入与商业条款；不得跨库写入或把 realtime 投影当作资金证明。

持久化使用参数化 JDBC/生成 Mapper 与 MapStruct，维护版本、逻辑删除及审计字段；业务表无 tenant_id。完整结构以 infrastructure 的 PostgreSQL 迁移为准。恢复命令使用本地 `PaymentIntentSnapshot`，校验 owner、格式、索引与哈希，保留原金额/期限/规则/输入引用；不按当前公开 Command 的校验规则或新合约重建旧意图。

本地 payload_hash 校验持久副本，不能冒充 productcenter 的 compiled_terms_hash。远端调用位于本地写事务之外；上下文绑定 helper 不等于只读事务。运行详情使用独立 REPEATABLE_READ 只读快照，写操作重新加锁核验。

## 创单 A0/A1

1. A0 以部署租户、收款商户、merchantOrderNo 固定原调用主体、入口模式及 orderId。ORDER/CHECKOUT/DIRECT_PAY 不可互换；同键改参冲突，逻辑删除不释放业务键。
2. 提交 A0 后请求 productcenter；网络失败保留 INIT，以原 orderId/命令恢复。首次准入检查当前商户、合约及额度，完成后的重放不重新准入。
3. A1 锁原意图，复核准入身份、条款、摘要和 materializeBefore。到达精确截止即拒绝首次落单；拒绝/过期结果先持久化，不能因合约恢复而重开同号意图。
4. 订单、冻结快照、INIT 整单费用、首次会话（如有）、到期任务及 COMPLETED 同事务提交；任一失败回滚。唯一键竞争/瞬态冲突整事务最多重试 3 次，不能通过另号掩盖失败。

Checkout 固定首次 sessionId 和原始 returnTargetCode，完成重放返回同一会话/期限，不续期；独立 session.create 使用新 requestKey。DirectPay 还固定首笔 actionKey、金额、choice 和输入种类，原始敏感输入不入创单库。

## 付款 A2/A3 与恢复

- A2 锁订单，按原调用主体与 actionKey 固定 allocationId/paymentId、金额、choice 和冻结条款，检查可付款窗口、单笔上下限/尾款例外及包含占用的次数上限。HELD 占用、订单版本、恢复游标与首轮 Task 同事务提交。
- 事务外准备 payment 加密输入并受理；acquire 只保存 opaque inputRef，原锁下最多 CAS 附加一次。敏感值/HMAC 指纹归 payment；超时、拒绝、回复错配不释放已经提交的占用，也不换 key、输入或渠道重扣。
- A3 从 payment 权威结果/事实读取确认信息，锁序为订单→allocation→fee。正终态需要版本、成功 attempt 与证据；负终态还需持久 FENCED。无记录、超时或证据不足不能当作未收款。
- HELD→CONSUMED 累计 gross 已付额；HELD→RELEASED 仅归还占用。订单、结果回执、满额费用确认及对应快照/通知登记同事务。`0 <= paid + reserved <= amount`；PAID/CLOSED 不重开，退款不减少 gross 或原成功事实。
- 原 delivery 完整摘要重放返回首次语义 ACK，不依赖远端再次在线；同资金事实的新 delivery 仍由结果回执去重。同版本冲突、倒退和来源错配拒绝。

商户/cashier/后台恢复复用同一原意图与 A3，不创建新 A2。NOT_OBSERVED、INPUT_REQUIRED 都不是未付款证明；只有 RESOLVED 携带的权威结果或未受理关闭证据才允许收敛。

InputLookup 仅查询原输入元数据，不解密、续期或重新生成场景。InputExpire 只在从未受理且已过期时保存原单笔关闭证明；不封禁父订单，不调用渠道。原合法 NONE 输入只按原固定摘要恢复，不把缺引用猜成 NONE。DirectPay 整体恢复只续办已有 A0/首笔，不因查无记录分配新 ID。

## 关闭与延期

C0 在订单锁下固定首个关闭请求、原因和 closeEpoch，将可关闭订单改为 CLOSING，阻止新付款/会话并登记后台任务。事务外先 fenceParent，再按 paymentId 分批关闭 HELD（每轮最多 16 笔），游标按 intent.rowVersion 推进，旧响应不能回退游标。

已受理付款复用 A3。未受理付款必须有 payment=null、持久封禁和 noCollectionEvidenceId，才写 NOT_ACCEPTED/0 版本事实；不能构造假 CLOSED payment。只有父门禁覆盖当前 epoch、所有子付款收敛、本地零占用且回执完整才进入 CLOSED。在途成功可先形成 PAID，部分收款关单不自动退款。

延期核对原主体、requestKey、expectedVersion、原冻结条款和原始截止；新截止严格晚于当前截止及当前时间，累计上限始终是 originalDueAt + extensionMaxSeconds。CLOSING/PAID/CLOSED 不可新延期；原回执重放不再次延长。

orderDueAt、订单版本、deadlineVersion、不可变回执与新到期任务同事务提交。延期不改变金额、费用、条款、付款执行期限或旧会话有效期。

## 收银台会话与浏览器

会话绑定原租户、主体、订单、requestKey 和审核过的 returnTargetCode，只允许原合约的安全回跳 code，不接受任意 URL。新会话限可续付 INIT/PENDING；CLOSE 取 sessionSeconds 与原 orderDueAt 较早值，KEEP_PENDING 使用原 sessionSeconds，向下对齐秒且必须仍有效。

会话 HMAC 绑定 sessionId/nonce/密钥引用与版本，库中只存 token_hash 等必要元数据。重放使用原版本重建并核验；旧键缺失/同版本替换返回 SESSION_UNAVAILABLE，不能偷偷续期或换 token。删键不等于撤销。

capability 限原订单摘要、本会话付款及进度/恢复，逐次校验 token 摘要、scope、状态、期限、Origin 和原归属；重复认证头拒绝。X-Checkout-Tenant 不是商户身份或任意 schema 选择权。买方 token 无商户创建、退款或会话管理权限。

撤销在订单→会话锁下永久设置 revoked_at，与幂等审计回执同事务。新认证/新预占拒绝旧链接，原已提交意图仍履约；不隐含关单或退款，不承诺阻断所有在途读。另签链接需新 key 且订单可续付。

浏览器规则：

- token 仅来自 URL fragment，读后移除 fragment，只保存在页面内存；不得写 query、cookie、localStorage 或日志。
- sessionStorage 在发起 HTTP 前写入并读回最小恢复信息（actionKey、金额、choice、paymentId），按租户/订单/会话隔离；不存 token、原输入或完整支付地址。存储损坏不能自动丢弃旧动作另扣。
- 金额使用 BigInt 整数分；缺少安全数字解析时拒绝不精确大整数。服务端复核金额、次数和状态。
- 回复丢失先 recover，已知原 paymentId 再 progress；只有 A3 收敛终态才清理动作。轮询默认 3 秒、每轮最多 30 次，后台标签暂停，人工刷新可续一轮；不以超时/返回页面判定成功失败。
- 同源脚本/样式/API、CSP、no-store、no-referrer 与 textContent；支付链接只打开原验证的未过期 HTTPS URL，链接 referrer 为 strict-origin。跨标签/设备不承诺全局恢复。
- JSAPI 桥接只允许固定方法/参数，原生回调只触发查询，不作资金证明；托管后端渠道身份/OAuth 接线尚未完成。

## 费用、事件与投影

整单费用只在 A3 恰好付清时由 INIT→CONFIRMED，使用原总额、冻结费率及舍入规则。部分付款不分次计费，退款不回改原费用快照。

退款费用汇总入口只接受受信 payment SERVICE 和最小权限，事务外查证权威累计，再按订单→fee 锁保存高水位、正差额 fee_adjustment 与语义回执。同版本同金额，旧版不能倒退，新版不能减少累计或超过原总额。确认前可保存依据，确认时补算；调整不改订单状态/gross。

订单版本变更在原事务写完整快照 Task；会话、输入引用和扫描游标不单独生成订单版本。发送原持久负载，不回查当前交易。realtime ACK 须绑定 delivery/businessEvent/producer/aggregate/sourceVersion，版本不小于原值，处置为 APPLIED/DUPLICATE/STALE。

## 商户通知

PaymentSucceeded、OrderFullyPaid、OrderClosed、RefundSucceeded、RefundFailed、RefundRequestCompleted 各自使用原冻结通知条款、固定正文与独立 eventId；不保证互相送达顺序。退款通知只登记投递，不改变资金账。内部受理 ACK 与商户送达 ACK 独立。

投递行和首轮 Task 与来源业务同事务。短事务先登记发送许可/计数和后继任务，再事务外联网；重试沿原事件、代次、次数和期限，EXHAUSTED 不重新授予窗口。框架失败重试不替代业务退避，商户必须按 eventId 去重。

targets/signing-identities 是受控不可变版本且绑定商户，首次解析后冻结路由指纹。只允许审核过的固定 HTTPS 443 目标、POST、无 query/userinfo/fragment；禁止私网放行、代理、自动重定向和任意 credential-ref。请求容量至少 65536 字节，响应不超过 4096 字节，各阶段超时及签名总耗时不得突破原合约预算。

每次签同一原 body，生成新 timestamp/nonce，KMS 显式保留原 key/version。只有预算内 2xx 且精确 `{"eventId":"原事件","result":"SUCCESS"}` 才确认；额外/重复字段、错 ID、空正文或异常编码均不能 ACK。正文、URL、凭证和原始响应不进诊断。

查询仅返回授权范围元数据。wake 要求受信主体、原 rowVersion、稳定 requestKey 和原因，调度变化与审计回执同事务；只处理原期限/次数内缺失或可恢复任务，不抢占 RUNNING/PENDING，不加速未来退避，不复活 ACKED/EXHAUSTED/过期投递。

## 后台任务与人工重试

| 任务 | 绑定与作用 |
|---|---|
| `pay.acquire.payment.intent.progress` | paymentId.generation；恢复原已提交付款意图 |
| `pay.acquire.order.deadline` | orderId.deadlineVersion；CLOSE 登记关单，KEEP_PENDING 保持可续付 |
| `pay.acquire.order.close.progress` | orderId.closeIntentRowVersion；续办原父门禁及子付款游标 |

handler 校验原 RUNNING 任务、代次、attempts、租约及业务归属。游标与唯一后继同事务提交，旧代/已完成幂等跳过。任务 FAILED、租约到期或 INPUT_REQUIRED 均不提供未收款证明，也不释放占用。

运营 detail 使用独立只读 RR 快照，不取交易排他锁。retry 在订单→allocation→游标锁下重验当前工作及 expectedTaskId/attempts/nextExecuteAt，仅重试原 FAILED/到期任务；缺任务不补建。稳定请求键/原因为不可变审计回执，调度与回执同事务，RETRIED 不表示金融成功或订单已关闭。

## 能力限制与待验收

未完成：首次输入准备在途与地址变更恢复、目标发布前开通验证/紧急吊销迁移、历史密钥保留清理、旧库任务补登记、恢复任务列表/告警、JSAPI 渠道身份/OAuth。无跨设备会话恢复或跨原通知窗口补发。

待验收：A0/A1/A2/A3 与费用/Task/任务的 PostgreSQL 原子回滚和并发；乱序/重复资金事实；关闭/延期/旧任务围栏；凭证/会话密钥轮换；真实浏览器存储、CSP、可信代理与渠道交互；商户签名/ACK；进程中断、失败重试和端到端恢复。测试入口见 [持久化测试说明](loadup-pay-acquire-infrastructure/src/test/README.md)。

## 阶段 3 事件边界

付款结果及退款汇总入口接受新 canonical 类型，并只兼容本入口原冻结标签；退款通知 DTO 同步新事实头。实际 service_id/权限、原订单/分配与准入绑定、receipt 与本地应用事务保持原实现；完整 PaymentFinancialEvidence 路径不调用 payment.facts，旧冻结载荷保持原读取，费用退款仍查 refundSummary 的最新累计。订单快照使用 pay.acquire.order-snapshot.v1，旧队列名明确为 DELIVERY_TYPE，消息实际字段和投影 ACK 不变。详细映射、回查保留原因和待运行验收见[实施记录](../design/PHASE3_IMPLEMENTATION.md)。本轮未构建或测试。

完整证据的类型约束、摘要及未运行验证见[财务证据记录](../design/PHASE3_FINANCIAL_EVIDENCE.md)。
