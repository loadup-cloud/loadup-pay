# Payment 业务实现

当前实现付款受理、权威查询、父订单执行封禁及单笔关闭的 COLA 业务切片。默认 bootstrap 仍为基础骨架；Maven 与 Spring 的 payment-business profile 共同启用业务装配。真实渠道提交、查询和退款仍待接入。

## 当前入口

以下均为内部 POST，只接受框架资源服务器已验证的指定 acquire SERVICE JWT；principal_type=SERVICE、sub=service_id、tenant_id 必须有效，service_id 必须等于配置的 acquire-service-id。商户凭证或自填 header 不能升级服务身份。

| 路径（前缀 /api/payment/executions） | Client API | 权限 | 响应数据 |
|----------------------------------|------------|------|----------|
| /accept | PaymentAcceptanceFacade.accept | pay:payment:accept | PaymentExecutionDTO |
| /detail | PaymentAcceptanceFacade.detail | pay:payment:read | PaymentExecutionDTO |
| /outcome | PaymentOutcomeFacade.outcome | pay:payment:read | PaymentOutcomeDTO |
| /close | PaymentControlFacade.close | pay:payment:close | PaymentCloseDTO |
| /fence-parent | PaymentControlFacade.fenceParent | pay:payment:fence | ParentFenceDTO |
| /parent-fence-status | PaymentControlFacade.parentFenceStatus | pay:payment:fence:read | ParentFenceDTO |

业务成功使用 SuccessResponse，固定错误码使用 FailureResponse。完整 PaymentExecutionFacade 继承受理、控制及结果查询切片，startAttempt 已新增独立持久准备切片，见后文；实际渠道提交器尚待接入。

## 付款受理与查询

accept 只代表付款意图持久受理；首次返回 INIT/version0、空 attempt/action/terminalEvidenceId。当前没有渠道后台执行器，不能将受理视为扣款成功，也不能因此释放 acquire 占用。

付款固定绑定 orderId/allocationId/admissionId/payeeMerchantId/金额/币种；完整 AdmittedOrderTermsDTO 必须有效且 schemaVersion=1，本笔金额不超过原总额，选择的渠道/方式属于原冻结能力。原条款允许合约停止后的原单续付，不重新请求当前 product/customer。跨笔金额、次数和尾款占用由 acquire 的 A2 编排负责，本地单笔校验不构造跨库原子性承诺。

可信 acquire 服务负责提供真实占用及原条款。compiledTermsHash 保留来源引用；本地 payloadHash 只校验持久副本，不能代替调用身份或跨库占用验证。preparedInputRef 冻结为原命令的一部分，后续执行仍需核验其归属、类型和有效期，不是直接扣款凭证。

受理按 parent_gate→payment_gate→payment_order 的顺序，在 READ_COMMITTED 事务中建立 OPEN 门禁和 INIT 付款。既有门禁必须 OPEN 且绑定相同订单/商户/服务；唯一键与瞬态冲突先回滚，再最多重试三次。同 tenant/paymentId 先比较原服务、摘要与完整解码命令，再返回原记录；initiatedAt 与 limitOperationId 不变。相同摘要不能掩盖改参，同 allocation 不能绑定第二笔付款。已关闭付款的 accept 重放只返回原 CLOSED 及证据，不重新打开门禁或扣款。

## 父门禁与收敛查询

fenceParent 持久写 FENCED，closeEpoch 取单调最大值；不同 requestKey 的低代次不会覆盖高代次，相同 key 改代次或其他参数报 IDEMPOTENCY_CONFLICT。没有任何付款时，也先建立父 tombstone，阻止迟到的新 paymentId。

parentFenceStatus 在父门禁行锁内查询。fenced 仅当当前持久代次覆盖请求代次；没有门禁、或查询高于当前代次，都不构造封禁证明。allChildrenConverged 还要求全部已有付款带可信终结证据；INIT/PROCESSING/UNKNOWN/CLOSING 或缺终结证据的终态均不算收敛。unknownCount 保守计入有 activeAttempt 的处理中/未知/关闭中付款，以及终态缺失证据的记录；不等同于仅统计 UNKNOWN 枚举。

