# 阶段 0 五条核心流程现状时序

阶段 3 后续切片已规范新事实类型与目标 Task 路由，下面的旧标签按阶段 0 历史协议理解。完整付款证据路径已替代符合条件的回查，旧冻结载荷查证、退款最新累计和恢复语义保留；当前时序增量及待运行门禁见[财务证据记录](PHASE3_FINANCIAL_EVIDENCE.md)，未构建或测试。

阶段 2 已在源码中将商业闭集解析归 domain，将额度编译改为仅接收额度片段；业务时序中的合约锁、原重放、Task 和 ACTIVE 检查不变。当前切片详见[阶段 2 记录](PHASE2_IMPLEMENTATION.md)，原阶段 0 时序保留历史含义，未运行构建或测试。

阶段 0 历史时序保持不变。阶段 1B 已将默认部署范围装配归 runtime、Web 绑定归 WebMVC，原生 Scheduler 扫描入口消费同一 DeploymentDataScope；业务时序和身份来源不变，详细现状见[1B 实施记录](PHASE1B_IMPLEMENTATION.md)。构建和测试未运行。

核对日期：2026-11-15。此处记录当前工作区源码行为，不是目标架构或运行验收结果。事务记号表示 Spring 本地事务回调及其提交边界，不代表已经在 PostgreSQL 验证原子性。跨服务箭头均不共享本地事务。

相关资料：[基线与例外](PHASE0_BASELINE.md)、[实际依赖清单](PHASE0_DEPENDENCIES.md)、[后续设计](ARCHITECTURE_EVOLUTION.md)。

## 1 身份和事务公共约定

- 入站身份先经 Resource Server 验证，再由各领域 ActorProvider/用例核对主体、来源服务和权限。当前部署范围由本地 DatabaseTenant 决定，不以请求体或普通租户 Header 建立身份，不要求机器 JWT 携带 tenant_id。
- acquire → productcenter 的准入调用委托原已验证主体；后台恢复与 payment/limitcenter 之间的调用使用各目标 audience 的 SERVICE 身份。不能把这两类调用互相替代。
- acquire 的 OrderCreationTransactions.writeLocal 使用 REQUIRES_NEW、READ_COMMITTED，拒绝已有事务；只对 AcquireWriteConflictException/TransientDataAccessException 最多执行三次完整本地事务。网络不在此循环内。readSnapshot 是独立只读 REPEATABLE_READ；withTenantContext 只绑定上下文和转换错误，不开启事务。
- payment、productcenter 发布和 limitcenter 各有自己的事务包装，不能把 acquire 的异常重试白名单推定为全系统规则。各写入口均按其原本的数据源和事务管理器执行。
- Task 的 claim/lease/attempts 管理一次任务执行；业务 cursor/generation 管理原业务后继；Task 管理目标交接。三者均不证明渠道是否收款。

依据：[OrderCreationTransactions](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderCreationTransactions.java)、[JwtAcquireActorProvider](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/java/io/github/loadup/acquire/adapter/security/JwtAcquireActorProvider.java)。

## 2 创单与商业准入

### 2.1 时序

~~~mermaid
sequenceDiagram
    participant C as 已验证调用方
    participant A as acquire
    participant AD as acquire数据库
    participant P as productcenter
    participant M as merchantcenter
    participant PD as productcenter数据库
    C->>A: create / checkout / directPay原意图
    A->>AD: A0事务 固定intent、orderId、摘要与入口
    AD-->>A: 提交INIT或读取原结果
    A->>P: 事务外 authorize 原orderId与委托身份
    P->>PD: 查原准入（有记录则校验重放）
    P->>M: 新准入事务外查询资格
    P->>PD: 本地事务 锁合约、复查准入、保存决定
    PD-->>P: 提交准入决定与来源版本
    P-->>A: 原决定及冻结条款或拒绝
    A->>AD: A1事务 锁原intent，物化或保存拒绝/过期
    Note over A,AD: 成功时订单、快照、费用、到期任务、可选会话及Task同提交
    AD-->>A: COMPLETED / REJECTED / EXPIRED
    A-->>C: 原订单或稳定结果
