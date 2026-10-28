# 付款与 attempt 的共同绑定

更新：2026-10-27，对应 review R4 的共同绑定与门禁上下文。当前只有源码/测试输入，没有运行验收。

## 责任

PaymentAttemptBinding 是 payment-domain 的纯 Java 值对象，关联已保存的 StoredPaymentAcceptance 与 StoredPaymentAttempt。
它没有 Spring、ORM、数据库锁、租户身份查询、编解码、远端 IO、时钟或资金更新，不授予 Y2 提交权。
PaymentExecutionBindings 仅在 payment-app 将领域失败映射为本模块错误，不承担锁序或业务编排，不引入新的公共模块/基类。

共同绑定规则由该值对象集中表达：

| 关系       | 要求                                                                         |
|------------|------------------------------------------------------------------------------|
| 付款身份   | payment.id == attempt.paymentId                                              |
| 原金额     | payment.amount == attempt.amount，沿用 CNY Money 和原聚合的币种/金额构造约束 |
| 收款商户   | payment.payeeMerchantId == attempt.binding.payeeMerchantId                   |
| 原额度操作 | payment.limitOperationId == storedAttempt.limitOperationId                   |
| 原调用服务 | acceptance.callerServiceId == storedAttempt.callerServiceId                  |
| 原付款摘要 | acceptance.requestDigest == storedAttempt.paymentRequestDigest               |

attempt 自身 requestKey/requestDigest 属于其请求幂等范围，不与原付款摘要混同。
activeAttemptId 的要求取决于操作类型，不在所有绑定构造中一概要求当前 active，以保留原始已提交请求的迟到观察。
冻结渠道/method/输入引用由 requireRoute 集中比较；调用方仍负责解码和校验原冻结意图，值对象不依赖公开 Command 或持久 JSON。

## 操作语义

| 领域要求                 | 前置条件                                                              | 额外业务门禁仍由调用方负责                                                      |
|--------------------------|-----------------------------------------------------------------------|---------------------------------------------------------------------------------|
| 共同 bound               | 以上六类绑定完整                                                      | 不表示当前 active、允许提交或允许修改                                           |
| requireActive            | 当前 activeAttemptId 指向该 attempt                                   | 适用于恢复快照、动作、投递等各自现有状态检查                                    |
| requireSubmittable       | active、付款 PROCESSING、attempt INIT、尚未跨过 Y2                    | 双 OPEN 门禁、有效输入/期限、风险、额度许可，以及锁内 CAS                       |
| requireObservable        | 原 executionEpoch > 0，沿用 StoredPaymentAttempt 的单向 Y2 元数据约束 | 不因 gate fenced、付款终态或当前 active 改变而直接禁止查询；应用资金结果仍走 Y3 |
| requireClosable          | active、付款不是 SUCCEEDED/FAILED/CLOSED                              | 关闭权限、门禁 epoch、是否可本地关闭，以及无收款证据仍独立判断                  |
| requireSubmissionRequest | active、付款 PROCESSING、attempt SUBMITTING 且已经提交准备            | 必须持有原内存 PaymentSubmissionTicket；不允许由此重建票据                      |

所有方法返回原只读绑定，既不变更聚合，也不生成 operation、attempt、渠道请求号、执行 epoch 或任务。
本地未提交关闭仍必须由原 INIT/epoch=0 检查与持久证据路径决定；requireClosable 本身不能证明未收款或释放资金占用。
原 PaymentExecutionLocks 的 parent→gate→payment 顺序、attempt 后续加锁和 OPEN 检查均保留。领域绑定不是锁持有或最终执行授权。

## Y3 的错误顺序

PaymentAttemptBinding.observationRejection 只使用已验证渠道身份/金额和持久记录，保持原语义：

1. 观察身份不匹配、目标付款不匹配或不是当前 active：返回 PAYMENT_IDENTITY_MISMATCH，由现有 Y3 写 CONFLICT receipt/anomaly。
2. 对应目标正确但共同持久绑定损坏：领域失败，app 映射 SNAPSHOT_INVALID；不伪造结果/receipt，不进入金融写入。
3. 观察金额与原金额不一致：PAYMENT_AMOUNT_MISMATCH，继续原冲突审计路径。
4. 原请求未跨 Y2：OBSERVATION_BEFORE_SUBMISSION，继续原冲突审计路径。
5. 无拒绝原因才进入原状态机、claim、容量、receipt 和 Outbox 原子流程。

错误目标优先于该记录其他绑定损坏，避免把已有可审计冲突改为无回执异常；既有 receipt 回放仍在这些检查之前，未移动幂等返回位置。
普通持久绑定失败使用 SNAPSHOT_INVALID；原渠道请求构造边界保留 INVALID_STATE 分类，冻结 route 不符仍为 SNAPSHOT_INVALID。

## 已接入的源码入口

- PaymentSubmissionPreparation：准备前/锁内重新绑定，跨 Y2 前 requireSubmittable；本地拒绝仍使用原关闭条件。
- PaymentExecutionService：当前 attempt 和新建初始 attempt 的共同 active 绑定；恢复/关闭决策与网络调用位置不变。
- PaymentResultService：共用 typed observation rejection，保留冲突回执、终态事实和原容量/投递处理。
- PaymentChannelCallbackService：原 endpoint/账户/请求号预检后 requireObservable，协议验签和原号查询仍在各自原位置。
- PaymentControlService：原未提交关闭分支共用 requireClosable，不以绑定值代替负面资金证据。
- PaymentChannelRequestFactory：原内存 winner 状态、prepared 参数及冻结 route 校验；原状态失败类型保持。
- PaymentActionService：共用 active 绑定，动作可见状态/代次/有效期/门禁继续单独核对。
- PaymentAttemptService：幂等请求重放共用共同绑定，不因此要求旧请求必须当前 active。
- PaymentOutcomeDeliverySupport：原锁内 active/冻结 route 绑定，仍核对 claim/capacity/事件内容与终态证据。