父封禁不改写付款状态，不触发渠道关闭，也不将父封禁成功当作可以释放每笔金额的依据。需要 acquire 后续逐笔控制和可信结果编排；未来付款写者继续遵守父门禁→单笔门禁→付款→attempt 的锁顺序。

## 单笔关闭与证据

PaymentCloseCommand 当前字段为 paymentId/orderId/payeeMerchantId/requestKey/fenceEpoch/reasonCode，fenceEpoch 必须大于0。回执身份 tenant+PAYMENT_CLOSE+paymentId+requestKey；父封禁为 tenant+PARENT_FENCE+orderId+requestKey。服务端使用 UTF-8 长度分帧计算摘要，绑定租户、服务、类型、订单、付款、商户、key、代次和原因；不接受客户端提供控制摘要。

关闭按相同锁顺序执行，先持久封禁付款，再依据锁内事实处理：

- 尚未建立付款及门禁：插入单笔 FENCED tombstone 与不可变未收款证据回执，返回 payment=null；不伪造金额为0的付款。
- 已有门禁但缺付款：必须找到原持久未收款回执，并核对订单/商户/服务；缺少证据返回 SNAPSHOT_INVALID，不将记录缺失当作未扣款。
- INIT/version0/无 activeAttempt：从未准备过尝试，可在门禁下本地转 CLOSING→CLOSED，同事务保存终结证据与回执。
- PROCESSING/UNKNOWN，或其他仍未终结的初始化记录：只转 CLOSING；原 attempt、金额和首次受理时间保持，不出具未收款证据。
- SUCCEEDED 保留成功事实，noCollectionEvidenceId 为空；FAILED/CLOSED 只复用原终结证据，不重开或改写终态。

PaymentCloseDTO 含 paymentId/orderId/当前实际 fenceEpoch/fenced、可空当前 PaymentExecutionDTO 和 noCollectionEvidenceId。相同 key 重放原回执并读取当前付款，不换证据、不另开门禁；未知付款随后由渠道工作流收敛时，可返回新的当前终态视图。原回执存在而门禁缺失视为损坏，不静默补建。不同 key 对同一未创建付款继续复用原未收款证据。

门禁、付款变更和回执在同一数据库事务提交，CAS 验证 row_version；响应失败不让已提交的关闭重新变成可扣款。未收款证据只供后续可信内部消费，acquire 已新增请求触发的权威结果刷新和释放源码；limitcenter 释放、Outbox 结果及渠道关闭扫描仍待接入。

## 数据库与组件复用

V20261022020000 创建 payment_parent_gate/payment_gate/payment_order；V20261022030000 新增 payment_control_receipt，并要求 CLOSED/FAILED 持有 terminal_evidence_id。本服务原4张业务表，加上本轮 payment_input 共5张，见后文。回执含 kind/target/order/payee/service/requestKey/requestDigest/requestedEpoch/reasonCode/noCollectionEvidenceId，业务唯一键不包含 deleted。

DO 继承框架 BaseDO；Mapper/TableDef/Tables 由 LoadUp MyBatis-Flex 包装处理器生成，隔离处理器路径显式包含上游处理器。仓储只使用生成列并显式约束 tenant/deleted，所有表示转换复用 Spring MapStruct。门禁 rowVersion 和付款 terminalEvidenceId 已贯通 domain/DO/DTO；当前模型直接演进，无旧构造器兼容层。

完整受理命令保存为 intent_payload LONGTEXT，SHA-256 校验其副本；payload/输入引用在诊断 toString 中隐藏。不保存原 JWT、付款码、渠道 openId 或渠道凭证。本轮 payment_input 已新增实际迁移；目标草案的 attempt、退款、父级退款汇总等结构尚未迁移，不能直接执行 design/schema/payment.sql 代替实际脚本。

## 构建与运行

从 loadup-pay 根目录执行；-am 保证使用当前依赖模型而不是陈旧安装 JAR：

```bash
mvnd clean test-compile -pl payment/payment-bootstrap -am -Ppayment-business \
  -DskipTests -Dskip.spotless=true -Dskip.spotbugs=true

./payment/build.sh -Ppayment-business
```