~~~

图中的 merchantcenter 查询只发生于尚无可重放准入的新决定；完成后的 acquire 请求直接回放原订单，不重新走该链路。

### 2.2 身份、键与事务

| 环节 | 当前约束与提交内容 |
|---|---|
| acquire 入口 | create 要求 pay:acquire:create；checkout 要求其独立权限。稳定所有权来自已验证主体派生的 scopeHash |
| A0 | 当前 schema 内以收款商户 + merchantOrderNo 查重，摘要另绑定部署范围、主体、金额、币种、产品 scope、期限和入口模式；固定 intentId/orderId，CHECKOUT 还固定首次 sessionId |
| 准入 | productcenter 要求 pay:product:admit；orderId 唯一且重放校验 caller scope 与完整 intent。首次资格查询在事务外，合约状态与准入在同一合约锁下决定 |
| 额度前置 | 准入读取真实激活发布记录，不把完整配置校验、INIT 占位或 Task 受理当作 ACTIVE |
| A1 | 锁原 intent；验证原准入、条款、摘要及 materializeBefore。到达精确截止禁止首次落单。拒绝/过期先提交结果；成功保存 INIT 订单、冻结快照、整单 INIT fee、到期任务、可选首次会话、COMPLETED 及订单快照事件 |
| 完成条件 | intent 为 COMPLETED/SUCCESS，订单与 admission 身份一致；Checkout 重放同一首次会话和期限，不续期 |

初始额度发布是独立前置流程：PolicyPublicationService 在合约锁内冻结激活命令并与 Task 同事务写 PUBLISHING；worker 事务外调用 limitcenter.activate，核验 policyId/version/hash/activatedAt 后短事务写 ACTIVE。业务发布状态与 Task 投递状态独立。

### 2.3 失败和恢复

- A0 后网络失败或 A1 回滚保留原稳定意图，调用方以同商户订单号和原参数重入；productcenter 按原 orderId 重放决定。
- 同键改参、换入口模式或改变主体不等价于重试；不能重新分配订单号绕过原拒绝/过期结果。
- 当前已检查的任务注册和服务入口中未定位 A0 INIT 的专用后台扫描器。此项记为自动续办覆盖缺口，不能宣称所有 INIT 均会后台自动完成。
- 合约经营状态与商户资格读取没有跨库原子窗口；暂停合约与本地新准入共锁，商户状态读取到决定之间仍有跨服务时差。

关键源码：[OrderCreationService](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderCreationService.java) createIntent、[OrderAdmissionService](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/service/OrderAdmissionService.java) authorize/decide、[PolicyPublicationService](../loadup-pay-productcenter/loadup-pay-productcenter-app/src/main/java/io/github/loadup/productcenter/app/service/PolicyPublicationService.java) publish/deliver。

## 3 付款执行与订单落账

### 3.1 时序

~~~mermaid
sequenceDiagram
    participant C as 商户或收银台
    participant A as acquire
    participant AD as acquire数据库
    participant P as payment
    participant PD as payment数据库
    participant L as limitcenter
    participant CH as 渠道
    C->>A: start 原actionKey、金额与choice
    A->>AD: A2事务 固定allocation/paymentId并HELD，登记恢复任务
    AD-->>A: 提交占用与订单快照
    A->>P: 事务外prepareInput（需要时）及accept原命令
    P->>PD: 受理事务 原门禁、INIT付款、恢复游标/任务、快照
    PD-->>P: 提交受理
    P-->>A: 已受理视图，不代表收款
    Note over P: PaymentRecoveryService唤醒原付款
    P->>PD: 本地事务 固定首次attempt与路由
    P->>L: 事务外reserve、begin、verify
    P->>PD: Y2事务 CAS为SUBMITTING，提交唯一执行身份
    P->>CH: 仅内存ticket赢家首次submit
    CH-->>P: 原身份观察或交互动作
    P->>PD: Y3事务 校验、receipt/claim/capacity、状态、Task
    P->>A: Task投递付款结果
    A->>P: 首次事件处理事务外facts回查
    A->>AD: A3事务 订单、allocation、结果回执、费用与通知/快照
    A-->>P: 持久语义ACK
