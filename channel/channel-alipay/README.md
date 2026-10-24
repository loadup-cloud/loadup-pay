# Alipay 渠道适配

本模块独立维护支付宝 OpenAPI v2 表单协议。AlipayProtocolClient、AlipayProtocolCodec、版本化凭证读取和测试已迁入；AlipayChannelProvider 注册统一策略，退款操作委托 AlipayRefundAdapter；查单/关闭委托 AlipayPaymentAdapter。

- 使用 loadup HttpTemplate 的 alipay 命名 HTTP 客户端及 trade-pay、trade-create、trade-query、trade-close、trade-refund、refund-query 操作。URL 由可信配置提供，不接受商户传入任意网关地址。
- 使用 loadup KmsTemplate 的确定版本 RSA_SHA256_PKCS1 签名；使用冻结的支付宝平台公钥验证原始响应节点字节，避免 JSON 重排破坏签名语义。
- 凭证及服务商授权分别按 reference/version 从配置映射的绝对文件路径读取。私钥不落入描述文件；授权令牌由 KMS 解密。版本化挂载资源需由部署侧保持内容不可变。
- 参数、令牌、公钥描述与原始响应不进入诊断 toString。
- 启用前缀为 `loadup.pay.channel.alipay.enabled`（默认关闭）；同级 credentials/authorizations 是 reference → version → 绝对 Path 的受控映射。启用时应用需组合 channel-core、HttpTemplate、KmsTemplate 和 JsonMapper。
- 时间源使用 channel-core 的 channelClock。运行/业务时区仍为系统本地；协议时间按支付宝官方 GMT+8 格式发送，成功时间按 GMT+8 解释后转换为业务时区，不把无时区文本当成服务器当地时间。依据：[官方 SDK 时间常量](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/AlipayConstants.java)。

支付宝启用时注册 Provider，目前实现付款码/JSAPI submit、退款提交/查询和支付 query/close；H5 submit 仍明确返回未实现能力。付款码/JSAPI 协议和类型化唤起动作源码已补；H5、动作持久化/商户返回、自动驱动、异步回调与证书模式仍待实现。

