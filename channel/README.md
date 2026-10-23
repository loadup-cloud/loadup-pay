# 渠道集成模块

渠道集成采用 **Facade + Strategy + Registry + Adapter**。payment 维护业务执行状态、选路资源和资金证据；独立渠道模块维护渠道协议与凭证。渠道与支付方式是两个维度：上游指定 ALIPAY/WECHAT，payment 在合约和商户绑定允许范围内冻结选路；PAYMENT_CODE/JSAPI/H5 决定该渠道的交互方式。

| 模块 | 职责 | 当前实现 |
|---|---|---|
| channel-api | ChannelPaymentFacade/ChannelNotificationFacade、两类 Provider SPI、ChannelActionCodec、不可变请求/结果与绑定契约、渠道错误码 | 已有源码，零 payment/Spring/数据库依赖 |
| channel-core | DefaultChannelPaymentFacade、不可变 Provider 注册表、严格动作快照编解码、自动装配 | 已有源码，按 channel 路由；重复注册启动失败 |
| channel-alipay | 支付宝协议、签名、验签、版本化凭证 | OpenAPI v2/RSA2、版本化凭证、付款码/JSAPI、退款、查单/关闭与回调验签策略源码已补；H5 待实现 |
| channel-wechat | 微信独立适配边界 | 已有 APIv3 签名/响应验签及凭证基础源码，支付适配器/Provider 待实现 |

## 调用与依赖

```text
上游 PaymentChoice(channel, method, bindingCode)
  -> payment 业务编排 / 冻结 bindingRevisionId + accountScopeHash
  -> payment-domain: PaymentChannelGateway
  -> payment-infrastructure: PaymentChannelGatewayImpl
       -> 以可信租户读取原不可变 binding revision
       -> channel-api: ChannelPaymentFacade
       -> channel-core: Registry[channel]
       -> channel-alipay / channel-wechat: ChannelProvider 策略
       -> loadup HttpTemplate / KmsTemplate
```

渠道 API、引擎和适配模块均不依赖 payment-client/domain/app/infrastructure。payment-domain 只依赖 channel-api；
payment-infrastructure 仅依赖 channel-api，通过构造器注入统一 Facade；payment-bootstrap 的 payment-business profile 显式装配 channel-core 和具体插件。它们当前随 payment 进程运行，不增加独立部署单元，也不增加测试 RPC。

模块依赖、模式职责与扩展步骤见 [渠道架构](ARCHITECTURE.md)。

## 统一 API

`ChannelPaymentFacade` 提供八个操作：

| 操作 | 输入与语义 |
|---|---|
| validate(context, validation) | Y2 前核验插件/支付方式/参数与冻结账户；不授予执行 epoch，不进行 HTTP、KMS、文件或资金操作 |
| submit(context, request) | 原执行信息＋订单标题/截止时间＋绑定明文输入；返回 ChannelPaymentSubmission，将动作与资金事实分开 |
| query(context, target) | ChannelPaymentTarget 包含原身份与原金额；查询原渠道原账户下的原请求 |
| recover(context, recovery) | 只查询原交易，返回验签观察与可恢复的有效动作；无原输入或提交许可，不重新创单/扣款 |
| close(context, target) | 同一原身份/原金额下收敛关闭；调用方负责先持久化关闭门禁，不能把超时视为未收款 |
| canRefund(refund) | 纯本地能力查询；允许在业务事务内调用，不读取绑定、文件、KMS 或网络 |
| submitRefund(context, refund) | 原交易、原请求号、退款金额与固定退款请求号 |
| queryRefund(context, refund) | 查询同一退款，不重建或改路由退款请求 |

`ChannelExecutionContext` 包含可信 tenantId 与调用方解析的不可变 `ChannelBindingRevision`。
Facade 在调用策略前校验租户、channel、bindingRevisionId、accountScopeHash；submit 还校验绑定的支付方式能力。
所有结果必须匹配原身份和原金额；退款还必须匹配原交易号/退款号。外部调用禁止在本地事务中执行。

没有对应 Provider、插件未启用或不支持能力时明确返回 CHANNEL_UNSUPPORTED_CAPABILITY；不回退到另一渠道或账户。
查询/关闭/退款复用原 revision，不重新读取当前活动路由，不因商户后续切换凭证而改变在途交易身份。

## 新增渠道