~~~

risk consult、渠道输入校验及本地期限检查也在首次提交准备中；它们不代表已经执行扣款。持久 Y2 之后的恢复只能沿原 attempt 查证/关闭，不能靠重新调用 start 构造第二次首次提交。

### 3.2 身份、键和结果

| 环节 | 当前事实 |
|---|---|
| acquire 入口 | 商户 start 要求 pay:acquire:payment:start 并验证原订单所有权；cashier 使用限原订单/会话的能力，两者复用内部业务操作 |
| A2 稳定键 | orderId + actor action scope + actionKey。固定 allocationId/paymentId、原金额、choice、冻结条款与摘要；原敏感输入不写 acquire 库 |
| payment 受理 | 仅配置的 acquire SERVICE 与 pay:payment:accept；以原 paymentId、allocationId、摘要和完整命令去重，父/单笔 gate 防止迟到受理 |
| 执行身份 | 首次 attempt 固定路由、渠道请求身份；limitOperationId、attemptId、epoch 和许可摘要绑定。只有成功提交 Y2 的当前内存 ticket 进入首次 submit |
| Y3 成功 | 原观察身份/金额/渠道交易归属一致，插入永久交易 claim、refund capacity、receipt，更新 attempt/付款，并写结果与投影 Task |
| Y3 负终态 | 需要可信 NO_COLLECTION，且持久封禁后写 FAILED/CLOSED 及证据；普通空记录、任务失败和超时不构成该事实 |
| A3 | 订单→allocation 加锁；成功 HELD→CONSUMED 并累计 gross，明确未收款才 HELD→RELEASED。结果回执、恰好付清的费用确认、快照和通知登记同事务 |
| 分层完成 | payment 终态、acquire A3、limit 结算、realtime 投影、商户送达分别完成。一个 ACK 不确认其他目标 |

PaymentAcceptanceService.accept 只持久受理，不同步调用渠道。Alipay H5 可只产生交互动作，PaymentResultService 此时不会制造观察回执、成功容量或资金状态转换。

### 3.3 未知结果与恢复所有者

- A2 提交后受理超时，原占用继续存在。AllocationRecoveryService 使用已冻结意图恢复输入元数据或幂等受理，不创建新 A2。
- payment 的恢复任务类型是 pay.payment.collection.progress，稳定业务对象为 paymentId；cursor 与后继任务共同提交。已开始的 attempt 走 recover/close 原身份。
- 首次提交异常或进程中断时，不推断“没有收款”。已提交的 SUBMITTING/PENDING/UNKNOWN 和相关占用等待查证。具体观察更新以 PaymentResultService 为准，不把所有传输异常直接改写成 UNKNOWN。
- native task 失败由 Task attempts/lease 管理；正常处理但未终态由业务轮询策略登记后继。FAILED 运维重试针对原任务，不能创建新金融意图。
- 付款事件首次处理会反查 payment；同 delivery 命中本地语义 receipt 可直接回放 ACK。主动 refresh/关单也复用 A3。

关键源码：[PaymentInitiationOperations](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationOperations.java) start/recover、[PaymentAcceptanceService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentAcceptanceService.java) accept、[PaymentSubmissionPreparation](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentSubmissionPreparation.java) prepare、[PaymentExecutionService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentExecutionService.java) progress、[PaymentResultService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentResultService.java) apply/success/noCollection、[PaymentOutcomeOperations](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/PaymentOutcomeOperations.java) convergeLocked、[PaymentRecoveryService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentRecoveryService.java)。

## 4 退款

### 4.1 时序

