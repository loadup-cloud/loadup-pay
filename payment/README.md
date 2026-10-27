# Payment 业务实现

应用间传输已于2026-10-23统一为 gRPC，见[RPC 规范](../design/RPC.md)。本文件旧批次中的内部 HTTP 描述与 `*_BASE_URL` 要求由该规范取代；商户/运营 HTTP 入口继续保留。实际业务 gRPC 随本应用 `*-business` 构建和运行 profile 装配，端口50051；本批通过 IDEA MCP 完成六业务应用及依赖的 `clean test-compile`；未执行测试或部署。

当前实现付款受理、权威查询、父订单执行封禁及单笔关闭的 COLA 业务切片。默认 bootstrap 仍为基础骨架；Maven 与 Spring 的
payment-business profile 共同启用业务装配。真实渠道提交、查询和退款仍待接入。

## 当前入口

以下均为内部 POST，只接受框架资源服务器已验证的指定 acquire SERVICE JWT；principal_type=SERVICE、sub=service_id、tenant_id
必须有效，service_id 必须等于配置的 acquire-service-id。商户凭证或自填 header 不能升级服务身份。

| 路径（前缀 /api/payment/executions） | Client API                             | 权限                   | 响应数据            |
|--------------------------------------|----------------------------------------|------------------------|---------------------|
| /accept                              | PaymentAcceptanceFacade.accept         | pay:payment:accept     | PaymentExecutionDTO |
| /detail                              | PaymentAcceptanceFacade.detail         | pay:payment:read       | PaymentExecutionDTO |
| /outcome                             | PaymentOutcomeFacade.outcome           | pay:payment:read       | PaymentOutcomeDTO   |
| /close                               | PaymentControlFacade.close             | pay:payment:close      | PaymentCloseDTO     |
| /fence-parent                        | PaymentControlFacade.fenceParent       | pay:payment:fence      | ParentFenceDTO      |
| /parent-fence-status                 | PaymentControlFacade.parentFenceStatus | pay:payment:fence:read | ParentFenceDTO      |

业务成功使用 SuccessResponse，固定错误码使用 FailureResponse。完整 PaymentExecutionFacade 继承受理、控制及结果查询切片，startAttempt
已新增独立持久准备切片，见后文；实际渠道提交器尚待接入。

## 付款受理与查询

accept 只代表付款意图持久受理；首次返回 INIT/version0、空
attempt/action/terminalEvidenceId。当前没有渠道后台执行器，不能将受理视为扣款成功，也不能因此释放 acquire 占用。

付款固定绑定 orderId/allocationId/admissionId/payeeMerchantId/金额/币种；完整 AdmittedOrderTermsDTO 必须有效且
schemaVersion=1，本笔金额不超过原总额，选择的渠道/方式属于原冻结能力。原条款允许合约停止后的原单续付，不重新请求当前
product/customer。跨笔金额、次数和尾款占用由 acquire 的 A2 编排负责，本地单笔校验不构造跨库原子性承诺。

可信 acquire 服务负责提供真实占用及原条款。compiledTermsHash 保留来源引用；本地 payloadHash
只校验持久副本，不能代替调用身份或跨库占用验证。preparedInputRef 冻结为原命令的一部分，后续执行仍需核验其归属、类型和有效期，不是直接扣款凭证。

受理按 parent_gate→payment_gate→payment_order 的顺序，在 READ_COMMITTED 事务中建立 OPEN 门禁和 INIT 付款。既有门禁必须
OPEN 且绑定相同订单/商户/服务；唯一键与瞬态冲突先回滚，再最多重试三次。同 tenant/paymentId
先比较原服务、摘要与完整解码命令，再返回原记录；initiatedAt 与 limitOperationId 不变。相同摘要不能掩盖改参，同 allocation
不能绑定第二笔付款。已关闭付款的 accept 重放只返回原 CLOSED 及证据，不重新打开门禁或扣款。

## 父门禁与收敛查询

fenceParent 持久写 FENCED，closeEpoch 取单调最大值；不同 requestKey 的低代次不会覆盖高代次，相同 key 改代次或其他参数报
IDEMPOTENCY_CONFLICT。没有任何付款时，也先建立父 tombstone，阻止迟到的新 paymentId。

parentFenceStatus 在父门禁行锁内查询。fenced 仅当当前持久代次覆盖请求代次；没有门禁、或查询高于当前代次，都不构造封禁证明。allChildrenConverged
还要求全部已有付款带可信终结证据；INIT/PROCESSING/UNKNOWN/CLOSING 或缺终结证据的终态均不算收敛。unknownCount 保守计入有
activeAttempt 的处理中/未知/关闭中付款，以及终态缺失证据的记录；不等同于仅统计 UNKNOWN 枚举。

父封禁不改写付款状态，不触发渠道关闭，也不将父封禁成功当作可以释放每笔金额的依据。需要 acquire
后续逐笔控制和可信结果编排；未来付款写者继续遵守父门禁→单笔门禁→付款→attempt 的锁顺序。

## 单笔关闭与证据

PaymentCloseCommand 当前字段为 paymentId/orderId/payeeMerchantId/requestKey/fenceEpoch/reasonCode，fenceEpoch 必须大于0。回执身份
tenant+PAYMENT_CLOSE+paymentId+requestKey；父封禁为 tenant+PARENT_FENCE+orderId+requestKey。服务端使用 UTF-8
长度分帧计算摘要，绑定租户、服务、类型、订单、付款、商户、key、代次和原因；不接受客户端提供控制摘要。

关闭按相同锁顺序执行，先持久封禁付款，再依据锁内事实处理：

- 尚未建立付款及门禁：插入单笔 FENCED tombstone 与不可变未收款证据回执，返回 payment=null；不伪造金额为0的付款。
- 已有门禁但缺付款：必须找到原持久未收款回执，并核对订单/商户/服务；缺少证据返回 SNAPSHOT_INVALID，不将记录缺失当作未扣款。
- INIT/version0/无 activeAttempt：从未准备过尝试，可在门禁下本地转 CLOSING→CLOSED，同事务保存终结证据与回执。
- PROCESSING/UNKNOWN，或其他仍未终结的初始化记录：只转 CLOSING；原 attempt、金额和首次受理时间保持，不出具未收款证据。
- SUCCEEDED 保留成功事实，noCollectionEvidenceId 为空；FAILED/CLOSED 只复用原终结证据，不重开或改写终态。

PaymentCloseDTO 含 paymentId/orderId/当前实际 fenceEpoch/fenced、可空当前 PaymentExecutionDTO 和
noCollectionEvidenceId。相同 key 重放原回执并读取当前付款，不换证据、不另开门禁；未知付款随后由渠道工作流收敛时，可返回新的当前终态视图。原回执存在而门禁缺失视为损坏，不静默补建。不同
key 对同一未创建付款继续复用原未收款证据。

门禁、付款变更和回执在同一数据库事务提交，CAS 验证 row_version；响应失败不让已提交的关闭重新变成可扣款。未收款证据只供后续可信内部消费，acquire
已新增请求触发的权威结果刷新和释放源码；limitcenter 释放、Outbox 结果及渠道关闭扫描仍待接入。

## 数据库与组件复用

V20261022020000 创建 payment_parent_gate/payment_gate/payment_order；V20261022030000 新增 payment_control_receipt，并要求
CLOSED/FAILED 持有 terminal_evidence_id。本服务原4张业务表，加上本轮 payment_input 共5张，见后文。回执含
kind/target/order/payee/service/requestKey/requestDigest/requestedEpoch/reasonCode/noCollectionEvidenceId，业务唯一键不包含
deleted。

DO 继承框架 BaseDO；Mapper/TableDef/Tables 由 LoadUp MyBatis-Flex 包装处理器生成，隔离处理器路径显式包含上游处理器。仓储只使用生成列并显式约束
tenant/deleted，所有表示转换复用 Spring MapStruct。门禁 rowVersion 和付款 terminalEvidenceId 已贯通
domain/DO/DTO；当前模型直接演进，无旧构造器兼容层。

完整受理命令保存为 intent_payload LONGTEXT，SHA-256 校验其副本；payload/输入引用在诊断 toString 中隐藏。不保存原 JWT、付款码、渠道
openId 或渠道凭证。本轮 payment_input 已新增实际迁移；目标草案的 attempt、退款、父级退款汇总等结构尚未迁移，不能直接执行
design/schema/payment.sql 代替实际脚本。

## 构建与运行

从 loadup-pay 根目录执行；-am 保证使用当前依赖模型而不是陈旧安装 JAR：

```bash
mvnd clean test-compile -pl payment/payment-bootstrap -am -Ppayment-business \
  -DskipTests -Dskip.spotless=true -Dskip.spotbugs=true

./payment/build.sh -Ppayment-business
```

运行设置 SPRING_PROFILES_ACTIVE=payment-business，JVM/TZ 为 系统默认时区，paymentClock 默认 systemDefaultZone，业务时间
DATETIME (6)，数据库会话 +08:00。配置稳定数据库 ID sequence site，并注入：

| 环境变量                                                                | 用途                               |
|-------------------------------------------------------------------------|------------------------------------|
| PAYMENT_JDBC_URL / PAYMENT_JDBC_USERNAME / PAYMENT_JDBC_PASSWORD        | 独立数据库与 Secret                |
| PAYMENT_JWT_ISSUER_URI / PAYMENT_JWT_AUDIENCE / PAYMENT_JWT_JWK_SET_URI | JWT 验证                           |
| PAYMENT_ACQUIRE_SERVICE_ID                                              | 可信 acquire 服务 ID，默认 acquire |

业务 profile 开启 Flyway，启动会执行未应用迁移。现有 deploy/pay.yml 仍为基础应用配置，业务部署需要 Secret/ConfigMap/profile
覆盖层；本批没有执行数据库迁移、打包业务镜像或部署 Kubernetes。

## 验证记录

2026-10-22：IDEA MCP 已恢复，已执行关键 app/domain/client/adapter、Converter 与测试源码的错误检查。IDEA 对生成 Mapper/Tables
仍有索引未解析提示，未将其标为检查通过；为验证注解处理，执行 payment-business 的窄范围 mvnd clean
test-compile，9个相关模块成功，MapStruct 与四个 DO 的生成类型编译通过。关闭 DTO 的嵌套 payment 映射已在编译反馈后显式声明并复验。

新增2类22个测试方法，累计本批业务切片6类46个方法已编译，未执行 JUnit。覆盖先关闭/父封禁后迟到受理、幂等改参、epoch 单调、INIT
本地关闭、未知保留CLOSING、成功终态保护、缺记录/缺门禁的证据边界、原证据重放、权限/事务拒绝及回执写入故障。

mock 端口只能证明编排和事务边界，不能替代真实 MySQL 锁、回滚和并发验收。真实 JWT/HTTP/MySQL 集成、渠道关闭收敛、金额/额度释放与后续结果事件仍待完成。

## 供 acquire 落账的权威结果查询

新增 POST `/api/payment/executions/outcome`，输入 IdQuery (paymentId)，要求同一受信 acquire SERVICE 身份及
`pay:payment:read`。返回 SuccessResponse<PaymentOutcomeDTO>，其中 payment 是既有 PaymentExecutionDTO，executionFenced
反映单笔持久执行门禁。

查询在 READ_COMMITTED 本地事务内按 parent_gate→payment_gate 加锁并重读付款；复核两级门禁/订单/商户/服务绑定与原持久命令，不创建门禁、不修改付款或发起渠道请求。父
gate 封禁不能替代该单笔标志。不存在付款只返回 NOT_FOUND，不能构造未收款结果或让 acquire 释放占用。

acquire 校验原 allocation/金额/币种，成功凭终结证据累计；FAILED/CLOSED 必须再有 executionFenced=true
才释放。持久状态或网络错误都不是单独的资金证据。本接口只暴露已有权威事实，真实渠道结果生产、未知关闭收敛及后台投递仍未实现。