1. 新建 channel-{vendor}，仅依赖 channel-api 与实际需要的 loadup 组件。
2. 实现 `ChannelProvider`，用 `channel()` 声明唯一渠道；直连/服务商模式由该策略按冻结绑定处理。
3. 用插件自身的 Spring Boot AutoConfiguration 条件注册 Provider；不同渠道可同时加载。
4. 在应用 bootstrap 组合插件和配置，新增路由无需修改 channel-core 的分支或工厂代码。
5. Provider 对响应验签、实际收款账户、金额和状态负责；未知结果保留 UNKNOWN，不制造成功或未收款证据。

支付宝插件启用时注册 AlipayChannelProvider，已实现退款提交/查询、支付查单和未付款关闭的协议映射；付款码与 JSAPI 提交已补源码，H5 仍明确拒绝未实现能力。微信已有 APIv3 协议基础源码，尚未注册 Provider。后续在独立插件内补齐 H5 与微信异步回调；支付宝验签回调已有源码；payment 自动提交/查单/关闭驱动已有源码，仍需后续验证；银联产品与接入机构仍待确认。源码接入不等于真实资金链路验收通过。

本批只调整源码、依赖、文档与测试源码。按用户要求，未运行 IDE 检查、构建、测试、迁移、部署或真实渠道操作。


支付 query/close 已统一采用 ChannelPaymentTarget，不保留旧 bare identity 重载。PaymentObservation.Source 新增 CLOSE_RESPONSE，区分关闭的组合证据和单独查询。支付宝单独 TRADE_CLOSED 不产生未收款证明，详见 [支付宝协议语义](channel-alipay/README.md)。

## 提交请求与结果

ChannelPaymentRequest 包含 PreparedChannelPayment、原 subject、原 payableUntil 和隐藏诊断的 ChannelPaymentInput。输入按付款码、已解析渠道买家标识、无输入三类建模；不能将 OAuth 授权码当成 buyer_id/open_id。ChannelPaymentSubmission 包含原身份/金额、可空的验签观察和可空动作，至少有一项；有动作时观察只能为受理，不得同时宣称支付成功。动作的渠道、支付方式和有效期由 Facade 与原请求核验。

payment 的 PaymentChannelRequestFactory 在事务外核对原 SUBMITTING attempt、执行 epoch、风控/额度许可与调用方/原金额，再读取单 attempt 绑定的短期密文输入；通过 MapStruct 转为渠道请求。独立渠道模块不反向读取 payment 的库或解密接口。当前提交协议、动作持久化与 payment 授权查询/幂等受理返回已有源码；主链自动驱动已有源码，托管 cashier 交互仍待完成。


## 模式与扩展边界

- Facade：上游仅调用 ChannelPaymentFacade，统一提交、查单、关单及退款契约。
- Strategy：每个渠道实现一个 ChannelProvider；直连/服务商、商户/子商户由该策略根据冻结账户身份处理。
- Registry：Spring 收集 Provider，启动时构造不可变 Channel→Provider 注册表；重复渠道注册立即失败，无厂商 switch/if 选路。
- Adapter：PaymentChannelGatewayImpl 适配 payment 的领域端口；各插件内部再适配厂商协议与 loadup HTTP/KMS 组件。

上游 channel 不是任意绕过合约的开关：payment 先校验合约和商户绑定，冻结 channel、method、bindingRevisionId、accountScopeHash。Facade 按请求 channel 查注册表，再校验同一冻结账户。新增厂商协议不得放入 payment 的业务 Service 或 GatewayImpl。

动作格式由 channel-api 的类型契约声明，channel-core 的 ChannelActionCodec 负责白名单类型、严格 JSON 与摘要核验，不接受任意类名。payment 保存动作快照并将已定义的动作投影为客户端 DTO，不解析厂商响应或生成渠道支付参数。动作投影属于表示映射，不参与渠道选择。

payment_action 与 Y3 受理回执在同一事务记录；重复结果保留首次动作，不能更换交易号或延长期限。动作只在原 active attempt、原执行 epoch、PROCESSING、双门禁 OPEN 且未过期时返回，终态或封禁后隐藏。调用方持锁仅处理本地状态；真实渠道调用仍在事务外。动作存储已由首次提交的自动执行器调用；JSAPI 创建动作响应丢失的查询恢复已有源码；关闭 ACK 丢失的确定证据仍待补齐。


## Y2 前预检