~~~mermaid
sequenceDiagram
    participant C as 已验证商户
    participant P as payment
    participant PD as payment数据库
    participant CH as 原渠道
    participant L as limitcenter
    participant A as acquire
    participant R as realtime
    C->>P: 原merchantRefundNo与退款意图
    P->>PD: 规划事务 原成功事实/容量锁、FIFO计划、占用、任务及投影
    PD-->>P: 提交原申请与子退款身份
    Note over P: RefundRecoveryService续办原request
    P->>PD: 准备事务 INIT子退款beginSubmission并提交
    P->>CH: 事务外submitRefund或recoverRefund原退款
    alt 无可信观察或传输不确定
        P->>PD: 保存uncertain并保留退款占用
    else 可信结果
        P->>PD: 结果事务 receipt/claim、child/capacity、投影与目标Task
    end
    P->>L: 成功子退款恢复额度（独立投递）
    L->>P: 首次restore-refund反查refund-facts
    P->>A: 费用汇总与商户通知（各自投递）
    A->>P: 首次费用汇总反查权威累计
    P->>R: 子退款与订单汇总投影（各自投递）
    Note over P,R: 各目标独立持久ACK，不等于商户最终送达
~~~

### 4.2 身份、幂等与资金事务

| 环节 | 当前约束 |
|---|---|
| 申请 | pay:payment:refund:create 且 actor 商户等于 payee；详情要求 refund:read 并核对商户 |
| 申请键 | 商户 + merchantRefundNo；摘要按部署范围、商户、订单、退款号、金额和原因分帧。原键异参冲突 |
| 子退款身份 | 首次规划固定 requestId 和各 refundId；渠道退款号由原 tenant/refundId 派生，重试不换号 |
| 规划事务 | 锁父订单和成功付款容量；核对退款窗口、原成功事实与 FIFO，保存计划、子退款占用、首轮恢复任务及投影 |
| 提交事务 | 子退款只有 INIT 可以 beginSubmission；提交后才联网。后续恢复沿原 identity，渠道适配不得构造新的退款请求身份 |
| 结果事务 | 验证观察来源、原退款身份、金额与渠道 claim；更新 child/capacity、receipt、永久成功 claim 和相关 Task。冲突记异常，不作为成功 |
| UNKNOWN | uncertain 是本地不确定状态，不冒充可信渠道回执；保留容量，不据任务失败释放 |
| 金融完成 | 子退款按可信结果终结，申请按全部子退款推进。原付款成功及订单 gross 不减少；退款与其后续交接各自独立 |

### 4.3 目标 ACK 和恢复

- RefundRecoveryService 使用原 requestId 和 generation，校验原 native claim；非终态时在本地事务提交 cursor 与唯一后继。任务调度身份不是渠道证据。
- RefundProjectionEvents 在规划、生命周期和终态保存子退款与订单金额汇总；成功 child 另登记 limit；终态 child 与完成 request 各自登记 merchant 事件。
- limitcenter 首次恢复先读本地 refund receipt，再回查 payment.refundFacts；匹配的旧回执直接重放，不要求 payment 再次在线。
- 原付款必须已 CONFIRMED，且有匹配成功版本、证据、attempt 的 CONFIRM 回执。否则 DEPENDENCY_NOT_READY 保持投递重试，不能提前确认退款额度恢复。
- 恢复原 initiatedAt 对应原桶，按权威累计退款高水位取正差；不创建退款发生日的新桶。笔数只在满足原完整退款规则时恢复一次。
- 费用处理先以 delivery receipt 重放；首次在事务外查证累计，再将费用依据/调整与 receipt 原子保存。
- 商户通知接收只验证并登记原退款事实、投递和 receipt；其 ACK 为 ENQUEUED/DISABLED。商户 HTTPS 回调仍有独立签名、尝试和送达 ACK。
- 任一目标投递失败不回滚已经证实的渠道退款成功，也不把其他目标一起确认。