本节是暂停验证后的新增源码，按用户要求未做 IDEA 检查、编译、测试、迁移或部署；前文编译记录只对应此前受理与控制切片。

## acquire 关单调用方接入状态

acquire 已新增显式关闭编排，依次调用 fenceParent、逐笔 close 与 parentFenceStatus，按原服务端 closeIntentId 固定请求
key；需授予 SERVICE 凭证 pay:payment:close/fence/fence:read。INIT 可本地关闭，未知仍等待后续真实渠道工作流。

payment=null 的关闭响应仍只表示持久 tombstone 与未收款回执；acquire 使用独立
NOT_ACCEPTED/0付款版本事实释放原占用，不补建付款。已受理付款则沿原正版本终态结果落账。上述接入源码未在本批执行验证或运行验收。

## 供额度服务核验的付款事实

新增 PaymentSettlementFactsFacade.paymentFacts 及 POST `/api/payment/facts/payment`，接 IdQuery (paymentId)，返回
PaymentSettlementFactsDTO（付款、limitOperationId、payeeMerchantId、单笔 executionFenced）。仅允许
PAYMENT_LIMITCENTER_SERVICE_ID（默认 limitcenter）对应 SERVICE 和 pay:payment:limit-facts，不复用商户或 acquire 原调用授权。

本地事务按父门禁→单笔门禁加锁并重读完整付款/原命令，校验门禁与原订单/商户/受理服务绑定。limitcenter
在持有其本地锁之前查询该接口，基于可信终态做确认/释放；支付写者须先提交自身事实，再在事务外通知额度服务，避免跨服务锁环。接口不创造成功/未收款事实，真实执行与结果生产仍待实现。

本批源码未运行检查、编译、测试或部署。

## 短期敏感输入与付款绑定（新增源码）

POST `/api/payment/inputs/prepare` 由 PaymentInputFacade 承接，仅允许指定 acquire SERVICE 和 `pay:payment:input:prepare`
。命令固定 paymentId/orderId/allocationId/payeeMerchantId/amountMinor/CNY/inputKey/choice/interaction；接受 PAYMENT_CODE
付款码、JSAPI 同渠道身份票据，以及 H5_SCENE（真实终端 IP 与 WAP/IOS/ANDROID 场景）。H5 + NONE 不走此接口，微信 H5 策略明确要求 H5_SCENE。

服务在数据库事务外复用 LoadUp KmsTemplate/OpenBao Transit 加密完整作用域负载；持久层只保存密文、keyName/version 和
HMAC-SHA256 指纹，付款意图只保存256位随机不透明引用。指纹绑定租户、受理服务及完整命令，避免对低熵付款码保存可离线枚举的普通哈希。业务唯一键
tenant+paymentId，不能换 inputKey 或敏感值覆盖原记录；重放使用原
fingerprintKeyId，不重新加密或延长到期时间。并发唯一键失败先回滚再读赢家。新建输入默认120秒，配置范围1—600秒；过期重放仍返回原引用/时间，受理和使用时拒绝过期输入。

首次 accept 在父门禁→单笔门禁之后锁 input，核对租户/原 acquire、订单/付款/占用/收款商户/金额/币种/渠道/方式。带 inputRef 的 H5 与付款码/JSAPI 一样，必须有匹配的 INIT 输入；无引用的 H5 只提供空输入，由实际渠道策略判断是否支持。既有付款的 accept 重放不因输入过期改变原事实。内部
PaymentInputAccess.bindToAttempt 可加入后续 Y2 同事务，将输入 INIT→BOUND 固定给唯一 attempt；另一个 attempt
无法复用。openForAttempt 仅为原活跃 PROCESSING attempt 在事务外解密，核对密文负载与原作用域/HMAC/有效期；不提供 HTTP
解密入口，也不把解密当作渠道提交或重试许可。真实 Y2/渠道工作流尚未实现。

V20261022090000__payment_input.sql 新增 payment_input，本服务实际5表。敏感字段诊断隐藏，字节缓冲在加解密后清零；原输入不进入
payment_order、acquire 意图或 Outbox。到期密文物理清理、异步恢复和更换 attempt 的新输入生命周期待补，不能仅凭过期判断渠道未收款。

业务 profile 新增 PAYMENT_INPUT_FINGERPRINT_KEY（至少32随机UTF-8字节）和 PAYMENT_KMS_ENDPOINT（HTTPS
origin），PAYMENT_KMS_TOKEN 由框架 KMS 运行身份使用。PAYMENT_INPUT_ENCRYPTION_KEY 默认 payment-input，部署前在 OpenBao
创建相应加密 key 并授予运行身份 encrypt/decrypt 权限；应用不开启管理权限或创建 key。保留旧 fingerprint-keys
条目直至不再需要原请求重放，active-fingerprint-key-id 与 map key 要一致；默认 YAML 配置 v1，轮换请同时覆盖 map。加密使用同名
key 的 Transit version 轮换，保留未清理密文所需解密版本。本地 HTTP 仅在显式 PAYMENT_KMS_ALLOW_INSECURE_HTTP=true 时允许。

新增9个输入准备/重放/轮换/并发回滚/作用域/到期/绑定边界测试方法源码；现有受理测试构造器已同步新依赖。按用户要求均未运行检查、编译、测试、DDL
或部署，先前编译记录不覆盖本节。

## 渠道收款绑定、版本与选路（新增源码）

新增 ChannelBindingFacade 的 publish/suspend/revoke/detail，POST 前缀 `/api/payment/channel-bindings`，分别要求
`pay:payment:binding:publish|suspend|revoke|read`。只接受 PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID（默认 pay-ops）的受信
SERVICE；商户和 acquire 不能选择或修改内部绑定。返回 SuccessResponse，错误仍为固定 FailureResponse。管理查询不返回渠道账户负载、凭证或授权引用。