运行设置 SPRING_PROFILES_ACTIVE=payment-business，JVM/TZ 为 Asia/Shanghai，paymentClock 默认 systemDefaultZone，业务时间 DATETIME(6)，数据库会话 +08:00。配置稳定数据库 ID sequence site，并注入：

| 环境变量 | 用途 |
|----------|------|
| PAYMENT_JDBC_URL / PAYMENT_JDBC_USERNAME / PAYMENT_JDBC_PASSWORD | 独立数据库与 Secret |
| PAYMENT_JWT_ISSUER_URI / PAYMENT_JWT_AUDIENCE / PAYMENT_JWT_JWK_SET_URI | JWT 验证 |
| PAYMENT_ACQUIRE_SERVICE_ID | 可信 acquire 服务 ID，默认 acquire |

业务 profile 开启 Flyway，启动会执行未应用迁移。现有 deploy/pay.yml 仍为基础应用配置，业务部署需要 Secret/ConfigMap/profile 覆盖层；本批没有执行数据库迁移、打包业务镜像或部署 Kubernetes。

## 验证记录

2026-10-22：IDEA MCP 已恢复，已执行关键 app/domain/client/adapter、Converter 与测试源码的错误检查。IDEA 对生成 Mapper/Tables 仍有索引未解析提示，未将其标为检查通过；为验证注解处理，执行 payment-business 的窄范围 mvnd clean test-compile，9个相关模块成功，MapStruct 与四个 DO 的生成类型编译通过。关闭 DTO 的嵌套 payment 映射已在编译反馈后显式声明并复验。

新增2类22个测试方法，累计本批业务切片6类46个方法已编译，未执行 JUnit。覆盖先关闭/父封禁后迟到受理、幂等改参、epoch 单调、INIT 本地关闭、未知保留CLOSING、成功终态保护、缺记录/缺门禁的证据边界、原证据重放、权限/事务拒绝及回执写入故障。

mock 端口只能证明编排和事务边界，不能替代真实 MySQL 锁、回滚和并发验收。真实 JWT/HTTP/MySQL 集成、渠道关闭收敛、金额/额度释放与后续结果事件仍待完成。

## 供 acquire 落账的权威结果查询

新增 POST `/api/payment/executions/outcome`，输入 IdQuery(paymentId)，要求同一受信 acquire SERVICE 身份及 `pay:payment:read`。返回 SuccessResponse<PaymentOutcomeDTO>，其中 payment 是既有 PaymentExecutionDTO，executionFenced 反映单笔持久执行门禁。

查询在 READ_COMMITTED 本地事务内按 parent_gate→payment_gate 加锁并重读付款；复核两级门禁/订单/商户/服务绑定与原持久命令，不创建门禁、不修改付款或发起渠道请求。父 gate 封禁不能替代该单笔标志。不存在付款只返回 NOT_FOUND，不能构造未收款结果或让 acquire 释放占用。

acquire 校验原 allocation/金额/币种，成功凭终结证据累计；FAILED/CLOSED 必须再有 executionFenced=true 才释放。持久状态或网络错误都不是单独的资金证据。本接口只暴露已有权威事实，真实渠道结果生产、未知关闭收敛及后台投递仍未实现。

本节是暂停验证后的新增源码，按用户要求未做 IDEA 检查、编译、测试、迁移或部署；前文编译记录只对应此前受理与控制切片。

## acquire 关单调用方接入状态

acquire 已新增显式关闭编排，依次调用 fenceParent、逐笔 close 与 parentFenceStatus，按原服务端 closeIntentId 固定请求 key；需授予 SERVICE 凭证 pay:payment:close/fence/fence:read。INIT 可本地关闭，未知仍等待后续真实渠道工作流。

payment=null 的关闭响应仍只表示持久 tombstone 与未收款回执；acquire 使用独立 NOT_ACCEPTED/0付款版本事实释放原占用，不补建付款。已受理付款则沿原正版本终态结果落账。上述接入源码未在本批执行验证或运行验收。

## 供额度服务核验的付款事实