关键源码：[RefundPlanningService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundPlanningService.java)、[RefundExecutionService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundExecutionService.java)、[RefundResultService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundResultService.java)、[RefundRecoveryService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundRecoveryService.java)、[RefundProjectionEvents](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/RefundProjectionEvents.java)、[LimitRefundService](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitRefundService.java)、[FeeRefundOutcomeService](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/FeeRefundOutcomeService.java)、[RefundNotificationService](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/RefundNotificationService.java)。

## 5 关单

### 5.1 时序

~~~mermaid
sequenceDiagram
    participant C as 商户或原到期任务
    participant A as acquire
    participant AD as acquire数据库
    participant P as payment
    participant PD as payment数据库
    participant CH as 渠道
    C->>A: close原订单与稳定请求
    A->>AD: C0事务 intent、CLOSING、closeEpoch、任务、快照
    AD-->>A: 提交关闭意图
    A->>P: 事务外fenceParent
    loop 每轮最多16个HELD，沿原游标
        A->>P: close原payment/epoch
        P->>PD: 锁原gate与attempt、读取旧关闭证据
        opt 无可重放证据且已提交原attempt
            P->>CH: 事务外close原执行身份
            CH-->>P: 明确证据或未知观察
            P->>PD: 仅可信NO_COLLECTION/CLOSE_RESPONSE单独保存证据
        end
        P->>PD: 独立结果事务应用原观察
        P-->>A: 真实付款结果或持久未受理证明
        A->>AD: A3事务应用结果、回执、快照与通知
    end
    A->>P: parentFenceStatus重新核对
    A->>AD: 完成检查事务 或保存游标与后继任务
    Note over A,AD: 父epoch覆盖、子项收敛、零占用及本地回执完整才CLOSED
~~~

### 5.2 约束与恢复

- 商户入口要求 pay:acquire:close 并校验原订单所有权；到期任务按持久 deadlineVersion 和原冻结 CLOSE/KEEP_PENDING 策略执行，不假冒商户新请求。
- 首个关闭请求以 orderId 固定 intent、requestKey、reason、owner 和 closeEpoch；重复必须匹配原摘要，不能换 key 接管流程。
- 父门禁阻止迟到新受理，但不是未收款证明。已受理付款使用真实终态及 A3；未受理时必须 payment=null + fenced + noCollectionEvidenceId，形成 NOT_ACCEPTED/0 版本，而非伪造 CLOSED payment。
- PaymentCloseService 先重放已保存的原 attempt 关闭证据。可信 NO_COLLECTION/CLOSE_RESPONSE 独立持久化后再交给 PaymentResultService；即使结果事务失败，也可继续应用旧证据。
- 关闭调用不确定时保留原状态/占用并查证，不能因关闭请求已返回或 task 已完成推断未收款。payment execution 对已开始的关闭 attempt 调用同一个 close/recovery 路径。
- acquire 的 OrderClosureRecoveryService 按原 intent rowVersion/current task 继续 resume；每轮游标/后继受持久版本约束，旧响应不回退游标。正常在途可以继续下一轮；抛出的依赖失败由原 native task 重试。
- 完成检查重新获取 parentFenceStatus；只有 epoch 覆盖、全部子项收敛、金额/笔数占用为零、allocation 终态回执完整才写 CLOSED。迟到成功可先将订单变为 PAID，不能再改为 CLOSED 或自动退款。
- acquire 运维 retry 要求配置的 pay-ops SERVICE 与 pay:acquire:recovery:retry，校验原 taskId、attempts、nextExecuteAt、generation；只重排原 FAILED 且仍需要的任务，并同事务保存审计回执。不会制造成功事实或重置金融意图。

关键源码：[OrderClosureOperations](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderClosureOperations.java)、[OrderClosureRecoveryService](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderClosureRecoveryService.java)、[OrderDeadlineService](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/OrderDeadlineService.java)、[PaymentCloseService](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentCloseService.java)、[AcquireRecoveryOperationsService](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/service/AcquireRecoveryOperationsService.java)。

## 6 额度结算

### 6.1 时序

