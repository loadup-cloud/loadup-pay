# WeChat 渠道适配

本模块实现独立的微信支付策略，业务契约只依赖 channel-api，复用 loadup HTTP/KMS 等技术组件，不依赖 payment。由 payment-bootstrap 的 payment-business profile 引入；启用 payment-wechat 运行配置后条件注册 WechatChannelProvider。

| 能力 | 当前源码范围 |
|---|---|
| APIv3 协议 | 最终 URI/请求字节签名、原始响应字节验签、平台公钥标识固定、时间窗/严格 JSON 校验 |
| 凭证 | 可信目录中的原版本描述文件、原商户 KMS key name/version、固定平台公钥/标识 |
| JSAPI 下单 | 直连、服务商/子商户、sp_openid/sub_openid、原 CNY 总金额与截止时间 |
| JSAPI 动作 | 服务端 RSA 签名、统一动作快照与客户端 DTO；预支付受理不表示收款成功 |
| 查询/恢复 | 原商户订单号、原账户/应用/金额核验，查询恢复不重新下单 |
| 关闭 | 原号查询后关闭，验签 204 ACK 组合证据；竞争时重新查单 |
| 普通退款 | 直连/服务商固定原退款号提交与查询；受理后查证成功/关闭，异常/不存在保留占用 |
| H5 下单/动作 | 直连/服务商 H5_SCENE、原金额/截止时间、验签原地址与最多五分钟 REDIRECT；纯查询恢复不重新下单 |
| 付款码 | 尚未实现，明确拒绝；不回退到其他渠道 |
| 微信支付/退款通知 | JSON 原字节/签名头验签、原 APIv3 key 解密、原账户/金额核验；分别接原付款与独立退款结果事务后 ACK |

## 依赖与配置

payment-infrastructure 只调用统一 Facade。插件自动装配 WechatSubmitAdapter、WechatPaymentAdapter、WechatRefundAdapter、WechatJsapiSigner 及自己的协议实现。跨服务通信仍为 gRPC，本插件与 payment 同进程运行。

可选运行配置为 [application-payment-wechat.yml](../../payment/payment-bootstrap/src/main/resources/application-payment-wechat.yml)。提供固定的 direct-jsapi/partner-jsapi/direct-h5/partner-h5/direct-query/partner-query/direct-close/partner-close/refund-create/refund-query operation。签名器核验实际方法、路径和官方 HTTPS 主机，不允许通过请求传入任意地址；不引入 SDK 自建 HTTP 客户端或自动重扣。

## H5 场景与动作（2026-10-24）

上游选择 WECHAT/H5，interaction 必须为 H5_SCENE，携带 payerClientIp（真实付款终端 IPv4/IPv6 字面量）与 sceneType（WAP、IOS、ANDROID）。由已认证商户服务端提供实际终端 IP；平台不把服务端出口 IP、HTTP 转发头链、主机名或 OAuth code 当作该字段。预检拒绝空地址、回环/未指定/组播、端口、方括号及 zone id，不进行 DNS 查询。IP 经原 KMS 输入准备流程加密，并以同一付款/订单/占用/商户/金额/渠道/方式固定，原 HMAC 指纹校验重放；原值不写入 acquire 意图或普通诊断。

插件映射 scene_info.payer_client_ip 与 h5_info.type（Wap/iOS/Android），H5 不发送 payer/openid，也不调用 JSAPI 唤起签名。直连和服务商分别走固定 H5 operation，始终使用原 app/mchid 或 sp_appid/sp_mchid/sub_mchid/sub_appid、notify_url、CNY 金额和原截止时间。

只接受已验签成功响应中的 h5_url，统一返回 ACCEPTED 与 WechatH5ChannelAction，经白名单快照转为客户端 REDIRECT/RedirectActionDTO。当前信任的跳转地址为 HTTPS 的 wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb；拒绝外部主机、userinfo、fragment 和非 HTTPS，保留地址的原始编码、参数与顺序，不拆分、不截断、不添加商户 redirect_url。动作有效期取调用开始时间加五分钟与原订单截止时间的较早值，请求耗时不能延长；结果返回时已经过期则不展示动作。