协议依据：[支付宝官方 Java SDK](https://github.com/alipay/alipay-sdk-java-all/tree/master/v2)、[支付宝退款查询说明](https://aipay.alipay.com/docs/vibe-pay/ai-web-app-payment-qianyi/api-list/alipay-trade-fastpay-refund-query.html)。协议成功不等于资金成功；退款查询仍需核验退款状态和原请求身份。

本批未执行测试、构建或真实渠道调用。


## 退款业务适配

退款请求携带冻结的原 trade_no、out_trade_no、同一子退款 out_request_no；金额以分转固定两位元字符串，不使用浮点数。直连/服务商使用各自原绑定和版本化授权。响应已先经过 RSA2 原始报文验签，随后按本次操作严格解析和校验。

- alipay.trade.refund 返回 10000 仅产出 ACCEPTED；不把累计 refund_fee 当作当前子退款金额，不使用 fund_change 推断本次退款终态。
- alipay.trade.fastpay.refund.query 显式查询 gmt_refund_pay。仅 code=10000、refund_status=REFUND_SUCCESS、原交易号/商户请求号/退款请求号匹配、refund_amount 等于本子退款、有效成功时间时产出 SUCCEEDED；若返回 total_amount，必须等于原支付金额。
- 10000 但无成功状态、未知状态、交易/退款不存在均保留 UNKNOWN；错误金额/身份/格式被拒绝。传输/验签异常由 payment 现有 uncertain 路径保留退款容量，原串行恢复任务继续查原请求。
- 普通退款没有统一独立 refund_id，成功引用为 alipay:sha256(length:trade_no + length:out_request_no)。它代表经过验签的渠道自然键，非支付宝颁发的流水号；原始元组已保存在本地退款与全局 claim 中。长度按 Java String.length，单个引用不超过现有 VARCHAR(128)。不改原商户退款请求号，不重置防重认领。
- 回执键由操作名与验签源摘要组成；不把原始响应、授权令牌或凭证文件写入业务日志。

HTTP 超时/验签失败后不换退款请求号，也不自动重新提交。当前首次提交已做单向防重，后续只查询；若首次网络请求未真正到达渠道，需要后续补同请求安全重提的恢复机制，不能将 NOT_FOUND 作为失败释放容量。

## 可选运行配置

payment-business 构建已包含插件。运行需显式组合 payment-business,payment-alipay，后者配置支付宝 HTTPS 网关及 trade-pay/trade-create/trade-query/trade-close/trade-refund/refund-query 六个命名操作，参见 application-payment-alipay.yml；仅显式激活该运行 profile 才启用支付宝插件。凭证/授权 reference→version→绝对文件路径映射由可信 Secret/外部配置注入，私钥仍由 KMS 持有。仅添加 profile 文件，未改动运行中 Kubernetes 配置。

本批新增退款适配、Provider 注册及协议时间测试源码，均未编译或执行；未进行迁移、部署、密钥操作或真实支付/退款。

## 支付查单与未付款关闭

query/close 接受 ChannelPaymentTarget(identity, amount)，由调用方提供原持久金额。请求只查原 out_trade_no，固定签名 app/收款绑定与服务商授权均沿用原 revision，不重新选路。查询响应不提供通用 seller_id，因此账户边界来自固定应用/授权作用域及原请求关联，不能接受商户任意构造的账户身份；协议入口仍核验可信租户、绑定 revision 和实际 accountScopeHash。

- 查询 10000 时核验 out_trade_no、trade_no、total_amount 及返回的 trans_currency。金额严格按元字符串解析为分，不使用买家实付或商户实收折后金额替代原交易金额。
- WAIT_BUYER_PAY 对应 ACCEPTED；TRADE_SUCCESS/TRADE_FINISHED 且有有效 send_pay_date 才对应 SUCCEEDED，成功时间按协议 GMT+8 转为业务本地时间。
- TRADE_CLOSED 可能是未付超时关闭，也可能是支付后全额退款，因此单独查询保持 UNKNOWN；NOT_FOUND、未知状态和非成功代码也保持 UNKNOWN。错误/不存在响应不一定返回金额，观察中的金额来自明确的原付款目标，不构造 0 元或猜测金额。
- close 先核验原交易为 WAIT_BUYER_PAY；再以原 out_trade_no/trade_no 调用仅允许关闭待付款交易的接口。只有匹配的验签关闭 10000 才产出 NO_COLLECTION。source=CLOSE_RESPONSE，sourceDigest 为原待付款查询摘要和关闭响应摘要的组合哈希，回执不会混同成单独查询事实。
- 若首次查询已成功，直接返回真实成功事实，不调用关闭；若关闭因付款竞态等返回非成功码，再查同一请求。未知/已关闭的初始查询不触发盲目关闭。调用方需先按业务协议持久封禁后续提交；渠道模块不拥有 payment 的门禁和账务状态。
- 若关闭请求已到渠道但成功响应丢失，后续仅查到 TRADE_CLOSED 仍不足以证明未收款；后续需补持久操作日志/对账恢复，不能用缺字段或超时释放占用。该边界未以假成功掩盖。

协议依据：[官方查单响应模型与状态定义](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/response/AlipayTradeQueryResponse.java)、[官方未付款关闭接口](https://developer.alibaba.com/docs/api.htm?apiId=1058&docType=4)。

本批统一查单/关闭契约与调用方，复用 AlipayBindingGuard/AlipayResponseReader 处理租户绑定和严格字段读取。新增状态映射、金额/身份防错、关闭竞态与复合来源测试源码，未执行验证。业务自动驱动已有源码，H5 与微信仍待补；下节记录已补的付款码/JSAPI 提交源码。

## 付款码与 JSAPI 提交

AlipaySubmitAdapter 接收统一 ChannelPaymentRequest，不接收任意商户 wire JSON、seller_id 或网关地址。原 seller_id 来自冻结绑定，subject/payableUntil 从 acquire 原订单透传；total_amount 为本次付款份额，费用与原订单总额口径不变。仅传入相同的原 out_trade_no，不自动重提或换号扣款。协议请求使用 GMT+8 的绝对 time_expire，按秒向下取整不延长原截止时间；本适配按渠道窗口要求拒绝剩余不足 60 秒或超过 15 天的请求，不覆盖渠道默认值来偷偷延长。

- 付款码：使用 alipay.trade.pay，scene=bar_code；只接收扫码枪/商户 API 传入的数字付款码，不增加扫码 UI。10003 为受理，其他不确定响应保持 UNKNOWN。10000 需校验原请求和金额，再沿相同查询路径取得稳定渠道成功事实；不使用本地时间或另一种时间字段替代查询成功时间。查询中的交易号也必须与提交响应一致。
- JSAPI：使用 alipay.trade.create；10000 校验原请求/交易号后仅返回 ACCEPTED 和 AlipayJsapiChannelAction(tradeNo, expiresAt)，由上游映射为 INVOKE_JSAPI/AlipayJsapiActionDTO。该动作不是支付成功事实。
- JSAPI 买家输入为已解析标识，不是一次性 OAuth code：identityType=USER_ID 使用 buyer_id；OPEN_ID 在签名应用作用域使用 buyer_open_id，在冻结 operatorAppId 作用域使用 op_app_id/op_buyer_open_id。appScope 必须匹配相应冻结应用，禁止把任意商户应用的 open_id 跨应用使用。直连/服务商的商户授权凭证继续由原版本化绑定和 KMS 解析。
- 商户 API 需先在自身应用完成授权并取得上述渠道标识；平台不新增用户主档。标识只走原短期加密输入，不对外提供解密接口。托管 cashier 的授权流程仍待接入。
- H5 尚未实现；不将服务商长期 app_auth_token 作为前端跳转参数，不用付款码/JSAPI 的受理冒充 H5 已完成。

依据：[官方付款码模型](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/domain/AlipayTradePayModel.java)、[官方 JSAPI 创单与应用作用域字段](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/domain/AlipayTradeCreateModel.java)。

payment 已补动作快照、Y3 受理同事务记录和授权查询/重放返回；动作交易号须与受理观察一致，有效期不能延长。动作编解码归 channel-api/core，不由 payment 解释支付宝响应。Y2 前本地能力/参数预检及提交自动驱动已补源码；预检不加载凭证、不发送渠道请求。JSAPI 创建响应/动作丢失后的原号查询恢复已有源码；支付宝签名回调/原号查证接线已有源码；待补：H5、关闭响应丢失恢复和真实沙箱验收。原指令只允许首次赢家提交，不能在任务重试时重建提交权。新增提交/请求工厂/身份与契约测试源码均未运行。


## JSAPI 创单动作恢复

AlipayPaymentAdapter.recover 复用严格 query：以原 out_trade_no 核验身份、金额、验签状态和 trade_no。仅原方式 JSAPI、WAIT_BUYER_PAY 且原 payableUntil 按秒向下取整后尚未到期，才返回同一 trade_no 的 AlipayJsapiChannelAction。到期沿用首次 CREATE 的原绝对截止时间，重复查询不能延期，也不调用 CREATE/PAY。原付款码或买家输入不参与查询恢复。

TRADE_SUCCESS/FINISHED 只返回真实成功观察；TRADE_CLOSED、NOT_FOUND/其他未知状态不能形成新动作或未收款证明。查询动作不是成功事实，payment 仍在原门禁和生命周期下决定是否持久化/返回。协议映射与重复恢复/参数错配测试源码已补，未执行验证或实际调用。


## 原生签名通知

独立 AlipayNotificationProvider/AlipayNotificationCodec 提供回调识别、验签和固定 ACK；不依赖 payment，也不直接应用资金状态。只支持 UTF-8 form/RSA2。解析拒绝重复字段、非法百分号/UTF-8 和超限收包，全部未知非空字段仍参与验签；签名排除 sign/sign_type。签名之后必须匹配原请求号、签名应用、收款 seller、gross total_amount 与 trade_status_sync 类型。仅凭 TRADE_SUCCESS/TRADE_CLOSED 文本不产生成功或未收款观察。

AlipayCredentialReader.verificationKey 从原版本描述文件读取平台 RSA 公钥，核验 tenant/app/seller；无需解密商户私钥或服务商授权 token。payment 只有验证原绑定成功后才调用原号查询，成功时间仍沿标准查询 send_pay_date，避免回调 gmt_payment 与查单时间口径冲突。回调结果在 Y3 提交后才返回 success，失败返回 failure。

`loadup.pay.channel.alipay.notifications.notify-urls.<tenant>.<bindingRevisionId>` 是可信 HTTPS URL 目录，仅用于 CREATE 附加 notify_url；不接受商户输入的任意通知地址。回调请求中的 tenant/endpoint 不能改变冻结收款账户。完整入口配置见 [payment 说明](../../payment/README.md)。

依据：[官方异步通知](https://developer.alibaba.com/docs/doc.htm?articleId=105301&docType=1&treeId=193)、[官方 V1 RSA2 验签实现](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/internal/util/AlipaySignature.java)、[官方创单 notifyUrl 字段](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/request/AlipayTradeCreateRequest.java)。源码和测试源码未执行构建/测试或真实回调验收，证书模式/其他通知类型未在本批扩展。


## 原退款号恢复（2026-10-24）

独立 recoverRefund 先调用原 alipay.trade.fastpay.refund.query，queryRefund 继续只读。只在原版本验签通过、code=10000、refund_status 未返回，且 trade_no/out_trade_no/out_request_no 三项全部返回并精确匹配原固定退款时，才允许重复原 alipay.trade.refund。若 refund_amount 返回，必须为合法零值或本次原退款金额；total_amount 若返回必须匹配原 gross 总额。金额/身份不符、缺字段、业务错误或显式状态不构成重提许可，REFUND_SUCCESS 沿查询正常收敛。

原 out_request_no 始终存在，原交易、退款金额、账户与凭证版本不变。退款状态缺失不代表失败或未退款，重提依靠支付宝对 out_request_no 的幂等保证。提交即使成功也只受理，查询/提交摘要组合为恢复证据；不凭 refund_fee 累计额或 fund_change 直接认领成功，不生成新退款号。恢复由原生任务调度，不在 HTTP 层自动循环。

依据：[支付宝官方交易退款幂等说明](https://developer.alibaba.com/docs/doc.htm?articleId=106384&docType=1&treeId=327)。源码与测试源码已补，未运行构建、测试或真实退款；未满足上述明确条件的未知结果继续保留占用并等待后续查询/运维处置。
