# 微信付款码支付

更新：2026-10-27。本页描述新增源码，不代表编译、测试或渠道验收通过。

## 协议选择

[微信当前 V3 付款码入口](https://pay.weixin.qq.com/doc/v3/merchant/4012647301.md)仍指向 V2。
采用官方 [直连付款码](https://pay.weixin.qq.com/doc/v2/merchant/4011937125.md) 与
[服务商付款码](https://pay.weixin.qq.com/doc/v2/partner/4011941293.md)：POST `/pay/micropay`，UTF-8 XML，
HMAC-SHA256，独立 APIv2 密钥。未根据第三方 SDK 或社区文章猜测 V3 codepay 路径。

保留扫码枪输入与商户纯 API；不增加摄像头扫码 UI，不引入用户资源，不生成 Native 收款二维码。
上游仍传 PAYMENT_CODE 及原安全输入票据。商户 API、DirectPay 和 Checkout 份额支付共用现有首提权限与固定金额规则。

## 模块与路由

WechatChannelProvider 在微信插件内按支付方式交给 WechatCodepayAdapter 或原 V3 JSAPI/H5 适配器；
统一 Facade 的渠道策略不增加支付宝/微信分支。payment 只保存原绑定和执行状态，不拼 XML 或解释微信错误字段。

WechatCodepayAdapter 在 Y2 前检查原 requestNo、整数分金额、127 字节商品描述、18 位微信付款码、
原截止时间、原绑定 PAYMENT_CODE 能力与可信服务端 IP。V2 不支持辅助平面四字节字符，标题提前拒绝。
不加载密钥、不调网络的预检不会产生提交权；只有原 Y2 赢家进行一次真实首提。

参数固定原 appid/mch_id；服务商模式增加冻结 sub_mch_id，可带原 sub_appid。
`total_fee` 是本次固定份额，不用优惠后实付金额替代；最大 Integer.MAX_VALUE 分。
`time_expire` 为原绝对期限按秒向下取整，再按微信协议 GMT+8 编码为 yyyyMMddHHmmss。
这不修改 JVM、Clock 或数据库系统本地时区。nonce 仅为协议随机数，不是新的业务请求号。

## 密钥、XML 和 HTTP

冻结的 WechatCredentialDocument 增加可选 apiV2Key（KmsCiphertext）。它与 apiV3Key 分开，
不存在时拒绝，不回退到 APIv3 密钥或当前最新版。沿原 reference/version 挂载路径及租户/app/商户校验读取，
使用原密文 keyName/version 经 KmsTemplate 解密；调用结束或异常时清除得到的可变字节。
描述文件仍包含现有 V3 查证/退款所需签名与平台公钥资料。

WechatV2Codec 按[官方签名规则](https://pay.weixin.qq.com/doc/v2/merchant/4011985891.md)排序非空原值、
附加 APIv2 key，以 HMAC-SHA256 输出大写摘要；不用 URL 编码后的字符串，不降级 MD5。
响应包含的扩展字段也参与验签。响应未带 sign_type 时仍使用原请求选择的 HMAC；明确不同算法则拒绝。
无签名的通信失败不是资金证据。

XML 限定 UTF-8、64 KiB、128 个字段、单层 xml 根；拒绝 DTD、外部实体、命名空间、属性、重复字段、
嵌套元素、其他编码、非法 UTF-8、第二根节点与未知结构。预定义字符转义还原后再验签，不把 XML 文本当作脚本执行。
字段、XML、密文和付款码不进入诊断 toString；临时签名消息中的 key 字节清除。

复用 HttpTemplate 的命名客户端 `wechat-v2` 和操作 `micropay`，运行 profile 已补配置源码。
API 不需要双向证书。没有传输重试、重定向或请求号替换；URL 来自可信配置，不从商户参数生成。
已有 V3 JSAPI/H5/查询/退款继续使用 `wechat`，不把 V2 密钥放进 Authorization 或浏览器。

调用服务器 IP 由可信配置按租户/不可变绑定提供，不使用付款人 IP 或转发头：

```yaml
loadup:
  pay:
    channel:
      wechat:
        codepay:
          server-ips:
            tenant-example:
              binding-revision-example: 203.0.113.10
```

这里只是文档示例，不设置真实出网 IP、修改 Secret、签约渠道或部署。IP 只解析字面值，不做 DNS 查询。
默认目录为空，未配置时预检拒绝付款码；不影响原 JSAPI/H5 路径。

## 观察、查证与关闭

签名验证后继续核对 app/商户/子商户、可返回的原请求号/金额/币种。
USERPAYING 对应 ACCEPTED，不是成功；其他不明业务错误保持 UNKNOWN，不能据此释放额度或 acquire 占用。
成功提交还需核验 MICROPAY、原请求号、标价金额和交易号，再经既有 V3 原号查询取得统一 success_time；
查询交易号与首提不一致时拒绝。原 V3 订单查询文档明确列出 MICROPAY，
见[普通商户查询响应](https://pay.weixin.qq.com/doc/v3/merchant/4012791858)。

查询与恢复不读取或再次使用付款码，不重新调用 micropay，不重新发放 Y2 提交权。
初次 HTTP/验签/本地提交结果丢失由原恢复任务查证；NOT_FOUND、超时、任务耗尽仍不是未收款证据。
V2 付款码不依赖新建 V3 支付通知地址，沿原后台查询与人工异常目录收敛。

WechatPaymentAdapter 在原验证查询后，只允许已知 JSAPI/MWEB 产品调用 V3 未付款关闭。
MICROPAY 或未知/缺失产品只返回查询事实，不发送不适用的 close，也不输出 NO_COLLECTION。
付款码 close/recovery 的成功观察仍可经原 Y3 记账，不因商户关闭请求丢弃真实收款事实。

## 未完成的撤销补偿

官方付款码要求超时查证后进行撤销；撤销对已成功交易可能自动退回资金，并要求双向证书。
这与“未付款关闭”的语义不同。当前未实现该操作、补偿账目、凭证与失 ACK 查证，不能声称付款码完整闭环或已可投产。
需补独立持久化撤销意图/操作证据、原商户 mTLS 证书选择、重试/撤销结果及实际退回资金核对，
再衔接独立退款/补偿模型。不能直接把撤销 SUCCESS 或 REVOKED 状态当作“从未收款”，
也不能绕过退款表影响成功付款金额。

因此待撤销付款可能继续停留在 UNKNOWN/CLOSING 并保留容量，进入现有查证/人工处置；
这是真实未完成项，没有通过释放占用或伪造终态掩盖。

## 测试源码

已补公开 HMAC 向量、带扩展字段 XML、转义/篡改/非法结构、缺失算法与 MD5 拒绝、
V2 密钥独立读取/异常擦除、单次 HTTP、直连/服务商固定身份与原金额、USERPAYING 与不明错误、
预检拒绝、提交与查询交易号不一致、MICROPAY/未知产品不调用 V3 close。

所有测试仅为源码，未运行；没有构建、真实 HTTP、KMS、渠道、数据库或部署验收。