新增 PaymentSettlementFactsFacade.paymentFacts 及 POST `/api/payment/facts/payment`，接 IdQuery(paymentId)，返回 PaymentSettlementFactsDTO（付款、limitOperationId、payeeMerchantId、单笔 executionFenced）。仅允许 PAYMENT_LIMITCENTER_SERVICE_ID（默认 limitcenter）对应 SERVICE 和 pay:payment:limit-facts，不复用商户或 acquire 原调用授权。

本地事务按父门禁→单笔门禁加锁并重读完整付款/原命令，校验门禁与原订单/商户/受理服务绑定。limitcenter 在持有其本地锁之前查询该接口，基于可信终态做确认/释放；支付写者须先提交自身事实，再在事务外通知额度服务，避免跨服务锁环。接口不创造成功/未收款事实，真实执行与结果生产仍待实现。

本批源码未运行检查、编译、测试或部署。

## 短期敏感输入与付款绑定（新增源码）

POST `/api/payment/inputs/prepare` 由 PaymentInputFacade 承接，仅允许指定 acquire SERVICE 和 `pay:payment:input:prepare`。命令固定 paymentId/orderId/allocationId/payeeMerchantId/amountMinor/CNY/inputKey/choice/interaction；接受 PAYMENT_CODE 付款码与 JSAPI 同渠道身份票据，H5 + NONE 不走此接口。

服务在数据库事务外复用 LoadUp KmsTemplate/OpenBao Transit 加密完整作用域负载；持久层只保存密文、keyName/version 和 HMAC-SHA256 指纹，付款意图只保存256位随机不透明引用。指纹绑定租户、受理服务及完整命令，避免对低熵付款码保存可离线枚举的普通哈希。业务唯一键 tenant+paymentId，不能换 inputKey 或敏感值覆盖原记录；重放使用原 fingerprintKeyId，不重新加密或延长到期时间。并发唯一键失败先回滚再读赢家。新建输入默认120秒，配置范围1—600秒；过期重放仍返回原引用/时间，受理和使用时拒绝过期输入。

首次 accept 在父门禁→单笔门禁之后锁 input，核对租户/原 acquire、订单/付款/占用/收款商户/金额/币种/渠道/方式。H5 禁止携带 inputRef；付款码/JSAPI 必须有匹配的 INIT 输入。既有付款的 accept 重放不因输入过期改变原事实。内部 PaymentInputAccess.bindToAttempt 可加入后续 Y2 同事务，将输入 INIT→BOUND 固定给唯一 attempt；另一个 attempt 无法复用。openForAttempt 仅为原活跃 PROCESSING attempt 在事务外解密，核对密文负载与原作用域/HMAC/有效期；不提供 HTTP 解密入口，也不把解密当作渠道提交或重试许可。真实 Y2/渠道工作流尚未实现。

V20261022090000__payment_input.sql 新增 payment_input，本服务实际5表。敏感字段诊断隐藏，字节缓冲在加解密后清零；原输入不进入 payment_order、acquire 意图或 Outbox。到期密文物理清理、异步恢复和更换 attempt 的新输入生命周期待补，不能仅凭过期判断渠道未收款。

业务 profile 新增 PAYMENT_INPUT_FINGERPRINT_KEY（至少32随机UTF-8字节）和 PAYMENT_KMS_ENDPOINT（HTTPS origin），PAYMENT_KMS_TOKEN 由框架 KMS 运行身份使用。PAYMENT_INPUT_ENCRYPTION_KEY 默认 payment-input，部署前在 OpenBao 创建相应加密 key 并授予运行身份 encrypt/decrypt 权限；应用不开启管理权限或创建 key。保留旧 fingerprint-keys 条目直至不再需要原请求重放，active-fingerprint-key-id 与 map key 要一致；默认 YAML 配置 v1，轮换请同时覆盖 map。加密使用同名 key 的 Transit version 轮换，保留未清理密文所需解密版本。本地 HTTP 仅在显式 PAYMENT_KMS_ALLOW_INSECURE_HTTP=true 时允许。

