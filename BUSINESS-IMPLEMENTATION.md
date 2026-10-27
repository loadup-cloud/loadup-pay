# 业务实施日志

当前完整范围与源码/测试/验收三类状态统一见 [根 README 能力矩阵](README.md#当前能力矩阵)，未完成工作见 [ROADMAP](ROADMAP.md)。
本文件保留按日期记录的实施轨迹，不再维护第二份“当前权威状态”总表。

付款码（商户扫码枪输入）、JSAPI/H5、纯 API DirectPay/创单后付款、直连/服务商及多次部分付款的目标不变。
Native 二维码、原生 APP、消费者用户及内部余额不在范围；对账和外部风控按既定后置边界推进。

用户暂停验证仍有效。下文“源码已补”不等于编译、测试或部署通过；旧段落中的“待补/当前继续点”只表示当时状态。
整理前总览保存在 [历史快照](design/history/BUSINESS-overview-before-review-R8.md)。

## 渠道模块拆分（2026-10-23）

渠道统一 API/策略 SPI 与通用契约移到 channel-api；路由引擎在 channel-core，不含支付宝/微信分支。payment 保留状态编排与领域 Gateway，基础设施桥接按原 revision 解析绑定后调用 Facade。支付宝协议、凭证、配置与测试源码移到 channel-alipay；channel-wechat 为独立待实现适配模块。详见 [渠道模块设计](channel/README.md)。

统一路由、租户/冻结账户防错、重复注册和桥接的测试源码已补；支付宝退款 Provider 已条件注册，微信及未实现收款能力明确拒绝。未运行 IDE 检查、构建、测试或部署。

## 支付宝退款 Provider（2026-10-23）

独立 channel-alipay 已补 AlipayRefundAdapter/AlipayChannelProvider；真实协议的退款提交返回受理，原请求退款查询严格核验成功状态/身份/金额/时间后产出终态观察。使用验签自然键生成稳定退款引用，复用现有 claim/容量/回执/Outbox 链路。可选 payment-alipay runtime profile 配置两项外部 HTTP 操作，运行默认不开启。协议 GMT+8 与业务本地时间分别转换，不改运行时区。

付款码/JSAPI/H5 收款动作、查单/关单、微信/银联与回调仍待实现；退款首次请求未到渠道时的同请求安全重提仍待补，不能把 NOT_FOUND 当无退款证明。本批测试源码未运行，未验收真实资金链路。

## 支付宝查单与关闭（2026-10-23）

支付 query/close 改为 ChannelPaymentTarget（原身份与原金额），所有调用方按统一契约更新，无兼容重载。AlipayPaymentAdapter 校验原请求与金额币种，成功要求真实渠道交易号与成功时间；单独 TRADE_CLOSED/NOT_FOUND 不推断未收款。关闭先查待付款，再核验关闭成功；竞态查回原单，组合来源使用 CLOSE_RESPONSE 和双响应摘要。共享严格响应读取与绑定校验复用于退款。可选支付宝 profile 增加查单/关闭两项 HTTP 操作。

统一 Facade 与桥接的测试源码已同步，新增支付宝查询/关闭协议测试源码，未执行。当前只有独立插件协议实现，payment 主链自动提交/查单/关单驱动仍待接；关闭成功 ACK 丢失后的确定恢复也待补，不能宣称所有在途已可收敛。

## 支付宝收款提交与原订单上下文（2026-10-23）

channel-api 新增独立明文输入、ChannelPaymentRequest/Submission 与 AlipayJsapiChannelAction；资金观察和唤起动作分开。支付宝付款码调用 trade.pay，成功后查询原请求取得规范事实；JSAPI 使用 trade.create，仅返回受理和 tradeNo 动作。H5 明确未实现，不泄露商户授权令牌生成前端 URL。统一 Facade、payment Gateway 与原提交工厂已改用新契约。

PaymentExecuteCommand 新增原 subject/payableUntil，从 acquire 当前订单冻结透传到持久意图和 provider-owned gRPC。受理仅对新交易检查期限，原键重放仍返回原结果；Y2 在准备和事务内再次校验截止。JSAPI 输入改为 identifier/identityType/appScope，不将 OAuth code 当 buyer ID，商户 API 先完成自身应用授权；无需平台用户资源。不保留旧字段/构造重载，旧开发快照/密文不混用。

已补协议提交、请求工厂、Facade/桥接和新契约测试源码，并同步 RPC 映射与原测试 fixture。未运行任何验证。仍缺动作持久化与商户查询返回、预检及主链自动驱动、H5、托管授权、回调和微信/银联；不能据此宣称商户调用已完成真实支付链路。


## 支付动作快照与授权返回（2026-10-23）

payment_action 以原 attemptId 为主键，追加保存原渠道身份、金额、执行 epoch、首次受理回执/来源摘要与动作有效期。PaymentResultService.recordSubmission 在同一 Y3 事务应用验签受理观察和保存首份动作，动作冲突或写入失败使该事务失败；不提供商户注入结果入口。

PaymentAcceptanceService 的详情、权威 outcome 与 PROCESSING 幂等重放读取原执行门禁，按可信原服务身份返回动作。已终结、关闭中、门禁封禁、非当前 attempt 或动作过期时不返回唤起动作。JSAPI 的动作交易号必须匹配 ACCEPTED 观察，动作不构成成功事实，也不延长原订单期限。

ChannelActionCodec 契约和严格编解码实现归 channel-api/core；payment 不维护支付宝动作的持久格式解析分支。新增测试源码覆盖幂等、冲突、过期、门禁与 Y3 调用事务边界；内存夹具不证明真实数据库原子回滚。DDL、编译、测试与运行装配均未执行；自动付款驱动与预检已补源码；H5、托管授权与微信实现仍待完成。


## 付款自动执行与恢复（2026-10-23）

受理记录、payment_recovery_cursor 和首轮 DelayedTask 同事务登记；幂等重放不重复登记。PaymentInitialAttemptService 统一内部自动首笔与既有 SERVICE startAttempt 的固定路由/原请求号创建，不开放商户自定 binding 或任意成功凭证。任务按原可信调用服务与 parent→single→payment→attempt 门禁读取，锁外执行预检、风控、额度和渠道调用。

Y2 只有一个内存 ticket 获胜者执行一次 submit；已 SUBMITTING/PENDING/UNKNOWN 的恢复只查询或关闭原 channel/binding/request，不重建 ticket、不换号。首次返回经 recordSubmission 在 Y3 应用观察并存动作。到期/封禁先持久 single gate，未 Y2 可本地证明无收款并终结；已 Y2 只能按原渠道关闭/查询，UNKNOWN/NOT_FOUND 保留占用。

正常处理中以 CAS 推进业务游标代次，并在同一事务登记不同 bizId 的后继；网络、结果写入和后继登记异常交给原生租约重试。原生重试耗尽后仍需运维重放，不能宣称已具备无限恢复。JSAPI 创建响应/动作快照丢失后的查询恢复已补源码；关闭 ACK 丢失的确定恢复、后台任务清理仍待补。

本批预检/执行/恢复、DDL 与聚焦测试源码已补，未运行 IDE、编译、测试、数据库、镜像、部署或真实支付操作。完整业务目标继续保留 H5/微信/银联、回调、托管交互、通知、重建/对账与所有未验证项。


## JSAPI 动作查询恢复（2026-10-23）

新增进程内 query-only recover 契约与 ChannelPaymentRecovery，独立支付宝插件从验签待付款查询重建原交易动作；payment 主链恢复改用 recover→recordRecovery，关闭流程仍 close→record。恢复不打开原付款输入、不重建提交 ticket、不发送 CREATE/PAY，原交易号/金额/期限由冻结记录核验。

查询观察、首份动作及返回投影仍在同一 Y3 事务；已有动作幂等保持首份数据。动作超过原期限、方法/交易号不匹配、非查询来源或资金事实冲突均拒绝；成功/过期/关闭/未知查询不产生新动作。原 UNKNOWN 状态不借此降级；终态/门禁封禁继续隐藏动作。

源码与聚焦测试源码已补，没有新增业务表、外部 HTTP/RPC 入口或兼容重载。编译、测试、数据库回滚/并发与真实资金调用仍暂停；关闭 ACK 丢失、H5/微信/银联、其他渠道回调、商户通知、托管交互、对账与整体未验证范围继续待办。


## 支付宝回调接入（2026-10-24）

原生 POST /api/payment/channel-callbacks/{endpointRef}、可信 endpoint→tenant/channel/bindingRevision 配置、原 attempt 查找索引、独立通知 Facade/策略、支付宝 RSA2/UTF-8 验签和原号查证源码已补。JSAPI CREATE 可从可信 tenant/bindingRevision URL 目录附加 notify_url，不能从商户支付参数选择目标。入口只收有界 form body，不接受 URL query 参数、租户头或原始成功证据写入。

验签与网络查询均在本地事务外；query 的标准成功时间/金额/交易号产生 CALLBACK 观察，复用现有 Y3 回执、全局流水 claim、状态及目标 Outbox 原子提交。回执复合键按通知 ID+查证事实摘要生成；源摘要组合验签内容与查询原文摘要。使用现有业务回执幂等，不再双写通知 Inbox 表。冲突/失败不返回 success，终态和退款仍保持原有独立语义。

新增迁移只有 payment_attempt 的 callback lookup 索引；原始签名报文不入业务表，当前保存归一事实与摘要。公钥验证可独立于授权令牌解密；原号查询仍需原渠道凭证可用。源码/安全边界测试源码已补，编译、测试、DDL、公开路由、真实回调与资金链路均未执行验证。H5/微信/银联、其他通知、运维恢复、托管交互、查询重建和对账仍继续完整目标。


## 2026-10-24 微信 JSAPI 源码

微信插件已条件注册 Provider，直连/服务商下单、原商户/子商户冻结、OpenID 应用作用域、原 gross CNY 金额/截止、KMS 客户端参数签名与统一动作快照/DTO 投影已补。原号查单核验账户/总额/成功时间，REFUND 保留原付款成功事实；pending 后验签关闭 204 ACK 才生成组合未收款证明。query-only recover 不调用下单，也不从查询中伪造 prepay_id。

测试源码与可选 payment-wechat profile 已补，未运行构建/测试/数据库/部署/真实调用。依赖框架 HTTP 新增的最终 URI/字节签名入口；仍需用户后续验证依赖装配。微信 JSON 回调/解密、退款、付款码、H5 typed scene、ACK 丢失证据恢复与实际接入继续待办。其他商户通知、托管收银/授权、查询重建、运维恢复及对账完整目标继续推进。


## 2026-10-24 微信普通退款源码

WechatRefundAdapter 接现有固定计划/Provider/恢复查询。直连与服务商携带原签名账户/子商户，核验原交易/订单/退款号、微信退款流水、原 total 与本次 refund；支持 int64 整数，优惠现金额不替代 gross。申请仅受理，查询 SUCCESS 核验渠道成功时间；CLOSED 且无成功时间才生成失败未退款事实，ABNORMAL/不存在保留占用。原付款金额不变，沿用退款回执、独立容量与 Outbox。固定 HTTP operation、自动装配、金额/状态/身份/事务边界与编码路径测试源码已补，未运行构建/测试/部署或真实退款。

首次请求未达渠道时原号安全重提、异常退款 API/人工流程、微信退款通知仍待实现。其余主链路、H5/付款码、收银台/授权、商户通知、查询重建、风控接入、对账及未验证范围继续完整目标。


## 2026-10-24 微信支付通知源码

统一通知 identify 取得可信 endpoint 的原绑定，微信先验签原字节后用原 apiV3Key 密文解封并 AES-GCM 解密，再定位 attempt；verify 继续精确核对原账户/订单/gross 金额。复用原查询/资金事实/Y3/Outbox，原生 ACK 增加 HTTP status，微信失败为 500 而非 200。支付宝 form/RSA2 协议独立保留。签名头和字节有界/不可变，密钥与解密工作字节 finally 清零。

本批生产/测试源码、HTTP JSON 入口和可信配置说明已补，未运行构建、测试、数据库或真实渠道操作。微信退款通知、自动/有界密钥轮换及验收仍待办；H5/付款码、原号安全重提、收银/授权、商户通知、查询重建、风控/对账等完整目标继续。


## 2026-10-24 微信退款通知源码

统一通知 SPI/Facade 增加类型化退款定位/核验，微信复用原字节验签/APIv3 解密，核验原商户/子商户、事件与 refund_status、原订单/交易/退款号及 gross 金额。独立退款入口与可信 endpoint 取得原固定子退款，拒绝歧义/未提交候选；原退款查询后通过 CALLBACK 回执与 RefundResultService 事务收敛，既有退款容量、流水认领、费用/额度/realtime Outbox 不变，原付款成功额不减。

退款专用 notify_url、原绑定查询索引及协议/编排/控制器/持久化边界测试源码已补。未运行任何构建、测试、DDL、部署或真实资金操作。原号安全重提、异常退款 API/人工处置、可信轮换、H5/付款码、收银/授权、商户通知、交易重建、风控/对账及所有未验证范围继续完整目标。


## 2026-10-24 原号退款恢复

统一 recoverRefund 与纯 queryRefund 分离，RefundExecutionService 非首次任务使用恢复策略，仍保留原 INIT 提交边界与固定退款计划。微信依据验签 HTTP404/RESOURCE_NOT_EXISTS，支付宝依据验签 code10000/缺退款状态且完整原自然键等精确条件，重提同一原幂等号和金融参数，组合查询/提交摘要；没有新退款号或支付模块厂商分支。恢复提交只受理/未知，不直接成功或释放容量，终态依靠原查询事实，纯查询/回调无提交副作用。

API/Provider/桥接/编排与策略测试源码、文档同步；未运行构建、测试或真实渠道操作。未知且不满足明确条件的结果仍保留占用，不能将任意错误当重提或失败依据。多 Pod 幂等/配置稳定性与任务崩溃演练、异常退款处置、可信轮换及其他完整业务目标继续待办。


## 微信 H5 与场景输入（2026-10-24）

上游 H5_SCENE 携带真实终端 IP 与 WAP/IOS/ANDROID，走既有 acquire 金额占用→payment 加密准备→不透明引用附加→原 attempt 绑定/事务外解密→渠道预检/提交。新增 gRPC oneof 场景分支，拒绝缺失字段和未知场景符号；payment 不增加厂商协议分支。

微信插件支持直连与服务商 H5 固定 operation，返回已验签原 h5_url，统一快照 WECHAT_H5 和客户端 REDIRECT。IP 不写普通意图/诊断；跳转地址不拼装、不改参数，动作不超过原截止与调用开始加五分钟。受理不等于收款，恢复只查原交易，不能因链接过期重下单。初始 payment_input CHECK 直接允许 H5，不做早期兼容处理；未执行 DDL。

已补场景/加密准备/绑定/重放、gRPC、原金额/账户/报文、动作快照/映射、期限与错误边界的测试源码。未运行 IDEA 检查、构建、测试、部署或真实渠道调用。H5 响应丢失/过期后的持久证据与安全恢复、支付宝 H5、微信付款码、托管收银台/授权、商户通知、查询重建、对账及其他原完整目标继续保留。


## acquire 商户通知（2026-10-24）

原订单/付款结果事务生成三类确定性事件与 merchant_notification_delivery，并登记 native delayedtask。重试只推进独立投递游标，原 body/事件/策略不变；后继在 HTTP 前提交，崩溃不丢后继。框架 RequestSigner/KMS 显式版本与 HttpTemplate 统一签最终URI/原字节，新 nonce/timestamp、严格语义 ACK、目标/租户/商户/原路由指纹约束、窗口耗尽已有源码。

acquire 业务装配、独立 DDL、配置样例与测试源码已补，未运行 IDEA、构建、测试、DDL、部署或实际发送。退款通知与通知运维/迁移及目录生命周期继续；本批不宣称完整商户通知或全部业务完成，其他渠道/收银台/交易查询/对账等完整目标不变。


## 退款商户通知（2026-10-24）

payment 终态退款事件独立 Outbox/gRPC、acquire 认证接收/原订单与已消费付款绑定、原合约登记/不可变 Inbox 回执已补。三类事件覆盖子退款成功/失败与整笔申请部分成功等完成态；统一复用 acquire 固定字节签名/严格语义 ACK/有限重试引擎，原财务金额和生命周期不变。新 receipt 迁移与设计 schema 已同步，payment 重复投递表草案移除。

生产/codec/ACK/接收权限/幂等/乱序付款/部分成功/HTTP 正文及 gRPC optional/int64 测试源码已补；按用户要求未运行 IDEA、构建、测试、DDL、部署、RPC 或实际发送。通知查询与审计重放、目标引用发布验证/授权迁移/吊销、历史密钥生命周期、其他渠道、完整收银台/授权、交易查询重建、风控与后置对账，以及所有未验证范围继续保留，完整目标未完成。


## 通知查询与运营恢复（2026-10-24）

新增商户隔离的通知详情/分页和运营专用 wake/审计分页。复用框架 PageQuery/PageDTO/PageResponse、DelayedTask retryFailed/reschedule/register，原生唤醒调整和不可变操作回执同 acquire 本地事务；不另建后台调度服务。当前代次/原业务次数与窗口/原始正文和路由固定，运行任务不抢占，待执行不加速，超期或耗尽不重置。操作者身份、显式版本、请求键和原因绑定，权限与幂等逻辑均在 Facade 应用服务中。

已补迁移/商户查询索引、DO/MapStruct/Gateway/HTTP/JWT 与客户端契约；查询 DTO 排除正文/签名路由和 native 错误，原因只供运营审计并在诊断隐藏。测试源码覆盖权限、隔离、精确重放/改参、版本、预算/窗口/退避、任务状态分支与事务失败传播；按用户要求未运行验证或真实调用。

完整目标继续：跨原预算的人工补发需单独合约授权设计；目标发布前引用验证/授权迁移/吊销、历史密钥生命周期、渠道产品与响应证据恢复、完整收银台/授权、交易查询重建、风控和后置对账，以及所有未验证内容仍保留，不宣称业务全部完成。


## 收银台付款后端（2026-10-24）

PaymentInitiationService 拆出共享 PaymentInitiationOperations，商户与 cashier 各自认证后调用相同金额/次数/尾款预占、安全输入与受理逻辑；无业务流程复制或假商户身份。托管幂等 actorScope=checkout/sessionId，原 owner/hash/商户/冻结合约不变；新动作锁原订单再锁会话，原已预占意图允许继续履约。结果刷新/关闭/事件支持两种 scope，使用内部 OrderPaymentScope，不依赖浏览器会话存活。

CashierFacade 后端与专用 POST start/progress、最小安全响应、server H5/WAP remoteAddr、显式托管目录/原能力交集已补。入站不允许浏览器填 OpenID/IP/interaction；JSAPI 不绕过 OAuth。scope 初期直接更新为摘要/本会话付款/进度三权限，不保留旧只读兼容分支。

新代码与授权/幂等/隔离/安全投影/场景/后台收敛测试源码已补，未运行 IDEA/构建/测试/DDL/部署/真实支付。H5 表单/原链接/有界轮询页面源码已续补；JSAPI OAuth、可信代理/真实域名与进一步恢复及浏览器验收仍需完成。其他渠道、通知生命周期、查询重建、风控/对账和全部未验证范围继续保留，完整目标未完成。


## 托管 H5 页面（2026-10-24）

页面已补手动部分金额、原单笔/尾款/笔数提示、显式方式、固定动作键重试、原 H5 链接和有界轮询源码。整数分/JSON 原文解析、sessionStorage 原动作恢复且不存 token/URL、accepted 明确判别、progress 收敛后才开放下一笔、超期不换单已有核心源码。新同源 ES module 与静态资源公开链、CSP/文本渲染、最小 referrer 规则及 API DTO/MapStruct 同步。

核心 Node 测试源码与 Java 摘要/accepted 测试源码已补，未运行任何 Node/Java/IDE/浏览器验证、DDL、部署或真实支付。新页面源码不等于完整收银验收；JSAPI OAuth、丢失响应/过期动作进一步恢复及多设备/真实浏览器/渠道证明继续。其他渠道、通知生命周期、交易重建、风控/对账和全部未验证目标仍保留。


## 收银台链接撤销（2026-10-24）

独立 revoked_at 永久标记与 checkout_session_revocation_receipt 实现原签发者管理；商户 detail/revoke/revocations
API、专门 read/revoke 权限、客户端契约与 MapStruct/DO/Gateway/迁移源码已补。撤销不改业务终态、金额、凭证或原期限，
已付清/关闭订单链接可撤销；没有恢复原链接的入口。原签发键重放已撤销链接明确拒绝。

撤销和新付款按订单→session 行锁串行化，锁后重查操作回执；标记和回执同事务，原已提交 A2 意图继续履约。
新键需当前版本，同键同参回首次回执，改参拒绝；审计查询绑定原 issuer/hash，输出不含能力凭证。
新增领域/管理服务/认证/预占/已提交意图/签发重放测试源码，全部运行验证仍暂停。
JSAPI OAuth、动作丢失/期限恢复、其他渠道能力、通知生命周期、交易重建、风控/后置对账及完整验证继续保留。


## 收银台原付款意图恢复（2026-10-24）

新增 capability 范围的 recover Command/DTO/Facade/HTTP 入口，按原 actionKey 定位 A2，即使 start 回复丢失仍保留原 paymentId。
共享 PaymentInitiationOperations 在原订单→会话锁内绑定 owner/scope/金额/choice/原准入，返回明确三态；
未观察到意图不创建预占，原安全输入未绑定保留 INPUT_REQUIRED，已绑定则在事务外使用原执行命令幂等受理。
非 HELD 意图不重受理；结果经过 PaymentOutcomeOperations/A3 后安全投影给页面，不把恢复成功等同支付成功。

页面原操作先恢复再重试，已知 paymentId 走原进度入口；未知不清元数据、不换键，终态经证据收敛后才允许下笔。
补充共享编排、capability/原会话和 JS 解析的测试源码，未运行验证。远端安全输入与本地引用间回复丢失的恢复已由 InputLookup 补源码；未受理输入到期的有证据关闭已接 InputExpire，
真实集成、JSAPI OAuth、其他渠道与对账等完整目标不变。


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


## 商户原 actionKey 恢复入口（2026-10-26）

POST `/api/acquire/payments/recover` 接收原 orderId/actionKey/amountMinor/choice，专门 merchant JWT 权限为
`pay:acquire:payment:recover`。恢复绑定原 tenant/callerScopeHash 与 merchant scope，不接受自报范围，不读取 checkout 的同名动作。
复用共享原意图恢复/原输入查回与条件到期证明/A3，找不到或缺输入时 accepted 明确 null；
只有 RESOLVED 的已受理原付款或经 A3 的未受理证据分别返回 true/false，不把依赖失败伪装成未受理。

补充原合法 NoInteraction H5 的恢复：以原 v1 摘要精确确认 NONE 模式才跳过输入查询/到期接口，
原字段全量经 MapStruct 重建摘要候选，不依据空引用猜测。敏感输入路径继续查原输入，不能切换到 NONE。
Java 恢复枚举提升为共享 PaymentRecoveryState，商户与 cashier 使用同一组语义，未保留早期兼容类型。

新增源码与权限/隔离/三态/原证据/未知 JSON 判别/无输入与损坏拒绝测试源码，未执行任何验证、部署或实际支付。
DirectPay 整体丢失回复、首次输入准备并发、JSAPI OAuth、其他渠道/通知生命周期/交易重建/风控/对账和完整验证仍需继续。


## DirectPay 首笔绑定与整体恢复（2026-10-26）

DirectPay 改为独立 DIRECT_PAY A0，固定非敏感订单字段和首笔 actionKey/金额/choice/输入种类，禁止与 ORDER/CHECKOUT 同号混用或改变首笔。
raw input 不进入创建库。原 DirectPay 先恢复原首笔，确实尚未提交才用原请求补做 start；结果统一为当前订单和 PaymentRecoveryDTO，
不再保留早期 PaymentStartDTO 的嵌套兼容表示。调用权限增加 payment:recover。

新 POST /api/acquire/payments/direct-pay/recover 接收原非敏感元数据，新增 direct-pay:recover 并要求 create/read/payment:recover。
未知 orderId 也能按原商户订单号恢复 A0：未观察到不分配 ID；INIT 通过仅续办入口继续原准入；已拒绝/到期返回白名单固定决定；
已完成后恢复原首笔。仅续办在 A0 事务内再次要求行存在，绝不因丢行重建新单；恢复不调用 start 或创建新 A2。

DTO、Facade、编码/摘要、COLA 编排、模式约束、MapStruct、开发期 CHECK 与表草案、测试源码已同步。
未执行 IDEA/构建/测试/DDL/部署或真实调用。首次输入准备并发/地址变动、JSAPI OAuth、其他渠道/通知/交易重建/风控/对账及全部原目标继续。


## A2 后台付款恢复源码（2026-10-26）

AllocationRecoveryQueue 在新 A2 事务内登记游标和 native 首轮任务，AllocationRecoveryService 实现 DelayedTaskHandler，
从持久化原 tenant/owner/order/payment/action 恢复内部续办权限。后台复用 PaymentInitiationOperations.recover 与 A3，
原收银台撤销/到期、商户离线不使已提交意图丢失，也不授予新付款权限。缺输入保持 HELD；原输入到期只有取得 payment 的
关闭证明并经 A3 落回执才释放。部分付款成功后原 allocation 结束恢复，父订单可继续 PENDING。

处理前核对 native RUNNING/attempts/租约/nextExecuteAt；事务内按订单、allocation、游标锁定，锁外调用 RPC，
再在事务内根据原回执结束或登记下一代任务。代次淘汰旧唤醒，native 重试失败不转换金融状态。
已补原子登记、异常回滚、租约/租户、旧代/重复、并发续排、缺输入、到期证明、精确回执和原权限边界测试源码；没有执行验证。
到期关单扫描/续办及 FAILED 运维处理仍待实现，不以本轮源码代替运行验收。


## 订单到期及后台关闭源码（2026-10-26）

已接 A1 原始到期登记、延期版本登记、原生到期 Handler 与关单 Handler。CLOSE 产生共享 C0；KEEP_PENDING
保持续付语义。C0 与首轮任务、扫描游标与后继任务分别原子提交，后台只使用原不可变关闭请求与内部原 scope。
商户 Facade 保留权限校验，关闭协议/门禁/资金证据编排提取到 OrderClosureOperations，RPC 均不跨本地事务。
延期读取原 DIRECT_PAY 元数据补齐，与 ORDER/CHECKOUT 共用原始截止和累计上限规则。

已写到期版本淘汰、到期策略、C0/游标原子登记、旧代/终态、无证明不关闭、缺原意图拒绝、锁外续办、延期重放/任务失败回滚测试源码。
本轮没有执行 IDEA、构建、测试、DDL 或 RPC；旧库补登记及 native FAILED 运维接口继续待办，完整业务目标未完成。


## Acquire FAILED 原任务运维恢复源码（2026-10-26）

新增受限 Facade/运营 HTTP、专用 SERVICE/权限校验、原任务查询与审计重试，只覆盖 acquire 付款恢复/订单到期/关单续办。
在原订单锁下共用 AllocationRecoveryService 的 held/回执判定和 OrderClosureOperations 的原关闭意图/代次判定，
不调用工作 Handler、渠道 API 或创建服务。FAILED、原 taskId/attempts/nextExecuteAt 完全匹配才允许重试；过期代次只留审计。

同库 native 重试+不可变回执原子提交；callerScope/requestKey 重放返回首份回执，改参拒绝，避免响应丢失导致再次重置 native 次数。
已补权限/前置条件/任务白名单/旧代/未来截止/原回执重放/事务回滚/三类业务绑定与诊断隐藏测试源码。
未运行 IDEA/构建/测试/DDL/HTTP/JWT/调度验证；payment/refund 的 FAILED 运维入口、列表告警、首次准备竞态与完整目标继续。


## Payment/refund FAILED 运维恢复源码（2026-10-26）

新增 PaymentRecoveryOperationsFacade 与运营 HTTP，只允许 configured pay-ops SERVICE、recovery read/retry 权限处理
COLLECTION/REFUND_REQUEST 原 native FAILED。复用原金融锁序、当前游标和终态判断，不执行 Handler 或渠道，不改变计划或资金预算。
原 taskId/attempts/nextExecuteAt 精确匹配，native 重试与首份审计原子提交，旧代/终态只记不再需要，重放不再次重排。

补强 RefundExecutionLocks 原 reference 与已锁请求的 ID/订单/商户/摘要绑定，新增 PAYMENT_VERSION_CONFLICT 错误码。
API、领域、BaseDO/MapStruct/生成 Mapper Gateway、Flyway 与测试源码已补；没有执行 IDEA/构建/测试/DDL/HTTP/JWT/调度验收。
完整业务目标未完成，任务列表告警、首轮准备竞态、JSAPI/其余渠道、通知生命周期、交易重建和对账继续。


## 2026-10-27 Review R3：正常未终结业务的有界轮询

付款与退款共享纯领域 RecoveryPollingPolicy，按原 initiatedAt/plannedAt 年龄退避，确定性抖动分散后继；原生传输失败重试继续独立处理。
配置改为 recovery-polling 的 initial/maximum/backoff-every/jitter 四项，旧固定间隔变量删除。后继游标/任务仍同事务，资金状态、占用及提交权不变。
领域、配置、长龄 handler 测试源码及 IT 装配已同步；未运行验证。渠道并发、指标、未知升级和证据保留仍待 R3 后续切片。


## 2026-10-27 Review R3：共享渠道恢复并发预算

channel-core 复用 LoadUp Resilience4j API，bootstrap runtime 装配 core binder；统一 Facade 的查询/恢复/关闭按渠道共享非阻塞 Bulkhead，默认各 16。
满额在 Provider 前抛 CHANNEL_RECOVERY_BUSY，原状态/占用与请求身份保持；不做跨渠道 fallback。首提不在该预算内，后续需在 Y2 前管理名额，不能丢弃已持久化的首次提交权。
新增线程交错/隔离/释放/首提与本地能力边界、配置及依赖门禁源码；没有编译/测试/渠道/部署验证。集群总配额、首提准入、业务指标/升级/保留继续推进。


## 2026-10-27 Review R3：恢复执行计时与在途量源码

实际付款/退款任务 adapter 外层接入共享 Micrometer，固定 kind/outcome 的 Timer 及 kind 的在途 Gauge。
恢复服务返回已提交续行决策，避免本地写重试重复计数；异常保留给原生任务，不据指标改变资金事实。无 registry 不另建 backend，也不跳过业务。
新增指标/真实 adapter 单测源码与服务决策断言，IT 装配同步，未运行验证。调用次数不是 UNKNOWN 库存，实际占用/最老年龄/失败任务/Outbox 快照、升级与保留继续后续实现。


## 2026-10-27 Review R3：UNKNOWN 只读存量源码

按配置租户范围、每租户只读 RR 聚合 UNKNOWN 付款/子退款数量、原名义分额和最老业务创建时间；完整采样后更新内存，失败保留旧样本并暴露新鲜度，scrape 不查库。
默认未启用，无共享 registry 不执行；没有全库租户绕过、手工 Mapper/SQL、表变更或金融写入。此金额不是额度 held，年龄不是 firstUnknownAt。
Collector/model 单测和两类实际 MySQL 聚合输入已补，未运行。真实 held/FAILED/Outbox、升级与保留等继续 R3 后续切片。


## 2026-10-27 Review R3：首次 UNKNOWN 时间源码

付款/子退款的 first_unknown_at 与第一次 UNKNOWN 状态 CAS 原子保存，使用既有 paymentClock 本地微秒时间；后续与终态保留，不改变公开 Command/意图/摘要或资金权限。
库存最老年龄改用当前 UNKNOWN 行的该字段，删除开发期 .unknown.oldest.business.age，统一 .unknown.oldest.age。
直接同步两份初始化迁移的列与 CHECK，不增加兼容默认/回填；没有执行数据库操作。补金融 IT 的回滚/首个提交/重复/终态保留与退出库存输入，均未运行。
R3 实际额度 held/FAILED/Outbox、未知升级与保留、首提/集群配额，以及 R1/R2 真实运行证明仍未完成。


## 2026-10-27 Review R3：长期未知分级查证源码

默认 900/86400 秒的纯领域运营关注策略接入独立 UNKNOWN Facade/HTTP 列表，仅运营 SERVICE 与 recovery read 权限读取凭证租户。
付款/子退款及父请求身份、首次 UNKNOWN 时间和当前游标元数据用于已有 detail/retry；keyset 读取只读 RR，无排他锁、总数查询或外部推进，时钟回拨的未来时间记录在 NORMAL 仍可分页。
Collector 新增阈值/采样周期 Gauge，提供独立 Prometheus 待查证/紧急互斥、未初始化/旧样本规则和源码用例；现有 Alertmanager 未开启，没有通知路由或真实发送。
补领域/配置、服务边界与实际 SQL/锁竞争 IT 输入；付款 IT 当前 15，退款 9。均未编译/测试/promtool/数据库/部署，完整 R1/R2/R3 运行证据和其余业务目标保持未完成。


## 2026-10-27 Review R5：事务 helper 显式命名

acquire/payment/product/limitcenter 的无事务租户范围改为 withTenantContext，realtime 的只读 RR 入口改为 readSnapshot；五个 helper 的有限本地写统一 writeLocal。
同步生产/单测/MySQL IT 调用，不保留旧方法。原隔离级别、传播、租户恢复、三次总尝试/回滚次序、模块错误映射和金融/渠道边界保持，未创建通用 BaseService 或跨库事务。
补五个 helper 契约测试源码；没有执行 IDEA/编译/测试/数据库/网络，R1/R2 的真实持久证明和 R5 运行验收仍未完成。当前契约见 design/TRANSACTION-BOUNDARIES.md。


## 2026-10-27 Review R4：纯领域共同付款绑定

PaymentAttemptBinding 集中付款与 attempt 的不可变跨记录绑定，显式区分 active、首次提交资格、观察、关闭与原提交请求构造；app 的 PaymentExecutionBindings 只保留本模块错误映射。
首提/恢复/Y3/回调/关单/动作/投递等源码接入共同约束，原锁序、租户鉴权、验签、冻结意图、Y2/CAS 与金融证据继续由原层负责。Y3 冲突和损坏快照保持不同处理，迟到观察不获得重扣权。
补逐字段篡改/状态/模式/错误分类测试源码与 Y3 无金融写入场景，没有编译、测试、数据库或渠道运行证据。R4 门禁完整上下文与入口组合验证、R6 私有意图表示仍待后续，详见 design/PAYMENT-BINDING.md。


## 2026-10-27 Review R4：门禁与完整执行上下文源码

PaymentGateBinding 校验父/付款门禁与当前付款，LockedPayment 内部只持有该合法绑定；完整 PaymentExecutionContext 拒绝将不同付款快照的门禁与 attempt 混接。
锁内首次提交要求双 OPEN，恢复/观察/关闭保留各自模式；Y3 错误目标优先及迟到资金事实继续处理。锁序/业务权限/证据和原 Y2 票据继续由原流程拥有，领域值不授予执行权。
同步控制关闭、outcome、动作与投递等路径，补门禁篡改/快照/事务/顺序/错误类型与 Y2 拦截测试源码；未编译/测试/数据库或渠道验证。R4 完整入口组合与 R1/R2/R5 运行证明仍未完成。


## 2026-10-27 Review R6：独立付款意图存储格式

acquire/payment 各自冻结 PaymentIntentSnapshot，完整本地嵌套条款与独立 format，标准 MapStruct 在公开 Command 边界转换，私有存储 mapper 隔离 API JSON 配置/注解。
当前 Command Bean Validation 仅影响新 encode，恢复校验归本地快照与原占用/付款索引；原期限/条款/金额/输入引用不从当前配置重建。旧裸 Command 或其他 owner 格式明确失败，不做兼容与自动回填。
补 codec/映射/格式/接口表示隔离及不可变性测试源码，未编译/测试/数据库/重启恢复验收；没有执行数据重建、回填或 DDL。两端 codec port 与公开 RPC 仍为原契约，见 design/PAYMENT-INTENT-SNAPSHOTS.md。


## 2026-10-27 Review R7：共用局部恢复规则

付款/退款共享 RecoveryTaskKey、RecoveryPosition 和无 IO 的 RecoveryTaskProtocol，规范 key、领取元数据、旧/未来代与后继计算不再各自重复。
两个阶段均复核当前代及原 dueAt，保留各自锁序、金融/退款状态机、原请求推进和同事务 successor。native token/claim/heartbeat/ACK 仍由组件所有，不增加 runner/Saga 或提交能力。
补共享参数化协议/领域测试及两类服务推进后时间异常输入，未运行编译/测试/数据库/原生 dispatcher/渠道。源码契约和未完成证明见 design/RECOVERY-PROTOCOL.md。