H5 页面跳回、访问地址或创建预支付均不表示收款成功。支付结果复用原号查询、通知和既有 Y3 事务；recover 只查询，不重新下单获取新链接，也不更换付款或 attempt。过期动作与丢失响应的持久证据恢复、托管收银台页面和 Referer/授权域名运营配置仍待后续完成；不能因为看不到链接而判定未收款。

依据：[微信普通商户 H5 下单](https://pay.wechatpay.cn/doc/v3/merchant/4012791834)、[服务商 H5 下单](https://pay.wechatpay.cn/doc/v3/partner/4012738604)。本批仅补源码及测试源码，未运行编译、测试、DDL 或真实渠道调用。

该协议依赖 loadup HTTP 组件新增的 HttpRequestSigner 入口：最终编码的 URI 和真正发送的字节参与签名，签名器取得隔离字节副本。仅安装旧 HTTP 组件快照无法提供该 API；框架依赖升级/编译仍未验证，按用户要求没有运行构建。

可信配置需要提供：

```yaml
loadup:
  pay:
    channel:
      wechat:
        credentials:
          merchant-signing:
            v1: /run/secrets/wechat/merchant-v1.json
        notifications:
          notify-urls:
            tenant-id:
              immutable-binding-revision: https://payments.example/wechat/notify
```

文件路径只能来自可信配置，不能从商户请求拼接。描述文件字段为 tenantId/reference/version/appId/merchantId/merchantSerial/signingKeyName/signingKeyVersion/platformKeyId/platformPublicKey，以及回调所需的 apiV3Key（KmsCiphertext 密文，含原加密 key name/version）。商户私钥存 KMS，描述文件只保存版本化引用与平台验签公钥；服务商描述文件匹配服务商 appId/merchantId，子商户身份来自冻结绑定。现阶段仅支持手工配置的固定验签公钥/标识，不宣称已实现证书自动下载或轮换。

微信下单必填 notify_url，按可信 tenant 与原 binding revision 取 HTTPS 地址，无查询串；缺失时在 Y2 前拒绝。上述地址是配置格式示意，不能作为默认真实地址。仓库内同一回调控制器已支持微信 JSON 与支付宝 form，按 endpointRef 的可信配置选择策略；地址必须指向对应微信原绑定的 endpointRef，不能把支付宝绑定对应的路径填成微信回调地址。

## 交易语义

JSAPI 输入仅接受与实际使用 appId 对应的 OPEN_ID。直连使用 openid；服务商未指定 subAppId 时使用 sp_openid，指定时使用 sub_openid。提交附带原商户/子商户、原 out_trade_no、CNY 总金额与原 time_expire；不把 OAuth code 当 OpenID。

prepay_id 只生成 ACCEPTED 与 WechatJsapiChannelAction，不生成 SUCCEEDED。前端参数为 appId/timeStamp/nonceStr/package/signType/paySign；package 与签名仅通过授权业务返回，诊断输出隐藏。动作期限取原截止时间与本次调用开始后两小时中的较早值，并按秒向下取整，不从查询恢复时重计。

查询成功响应必须核对原 app/mchid（服务商含 sp/sub）、原商户订单号、amount.total 与 CNY。用户实付 payer_total 受优惠影响，不能取代订单总金额。SUCCESS/REFUND 在交易号和渠道 success_time 齐备时记录原付款成功，退款通过独立退款流程维护，不冲减原支付金额；渠道时间转为注入 Clock 的本地时区。NOTPAY/USERPAYING 为受理中，CLOSED/REVOKED/PAYERROR/不存在或其他未知结果均不单独构成未收款证明。

关单先查原号，受理中再发关闭；只有原查询与验签关闭 204 ACK 的组合才产生 CLOSE_RESPONSE/NO_COLLECTION。关闭失败重新查证原交易。响应丢失后的关闭证据恢复仍待补，不能凭网络超时释放资金或额度占用。

query/recover 不返回 prepay_id，因此恢复仅返回资金观察，不重新调用下单；已有动作继续受原快照、门禁和有效期控制。若首次预支付响应在持久化前丢失，动作恢复还需要后续持久化协议证据方案。

协议依据：[官方服务商下单参数](https://github.com/wechatpay-apiv3/wechatpay-go/blob/main/docs/partnerpayments/jsapi/PrepayRequest.md)、[官方查询/关闭实现](https://github.com/wechatpay-apiv3/wechatpay-go/blob/main/services/partnerpayments/jsapi/api_jsapi.go)、[官方 JSAPI 开发指引](https://pay.wechatpay.cn/doc/v3/partner/4012069859)。

本批为源码与测试源码，未运行 IDEA 检查、编译、测试、迁移、部署或真实渠道调用。JSAPI 实际接入验收、微信回调/H5/付款码、异常退款处置及原响应丢失恢复继续待办。


## 普通退款协议（2026-10-24）

WechatRefundAdapter 由同一 WechatChannelProvider 暴露 submitRefund/queryRefund，不在 payment 中加入微信分支。canRefund 只检查原渠道和请求号格式，可在本地退款锁内调用，不加载凭证或访问网络。固定计划中的原收款账户、原支付 transaction_id/out_trade_no、原金额和独立 out_refund_no 贯穿执行；每笔部分退款使用自己的固定退款号，不重建号码。

提交调用 POST /v3/refund/domestic/refunds，传原 transaction_id、out_refund_no、amount.refund/total/currency；服务商携带原 sub_mchid。查单调用 GET /v3/refund/domestic/refunds/{out_refund_no}，服务商查询同样带原 sub_mchid。签名仍由原绑定的版本化商户凭证完成。退款响应一般不回显商户号，必须校验原交易号、原商户订单号、原退款号、微信 refund_id、原 gross 总额和本次 gross 退款额；有回显的商户号也必须与原账户匹配。金额按 int64 整数分校验，不接受浮点或字符串金额，不用优惠后 payer_total/payer_refund 替代 gross 值。

退款提交即使返回 SUCCESS，也只记 ACCEPTED，等待统一查询路径提供金融终态，避免受理时间与退款成功时间混用。查询 SUCCESS 要求 refund_id 与 RFC3339 success_time 齐备，成功时间转为业务 Clock 的本地时区；原支付金额保持不变，退款事实由现有独立退款表、回执、容量与 Outbox 流程维护。查询 CLOSED 且没有成功时间时映射 FAILED_WITH_NO_REFUND，沿原退款失败流程释放本次容量；PROCESSING 保持受理中，ABNORMAL/未知状态/不存在保持 UNKNOWN，不能假定资金已退回或未发生退款。

退款号允许厂商声明的字母、数字、_ - | * @，长度不超过 64。固定 operation 对实际路径中的单个标识段解码后校验；编码的分隔符、路径穿越、双重编码与非法百分号不能进入签名请求。

插件不把收款 notify_url 当退款通知地址，也不覆盖商户平台已配置的退款通知；可为原 tenant/revision 配置独立 refund-notify-urls；配置后提交退款会显式传入该地址。缺失时保留商户平台退款通知配置，不复用收款 notify_url。原退款请求首次未达渠道时的安全重提、ABNORMAL 人工/异常退款 API 与退款回调继续待办；查询不存在不会自动释放容量或更换退款号。

依据：[官方退款请求与查询实现](https://github.com/wechatpay-apiv3/wechatpay-go/blob/main/services/refunddomestic/api_refunds.go)、[官方退款金额定义](https://github.com/wechatpay-apiv3/wechatpay-go/blob/main/docs/refunddomestic/Amount.md)、[官方服务商退款状态指引](https://pay.wechatpay.cn/doc/v3/partner/4013080623)。本批源码、测试源码与配置已补，未运行验证或真实退款。


## 微信支付通知（2026-10-24）

WechatNotificationProvider 通过统一通知 Facade 注册，协议仍在微信插件内。ChannelNotificationInput 新增有界、不可变的签名头集合；头名归一为小写，保留重复值供验签策略拒绝。回调控制器接受 application/json 与既有 form，仅捕获 Wechatpay-Serial/Timestamp/Nonce/Signature/Signature-Type 五类头，不从请求读取租户或绑定。

微信的 out_trade_no 位于加密 resource 内，因此 identify 现在接收可信 ChannelExecutionContext。服务端配置 endpointRef→tenant/channel/原 binding revision 后，先按原平台公钥和标识验签原始字节，再用原版本 APIv3 key AES-256-GCM 解密；解出的商户/子商户、通知类型、交易状态与订单号核验通过，才产生查找原 attempt 的提示。verify 随后重做认证并核对原 target 的订单/金额，提示本身不授予任何金融更新权。支付宝仍由自己的 form/RSA2 策略处理。

支持 TRANSACTION.SUCCESS/encrypt-resource/transaction/AEAD_AES_256_GCM，拒绝重复字段、非法 UTF-8、无效签名类型/平台标识/时间窗、重复签名头和坏 GCM tag。通知 create_time 与解密 success_time 必须为有效带偏移时间，但不会直接写入付款成功事实。associated_data 允许缺失或空字符串，始终作为 GCM AAD 使用。密钥从原描述文件中的 apiV3Key 密文通过 KMS 解封；长度必须为 32 字节，密钥、密文工作副本和解密字节在 finally 中清零，不记录到诊断或数据库。

解密验证后沿原 payment callback 流程查原渠道交易，核对 transaction_id，再由 Y3 事务保存 CALLBACK 回执/金融事实/Outbox。只有非冲突提交后返回微信 HTTP 200 固定 JSON ACK；异常或冲突返回 HTTP 500 固定 FAIL，不能用 HTTP 200 包装失败。退款通知使用独立入口并接独立退款流程，不进入付款成功处理。

部署配置示意（仅格式，需替换为真实租户及原 revision）：

```yaml
loadup:
  modules:
    payment:
      channel-callback:
        endpoints:
          wx-binding-v1:
            tenant-id: tenant-id
            channel: WECHAT
            binding-revision-id: immutable-binding-revision
```

该 endpointRef 对应路径 /api/payment/channel-callbacks/wx-binding-v1，微信 notify_url 使用它的真实 HTTPS 地址。原绑定与版本化凭证不能按请求内容切换；当前仍是固定平台公钥/标识和单个原 APIv3 key，不包含自动平台证书或 APIv3 密钥轮换。生产切换、长期延迟通知的有界可信轮换策略及真实渠道验收仍待补齐。

依据：[微信官方支付通知](https://pay.wechatpay.cn/doc/v3/merchant/4012791861)、[官方通知验签/解密实现](https://github.com/wechatpay-apiv3/wechatpay-java/blob/main/core/src/main/java/com/wechat/pay/java/core/notification/NotificationParser.java)。协议、桥接/控制器、原生 ACK 与合成 RSA/AES-GCM 测试源码已补，未运行构建、测试、数据库或真实回调验收。


## 退款通知接线（2026-10-24）

统一 ChannelNotificationFacade/Provider 新增 identifyRefund(context,input) 与 verifyRefund(context,PreparedChannelRefund,input)。复用原字节签名验证与 APIv3 AES-GCM 解密，退款只接受 REFUND.SUCCESS/REFUND.CLOSED/REFUND.ABNORMAL，原资源类型必须为 refund，事件后缀与解密 refund_status 一致。直连核验 mchid；服务商核验 sp_mchid/sub_mchid，退款通知不要求支付 appId。

回调核对原 out_trade_no、transaction_id、out_refund_no、refund_id、原 amount.total 与本次 amount.refund。通知金额可能不含 currency，按原固定 CNY 退款绑定；若有 currency 则必须为 CNY，不接受浮点/字符串，也不把 payer_total/payer_refund 当 gross。SUCCESS 必须有合法带偏移 success_time；CLOSED 携带成功时间的矛盾响应拒绝。以上仅返回已认证触发元数据，不直接构造退款金融终态。

原生 JSON 入口为 POST /api/payment/channel-callbacks/{endpointRef}/refund，与支付回调共享有界收包和签名头采集，但委托独立 RefundChannelCallbackFacade。原 tenant/revision 由同一可信 endpoint 配置确定；退款专用路径在 resource-server 中放行 JWT，实际认证由渠道验签/解密完成。支付宝未实现的退款通知明确拒绝，不回退或交给收款回调。

RefundCallbackTargetGateway 只查 tenant/原 revision/原商户订单号/固定退款号，最多读两条以拒绝歧义；未提交的 INIT 或无 submissionStartedAt 退款不能由外部通知激活。再核对原付款成功时间与退款容量的币种/总额，取得原 PreparedChannelRefund。verifyRefund 精确核验后才查询同一退款，query 的真实退款流水还要与回调一致；只有现有 RefundResultService 事务返回非 CONFLICT 才输出 200 SUCCESS，失败返回 500 FAIL。未知查询可落 UNKNOWN 并继续原恢复轮询，不能释放容量。

CALLBACK 回执键绑定通知 ID 与查询金融事实摘要，允许同一通知第一次未知、后续查证成功时推进；金融去重、原退款锁序、全局退款流水认领、费用/额度/查询投影等独立 Outbox 沿用现有结果处理。原订单和支付成功额不改变。

退款地址通过可信 notifications.refund-notify-urls 配置，示意：

```yaml
loadup:
  pay:
    channel:
      wechat:
        notifications:
          refund-notify-urls:
            tenant-id:
              immutable-binding-revision: https://payments.example/api/payment/channel-callbacks/wx-binding-v1/refund
```

该地址只按原 tenant/revision 取值；收款 URL 与退款 URL 分开验证并保持不可变。多个租户或绑定共享服务商账户时，应为每个原绑定配置对应入口，避免仅依赖单个服务商默认地址无法定位原租户。

依据：[官方服务商退款通知字段](https://pay.wechatpay.cn/doc/v3/partner/4012886275)、[官方商户退款通知](https://pay.wechatpay.cn/doc/v3/merchant/4012791906)。新增 refund_order 的 callback lookup 索引迁移源码，未执行 DDL；协议/编排/控制器/持久化边界测试源码已补但未运行。异常退款处置、原号安全重提、轮换及真实回调/资金验收继续待办。


## 原退款号安全重提（2026-10-24）

WechatRefundAdapter.recover 先按原 binding 和固定 out_refund_no 查询。只有已通过平台验签、operation=REFUND_QUERY、HTTP404 且 code 精确为 RESOURCE_NOT_EXISTS 时，才调用一次原 submit，保留原 transaction_id、out_refund_no、amount.refund/total/CNY 与原 sub_mchid，通知地址仍按原 revision 的不可变可信配置取得。不同部署不得修改同一原 revision 的请求配置；不更换退款号或收款账户。没有内部 HTTP 重试循环，每次是否后继由现有原生恢复任务决定。

不存在并不生成 FAILED_WITH_NO_REFUND 或释放容量；安全性来自微信对相同商户退款号的幂等保证。重提返回 ACCEPTED/UNKNOWN，仅携带查询+提交组合摘要，仍等待原号查询 SUCCESS/CLOSED 的权威结果。已存在的 SUCCESS/CLOSED/PROCESSING/ABNORMAL、其他状态码/业务码、错误元数据/身份、验签或网络异常均不重提。queryRefund 本身保持只读，即使返回不存在也不调用退款申请。

该处理覆盖本地已标记提交、请求实际尚未发送或响应丢失后的恢复；并发恢复即使同时重提，也必须始终使用同一外部幂等键与金融参数。配置变更、真实多 Pod 竞态及渠道幂等仍待实际验收。本批未运行构建、测试或真实调用。依据：[微信官方退款最佳实践](https://pay.wechatpay.cn/doc/v3/merchant/4014959631)。

## 2026-10-27 V2 付款码增量

微信官方当前付款码入口仍要求 V2。新增独立 WechatCodepayAdapter/V2Client/V2Codec、APIv2 密钥读取、直连/服务商固定账户首提和原号 V3 查询接线；复用统一 Facade，不改 payment 为渠道实现。安全输入、错误观察与关闭产品门禁见 [付款码设计](../../design/WECHAT-CODEPAY.md)。付款码撤销/mTLS/持久补偿仍未实现，UNKNOWN/CLOSING 不会因此释放容量。测试源码已补，均未运行；历史“付款码待补”记录由本节细化，不能据此宣称完整付款码能力。