~~~mermaid
sequenceDiagram
    participant P as payment的结果Task
    participant L as limitcenter
    participant PD as payment数据库
    participant LD as limitcenter数据库
    P->>L: SERVICE confirm或release原operation与终态证据
    L->>LD: 读取原reservation与owner
    L->>P: 本地事务外查询payment facts
    P->>PD: 原门禁下读取权威事实
    PD-->>L: 原operation/payment/金额/版本/证据
    L->>LD: 结算事务 gate→policy→原buckets→reservation/items
    alt 成功付款confirm
        L->>LD: 验证EXECUTING及原attempt，held转used
    else 明确未收款release
        L->>LD: 只减held；保存FENCED，迟到Reserve不可开门
    end
    L->>LD: receipt与全部变更同提交
    L-->>P: 原语义ACK
    P->>P: 校验ACK，完成本目标Task
~~~

### 6.2 先决条件、锁与幂等

| 项目 | 当前行为 |
|---|---|
| 身份 | 只接受配置的 payment SERVICE 和 pay:limit:confirm/release；owner 与原 reservation 来源一致 |
| 稳定身份 | operationId/paymentId，原 policy/version/hash、initiatedAt、金额与币种；CONFIRM/RELEASE 语义摘要不含投递 eventId |
| Confirm | 回查 SUCCEEDED、原成功 attempt、正版本与证据；本地要求 EXECUTING、OPEN gate，绑定原明细并将 held→used |
| Release | 回查 FAILED/CLOSED 且 executionFenced；可先于 Reserve 保存 gate/receipt，不构造虚假 reservation；EXPIRED 不重复归还 |
| 事务 | operation gate→policy head/revision→按稳定顺序原 buckets→reservation/items；bucket、明细、reservation、gate 与 receipt 原子提交 |
| 完成 | payment 投递器验证原 operation/payment/源版本/证据及期望状态；只有本目标 ACK 合格才完成本次投递 |

Confirm/Release 当前会在读取本地结算 receipt 前回查 payment，因此即使是重复语义也可能受源端可用性影响；不要把退款恢复“先 receipt 再远程”的优化推定到这两个接口。

### 6.3 执行许可、过期和退款恢复

- Reserve/Begin/Verify 是渠道执行前的独立协议，不是终态结算 ACK。Begin 和 expire 争用同 operation gate；同 attempt 重放原 permit/epoch，不产生新执行权。
- expire 只释放达到期限的 RESERVED，不释放 EXECUTING/CONFIRMED。当前提供显式 expire；未定位自动过期扫描器，不能以 TTL 作为未知付款释放方案。
- 成功退款恢复独立于本次 Confirm/Release，必须先有原 CONFIRM receipt；按第 4 节的原桶、高水位和 refund receipt 执行。原合约当前是否活跃不覆盖 reservation 冻结解释。
- 超时、空事实或证据不匹配不修改额度；payment 的原 Task 保留重试。死信/FAILED 的人工处理沿原事件与权限进行，不改成直接写桶。

关键源码：[LimitSettlementService](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitSettlementService.java) confirm/release/facts/items、[LimitExecutionService](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitExecutionService.java)、[PaymentLimitOutcomeDelivery](../loadup-pay-payment/loadup-pay-payment-app/src/main/java/io/github/loadup/payment/app/service/PaymentLimitOutcomeDelivery.java)、[LimitRefundService](../loadup-pay-limitcenter/loadup-pay-limitcenter-app/src/main/java/io/github/loadup/limitcenter/app/service/LimitRefundService.java)。

## 7 核心跨领域契约登记

普通内部 JSON 使用 POST 和 SuccessResponse/FailureResponse；表中的 ACK 仅确认该接收方持久语义，认证/传输/未知错误不映射成金融拒绝。下列是五条流程的关键操作，不是全系统 API 清单。