渠道请求构造同时补齐原冻结渠道/method 的对照，原先已检查输入引用；此项只拒绝不一致的持久路由，不提供换渠道或 fallback。
领域聚合/DO 重建/外部输入边界的防御性检查继续保留；不是把所有校验挪到单个万能 Validator。

## 测试与未完成范围

PaymentAttemptBindingTest 源码覆盖逐项付款身份/金额/商户/operation/caller/paymentDigest 篡改，提交前/已提交 winner
区分、终态/旧 attempt 继续可观察、冻结 route、冲突顺序和原错误原因。
PaymentExecutionBindingsTest 源码覆盖同一批篡改在各模式的错误映射、原请求边界 INVALID_STATE、无提交权扩张与观测冲突不被误译为幂等错误。
PaymentResultServiceTest 扩充真实 app 编排输入：错误目标加损坏绑定仍只记冲突；正确目标遇到相同损坏时不记金融结果/新
receipt，不更新 attempt/付款/claim/capacity/Outbox。
其他既有入口测试和实际 MySQL IT 源码继续保留，不用这些纯值对象测试替代完整入口与真实事务证明。

本轮未执行 IDEA、编译、单测、数据库或渠道验收，没有新增通过证据。
门禁与完整执行上下文源码现已补充如下。R4 后续仍需所有入口组合的篡改/终态/权限运行证明，并结合 R6 收敛冻结意图表示；不宣称
R4 全部完成。

## 完整门禁执行上下文

新增纯领域 PaymentGateBinding，构造时一次核对父门禁与付款门禁的 orderId、目标 paymentId、收款商户、原 caller，且拒绝 INIT 门禁。
父门禁的 paymentId 必须为 null，付款门禁的 paymentId 必须匹配当前付款；FENCED 仍可作为合法已关闭门禁视图，只有首次提交要求双
OPEN。

LockedPayment 改为直接持有已校验的 PaymentGateBinding，原 payment/parent/gate 访问均由它派生；删除旧三参数原始记录构造方式，生产与测试调用方同步经过局部
checked factory。
PaymentExecutionLocks 保持 find→parent→payment gate→payment 锁序及原 caller 检查，在真实可写事务中构造该视图，拒绝无事务或只读事务后才允许访问锁网关。
初查与锁后付款 ID/订单身份也必须仍匹配请求，不能将另一个完整付款视图当作本次请求目标。

PaymentExecutionContext 将 PaymentGateBinding 与 PaymentAttemptBinding 组合，并要求两者引用同一个完整付款值快照（含版本/状态/原意图等），不能仅凭相同
ID 混用不同快照。

| 完整上下文模式     | 规则                                                                                                               |
|--------------------|--------------------------------------------------------------------------------------------------------------------|
| activeContext      | 完整绑定 + 当前 active；可用于恢复，门禁可以已经 FENCED                                                            |
| submittableContext | 完整绑定 + 双 OPEN + PROCESSING/INIT/未跨 Y2；锁内提交资格变化保持 EXECUTION_FENCED，绑定损坏仍为 SNAPSHOT_INVALID |
| observableContext  | 完整绑定 + 已提交原请求；不要求 OPEN 或非终态。Y3 仍先处理错误目标冲突，再组装上下文                               |
| closableContext    | 完整绑定 + active + 非终态；允许已 FENCED，不提供无收款证据                                                        |

已接入锁内 Y2、恢复/本地关闭、Y3、动作与结果投递；控制关闭入口使用现有父门禁/付款门禁锁值构造同样的上下文。
付款 outcome 的门禁内部关系改由同一值对象核对；创单/关单对外请求的 actor/command 绑定检查继续保留，属于外部协议边界。
首次 attempt 创建后，使用更新后的付款值与仍持有的门禁重新组装本地视图，不能把创建前后的付款快照混接。

这些值不跟踪锁租约、事务生命周期或持久票据归属；在事务外构造/保留值也不会成为可扣款凭据。Y2 仍只有原 writeLocal 中 CAS
赢得并返回的 PaymentSubmissionTicket 可以跨出事务提交一次。
渠道 IO、令牌、额度许可、输入/期限及金融证据的检查没有搬进领域值；没有增加事务内网络或渠道调用。

### 新增测试源码

- PaymentExecutionContextTest：父/付款门禁十类独立字段/状态篡改、任一 FENCED 拦截首次提交、已提交及终态可观察、关闭不发明证据、拒绝相同
  ID 的不同版本快照混用及诊断字段隐藏。
- PaymentExecutionLocksTest：原网关调用/锁序、只读/无事务在网关前拒绝、caller 不符不获取金融锁、完整但属于另一付款 ID
  的视图仍拒绝。
- PaymentExecutionBindingsTest：门禁变化映射 EXECUTION_FENCED、绑定损坏优先 SNAPSHOT_INVALID、关闭门禁仍允许原请求观察。
- PaymentSubmissionPreparationTest：新门禁视图已 FENCED 时不跨 Y2、不绑定输入、不更新 attempt/付款，也不制造无收款证据。

所有测试只补源码，未执行 IDEA/编译/单测/MySQL/渠道；网关 mock 的顺序断言不证明实际 SQL 锁持有或隔离，R1/R2 与完整 R4
运行验收继续保留。