新增9个输入准备/重放/轮换/并发回滚/作用域/到期/绑定边界测试方法源码；现有受理测试构造器已同步新依赖。按用户要求均未运行检查、编译、测试、DDL 或部署，先前编译记录不覆盖本节。

## 渠道收款绑定、版本与选路（新增源码）

新增 ChannelBindingFacade 的 publish/suspend/revoke/detail，POST 前缀 `/api/payment/channel-bindings`，分别要求 `pay:payment:binding:publish|suspend|revoke|read`。只接受 PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID（默认 pay-ops）的受信 SERVICE；商户和 acquire 不能选择或修改内部绑定。返回 SuccessResponse，错误仍为固定 FailureResponse。管理查询不返回渠道账户负载、凭证或授权引用。

发布命令包含 payeeMerchantId/bindingCode/channel/accessMode、类型化 identity、credentialReference/credentialVersion、capabilities、priority、requestKey/expectedVersion。支付宝 ALIPAY_V1 固定签名 appId、实际 sellerId、可选 operatorAppId，服务商额外使用版本化商户授权引用；微信 WECHAT_V1 固定 appId/merchantId，服务商再固定 subMerchantId 和可选 subAppId。所有密钥及授权 token 均在 Secret/KMS 资源中，只传引用与版本。依据 [微信服务商 JSAPI 文档](https://pay.wechatpay.cn/doc/v3/partner/4012759974) 和[支付宝多商家收款说明](https://github.com/AlipayDocs/open-docs/blob/main/support/支付宝小程序/案例分析/小程序支付如何实现多商家收款.md)区分实际收款与应用作用域；不将渠道身份合并为通用 wire 参数。

bindingCode 在同 tenant/payee 下唯一，channel/accessMode 不可在原 code 上变更。第一次 expectedVersion=0，领域先 INIT，再原子提交 ACTIVE 头、不可变 revision 和变更回执；后续发布须匹配当前 head.rowVersion，新增 revision 后切换当前指针。priority 保留为通用绑定元数据；冻结选路方案内的候选顺序由 rules 数组决定。状态/指针/优先级只通过列白名单与 CAS 更新，不覆盖旧 revision。普通暂停 SUSPENDED 可通过发布新版本恢复；永久 REVOKED 不能重新发布或退回暂停，需要新 bindingCode。暂停/撤销不删除旧版本，旧查单/原路退款依然能通过 revisionId 查找。

tenant+bindingId+requestKey 对发布、暂停、撤销共用唯一键；相同 key 改参数/操作类型拒绝。重放返回该操作当时的 appliedVersion/state/revision，不把旧发布重放成当前激活操作；detail 才返回当前头。摘要绑定租户、指定服务、完整命令和 expectedVersion，能力集合排序后计算，避免 Set 顺序造成假冲突。头、revision、回执在同一 READ_COMMITTED 事务提交，唯一键冲突先回滚再重读赢家。

账户范围哈希按渠道与实际收款账户计算（支付宝 sellerId、微信实际 mchid/sub_mchid），排除租户、接入模式、应用和凭证/授权版本。同一实际账户的不同别名必须共用财务唯一范围；完整应用/服务商身份仍由不可变 identityPayload/identityHash 冻结，不以去重范围代替请求验签或身份校验。身份负载保存完整非秘密元数据与版本化授权引用，identityHash 使用类型化规范编码；MySQL JSON 重排空白/键序不会破坏校验。读取严格拒绝未知字段、篡改、错误模式/身份及损坏能力位。

PaymentRouteSelector 已接入 Y1，在 parent/payment 锁内读取原冻结 routingProfileRef，并按原合约允许的 accessMode 和 channel/method 筛选当前 ACTIVE 绑定。候选头按稳定顺序加锁，最终按不可变方案 rules 的顺序选择并冻结 revision。没有合法绑定返回 PAYMENT_CHANNEL_BINDING_UNAVAILABLE；缺失或归属不匹配的方案返回 PAYMENT_ROUTING_PROFILE_UNAVAILABLE。真实渠道提交/结果仍待完成，accept 本身仍只持久受理 INIT。

V20261022100000__channel_bindings.sql 新增 channel_merchant_binding/channel_binding_revision/channel_binding_change_receipt，payment 当前实际迁移共8表。银联身份/产品/接入机构尚未确定，本轮不提供可发布的银联 identity。ACTIVE 表示受信运维允许路由，不证明已完成渠道进件、产品开通、Secret 引用可用性或真实协议适配；这些发布校验与实际接入仍在 B02/B08 后续范围。撤销路由也不声称中止已冻结的在途请求。

新增17个编排/规范编码/选路边界测试方法源码，同步2处旧领域夹具到完整身份与版本模型；未运行检查、构建、测试、DDL、镜像或部署。不能沿用更早的编译记录证明本批通过。

## 首笔 attempt、额度与 Y2 单次提交权（新增源码）

新增 PaymentAttemptFacade/startAttempt，内部 POST `/api/payment/executions/start-attempt`，仅指定 acquire SERVICE+`pay:payment:attempt:start`。PaymentAttemptStartCommand 首笔 previousAttemptEvidenceId 必须为空，choice/preparedInputRef 与原受理意图一致。Y1 按 parent_gate→payment_gate→payment_order 加锁，校验原调用服务/意图，再选原合约允许的路由并检查短期输入，固定 attemptId、原 bindingRevision 和32位随机渠道请求号。INIT attempt 与 PaymentOrder 的 PROCESSING/activeAttemptId 同事务提交，不发送渠道请求。

相同 paymentId/requestKey 校验租户/服务、完整动作摘要与原付款摘要，返回当前付款，不再选路或换请求号；终态/封禁后的原键重放不重开执行。新键不能盲建第二 attempt，当前安全替换仍待 limitcenter 的旧 attempt 权威查证与新输入生命周期实现。merchant 主链仍为原付款金额，未更新任何成功金额。

内部 PaymentSubmissionPreparation.prepare 只供后续受信执行器调用，不开放 HTTP。先读取固定 payment/attempt，非 INIT 不再发额度/risk请求；调用默认 DEFAULT_ALLOW/NOT_CONNECTED 风控，再在事务外依次 Reserve、BeginExecution、Verify，使用原 limitOperationId、付款首次 initiatedAt 和冻结 policy id/version/hash。校验完整 operation/payment/attempt、策略、币种/金额、原时点、正 epoch 和64位 HMAC permit；每个 attempt 的 begin requestKey 固定为 attempt/attemptId。网络失败保留原 attempt 和额度事实，不当作未收款或换号重扣。

最后 Y2 在同一锁序下重读 payment、锁 attempt，仅 INIT 可进入；复核两级 OPEN 门禁、活跃 attempt、原完整绑定及输入有效期，同事务绑定敏感输入并 CAS 到 SUBMITTING，保存实际风险结论、许可 SHA-256（不存 raw permit）、submissionStartedAt 和 rowVersion。只有事务赢家得到一次内存 PaymentSubmissionTicket；重放/竞争者为空，不能重建提交票据。SUBMITTING 表示可能已发送，崩溃后必须走原号查证，租约过期不能重新提交。本轮未连接发送器，不将 Y2 票据或 PROCESSING 当渠道成功。

风控 DENY、权威预占过期/超限，以及 Y2 前输入过期，只在重新加锁确认原 attempt 仍 INIT/epoch0 后才能生成本地未提交证明、封禁单笔门禁、关闭 attempt 并将付款置 FAILED（已有 CLOSING 则 CLOSED），三者同事务。另一 worker 已跨 Y2 时不出具该证明。显式 close 同样新增对 INIT attempt 的本地安全关闭；SUBMITTING/PENDING/UNKNOWN 或缺 attempt 证据继续 CLOSING，不凭网络超时释放金额。终结事实可供既有 acquire/limitcenter 权威接口消费；本轮尚未投递自动额度释放/结果事件。

DefaultRiskGateway 只在未配置 RiskConsultGateway 时装配，兼供未连接风控的结果确认；它不会吞掉已配置外部风控的异常。最终结果异步通知、外部接入规则和失败策略仍后续完成。新增出站 PaymentLimitClient 复用 LoadUp HttpTemplate 与独立挂载 SERVICE JWT，单部署显式限制出站 tenant，不转发商户凭证。需 pay:limit:reserve/execute/verify，接收侧验证签名/issuer/audience；读取文件支持凭证轮换。

新环境为 PAYMENT_LIMITCENTER_TENANT_ID、PAYMENT_SERVICE_ID（默认 payment）、LIMITCENTER_JWT_AUDIENCE、PAYMENT_LIMITCENTER_TOKEN_FILE 和 LIMITCENTER_BASE_URL。V20261022110000__payment_attempt.sql 新增第9表，所有时间 DATETIME(6) 本地时区，主键复用框架 IdGenerator。支付受理仍未自动调度 attempt/Y2；真实提交器、渠道结果/查单/关闭/交易号 claim、quota 终态投递和恢复扫描继续后续实现。

本批新增14个测试方法源码，覆盖准备/重放/拒绝盲替换、Y2一次性、风险拒绝、许可错配、过期/关单竞争与事务边界；账户范围测试与原控制构造器已同步。按用户要求未执行 IDEA 检查、构建、JUnit、DDL、镜像或部署。mock 边界不是 MySQL 实际锁、回滚与并发验收。

## 不可变选路方案（新增源码）

RoutingProfileFacade 提供 POST `/api/payment/routing-profiles/publish|detail`，仅允许配置的 PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID（默认 pay-ops）受信 SERVICE，分别要求 `pay:payment:routing:publish|read`。publish 输入 spec(profileRef/payeeMerchantId/rules) 和 requestKey；每个 rule 为 bindingCode/channel/accessMode/method，数组顺序决定优先级。最多100条、禁止完全重复；不接收任意商户绑定或渠道身份负载。

profileRef 在同 tenant 内唯一并表示不可变版本；调整规则必须发布新引用并通过新合约使用。tenant/caller/requestKey 唯一，原键重放返回原定义和首次时间，不重查绑定或写入；相同 key 改定义/顺序、同 ref 改 key 均拒绝。发布在本地事务锁定同 payee 的绑定头并核对 ACTIVE/真实 revision/方式能力，锁序按 head.id，避免规则优先级成为锁顺序。没有跨服务网络调用。

Y1 的 PaymentRouteSelector 现已强制读取原 routingProfileRef，校验租户、实际 payee 和冻结引用。先与原合约渠道/方式/接入模式求交，再按方案顺序选取当前 ACTIVE 头的不可变 revision。方案不存在或属于其他商户返回 PAYMENT_ROUTING_PROFILE_UNAVAILABLE；方案没有允许组合返回 PAYMENT_UNSUPPORTED_CAPABILITY；没有可用的列名绑定返回 PAYMENT_CHANNEL_BINDING_UNAVAILABLE。方案明确列出的后续候选可用于尚未建立的 attempt，不会绕到任意活跃绑定；binding head 的通用 priority 不覆盖方案顺序。不能据此替换已可能提交的 attempt。

方案引用稳定绑定 code；绑定凭证/身份轮换后，新 attempt 读取新 revision，已有 attempt 继续保留原 revision。暂停/撤销使该头不再参与新选路，重放发布方案不能恢复它；恢复须通过原绑定生命周期规则。此机制不声称能够中止已在途渠道调用或证明 Secret、进件与真实协议可用。

V20261022120000__routing_profile.sql 新增 payment 第10表。LONGTEXT 保存规范化强类型定义，SHA-256保护顺序、商户及引用；读取拒绝未知字段和错配，容忍JSON对象键序/空白变化。模型与 DO 转换继续使用 Spring MapStruct、BaseDO 和生成 Mapper/TableDef。新增22个测试方法源码；按用户暂停验证要求，未运行 IDEA、编译、测试、DDL 或部署。product 合约发布前的跨服务引用/能力全集验收仍待接入；真实渠道发送、查证、结果与退款尚未完成。

## Y3 结果应用、交易号认领与终态事务事件（新增源码）

新增内部 PaymentResultService.record(tenant, PaymentObservation)，不暴露商户/运维 HTTP 成功上报入口。PaymentChannelGateway 的支付 submit/query/close 返回类型已改为包含完整规范事实的 PaymentObservation；旧 ChannelPaymentOutcome 保留为领域状态迁移输入。适配器必须先完成验签/解密及实际 app/商户/子商户身份校验，随后构造观察值；当前尚无真实渠道适配器，也没有将类型名称、sourceDigest 或客户端 evidenceId 当作真实性证明。超时、NOT_FOUND 和无验签响应不能产生 NO_COLLECTION。

观察值包含原 payment/attempt/channel/bindingRevision/accountScopeHash/requestNo、金额币种、结果、可空真实交易号/渠道成功时间、来源、稳定 receiptKey 和已验签原包摘要。只保存规范化非敏感字段和原包哈希，不落原始签名包/付款码/凭证。成功和资金已退回观察必须有交易号与时间。金额固定、CNY，时间规范为本地微秒。factsDigest 排除签名/来源/重发键，只比较完整资金事实；同回执只换签名/重发元数据可幂等，改资金事实记录差错。

Y3 先 parent_gate→payment_gate→payment_order，再锁原 attempt，核对活跃尝试、原金额、商户、意图摘要、服务和额度 operation。成功原子提交：真实交易号 claim、PaymentOrder/attempt 成功、原渠道 succeeded_at 和本地 terminalEvidenceId、独立 refund_capacity、不可变 receipt，以及 acquire/limit/realtime/risk 四个独立 LoadUp Outbox。succeeded_at 与状态/证据用同一付款行 CAS，不分两次状态写入。既有 CLOSING 中的真实收款仍记录为成功，供 acquire 后续按冻结迟到收款策略处置；不能改成未收款释放。

channel_transaction_claim 以 channel/实际 accountScopeHash/channelTransactionNo 全局唯一，再约束 tenant/attempt 和 tenant/payment 唯一。同账户跨租户/app/凭证别名也不能重复认领。仓储只有这一个精确财务键查询使用 TenantManager.withoutTenantCondition，其他回执/容量/差错均按原租户；不向调用方返回其他租户的认领信息。并发冲突经既有事务回滚/有限重试重读赢家后写差错，不生成第二成功。已有成功的新合法回执不重复初始化容量或发事件，也不修改原交易号/证据/时间。

NO_COLLECTION 仅在真实适配器证明终局不可收款后使用；本批首笔 attempt 下同事务封禁单笔 gate、结束 attempt、将付款置 FAILED 或完成 CLOSING→CLOSED，并发四目标事件。多 attempt 的付款全局证明仍须后续安全替换机制；不能只凭一个失败尝试释放整笔。UNKNOWN 保留在途，不初始化容量；迟到中间态不能撤销终态。身份/金额、原回执资金事实、全局交易号归属、终态冲突和渠道冲正均保存不可变差错，不改正常金额；冲正需后续独立补偿流程。

原本地 INIT 付款关单、INIT attempt 未跨 Y2 的关单，以及风控/额度拒绝产生的本地终态，也已接入同事务四目标事件。原键重放及已终态查询不重复发事件。每个目标有独立 deliveryId，同一终态共用 businessEventId/付款版本；复用框架 OutboxTemplate 和同 DataSource 事务，任一写入失败回滚整笔 Y3。本批只完成生产端；四目标投递 handler/接收方 Inbox、重放管理和真实链路尚待闭环，不以任务已入库宣称送达。框架对未注册 handler 的事件会保留失败/重试记录，不应手工 ACK。

V20261022130000__payment_results.sql 新增四张业务表，payment 累计14张业务表；Outbox 标准表复用组件自带迁移。MapStruct/BaseDO/生成 Mapper 和 QueryWrapper 延续原规范。新增25个测试方法源码，覆盖成功原子边界、回执幂等、金额/身份冲突、跨租户认领、终态顺序、关单竞争、四目标事件和失败回滚边界；旧本地关单/风控测试源码同步。按暂停验证要求未运行 IDEA、编译、测试、DDL、镜像或部署，mock 事务边界不能代替真实 MySQL 并发/回滚验收。