ChannelPaymentValidation 携带原身份/金额、method、subject、payableUntil 和短期输入，刻意不带 executionEpoch。Facade 通过同一 Provider 注册表调用 validate；该方法只做能力与参数校验，不能发送 HTTP、加载文件、解密 KMS 或产生任何交易。解密由 payment 在事务外校验原未绑定输入后进行，明文仅传给本地策略。

支付宝 validate 与 submit 共用参数规则；尚未实现的 H5、无 Provider 的微信、错误付款码、错误应用作用域或不符合渠道时间窗的请求在额度申请/Y2 之前拒绝。拒绝时仍需在原门禁下核实 attempt 没有进入 Y2，才能生成本地无收款证明；并发赢家已经进入 Y2 时改为查询原交易。预检通过不保证后续网络成功，也不授权任务重试重新扣款。


## 查询恢复唤起动作（2026-10-23）

ChannelPaymentRecovery 冻结原 identity/amount/method/executionEpoch/payableUntil，不带付款码、买家标识、preparedInputRef 或提交 ticket。它与 PreparedChannelPayment 共享只读 ChannelPaymentExecution 元数据接口；该接口本身不授予提交权。Facade 的 recover 仍走 Provider 注册表，核验原身份/金额、QUERY_RESPONSE 来源与动作方式/期限，无厂商路由分支。

支付宝插件通过同一 out_trade_no 查询，只有已验签 WAIT_BUYER_PAY、原金额/交易号核验通过、原 method=JSAPI 且原截止时间未到才重建 AlipayJsapiChannelAction。动作到期使用原 payableUntil 按秒向下取整，与首次 CREATE 一致；不会从恢复时间重新计时。付款码、已成功、未知、已关闭或过期只返回资金观察，不产生动作。recover 不调用 CREATE/PAY，也不依赖原短期付款输入仍可解密。

payment 恢复任务通过 MapStruct 从原 attempt/冻结期限构造查询上下文，调用 recordRecovery 将查询观察和动作放入同一 Y3 事务。action 仍遵守原 payment 状态、当前 attempt/epoch 和双门禁的读取规则；已有首份动作不能被新交易号或更晚有效期覆盖。payment 已经进入 UNKNOWN 时保留原保守状态/可见性规则，不靠一条中间观察强行回退到 PROCESSING。

新增契约、插件/编排/桥接与测试源码，未运行构建、测试或真实渠道调用；这不解决没有确定未收款证据的关闭 ACK 丢失，也不完成 H5/微信或真实回调。


## 独立回调策略（2026-10-24）

ChannelNotificationFacade/ChannelNotificationProvider 与支付 Facade 共用同一独立模块边界，通知策略单独按 Channel 注册。identify 只产生不可信原请求号提示；verify 必须持可信租户与原冻结绑定，核验签名、原身份和金额，返回 VerifiedChannelNotification 查询触发元数据，不返回金融终态。固定 ACK 由策略提供，payment 不拼装支付宝协议字符串。

支付宝插件已补 UTF-8 form/RSA2 解析和验签：64 KiB 收包上限、重复字段/非法百分号/非法 UTF-8 拒绝，未知非空字段也参与签名；排除 sign/sign_type 后按字段排序。验签后核对原 out_trade_no、app_id、seller_id、total_amount 和通知类型，绑定原 trade_no。公钥来自原版本描述文件，验签本身不读取商户私钥或解密服务商授权；后续原号查证仍按原凭证版本调用渠道。

payment 回调入口先由配置 endpointRef 定位 tenant/binding revision，不能由请求头/参数选择租户；验签成功后锁外 query 原交易，归一为 CALLBACK 来源观察，再沿原 Y3 记回执/资金事实/Outbox。回执键绑定 notify_id 与权威查询事实摘要，允许同一触发的未知→成功查证推进；重复相同金融事实不重复应用。ACK 只在 Y3 返回已提交且非 CONFLICT 后产生，异常返回失败 ACK。验签通知的 trade_status 不直接决定付款金额或终态。

依据：[官方异步通知与验签规则](https://developer.alibaba.com/docs/doc.htm?articleId=105301&docType=1&treeId=193)、[官方 Java 验签实现](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/internal/util/AlipaySignature.java)。源码/测试源码已补，未运行构建、测试、数据库或真实渠道验收；微信/银联通知和退款外部通知仍待实现。