| 操作与提供方 | 类型 | 幂等与成功含义 | 未知/失败后的所有者 |
|---|---|---|---|
| productcenter admissions/authorize | 准入命令 | orderId + 原主体/完整意图；返回持久准入决定，非渠道执行许可 | acquire 原 A0 重入；product 原决定回放 |
| limitcenter operations/activate | 策略发布命令 | policyId/version/hash；ACK 表示该策略激活 | productcenter 原 publication Task |
| payment input-prepare/lookup/expire | 输入准备、查询、封禁命令 | 原付款/订单/输入请求绑定；expire 需持久未受理证明 | acquire 原意图恢复；不按缺失输入另扣 |
| payment operations/accept | 受理命令 | paymentId/allocationId + 完整摘要；ACK 表示受理持久化 | acquire allocation recovery，payment 注册执行任务 |
| limitcenter reserve/begin/verify | 预占/执行门禁 | 原 operation/attempt/epoch；verify 是时点观察 | payment 原执行准备与恢复 |
| payment outcome/facts/refund-facts/refund-summary | 权威查询 | 返回原身份及证据/累计；查询结果必须经消费方绑定验证 | 原调用工作流，不由通用 HTTP 自动重试 |
| acquire events/payment-outcome | 终态事件接收 | delivery digest + 源证据；首次回查后与 A3/receipt 同提交 | payment acquire 目标 Task |
| limitcenter confirm/release | 结算命令 | operation 语义回执；held→used 或可信释放/封禁 | payment limit 目标 Task |
| payment fence-parent/close/parent-fence-status | 门禁命令与查询 | 原 parent/payment/closeEpoch；关闭请求不等于未收款 | acquire 原关闭任务；payment 原执行查证 |
| limitcenter restore-refund | 退款恢复命令 | refundId + 完整摘要；原桶正差/NO_ACTION 回执 | payment refund limit 目标 Task |
| acquire events/fee-refund | 汇总事实接收 | delivery + 摘要；权威累计、费用高水位与 receipt | payment refund fee 目标 Task |
| acquire events/refund-notification | 通知登记命令 | delivery + 摘要；ENQUEUED/DISABLED，非商户已收到 | payment merchant 目标 Task；商户送达另由 acquire 任务负责 |
| realtime projections | 快照应用 | delivery/businessEvent/producer/aggregate/version；APPLIED/DUPLICATE/STALE | 各来源独立投影 Task |

准确传输路径以提供方接口常量为准：[ProductHttpService](../loadup-pay-productcenter/loadup-pay-productcenter-client/src/main/java/io/github/loadup/productcenter/client/http/ProductHttpService.java)、[PaymentHttpService](../loadup-pay-payment/loadup-pay-payment-client/src/main/java/io/github/loadup/payment/client/http/PaymentHttpService.java)、[LimitCenterHttpService](../loadup-pay-limitcenter/loadup-pay-limitcenter-client/src/main/java/io/github/loadup/limitcenter/client/http/LimitCenterHttpService.java)、[AcquireHttpService](../loadup-pay-acquire/loadup-pay-acquire-client/src/main/java/io/github/loadup/acquire/client/http/AcquireHttpService.java)。共享事件归 contracts-events，不归 payment-client。

## 8 已登记的缺口和验收边界

| 编号 | 当前限制或待证事项 | 不应作出的推断 |
|---|---|---|
| F0-1 | 未定位 A0 INIT 专用自动续办扫描器；调用方/显式恢复可重入原意图 | 所有创单断点都自动后台恢复 |
| F0-2 | 显式额度 expire 已有；自动扫描和跨 attempt 安全替换未纳入当前实现 | EXECUTING 可到期释放或换新 attempt 重扣 |
| F0-3 | 付款事件和额度结算仍有首次同步回查，Confirm/Release 回查还先于 receipt | 当前已具备完全自治的事实消费 |
| F0-4 | 各本地事务与锁序已从源码核对，未运行真实数据库并发/回滚 | 时序图证明 PostgreSQL 和多进程故障恢复已通过 |
| F0-5 | 查询重建、商户回调、渠道真实行为与部署网络另有验收范围 | 某个目标受理 ACK 等于端到端完成 |

阶段 0 在文档层面完成这些缺口的登记，不通过编造节点或删除未知分支把流程画成已经闭环。