发布命令包含 payeeMerchantId/bindingCode/channel/accessMode、类型化
identity、credentialReference/credentialVersion、capabilities、priority、requestKey/expectedVersion。支付宝 ALIPAY_V1 固定签名
appId、实际 sellerId、可选 operatorAppId，服务商额外使用版本化商户授权引用；微信 WECHAT_V1 固定 appId/merchantId，服务商再固定
subMerchantId 和可选 subAppId。所有密钥及授权 token 均在 Secret/KMS
资源中，只传引用与版本。依据 [微信服务商 JSAPI 文档](https://pay.wechatpay.cn/doc/v3/partner/4012759974)
和[支付宝多商家收款说明](https://github.com/AlipayDocs/open-docs/blob/main/support/支付宝小程序/案例分析/小程序支付如何实现多商家收款.md)
区分实际收款与应用作用域；不将渠道身份合并为通用 wire 参数。

bindingCode 在同 tenant/payee 下唯一，channel/accessMode 不可在原 code 上变更。第一次 expectedVersion=0，领域先 INIT，再原子提交
ACTIVE 头、不可变 revision 和变更回执；后续发布须匹配当前 head.rowVersion，新增 revision 后切换当前指针。priority
保留为通用绑定元数据；冻结选路方案内的候选顺序由 rules 数组决定。状态/指针/优先级只通过列白名单与 CAS 更新，不覆盖旧
revision。普通暂停 SUSPENDED 可通过发布新版本恢复；永久 REVOKED 不能重新发布或退回暂停，需要新
bindingCode。暂停/撤销不删除旧版本，旧查单/原路退款依然能通过 revisionId 查找。

tenant+bindingId+requestKey 对发布、暂停、撤销共用唯一键；相同 key 改参数/操作类型拒绝。重放返回该操作当时的
appliedVersion/state/revision，不把旧发布重放成当前激活操作；detail 才返回当前头。摘要绑定租户、指定服务、完整命令和
expectedVersion，能力集合排序后计算，避免 Set 顺序造成假冲突。头、revision、回执在同一 READ_COMMITTED 事务提交，唯一键冲突先回滚再重读赢家。

账户范围哈希按渠道与实际收款账户计算（支付宝 sellerId、微信实际
mchid/sub_mchid），排除租户、接入模式、应用和凭证/授权版本。同一实际账户的不同别名必须共用财务唯一范围；完整应用/服务商身份仍由不可变
identityPayload/identityHash 冻结，不以去重范围代替请求验签或身份校验。身份负载保存完整非秘密元数据与版本化授权引用，identityHash
使用类型化规范编码；MySQL JSON 重排空白/键序不会破坏校验。读取严格拒绝未知字段、篡改、错误模式/身份及损坏能力位。

PaymentRouteSelector 已接入 Y1，在 parent/payment 锁内读取原冻结 routingProfileRef，并按原合约允许的 accessMode 和
channel/method 筛选当前 ACTIVE 绑定。候选头按稳定顺序加锁，最终按不可变方案 rules 的顺序选择并冻结 revision。没有合法绑定返回
PAYMENT_CHANNEL_BINDING_UNAVAILABLE；缺失或归属不匹配的方案返回 PAYMENT_ROUTING_PROFILE_UNAVAILABLE。真实渠道提交/结果仍待完成，accept
本身仍只持久受理 INIT。

V20261022100000__channel_bindings.sql 新增
channel_merchant_binding/channel_binding_revision/channel_binding_change_receipt，payment
当前实际迁移共8表。银联身份/产品/接入机构尚未确定，本轮不提供可发布的银联 identity。ACTIVE
表示受信运维允许路由，不证明已完成渠道进件、产品开通、Secret 引用可用性或真实协议适配；这些发布校验与实际接入仍在 B02/B08
后续范围。撤销路由也不声称中止已冻结的在途请求。

新增17个编排/规范编码/选路边界测试方法源码，同步2处旧领域夹具到完整身份与版本模型；未运行检查、构建、测试、DDL、镜像或部署。不能沿用更早的编译记录证明本批通过。

## 首笔 attempt、额度与 Y2 单次提交权（新增源码）

新增 PaymentAttemptFacade/startAttempt，内部 POST `/api/payment/executions/start-attempt`，仅指定 acquire SERVICE+
`pay:payment:attempt:start`。PaymentAttemptStartCommand 首笔 previousAttemptEvidenceId 必须为空，choice/preparedInputRef
与原受理意图一致。Y1 按 parent_gate→payment_gate→payment_order 加锁，校验原调用服务/意图，再选原合约允许的路由并检查短期输入，固定
attemptId、原 bindingRevision 和32位随机渠道请求号。INIT attempt 与 PaymentOrder 的 PROCESSING/activeAttemptId
同事务提交，不发送渠道请求。

相同 paymentId/requestKey 校验租户/服务、完整动作摘要与原付款摘要，返回当前付款，不再选路或换请求号；终态/封禁后的原键重放不重开执行。新键不能盲建第二
attempt，当前安全替换仍待 limitcenter 的旧 attempt 权威查证与新输入生命周期实现。merchant 主链仍为原付款金额，未更新任何成功金额。

内部 PaymentSubmissionPreparation.prepare 只供后续受信执行器调用，不开放 HTTP。先读取固定 payment/attempt，非 INIT
不再发额度/risk请求；调用默认 DEFAULT_ALLOW/NOT_CONNECTED 风控，再在事务外依次 Reserve、BeginExecution、Verify，使用原
limitOperationId、付款首次 initiatedAt 和冻结 policy id/version/hash。校验完整 operation/payment/attempt、策略、币种/金额、原时点、正
epoch 和64位 HMAC permit；每个 attempt 的 begin requestKey 固定为 attempt/attemptId。网络失败保留原 attempt
和额度事实，不当作未收款或换号重扣。

最后 Y2 在同一锁序下重读 payment、锁 attempt，仅 INIT 可进入；复核两级 OPEN 门禁、活跃 attempt、原完整绑定及输入有效期，同事务绑定敏感输入并
CAS 到 SUBMITTING，保存实际风险结论、许可 SHA-256（不存 raw permit）、submissionStartedAt 和 rowVersion。只有事务赢家得到一次内存
PaymentSubmissionTicket；重放/竞争者为空，不能重建提交票据。SUBMITTING 表示可能已发送，崩溃后必须走原号查证，租约过期不能重新提交。本轮未连接发送器，不将
Y2 票据或 PROCESSING 当渠道成功。

风控 DENY、权威预占过期/超限，以及 Y2 前输入过期，只在重新加锁确认原 attempt 仍 INIT/epoch0 后才能生成本地未提交证明、封禁单笔门禁、关闭
attempt 并将付款置 FAILED（已有 CLOSING 则 CLOSED），三者同事务。另一 worker 已跨 Y2 时不出具该证明。显式 close 同样新增对
INIT attempt 的本地安全关闭；SUBMITTING/PENDING/UNKNOWN 或缺 attempt 证据继续 CLOSING，不凭网络超时释放金额。终结事实可供既有
acquire/limitcenter 权威接口消费；本轮尚未投递自动额度释放/结果事件。

DefaultRiskGateway 只在未配置 RiskConsultGateway 时装配，兼供未连接风控的结果确认；它不会吞掉已配置外部风控的异常。最终结果异步通知、外部接入规则和失败策略仍后续完成。新增出站
PaymentLimitClient 复用 LoadUp HttpTemplate 与独立挂载 SERVICE JWT，单部署显式限制出站 tenant，不转发商户凭证。需 pay:
limit:reserve/execute/verify，接收侧验证签名/issuer/audience；读取文件支持凭证轮换。

新环境为 PAYMENT_LIMITCENTER_TENANT_ID、PAYMENT_SERVICE_ID（默认
payment）、LIMITCENTER_JWT_AUDIENCE、PAYMENT_LIMITCENTER_TOKEN_FILE 和
LIMITCENTER_BASE_URL。V20261022110000__payment_attempt.sql 新增第9表，所有时间 DATETIME (6) 本地时区，主键复用框架
IdGenerator。支付受理仍未自动调度 attempt/Y2；真实提交器、渠道结果/查单/关闭/交易号 claim、quota 终态投递和恢复扫描继续后续实现。

本批新增14个测试方法源码，覆盖准备/重放/拒绝盲替换、Y2一次性、风险拒绝、许可错配、过期/关单竞争与事务边界；账户范围测试与原控制构造器已同步。按用户要求未执行
IDEA 检查、构建、JUnit、DDL、镜像或部署。mock 边界不是 MySQL 实际锁、回滚与并发验收。

## 不可变选路方案（新增源码）

RoutingProfileFacade 提供 POST `/api/payment/routing-profiles/publish|detail`，仅允许配置的
PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID（默认 pay-ops）受信 SERVICE，分别要求 `pay:payment:routing:publish|read`。publish 输入
spec (profileRef/payeeMerchantId/rules) 和 requestKey；每个 rule 为
bindingCode/channel/accessMode/method，数组顺序决定优先级。最多100条、禁止完全重复；不接收任意商户绑定或渠道身份负载。

profileRef 在同 tenant 内唯一并表示不可变版本；调整规则必须发布新引用并通过新合约使用。tenant/caller/requestKey
唯一，原键重放返回原定义和首次时间，不重查绑定或写入；相同 key 改定义/顺序、同 ref 改 key 均拒绝。发布在本地事务锁定同 payee
的绑定头并核对 ACTIVE/真实 revision/方式能力，锁序按 head.id，避免规则优先级成为锁顺序。没有跨服务网络调用。

Y1 的 PaymentRouteSelector 现已强制读取原 routingProfileRef，校验租户、实际 payee 和冻结引用。先与原合约渠道/方式/接入模式求交，再按方案顺序选取当前
ACTIVE 头的不可变 revision。方案不存在或属于其他商户返回 PAYMENT_ROUTING_PROFILE_UNAVAILABLE；方案没有允许组合返回
PAYMENT_UNSUPPORTED_CAPABILITY；没有可用的列名绑定返回 PAYMENT_CHANNEL_BINDING_UNAVAILABLE。方案明确列出的后续候选可用于尚未建立的
attempt，不会绕到任意活跃绑定；binding head 的通用 priority 不覆盖方案顺序。不能据此替换已可能提交的 attempt。

方案引用稳定绑定 code；绑定凭证/身份轮换后，新 attempt 读取新 revision，已有 attempt 继续保留原
revision。暂停/撤销使该头不再参与新选路，重放发布方案不能恢复它；恢复须通过原绑定生命周期规则。此机制不声称能够中止已在途渠道调用或证明
Secret、进件与真实协议可用。

V20261022120000__routing_profile.sql 新增 payment 第10表。LONGTEXT
保存规范化强类型定义，SHA-256保护顺序、商户及引用；读取拒绝未知字段和错配，容忍JSON对象键序/空白变化。模型与 DO 转换继续使用
Spring MapStruct、BaseDO 和生成 Mapper/TableDef。新增22个测试方法源码；按用户暂停验证要求，未运行 IDEA、编译、测试、DDL
或部署。product 合约发布前的跨服务引用/能力全集验收仍待接入；真实渠道发送、查证、结果与退款尚未完成。

## Y3 结果应用、交易号认领与终态事务事件（新增源码）

新增内部 PaymentResultService.record (tenant, PaymentObservation)，不暴露商户/运维 HTTP 成功上报入口。PaymentChannelGateway
的支付 submit/query/close 返回类型已改为包含完整规范事实的 PaymentObservation；旧 ChannelPaymentOutcome
保留为领域状态迁移输入。适配器必须先完成验签/解密及实际 app/商户/子商户身份校验，随后构造观察值；当前已补支付宝退款、查单/关闭协议源码，收款提交与自动驱动仍待接；不将类型名称、sourceDigest
或客户端 evidenceId 当作真实性证明。超时、NOT_FOUND 和无验签响应不能产生 NO_COLLECTION。

观察值包含原 payment/attempt/channel/bindingRevision/accountScopeHash/requestNo、金额币种、结果、可空真实交易号/渠道成功时间、来源、稳定
receiptKey 和已验签原包摘要。只保存规范化非敏感字段和原包哈希，不落原始签名包/付款码/凭证。成功和资金已退回观察必须有交易号与时间。金额固定、CNY，时间规范为本地微秒。factsDigest
排除签名/来源/重发键，只比较完整资金事实；同回执只换签名/重发元数据可幂等，改资金事实记录差错。

Y3 先 parent_gate→payment_gate→payment_order，再锁原 attempt，核对活跃尝试、原金额、商户、意图摘要、服务和额度
operation。成功原子提交：真实交易号 claim、PaymentOrder/attempt 成功、原渠道 succeeded_at 和本地 terminalEvidenceId、独立
refund_capacity、不可变 receipt，以及 acquire/limit/realtime/risk 四个独立 LoadUp Outbox。succeeded_at 与状态/证据用同一付款行
CAS，不分两次状态写入。既有 CLOSING 中的真实收款仍记录为成功，供 acquire 后续按冻结迟到收款策略处置；不能改成未收款释放。

channel_transaction_claim 以 channel/实际 accountScopeHash/channelTransactionNo 全局唯一，再约束 tenant/attempt 和
tenant/payment 唯一。同账户跨租户/app/凭证别名也不能重复认领。仓储只有这一个精确财务键查询使用
TenantManager.withoutTenantCondition，其他回执/容量/差错均按原租户；不向调用方返回其他租户的认领信息。并发冲突经既有事务回滚/有限重试重读赢家后写差错，不生成第二成功。已有成功的新合法回执不重复初始化容量或发事件，也不修改原交易号/证据/时间。

NO_COLLECTION 仅在真实适配器证明终局不可收款后使用；本批首笔 attempt 下同事务封禁单笔 gate、结束 attempt、将付款置 FAILED
或完成 CLOSING→CLOSED，并发四目标事件。多 attempt 的付款全局证明仍须后续安全替换机制；不能只凭一个失败尝试释放整笔。UNKNOWN
保留在途，不初始化容量；迟到中间态不能撤销终态。身份/金额、原回执资金事实、全局交易号归属、终态冲突和渠道冲正均保存不可变差错，不改正常金额；冲正需后续独立补偿流程。

原本地 INIT 付款关单、INIT attempt 未跨 Y2 的关单，以及风控/额度拒绝产生的本地终态，也已接入同事务四目标事件。原键重放及已终态查询不重复发事件。每个目标有独立
deliveryId，同一终态共用 businessEventId/付款版本；复用框架 OutboxTemplate 和同 DataSource 事务，任一写入失败回滚整笔
Y3。本批只完成生产端；四目标投递 handler/接收方 Inbox、重放管理和真实链路尚待闭环，不以任务已入库宣称送达。框架对未注册
handler 的事件会保留失败/重试记录，不应手工 ACK。

V20261022130000__payment_results.sql 新增四张业务表，payment 累计14张业务表；Outbox 标准表复用组件自带迁移。MapStruct/BaseDO/生成
Mapper 和 QueryWrapper 延续原规范。新增25个测试方法源码，覆盖成功原子边界、回执幂等、金额/身份冲突、跨租户认领、终态顺序、关单竞争、四目标事件和失败回滚边界；旧本地关单/风控测试源码同步。按暂停验证要求未运行
IDEA、编译、测试、DDL、镜像或部署，mock 事务边界不能代替真实 MySQL 并发/回滚验收。

## 2026-10-23 额度与风控终态消费（新增源码）

新增 PaymentLimitOutcomeOutboxHandler 与 PaymentRiskOutcomeOutboxHandler，分别消费 pay.payment.outcome.limit.v1 和
pay.payment.outcome.risk.v1。复用框架 Outbox 的租约、次数、失败记录与重试；每个目标独立完成，失败不阻塞其他目标。acquire/realtime
两个消费者仍待实现，不声明四服务链路已经联通。

PaymentOutcomeDeliverySupport 校验当前 tenant 上下文、已登记且 PROCESSING 的队列元数据、预期
type/schema、producer/aggregate/paymentVersion/businessEvent/evidence，再严格解码。原事实在 parent gate→payment
gate→payment→attempt 锁下复核，包括原命令、金额/CNY、商户、服务、额度 operation、方式及敏感引用。成功必须具备原 attempt 的真实交易号
claim 和同金额/原时间的退款容量；已退款金额不减少原成功基数。负终态必须持续 FENCED 且 attempt 未收款证据一致。成功事件之后新增的
gate fence 不撤销既有成功事实。锁内只读一致快照，返回后释放本地事务才调用下游，避免额度回查 payment 时形成持锁等待。

额度确认使用 claim 原渠道成功时间、原正付款版本/evidence/businessEvent 和原金额/CNY；负终态以固定
PAYMENT_FAILED/PAYMENT_CLOSED 原因释放。confirm ACK 要求 CONFIRMED 及原 payment/operation/policy/version/initiatedAt
一致；release ACK 要求 fenced、原付款版本/证据一致，reservation 可空，非空时为原 RELEASED 或 EXPIRED。超时、错误
ACK/信封或业务失败均传播给框架保留重试，不能伪造成功。limitcenter 仍反查 payment 权威事实。

PaymentLimitClient/PaymentLimitHttpClient 新增 confirm/release，复用现有单出站 tenant、挂载 SERVICE JWT、audience
和轮换读取。application-payment-business.yml 增加 HTTP 命名操作，路径
/api/limit/settlement/confirm|release。PAYMENT_LIMITCENTER_TOKEN_FILE 对应令牌除 reserve/execute/verify 外还需 pay:limit:
confirm、pay:limit:release；接收侧继续验签与校验 issuer/audience/服务权限。limitcenter 回查使用原专用凭证和 pay:payment:
limit-facts，不转发商户 token。

风控确认复用原持久咨询与 RiskDecision。RiskOutcome 新增 tenantId/businessEventId，绑定正 sourceVersion/终态/evidence；外部实现须按
tenant/businessEvent 去重，接收方接受事实后才返回，异常不得转换为默认放行或成功。只有咨询/结果两个角色均未配置时才装配默认
NOT_CONNECTED 实现；外部集成需同时提供两个角色，不能混用外部咨询和静默丢弃结果。没有 attempt 或从未咨询的本地关单只完成“无需通知”任务，不伪造
DEFAULT_ALLOW/咨询。默认未连接 provider 的结果 no-op 是明确配置契约，不冒充外部送达。

新增39个测试方法源码，覆盖源/租户/队列/版本复核、成功证据/容量、后置 fence、空 reservation/已过期释放、ACK
绑定、稳定重试、风控未咨询/失败、JWT轮换/错误身份/事务边界及默认装配。本批未运行 IDEA、构建、测试、数据库迁移或部署；真实
MySQL/HTTP/JWT/Outbox 端到端验收仍待进行。无新增业务表。

## 2026-10-23 acquire 目标投递续批（新增源码）

新增 PaymentAcquireOutcomeOutboxHandler 和 PaymentAcquireOutcomeDelivery，第三个独立目标经既有
PaymentOutcomeDeliverySupport 复核源队列与原终态/交易号证据，再在本地锁释放后通过 SERVICE HTTP 调 acquire
/api/acquire/events/payment-outcome。重试保持原 EventEnvelope，必须收到 accepted=true、原
delivery/businessEvent/payment/version/evidence 一致且 orderVersion 合法的语义
ACK；空回执、错误版本/证据、网络失败不完成队列。acquire/realtime 不会与额度/风控串行绑定；realtime handler 仍待实现。

新增 PaymentAcquireProperties，配置 PAYMENT_ACQUIRE_TENANT_ID（默认复用
PAYMENT_LIMITCENTER_TENANT_ID）、PAYMENT_SERVICE_ID、ACQUIRE_JWT_AUDIENCE、PAYMENT_ACQUIRE_TOKEN_FILE、ACQUIRE_BASE_URL。挂载令牌需
pay:acquire:payment:outcome；接收侧配置 ACQUIRE_PAYMENT_SERVICE_ID，默认 payment。payment 的 limit/acquire 客户端复用
PaymentServiceTokenReader，每次调用重新读挂载文件，核对 tenant/sub/service/principal
type/audience/exp/nbf；只做出站预检，接收侧继续验签，令牌不进入事件或数据库。

/api/payment/facts/payment 增加指定 acquire SERVICE+pay:payment:acquire-facts，只读原 callerServiceId 所属付款；原
limitcenter SERVICE+pay:payment:limit-facts 不变。acquire 因而能够反查原额度 operation 和实际 payee，不能只凭队列上报资金事实。payment
app/infra 增加 acquire-client 协议依赖，没有反向依赖 acquire 实现或破坏领域层隔离。

新增37个测试方法源码（含 acquire HTTP facts 回查测试），覆盖端到端应用事务边界、原 delivery/同资金事实/商户 refresh
幂等、部分/全额/未收款、事件来源/版本/operation、防伪服务身份、ACK
校验、挂载令牌轮换和事实角色权限。未运行检查/构建/测试/DDL/部署；真实渠道发送、realtime、退款与全部剩余范围继续推进，不能据此宣称业务链路已通过联调。

## 2026-10-23 realtime 第四目标与完整付款快照

第四目标由原占位 pay.payment.outcome.realtime.v1 改为设计中的
pay.payment.snapshot.realtime.v1，直接删除旧消息形式，不做前期兼容分支。PaymentResultEvents 在 Y3/本地终态事务内读取固定
attempt（若有）与成功 claim，冻结 channel/accessMode、原 initiatedAt、原渠道 succeededAt 和源版本；三类 acquire/limit/risk
继续使用自己的终态事件。未成功/未尝试没有伪造成功时间或渠道，接收侧不需要反查 payment。

新增独立 handler、严格快照解码、SERVICE HTTP 与 ProjectionAckDTO 校验。只从已登记 PROCESSING 队列发送原完整快照；接收方已投影更高版本时，STALE
ACK 也是合法接收，不能重置到旧状态。ACK 必须绑定 delivery/businessEvent/producer/aggregate/收到版本，projectedVersion>
=收到版本且处置为 APPLIED/DUPLICATE/STALE。目标故障保留原任务重试，不影响其他目标或已经提交的交易。

配置 REALTIME_BASE_URL、REALTIME_JWT_AUDIENCE、PAYMENT_REALTIME_TOKEN_FILE，令牌需 pay:realtime:projection:payment；出站
tenant 默认复用 PAYMENT_LIMITCENTER_TENANT_ID，可用 PAYMENT_REALTIME_TENANT_ID 覆盖。realtime 自有数据库、源版本与商户读取权限，完整说明见
realtime/README.md。当前仅自动生产付款终态；INIT/中间态、订单/退款来源生产和重建仍待完成。新增源码/测试未运行任何验证或部署。

## 2026-10-23 商户退款申请与固定计划（新增源码）

新增 RefundFacade 的 app/infra/adapter 实现及 POST /api/payment/refunds/create、/detail，返回
SuccessResponse<RefundRequestDTO>。只接受已验证 JWT 的 MERCHANT/MERCHANT_APP、实际 merchant_id 和 pay:payment:refund:
create/read 权限；请求 payee 必须等于可信商户，未知商户资源查询返回 NOT_FOUND。SERVICE/OPERATOR 不因权限字符串获得任意商户退款入口，不转发
acquire 的内部 SERVICE 凭证。发起主体按 issuer/type/subject/merchant 保存摘要用于审计，幂等键仍为
tenant/payee/merchantRefundNo，商户内授权应用可回放同一原申请。

首次请求锁 parent_gate，再按 paymentId 升序锁成功付款容量；在此锁下读取同库已验证原付款、成功 attempt、永久渠道交易号 claim
及原冻结 admitted terms。不回查当前
product/customer/acquire，不锁着数据库跨服务请求。每笔候选金额、币种、实际商户、原订单/attempt/claim/成功时间和统一准入条款须一致，成功总额不得超过固定订单额。原
parent FENCED 仅禁止继续收款，不禁止退款；已部分收款订单也可按既有可退容量退款，不重开订单或修改 gross 已付金额。

新申请按原每笔 succeededAt + refund.windowSeconds 检查窗口，精确边界及过期付款不参与分配；以 eligible
succeededAt/paymentId 排序生成 FIFO 固定计划。原 enabled=false 拒绝，非 FIFO 规则返回 UNSUPPORTED_CAPABILITY，显式分配参数在
FIFO 下拒绝。partialAllowed=true 可按申请金额部分分配；false 要求本次金额等于所有窗口内可退余额，且窗口内无已有占用。已退/已预占金额均扣除可用容量。

同一可写事务保存 refund_request、refund_allocation_plan、refund_order 与全部 capacity CAS。申请和每个子项初始
INIT、版本0；原子冻结原成功 attempt/binding/channel/account
scope/请求号/交易号/成功时间与完整准入条款摘要。固定32位子退款请求号首次产生后持久保存，不在重试时重新路由或改退其他付款。容量变化仅增加
reservedMinor/版本，不提前增加 refundedMinor，也不修改成功付款或 acquire 订单。任一步失败回滚整个本次计划；唯一键竞争由既有有限事务重试读取赢家。

同 merchantRefundNo 原参数重试直接读取原计划及当前进度，不重新检查当前窗口/合约、不重做分配或容量预占；改
order/amount/reason 拒绝。查询在原 parent 锁下组合申请、固定计划及子项，复核金额/顺序/版本/身份/保存条款
hash。本节为规划批次记录；后续单步串行执行边界与结果落账源码见下节。真实渠道退款协议、自动查证、订单退款汇总、退费/额度恢复及事件仍未接通；INIT
不代表钱已退回。

新增 V20261023030000__refund_planning.sql 三张表。refund_terms 实际采用强类型原准入 JSON 的 LONGTEXT +
JSON_VALID/字节上限/sha256，避免审计日志输出；新增原支付请求号/实际交易号/成功时间以固定原路信息，审计时间仍由 BaseDO
监听器生成。新增规划、边界、重放、容量占用、商户鉴权和 MapStruct 存储转换测试源码，全部未运行；未执行 IDEA
检查、构建、测试、DDL、镜像或部署。真实数据库并发/回滚及渠道验收仍待执行。

## 2026-10-23 退款提交边界与验证结果收敛（新增源码）

RefundExecutionService 提供内部单步串行编排：在 parent_gate→refund_request→capacity（paymentId）→当前子退款锁下，只选择第一个未终结子项。INIT
赢家将子项与申请一起持久推进 PROCESSING 并写 submissionStartedAt，提交事务后才拿到首次提交票据；重复 worker 只按原退款号
queryRefund。PROCESSING/UNKNOWN 阻止后续子项，确定成功或失败后，下一次驱动才选择原固定计划下一项，不重新分配失败金额。当前尚无自动任务注册，progress
不会自己创建后台线程或循环发网络请求。

PaymentChannelGateway 退款接口改为返回完整 RefundObservation，PreparedChannelRefund
补原付款金额与实际交易号，以满足原路退款所需原总额/身份。canRefund 仅允许本地能力查找；无真实 binder
或明确不支持时，在提交边界之前拒绝。单步渠道调用在本地事务外，原始回复必须绑定当前退款及相应提交/查单来源。网络异常仅产生本地
UNKNOWN，不伪造渠道回执、不释放占用；已提交后即使查询 NOT_FOUND，也不能当作确定未退款或重新发起另一请求。当前无真实
PaymentChannelGateway 实现，不能宣称渠道退款已联通。

RefundResultService.record 只接内部验签/解密/实际账户核验后的强类型事实，无商户 HTTP
成功注入入口。锁内核对原成功付款/attempt/claim、冻结退款/原渠道请求号、原支付交易号、金额/币种、成功时间和既有提交边界。异常身份/金额、相同回执改资金事实、终态矛盾、真实退款流水已被另一退款或租户认领，写不可变回执/差错而不修改资金。回执键是单次渠道观察标识（tenant/refund/source/receiptKey），不能将会变化的查单结果都复用同一个退款号作为观察键。

成功同时写永久 refund_transaction_claim、子退款成功/时间/流水、申请成功累计及
capacity.refunded+=amount/reserved-=amount；确定未退款的失败只释放 reserved，并累计 failed；UNKNOWN 保持容量。中间观察不回退
UNKNOWN，终态不重开；同回执或不同观察重复证明同一成功不能重复消耗容量。原付款/订单状态、gross 成功额、原总额及 fee
均不被退款改写。提交、结果及回执任一步失败由同一本地事务回滚，数据库唯一键竞争通过既有有限重试重读认领赢家。

V20261023030000__refund_planning.sql 补未部署的 submission_started_at 列与状态 CHECK；V20261023040000__refund_results.sql
新增 refund_channel_receipt、refund_result_anomaly、refund_transaction_claim。实际退款流水以
channel/account_scope_hash/channel_refund_transaction_no 全局唯一，不包含 tenant/app/credential
以免重复认领；跨租户读取仅内部精确键，绝不对外列举。结果观测保存规范化非秘密 JSON，诊断输出隐藏负载。

新增提交防重/事务外网络、能力缺失、UNKNOWN、终态冲突、重复回执/认领、存储转换及结果摘要测试源码。未运行 IDEA
检查、构建、测试、DDL、镜像或部署；真实 MySQL 并发/回滚/唯一键重试仍待验收。真实渠道协议、自动串行驱动/查证、订单退款汇总/投影、退费、限额恢复、Outbox
通知继续按 BUSINESS-IMPLEMENTATION.md 完整范围推进。

## 2026-10-23 订单退款金额聚合与投影事件（新增源码）

新增纯领域 OrderRefundSummary 和独立 order_refund_summary 表，冻结原 order/payee/CNY/固定总额，持有累计 refunded/reserved
及独立版本。首次退款计划在同一事务增加本次总占用；子项确定成功把 reserved 转为 refunded，确定未退款失败仅释放
reserved。INIT→PROCESSING/UNKNOWN 等非资金变化不推进金额汇总版本。原订单/付款生命周期与 gross 已收金额保持原模型。锁顺序沿用
parent→request/capacity/child，最后在已持 parent 锁的事务内锁金额汇总，CAS 校验原版本和金额。

RefundProjectionEvents 已接规划、首次提交、渠道结果和本地 UNKNOWN 的事务。逐笔退款每个新版本读取刚落库的完整子项/申请头，校验精确版本，冻结真实
BaseDO.createdAt、实际渠道 succeededAt 与完整状态/金额，写 pay.payment.refund.realtime.v1；真实 updatedAt
用作原事件时间。资金汇总新版本同时写 pay.payment.refund-summary.realtime.v1。任何本地汇总/Outbox 写失败回滚该业务事务；幂等重放和
IGNORED/CONFLICT 观察不重复生成版本事件。

直接重构旧 OrderRefundSummaryEventDTO：只保留
orderId/payeeMerchantId/currency/refundedMinor/refundReservedMinor/refundSummaryVersion 六项固定大小字段，移除随历史申请无限增长的
requests 列表、RefundRequestSummaryDTO 和 realtime 的专用申请进度模型。金额聚合是完整替换快照，逐笔退款保持独立版本与分页读取，申请详情仍走
RefundFacade；realtime 不逐笔累加金额。此为前期直接契约重构，无旧格式兼容。原数据库审计时钟与渠道时钟独立，退款投影保留两者原值，不用本地
createdAt 强制校正或拒绝真实渠道 succeededAt。

新增两个独立 Outbox handler 和严格类型解码，命名 HttpTemplate realtime/refund、realtime/refund-summary，复用
PAYMENT_REALTIME_TOKEN_FILE 和 REALTIME_JWT_AUDIENCE。payment SERVICE 凭证额外需要 pay:realtime:projection:refund、pay:
realtime:projection:refund-summary。worker 校验可信租户、已登记 PROCESSING 队列、消息目标/聚合，事务外重放原完整负载；ACK 需匹配
delivery/businessEvent/producer/aggregate/sourceVersion，projectedVersion 不小于源版本，允许
APPLIED/DUPLICATE/STALE。两个目标独立失败/重试，不读取当前交易来重建旧消息。

V20261023050000__order_refund_summary.sql 新增金额汇总表。新增领域金额/事件事务/时间冻结/消息重放/ACK/严格解码/HTTP
操作测试源码，同步原服务调用方和已重构投影契约。未运行检查、构建、测试、DDL、镜像或部署。自动调度、真实渠道、费用基数/退费与额度恢复下游、商户退款通知、重建及端到端验收仍待完成。

## 2026-10-23 退款汇总权威查询与费用独立投递（新增源码）

`PaymentRpc/RefundSummary(IdQuery(orderId))` 提供六字段固定大小 `OrderRefundSummaryEventDTO`。仅配置的 acquire SERVICE 加 `pay:payment:refund-summary:read` 可调用，还必须匹配原 parent gate 的 callerServiceId。按 parent→summary 锁顺序读取同租户事实，原门禁 FENCED 不阻断独立退款查询；缺少汇总返回 NOT_FOUND，不伪造零退款。

退款规划与终态结算更新金额汇总时，在原事务内新增独立 `pay.payment.refund.fee.v1` Outbox 目标。费用与 realtime 目标各有稳定 delivery/businessEvent 和独立重试，负载冻结为当时同一金额版本；任一目标入队失败原事务回滚。新增 `RefundFeeOutboxHandler`/`RefundFeeDelivery` 校验可信租户、已登记 PROCESSING 队列及精确类型/聚合，再事务外调用 `AcquireRpc/FeeRefund`。严格 codec 防止 fee/realtime 负载混用，不用当前金额重建旧消息。

ACK 必须匹配原 deliveryId/businessEventId/orderId/sourceVersion，accepted=true 且 appliedVersion>=sourceVersion。更高 appliedVersion 表示 acquire 已回查验证更晚的累计事实；不能凭其他目标成功确认费用目标。重试复用原负载及原身份，最终确认依赖 acquire 的费用与业务回执同事务提交。

复用已有 acquire gRPC channel、`PAYMENT_ACQUIRE_TOKEN_FILE`/`ACQUIRE_JWT_AUDIENCE`，payment SERVICE 增加 `pay:acquire:fee:refund`。反向权威查询复用 acquire 的 payment channel 和凭证，需上述查询权限。每次发送仍重读挂载文件支持凭证轮换。无新增内部 HTTP 或兼容回退。

本批新增 app/codec/投递/客户端测试源码并更新原事件数量断言，未运行检查、构建、测试、DDL 或部署。真实渠道退款、自动驱动/恢复、额度恢复与商户通知仍未完成。

## 2026-10-23 退款额度目标与权威事实（新增源码）

成功子退款在原结果事务追加 `pay.payment.refund.limit.v1` 独立 Outbox 任务，冻结原 refund/version/evidence、operation、金额及该付款成功退款累计。INIT/PROCESSING/UNKNOWN/FAILED 不生产额度恢复成功事件。逐笔 realtime、订单金额 realtime、费用和额度目标有独立身份、重试和 ACK；任一任务入队失败，原退款结果事务回滚。

`RefundLimitOutboxHandler`/`RefundLimitDelivery` 校验原已领取队列与可信租户，严格解码并转换原冻结事件为 `LimitRefundRestoreCommand`，事务外调用 `LimitCenterRpc/RestoreRefund`。ACK 精确绑定 operation/payment/refund/version/businessEvent/evidence/原累计，权威累计不能小于原累计，且必须有正权威版本及一致的 RESTORED/NO_ACTION 结果。Confirm 晚到、依赖失败或落库失败不会确认额度任务；不要求 fee/realtime 先完成。

`PaymentRpc/RefundFacts` 只供配置的 limitcenter SERVICE 和 `pay:payment:refund-facts` 查询。它复用原财务加锁路径，核验成功子项、原付款永久成功 claim 与真实退款全局 claim，返回 `RefundSettlementFactsDTO`：成功退款事实、原成功付款金额/版本/证据/attempt/受理时间及当前付款退款容量版本。不存在或未成功不返回成功恢复证明，旧事件可由接收方回查取得更晚的付款累计事实。

复用已有 limitcenter channel/`PAYMENT_LIMITCENTER_TOKEN_FILE` 和反向 `LIMITCENTER_PAYMENT_TOKEN_FILE`，分别需 `pay:limit:refund:restore` 与 `pay:payment:refund-facts`。无新增 HTTP 回退、假渠道或商户成功记账端点。新增鉴权/事实核验/独立事件/严格 codec/ACK/精确 int64/客户端测试源码，未执行编译、测试、数据库或 RPC 验收。

## 2026-10-23 退款自动驱动与恢复调度（新增源码）

退款计划持久化时，`RefundPlanningService` 在原 parent/request/容量/Outbox 事务中登记 `refund_recovery_cursor` 与 DelayedTask 首轮任务。首个 dueAt 使用原 plannedAt，重复商户请求沿用原计划，不新增游标或任务。登记失败使整个原事务失败，不能出现受理了计划却未登记恢复工作的提交。

任务类型 `pay.payment.refund.progress`，bizId 为 `requestId.generation`（按最后一个点解析，支持带点的原业务 ID）。generation 从0开始，每轮后继递增，和退款申请/子项的财务版本独立。游标只保存当前续行身份；任务领取、次数、租约和执行状态仍由 loadup DelayedTask 负责，没有另建第二套执行队列。

`RefundRecoveryTaskHandler` 在组件已领取并提交后调用应用服务。应用核对可信租户、持久任务的 RUNNING/attempt/有效租约和规范 key，再于 parent→request→cursor 锁顺序下读取当前事实。旧代次、已完成游标直接结束；所有子退款已终结时只标记游标完成，无须渠道 provider。否则事务外调用原 `RefundExecutionService.progress`：第一提交仍只有持久 INIT→PROCESSING 赢家，重复/恢复执行查询原渠道退款号，UNKNOWN 仍阻止固定 FIFO 后续子项。

一次进度完成后，再在本地事务重读原请求与游标。已终结则完成游标；仍未终结则 CAS 推进代次、保存下轮 dueAt，并原子登记新的 DelayedTask。正常等待通过后继任务继续轮询，不抛失败来耗尽重试次数；每轮仍只驱动原固定计划的一个子项。任务后继登记失败会回滚游标推进并向 dispatcher 报错。若旧任务完成 ACK 丢失，重放发现游标已推进即结束，不重复登记下一代。

渠道异常、缺少真实 binder、任务处理崩溃等仍沿用 DelayedTask 的失败重试和租约恢复；重试耗尽后任务保持 FAILED，资金预占和原计划不变，不据此自动释放或伪造成功。当前可由受控运维在可信租户范围使用组件 `retryFailed(type,bizId)` 恢复同一代任务；专用运维鉴权/查询/重放入口尚待实现。通用任务取消不能撤销渠道副作用，也不等同于业务退款取消或强截止封禁。

payment-app 引入 `loadup-components-delayedtask`，payment-business bootstrap 选择现有 local Scheduler binder，默认骨架不引入业务调度。组件扫描、Mapper、共享 DataSource/事务管理器及 `classpath:db/migration` 沿用当前装配；需要同时包含组件的 V20261019000001__create_delayed_task.sql 和本批 V20261023090000__refund_recovery_cursor.sql。未运行迁移，前期历史开发数据不自动补建游标。

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| PAYMENT_RECOVERY_INITIAL_DELAY_SECONDS | 30 | 付款/退款共享轮询下限；其余退避配置见 Review R3 节 |
| PAYMENT_SCAN_INTERVAL | PT30S | local Scheduler 唤醒扫描器的 fixedDelay；首次等待沿组件默认一分钟 |
| PAYMENT_TASK_BATCH_SIZE | 100 | 每次有界逐条领取 |
| PAYMENT_TASK_MAX_ATTEMPTS | 5 | 同一代异常执行的重试上限，不是正常处理中总轮询上限 |
| PAYMENT_TASK_LEASE | PT5M | 单次执行租约，须大于处理可能耗时；无自动续租 |
| PAYMENT_TASK_RETRY_DELAY | PT1M | 异常重试间隔 |

任务与日期均使用系统本地时区，不强制 JVM/TZ/JDBC 时区。多 Pod 均可扫描，领取由数据库条件更新互斥；租约过期仍是至少一次，原退款号和结果幂等负责副作用安全。正常轮询会产生保留的后继任务历史，归档/清理与失败积压运维仍待补。

已补计划注册/失败传播、key/cursor、正常后继、旧代次、终结、异常与竞争边界测试源码。内存夹具只验证调用事务边界，不证明数据库回滚或多副本行为；本批未运行 IDEA 检查、构建、测试、DDL、镜像或部署。真实渠道仍待接入，不能宣称已完成真实自动退款。

## 独立渠道模块（2026-10-23）

渠道协议从 payment-infrastructure 抽离到 [channel 模块](../channel/README.md)。payment-domain 的渠道端口引用 channel-api 统一契约；PaymentChannelGatewayImpl 只负责取得可信租户/原绑定 revision 并委托 ChannelPaymentFacade。统一 Facade 通过 Provider 注册表按请求 channel 路由，payment 不导入支付宝或微信协议实现。

payment-business bootstrap 组合 channel-alipay/channel-wechat；支付宝协议配置改为 loadup.pay.channel.alipay，并使用 channelClock。新增渠道模块不增加 K8s 应用。退款事务内的 canRefund 为纯本地注册能力查询；后续提交和查询在事务外取得原 revision。支付宝付款码/JSAPI 提交、查询/关闭、退款 Provider 与支付动作持久化/授权查询源码已补；自动收款驱动源码已补；支付宝签名回调与原号查证源码已补；微信支付通知已有源码；H5、微信退款及银联回调仍待实现。源码及测试源码未验证。

支付宝退款策略已在独立插件内条件注册，复用原退款单步执行与恢复链路；运行组合 payment-business,payment-alipay，配置/限制见 [支付宝模块](../channel/channel-alipay/README.md)。已补源码与测试源码，未运行验证或真实调用。

## 支付宝查单/关闭统一契约（2026-10-23）

PaymentChannelGateway 与统一 ChannelPaymentFacade 的 query/close 均改用 ChannelPaymentTarget(identity, amount)，查询返回也验证原金额。协议映射在独立 channel-alipay，payment 基础设施仅透传原绑定上下文。PaymentObservation.Source 增加 CLOSE_RESPONSE；真实待付款查询与关闭成功的组合事实才可形成支付宝未收款观察。单独 TRADE_CLOSED/NOT_FOUND 保持未知。

可选 payment-alipay runtime profile 已配置支付查询/关闭及退款两类操作。当前已补受理后自动提交/查单/关闭驱动源码，不增加商户上报成功入口。测试源码与设计已更新，未运行构建、测试或真实渠道调用。

## 收款提交上下文与动作（2026-10-23）

PaymentExecuteCommand 增加 @NotBlank subject 和 @NotNull payableUntil，由 acquire 从 StoredAcquireOrder.subject/order.orderDueAt 冻结；不可从付款受理时间重新推导订单期限。新增受理在幂等重放分支之后校验期限，Y2 准备及持锁原子边界再检查；原请求的过期重放不重新扣款。

JSAPI ChannelIdentityInteractionDTO 改为 channel/identifier/appScope/identityType，identifier 为已解析渠道用户标识，identityType=USER_ID/OPEN_ID，不是授权 code。该变更同步 provider-owned payment.proto 与 Mapper，不保留旧字段或兼容构造；旧开发密文/意图需重新准备，未执行任何清理或迁移。

PaymentChannelRequestFactory 核对原 SUBMITTING attempt、金额、epoch、调用方、额度操作与许可/风控，再通过 PaymentInputAccess 打开原绑定输入；PaymentChannelInputConverter 生成独立渠道请求与前端动作 DTO。桥接提交返回 ChannelPaymentSubmission，不再仅返回 PaymentObservation。付款码/JSAPI 协议在独立支付宝插件实现；目前支付动作快照、Y3 同事务写入和授权详情/outcome/幂等返回已有源码；自动驱动发送源码已补；托管 cashier 的前端交互仍待补。

可选 payment-alipay runtime profile 增加 trade-pay/trade-create 操作；尚未开启或部署。新增源码和测试源码未编译或执行，详见 [支付宝说明](../channel/channel-alipay/README.md)。


## 动作生命周期与模块责任（2026-10-23）

PaymentActionService 仅编排原 attempt 的动作快照和可见性；PaymentResultService.recordSubmission 将受理观察与动作放在同一 Y3 本地事务。首次动作固定原交易号、执行 epoch、有效期及来源回执，重复受理不能替换或延期。PaymentAcceptanceService 的详情/outcome/幂等返回在原服务身份校验与执行门禁下读取，只有 PROCESSING、双门禁 OPEN、当前 SUBMITTING/PENDING/UNKNOWN attempt 且未过期才附带 action。

payment_action 的 DO、生成 Mapper 接入、MapStruct、迁移源码位于 payment-infrastructure；动作格式由 channel-api/core 的 ChannelActionCodec 负责，厂商支付协议由 channel-alipay/channel-wechat 负责。payment 仅做存储和客户端 DTO 投影，不在编排中选择厂商实现。

本地事务保持外部调用禁入；持有门禁读取动作不会触发渠道查询。动作存储失败或与首次动作冲突使同一 Y3 事务失败。该源码已有自动提交生产者接线，尚不能据此宣称真实支付闭环已完成；新增迁移、编译、测试、数据库回滚/竞争和运行装配未执行验证。


## 付款执行任务（2026-10-23）

payment-business 已组合原生 DelayedTask/Scheduler local binder。受理事务在保存 payment INIT 后调用 PaymentRecoveryService.register，同库写 payment_recovery_cursor 并登记 pay.payment.collection.progress；回放原受理不补发任务。前期不兼容旧受理，现有开发数据不会自动补游标。

PaymentExecutionService 从原固定命令创建首个 attempt，再执行渠道本地 validate、风险咨询与原额度/Y2。validate 的输入不带 executionEpoch；短期原输入在锁外 KMS 打开。唯一 ticket 可调用渠道一次，响应通过 PaymentResultService.recordSubmission 落入 Y3。后续代次只按原身份/金额 query；支付期限到达或门禁封禁时先在本地请求关闭，再锁外 close。仅从未 Y2 或已核验的无收款证明可终结并发布释放事件。

任务 handler 校验可信租户、登记任务、RUNNING 状态、尝试次数与有效租约。正常等待原子登记下一代任务，异常由原生重试处理；代次是调度幂等号，不能替代渠道请求号或提交权。轮询改用共享 loadup.modules.payment.recovery-polling 配置，按原业务存续时间退避并抖动；所有业务时间仍为系统本地时间。旧固定间隔变量不再读取，详见 Review R3 节。

尚未执行编译、测试、迁移、真实数据库回滚/并发或多 Pod 恢复演练。JSAPI 创建 ACK 丢失后的动作查询重建已有源码；关闭 ACK 丢失、耗尽任务运维重放、H5/托管授权/其他渠道回调及真实回调验收继续待办；不通过盲目重复提交弥补缺口。


## 不重提扣款的动作恢复（2026-10-23）

已 SUBMITTING/PENDING/UNKNOWN 且未进入关闭流程的任务使用 ChannelPaymentRecovery→PaymentChannelGateway.recover。PaymentSubmissionConverter 只投影原 attempt 身份、金额、方式、epoch 和原冻结 payableUntil；不生成 PaymentSubmissionTicket，也不走 PaymentChannelRequestFactory/PaymentInputAccess。即使原短期输入已过期，仍可查询原交易，渠道签名凭证仍按原绑定版本解析。

插件返回的 QUERY_RESPONSE 观察与动作通过 PaymentResultService.recordRecovery 复用 Y3 原子应用；动作写入/冲突与观察回执同事务，动作读取仍受 PROCESSING/当前 attempt/epoch/双门禁/期限限制。UNKNOWN 财务状态不被中间查询降级。首次提交仍只来自内存 Y2 赢家，关闭仍先持久门禁再使用原 target close。

本批只补源码和测试源码，没有运行构建、测试或真实操作；支付宝 WAIT 原交易的 JSAPI 动作可查询重建，其他渠道/方式不制造动作，关闭 ACK 丢失仍需要额外确定证据。


## 渠道回调入口（2026-10-24）

`POST /api/payment/channel-callbacks/{endpointRef}` 返回渠道原生 ACK，不包装 SuccessResponse。当前 HTTP 收包支持 application/x-www-form-urlencoded 和 application/json，最大 65536 字节，拒绝空包与 query string；支付宝策略提供 success/failure，只有 Y3 写入返回非冲突才输出 success。该路径通过 resource-server 的 permit-all 放行 JWT，实际认证由原冻结平台公钥验签与账户/金额核验完成，其余业务接口保持 SERVICE 鉴权。

协议 success 表示通知处理已提交；付款结果以查询事实和原状态机为准，未知结果继续保留占用并由原恢复任务查证。

endpointRef 只是配置定位号，不能替代签名。可信配置固定 tenantId/channel/bindingRevisionId；通过原 binding revision+out_trade_no 查已进入 Y2 的 attempt，核验原 payment/金额/限额操作/调用方摘要，再验签。只有验签成功后才查原渠道原账户；回调状态仅作触发，金额/成功时间来自原查询。Y3 保存 CALLBACK 回执及组合摘要，重复金融事实不会再次应用；未知或关闭通知不产生未收款证明，退款不回写付款成功额。

以下为外部 ConfigMap 的配置形状，示例 ID/地址需替换为真实配置；旧 revision 的 endpoint/平台公钥在原交易和回调收敛前应保留：

```yaml
loadup:
  modules:
    payment:
      channel-callback:
        endpoints:
          alipay-revision-20261024:
            tenant-id: tenant-example
            channel: ALIPAY
            binding-revision-id: binding-example
  pay:
    channel:
      alipay:
        notifications:
          notify-urls:
            tenant-example:
              binding-example: https://pay.example.com/api/payment/channel-callbacks/alipay-revision-20261024
```

URL 目录只作用于 JSAPI CREATE 的 notify_url，要求 HTTPS、无用户信息/query/fragment，按可信 tenant+原 revision 精确匹配；未配置时沿现有查询任务跟踪，不声明回调已启用。付款码不附加该 URL。endpoint 目录默认空，未登记入口无法处理付款。新增 V20261024010000__payment_callback_lookup.sql 为查找建立索引，不新增收包业务表。

已补原生入口、校验顺序、失败 ACK、签名/字段/编码和配置约束测试源码；内存夹具不证明真实提交。未执行构建、测试、迁移、公开路由或真实渠道回调，微信/银联通知、H5、托管授权与完整资金验收继续待办。


## 微信加密支付通知（2026-10-24）

回调入口支持 JSON，采集五类 Wechatpay 签名头的原多值，不读取请求租户或绑定。先由可信 endpointRef 配置加载原 tenant/channel/revision，将上下文交给 channel 的 identify；微信插件在验签/解密后提取原订单号，payment 才查原 attempt，随后 verify 原 target、查询渠道并沿既有 Y3 落事实。没有微信报文或密钥处理进入 payment。

统一 ACK 新增 httpStatus；控制器返回微信策略的成功 200 或失败 500，支付宝仍用原生 success/failure。apiV3Key 密文配置归微信原版本凭证，KMS 解封/清零和平台公钥验证归插件。相关协议与客户端构造器直接更新，不保留兼容重载。测试源码已补，未运行验证；微信退款通知、轮换及真实回调验收继续待办。


## 微信退款通知（2026-10-24）

POST /api/payment/channel-callbacks/{endpointRef}/refund 接收原生 JSON，仅委托 RefundChannelCallbackFacade。可信配置仍固定 tenant/channel/原 revision；微信通知策略验签/解密取得原订单和退款号，RefundCallbackTargetGateway 在原绑定下有界查找已提交子退款，核对原付款成功容量，得到固定 PreparedChannelRefund。未提交 INIT 不能被回调激活，重复定位拒绝。

verifyRefund 通过后查原渠道退款，核对原身份、金额及真实退款流水；RefundCallbackObservationConverter 转为 CALLBACK，回执键绑定通知 ID 与金融事实摘要，沿 RefundResultService 的原事务落结果/容量/独立 Outbox，非冲突提交后才返回微信 200，异常/冲突为 500。没有微信协议进入 payment，也不修改原付款成功额。

微信 notifications.refund-notify-urls 按 tenant/revision 配置退款专用 HTTPS 地址，提交时由插件附加 notify_url；缺失时保留平台默认，不复用收款地址。新增查询索引 migration V20261024140000__refund_callback_lookup.sql，仅源码未执行。测试源码已补，尚未运行构建、测试、数据库或真实回调验收。


## 退款恢复与纯查询分离（2026-10-24）

RefundExecutionService 首个 INIT 赢家保留本地提交边界后执行 submitRefund；所有非首次进度改用统一 recoverRefund，不在 payment 选择厂商重提条件。Facade/Provider 按原绑定处理，允许原号查询及明确幂等条件下的一次原参数重提。恢复的 SUBMIT_RESPONSE 只能为 ACCEPTED/UNKNOWN；查询成功/关闭仍走既有金融结果接收，CALLBACK 或终态提交观察不能作为恢复结果越过查询确认。

原请求号、计划、原付款、金额与账户不变，容量保持占用；查询/提交/记录仍在本地事务外/既有 Y3 中分别执行。transport 失败继续记 uncertain，结果冲突不确认任务。纯查询及退款回调保持 queryRefund，不产生退款申请副作用。已有原生游标/后继任务/租约机制继续使用，无新增业务表、迁移、内部 RPC 或模拟资金结果。已补测试源码，未运行验证或真实恢复演练。


## 微信 H5 接线（2026-10-24）

H5_SCENE 输入复用短期 KMS 密文/原 HMAC/唯一付款引用与 INIT→BOUND 生命周期；无 H5 特殊解密或跳过绑定路径。PaymentChannelInputConverter 将场景映射为 ChannelH5Input，微信插件返回 WechatH5ChannelAction 后，统一 Y3/action 快照与授权展示转换为客户端 REDIRECT。action_kind=WECHAT_H5 由 channel-core 的严格白名单编码支持，payment 不解析 h5_url 厂商响应。直连/服务商的固定 H5 operation 已加入可选 payment-wechat 配置。

地址有效期为原截止与渠道调用开始加五分钟的较早值，受理不是资金事实；原查询恢复不重复下单，也不重新生成或延长旧地址。初始 payment_input CHECK 已直接允许 H5；按前期不兼容原则同步初始建表源码，没有运行 DDL。详见 [渠道 H5 说明](../channel/channel-wechat/README.md)。本批未运行构建、测试或真实调用。


## 退款商户通知来源（2026-10-24）

RefundProjectionEvents 在子退款终态事务写独立 `pay.payment.refund.merchant.v1` Outbox，最后一个固定子退款完成时再写整笔申请终态（允许 PARTIAL_SUCCEEDED）。源身份/金额/商户退款号与原计划一致，审计时刻来自落库快照；初始/处理中/未知不产生终态通知。

RefundMerchantOutboxHandler/RefundMerchantDelivery 核对可信租户、已登记 PROCESSING 源消息、精确聚合/版本，事务外调用 AcquireRpc/RefundNotification。逐字段核对原 delivery/business/aggregate/version 和确定性 merchantEventId；ENQUEUED/DISABLED 只是原策略登记结果。其他目标成功不能确认此消息；失败独立重试，不回滚已完成退款。payment 不再单独实现商户签名发送表/引擎。

沿用 PAYMENT_ACQUIRE_TOKEN_FILE 与 acquire channel；发行的 payment SERVICE token 需增加 `pay:acquire:refund:notification`。没有签发新令牌或实际发送。详见 [客户端契约](../design/CLIENT-API.md)。生产/投递与部分成功测试源码已补，未运行验证。


## 原安全输入引用恢复（2026-10-24）

payment 的独立 InputLookup Facade/gRPC 以 acquire SERVICE+input:read 校验原不可变意图的完整范围，
只返回原 opaque 引用/摘要/期限，不解密、指纹计算、改数据或续期。found 明确存在并与可空 input 一致；
过期/BOUND 仍回原元数据。acquire 恢复缺失引用时事务外查询，再在原订单→会话锁下 CAS 绑定原引用，继续原命令幂等受理和 A3。
不再为已存原输入重新生成当前 IP/场景。异常不当缺失、缺失不当未收款、到期不直接释放占用，金融编号/金额/渠道不变。

新增权限须由服务 JWT 发行方配置，没有签发令牌或修改部署。provider/consumer/MapStruct/proto/接线与测试源码已补，
未运行任何验证；未受理输入到期后的有证据关闭源码已补；第一次准备仍在途的竞态与其余完整业务范围继续。


## 未受理输入到期收敛（2026-10-26）

InputExpire 是完整原输入范围绑定的内部条件操作，要求 acquire SERVICE+input:expire 权限。
在原 parent/child/payment/input 锁序内，已有付款保持原状；有效未受理输入仍可原命令受理；
到期且从未受理时复用单笔 close 原子写门禁与固定控制回执，不封禁父订单或调用渠道。
回复丢失可用原请求重放证明，孤立数据没有证明不能按无付款记录直接释放。

acquire 原 actionKey 恢复据三态处理，关闭证明经通用 applyClose/A3 核对并幂等释放原 allocation。
paid gross、其他占用和订单业务状态不改变，后续 progress 不虚构 payment 行；页面得到明确收敛结果才允许下一笔。
控制 API/DTO/Protobuf/MapStruct/consumer、恢复接线及测试源码已补，未运行验证、令牌签发、部署或真实调用。
首次准备并发/地址变化、JSAPI OAuth、其他渠道、通知/交易重建/风控/对账及完整目标继续。


## 付款/退款 FAILED 原任务运维恢复（2026-10-26）

`PaymentRecoveryOperationsFacade` 提供两个运营 HTTP 入口，均返回 no-store 标准响应：

| 接口 | 权限 | 作用 |
|---|---|---|
| POST /api/payment/operations/recovery/tasks/detail | pay:payment:recovery:read | 查询一个原恢复任务及精确失败快照 |
| POST /api/payment/operations/recovery/tasks/retry | pay:payment:recovery:retry | 对仍有原业务工作的 FAILED 任务登记审计并重试 |

身份复用 JwtPaymentActorProvider，必须是已验证的 SERVICE，service_id/sub 等于
`PAYMENT_RECOVERY_OPERATIONS_SERVICE_ID`（默认 pay-ops）；普通 acquire、商户或退款调用者不获得运维权限。
租户来自验证后的 JWT，issuer/audience/签名/期限沿资源服务器处理，本轮没有签发令牌。

kind 只允许 COLLECTION 与 REFUND_REQUEST，subjectId 分别是原 paymentId、原 refundRequestId；generation 是原恢复代次。
详情使用非锁定的只读快照；retry 按既有父门禁→单笔门禁→付款/原退款计划锁序复核，再锁恢复游标。重试时付款绑定仍以原 persisted callerServiceId 校验，
不会把 pay-ops 作为原金融调用者；退款沿原请求/订单/商户/摘要绑定检查。终态、完成游标或旧代不会重新执行金融工作。

重试命令精确绑定 expectedTaskId、expectedAttempts、expectedNextExecuteAt（微秒精度），并带稳定 requestKey 和审计 reason。
只允许原 native FAILED，原 executeAt 未到不提前唤醒；缺任务不补建，非 FAILED 不改写，不提供改期/取消/执行 Handler 入口。
执行仍由原 native worker、Y2 首次提交门禁、原渠道查询/退款计划决定；不生成新 attempt/refundRequest/渠道请求号，
不切换账户、渠道或重排退款子单，也不恢复业务资金预算。native retryFailed 仅重置其技术重试次数，保留原业务 deadline。

`payment_recovery_retry_receipt` 与 native retryFailed 同库事务提交，唯一键为 tenant/callerScopeHash/requestKey。
完整重放只返回首次回执，即使同一 native 任务又耗尽也不再次重置次数；同键改参冲突。新明确运维决定使用新 key 和最新失败快照。
RETRIED 表示原唤醒已重排，NO_LONGER_NEEDED 表示该代或业务已结束、只记审计；两者都不是支付/退款结果。
原金融终态、已消费/预占额度和退款容量不被运维入口修改。query DTO 不返回 native lastError、冻结命令、渠道凭证或付款输入。

迁移 `V20261026140000__payment_recovery_retry_receipt.sql`、BaseDO/生成 Mapper/MapStruct/Gateway、权限/原条件/重放/回滚及
退款锁绑定测试源码已补。未运行 IDEA、编译、测试、数据库、HTTP/JWT 或调度器验收，也没有执行 DDL、部署或重试实际任务。
任务列表/告警、旧库补登记、渠道与其他完整业务目标继续待办。


## Review A 批：只读详情与真实持久化测试（2026-10-26）

R2 的 detail 走短 `readSnapshot`（REQUIRES_NEW/REPEATABLE_READ/readOnly），COLLECTION 读取原付款与游标，
REFUND_REQUEST 非锁定重建原退款计划与游标。查询不进入 PaymentExecutionLocks/RefundExecutionLocks，也不执行 FOR UPDATE。
重试继续 `lockForRetry`，持原金融锁序重新校验状态与任务前置条件；读快照过时不扩大权限。
`PaymentAcceptanceTransactions.read` 保持租户上下文语义，避免误把渠道调用包入只读事务。

R1 新增真实 MySQL/Testify/Testcontainers `RecoveryPersistenceIT` 源码：实际 Flyway、生成 Mapper、Gateway、DelayedTask 与事务管理器，
覆盖初始登记/后继登记回滚、双 worker 单一 successor、读快照不阻挡游标写入且保持原多表视图、native FAILED 重试与真实审计 Gateway 的共同回滚。
测试位于 payment-infrastructure，按显式 `mysql-it` profile 运行；用例范围、命令与未覆盖项见 `payment-infrastructure/src/test/README.md`。
这些用例不调用渠道，也不证明 Y2、A2/A3 金额或退款容量已经正确。

按用户暂停验证要求，尚未运行 IDEA、编译、单测、容器或数据库；R1 尚无新的运行证据，R2 尚无轮询负载实测。


R1 后续已补 `FinancialPersistenceIT` 的 Y2 竞争/ticket 丢失/attempt 回滚与退款计划-容量并发/回滚源码。
实际数据库依赖不使用 mock；额度/风控/渠道端口为受控替身，测试不执行真实扣款。退款容量为已建立前置数据，
不代表完整成功入账/退款准入流程已验证。详细入口、范围与命令见 infrastructure 测试 README；所有新 IT 尚未编译或运行。


## Review R1：支付结果与 Outbox 测试源码

[PaymentResultPersistenceIT](payment-infrastructure/src/test/README.md) 新增 10 个测试输入，复用实际 Y3 编排、金融 Gateway、事务与四目标 Outbox，覆盖 5 处写入后的回滚、原请求查询恢复、receipt 并发/冲突、跨租户流水归属及权威关单回执。
领域已支持的 CLOSE_RESPONSE 同步纳入当前开发期 payment_channel_receipt CHECK。原 Y2 提交作为真实持久化前置数据；渠道事实为受控替身，初始创建/首提/action 分支不得调用。
当前仅完成源码，未编译、未运行数据库或测试，不能视为 R1 验收或真实渠道/网络故障演练通过。


### 付款恢复 handler 的数据库用例源码

PaymentResultPersistenceIT 继续增加 3 个真实 claim/handler/finish 用例，总计 13 个输入。
覆盖租约失效后的旧 claim 拒绝、原查询仍在途时新 worker 与当前 generation 收敛、未来 generation 被拒绝且当前任务仍可执行。
采用测试可推进 Clock 与并发屏障；实际游标、native task、Y3、receipt 和 Outbox 不使用替身。
源码断言不增加 epoch、不再获取首提 ticket、不调用 submit；目前尚未编译或运行，不代表跨进程/真实渠道演练通过。退款 handler 组合源码见下节；真实运行和调度演练仍待验收。


### 退款结果与恢复 handler 的数据库用例源码

新增 [RefundRecoveryHandlerPersistenceIT](payment-infrastructure/src/test/README.md) 的 8 个输入，使用实际付款 Y3 生成的成功容量，真实预占、退款子单/请求、汇总、结果回执、金融锁序与各目标 Outbox。
覆盖四处写入后的回滚、租约失效/重新 claim、旧查询与后继 generation 交错、未来 generation 拒绝及同退款事实不同 receipt 重放。
源码断言原付款金额与成功状态不变，退款容量/汇总只结算一次，恢复复用原退款号与账户，不重新创建首次提交；渠道、验签与实际发送未接入。
目前未编译或运行，R1 仍待真实数据库证明；多付款 FIFO 串行退款、实际扫描器与跨进程故障继续按独立范围补齐。


### Review R8：退款事件类型的契约归属

退款汇总的 REALTIME_TYPE/FEE_TYPE 由 client 的 OrderRefundSummaryEventDTO 定义，事件生产者与基础设施 codec 共用同一协议值。
codec 不再为读取事件类型引用 app.service.RefundProjectionEvents；没有变更现有 wire 值、payload、摘要或状态迁移，也没有新增兼容分支。
该边界纳入六服务源码/POM 门禁，尚未运行验证。


## Review R3：付款与退款的有界退避轮询

PaymentRecoveryService 与 RefundRecoveryService 共同注入纯领域 RecoveryPollingPolicy，只计算正常未终结分支的后继 dueAt。
年龄分别来自原 payment.initiatedAt / refund.plannedAt，不因 native task 重新 claim、业务 generation 增长或应用重启而从零计算。
这是业务存续时间，不冒充首次进入 UNKNOWN 的独立时间指标。

| 运行变量 | 默认 | 约束 |
|---|---|---|
| PAYMENT_RECOVERY_INITIAL_DELAY_SECONDS | 30 | 1..86400 秒，最终等待下限 |
| PAYMENT_RECOVERY_MAXIMUM_DELAY_SECONDS | 1800 | 不小于 initial，至多 86400 秒 |
| PAYMENT_RECOVERY_BACKOFF_EVERY_SECONDS | 900 | 每满一个年龄区间，名义等待翻倍；1..604800 秒 |
| PAYMENT_RECOVERY_JITTER_PERCENT | 20 | 0..50；按租户/任务类型/原业务 ID/generation 确定偏移 |

配置前缀为 loadup.modules.payment.recovery-polling。名义等待在 maximum 处饱和，抖动窗口裁剪到 initial..maximum；
默认新业务等待 30..36 秒，长时间未终结后等待 1440..1800 秒。上限处仍保留分散，不把所有任务重新压到同一秒。
时间从 max(now, previousDueAt) 起算，后继始终晚于原 dueAt；已有 native task 不改写，新登记的后继使用当前配置。

删除旧 payment-recovery/refund-recovery.poll-delay-seconds 和 PAYMENT_POLL_DELAY_SECONDS / PAYMENT_REFUND_POLL_DELAY_SECONDS，不保留兼容读取。
不会变更原请求号、账户、epoch、门禁、receipt、金额、占用、成功/失败判定或任务授权。正常未知结果可继续登记后继；
传输异常仍抛给原 native 任务重试/FAILED，轮询策略不代表最终资金结果。

领域/配置测试及付款、退款长龄 handler 测试源码已补。原生租约交错 IT 显式 jitter-percent=0，用固定时序证明协议边界，不能替代生产抖动策略的运行验证。
本轮未检查、编译、测试、迁移或部署；渠道并发限制、业务指标、长期未知升级和证据保留/归档尚未实现，R3 未完成。
操作语义和剩余范围见 [恢复运营说明](../design/RECOVERY-OPERATIONS.md)。


### Review R3：按渠道的恢复并发

payment-business runtime 新增 LoadUp Resilience4j core binder，channel-core 使用其标准 BulkheadRegistry。
统一渠道入口对付款/退款查询、恢复和关闭共享各渠道预算；支付宝/微信/银联的 PAYMENT_ALIPAY_RECOVERY_MAX_CONCURRENT / PAYMENT_WECHAT_RECOVERY_MAX_CONCURRENT / PAYMENT_UNIONPAY_RECOVERY_MAX_CONCURRENT 配置默认各 16。
配置在 application-payment-business.yml 的标准 resilience4j.bulkhead.instances，max-wait-duration=0ms；预算满额返回 CHANNEL_RECOVERY_BUSY，由原任务重试/FAILED 机制处理。
不会增加首次扣款、释放金额或重建提交权；退款依赖异常仍可标为 UNKNOWN，预占保持。原首提 submit/submitRefund 不受此预算装饰，需后续在提交边界前单独完成并发准入。
此预算是每进程、每渠道的恢复操作并发，不是全渠道总流量或集群配额。业务指标、升级/保留及实际多实例验证继续后续切片。
新增 Facade 并发/释放/首提边界测试及 bootstrap/API 依赖门禁源码，尚未编译或运行。


### Review R3：恢复 handler 执行指标

实际 PaymentRecoveryTaskHandler / RefundRecoveryTaskHandler 已接入 RecoveryExecutionMetrics，使用 Spring 提供的共享 MeterRegistry。
不存在共享 registry 时不创建私有 backend，仍正常执行业务。观测在整个 handler 外层，恢复服务返回已提交的续行决策后再记录，事务闭包内部重试不重复计数。

| Micrometer 名称 | 类型 | 固定标签 | 含义 |
|---|---|---|---|
| loadup.payment.recovery.execution | Timer | kind=payment/refund；outcome=continued/completed/skipped/failed | handler 调用次数及墙钟执行耗时，包含 native 重试和旧代重放 |
| loadup.payment.recovery.inflight | Gauge | kind=payment/refund | 本进程正在执行该类 handler 的数量 |

continued 表示当前游标和原生后继已提交；completed 表示执行推进后完成游标；skipped 表示预检无需推进或执行后已失去当前代；failed 表示该次调用抛错（不代表交易资金失败）。
预检发现已是终态并顺便完成游标也记 skipped，因为没有再推进渠道。handler 记录完成不表示 native ACK 一定成功，lease_lost 仍由原生 dispatcher 观测。

标签不包含 tenant/order/payment/refund/attempt/requestNo、taskId、generation、账户、金额、URL 或异常文本。
Timer.count 不是未终结交易存量；inflight 也不是长期 UNKNOWN 数量。UNKNOWN 库存/最老年龄、真实占用金额、FAILED 任务存量及 Outbox 新鲜度仍需独立快照指标。
调用期记录指标的 RuntimeException 只记固定来源与异常类型，不覆盖原业务异常，不写数据库、不变更重试或金融事实。

新增指标和两个真实 adapter 的单测源码，并同步恢复服务的已提交决策断言及 IT 装配；本轮未编译/运行，也未启用或观测真实 Prometheus。


### Review R3：UNKNOWN 只读存量快照

新增独立 RecoveryInventoryCollector ScheduledTask，按服务端配置租户范围读 RR 快照，并在完整范围成功后发布 UNKNOWN 付款/子退款数量、名义金额和首次 UNKNOWN 最老年龄。
金额不表示实际额度 held；年龄取当前 UNKNOWN 行的 first_unknown_at。首次采样前 NaN，失败保留整个旧样本并暴露 sample.age；scrape 只读内存。
采用生成 Mapper/TableDef/聚合接口和 Spring MapStruct，没有手工 Mapper/SQL、表迁移、行锁、网络回查或资金写入。
默认 PAYMENT_RECOVERY_INVENTORY_ENABLED=false，范围用 PAYMENT_RECOVERY_INVENTORY_TENANTS，周期 PAYMENT_RECOVERY_INVENTORY_INTERVAL_SECONDS 默认 300 秒。
没有共享 MeterRegistry 或未启用时不读取数据库，不创建 backend；启用时非空范围最多 100 个不同租户。
指标、严格口径、聚合 API 依据与限制见 [存量说明](../design/RECOVERY-INVENTORY.md)。Collector/model 单测和付款/退款实际 MySQL IT 输入已补，均未运行；真实 held/FAILED/Outbox 等仍待补。


### Review R3：首次 UNKNOWN 时间

payment_order / refund_order 的 first_unknown_at 由实际金融 gateway 与首次 UNKNOWN 状态 CAS 同事务写入，后续查证、任务代次和终态均保留，不加入公开接口或冻结意图。
库存改用该字段，指标为 loadup.payment.recovery.inventory.unknown.oldest.age；不再使用创单年龄，不兼容旧开发期指标名。
两份初始化迁移增加列和 UNKNOWN 必有时间的 CHECK，未执行 DDL/迁移；MySQL IT 补回滚、重复、终态保留与退出库存的输入，未运行。


### Review R3：长期 UNKNOWN 分级查证

新增 POST /api/payment/operations/recovery/unknown/list 与独立 PaymentUnknownRecoveryFacade，只对已验证运营 SERVICE + pay:payment:recovery:read 开放当前凭证租户。
每行是原付款或子退款，返回原身份、首次未知时间、观察级别、版本和父恢复 generation/completed；后续只能通过已有 detail 获取当前任务、retry 加锁审计。列表不赋予重新扣款/注入结果/释放权限。
REVIEW/URGENT 默认 900/86400 秒，可由 PAYMENT_RECOVERY_REVIEW_AFTER_SECONDS / PAYMENT_RECOVERY_URGENT_AFTER_SECONDS 配置，达到边界即提示。
NORMAL 包含回拨后时间晚于当前时钟的行，年龄为 0；时间/ID keyset 分页最多 100+1，无总数查询，实际 RR 只读、无 FOR UPDATE/金融写入或渠道 IO。
新增阈值/采样周期 Gauge 与独立 Prometheus 规则/用例；不自动加载规则、不启用现有关闭的 Alertmanager，也未发送通知。
契约、源码测试与待完成范围见 [分级查证](../design/RECOVERY-ESCALATION.md)；本轮均未编译或运行，付款/退款 IT 当前 15/9 个输入。


### Review R4：共同付款与 attempt 绑定

首提/恢复/Y3/回调/关单、原渠道请求、动作、attempt 请求重放和结果投递的共同绑定由纯领域 PaymentAttemptBinding 维护：付款 ID、原 Money、收款商户、额度 operation、原 caller/paymentDigest。
active/submittable/observable/closable/原 winner 请求前置条件分开；迟到原请求可以继续观察，Y3 再决定是否属于当前目标，不因 gate fenced 丢弃资金事实。
PaymentExecutionBindings 只映射本模块错误；原权限、锁序、输入/冻结 route、有效期、Y2 唯一票据、claim/receipt/容量/Outbox 仍在各自原流程。渠道请求构造补冻结 channel/method 对照。
领域/适配逐项篡改及 Y3 冲突/损坏分类测试源码已补，均未编译/运行；完整 R4 后续和证据范围见 [绑定契约](../design/PAYMENT-BINDING.md)。


### Review R4：门禁与完整执行上下文

LockedPayment 仅持有合法 PaymentGateBinding，生产/测试删除旧三参数原始构造；父/付款门禁的内部身份关系集中校验。
纯领域 PaymentExecutionContext 组合该门禁与 attempt，要求同一个完整付款快照；锁内 Y2/恢复/关闭/Y3/动作/投递接入各自模式。
PaymentExecutionLocks 保持原锁序，提前拒绝无事务/只读事务；首次提交要求双 OPEN，已提交事实在 FENCED 或终态后仍可观察，值对象不能替代持久 Y2 唯一提交权或资金证据。
新增领域/局部映射/锁适配/Y2 拦截测试源码，均未运行；完整说明与待验收范围见 [绑定契约](../design/PAYMENT-BINDING.md)。


### Review R6：付款恢复意图快照

本服务不再直接持久化公开 PaymentExecuteCommand JSON；独立 PaymentIntentSnapshot 及嵌套本地条款由 MapStruct 转换，使用 owner 格式和固定存储 JSON 规则。
恢复不重新执行当前 Command Bean Validation 或查询当前合约填值；仍验证快照自身、哈希及原落库索引，保留金额/期限/规则版本/输入引用。
旧裸 Command/错误 owner/未知格式明确失败，没有双读、回填或兼容分支。未执行编译/测试/数据库或开发数据重建，完整范围与测试源码见 [快照契约](../design/PAYMENT-INTENT-SNAPSHOTS.md)。


### Review R7：局部恢复协议

Payment/RefundRecoveryService 共用 task key、领取元数据与两阶段 current/successor 纯规则，原 typed 身份/schema/bizId 保持，付款和退款锁序、状态机及原号推进独立。
当前代 dueAt 在推进前后都与原 executeAt 核对；心跳可延长 lease，应用校验不替代 native token fencing/ACK，不引入 Saga、泛型执行 runner 或新扣款权限。
共享协议/领域与服务边界测试源码已补，未编译/运行，完整约束见 [恢复协议](../design/RECOVERY-PROTOCOL.md)。
