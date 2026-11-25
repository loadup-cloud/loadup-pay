# WeChat Binder Architecture

## 协议边界

- APIv3：最终 URI/请求字节签名、原始响应字节验签、平台公钥标识固定、严格 JSON 和时间窗校验。
- V2 付款码：HMAC/严格 XML、独立版本密钥和命名 HTTP 配置。
- 凭证使用冻结的商户 KMS key/version 与可信目录描述文件。

## 适配器

| 适配器 | 职责 |
|---|---|
| `WechatSubmitAdapter` | JSAPI/H5 下单、唤起签名与动作构造 |
| `WechatPaymentAdapter` | 原号查询/恢复/关闭 |
| `WechatRefundAdapter` | 直连/服务商退款提交、查询与有限恢复 |
| `WechatCodepayAdapter` | V2 付款码首提与原号查证 |
| `WechatNotificationProvider` | 支付/退款通知验签、解密与 ACK |
| `WechatStatementProvider` | 官方 ALL 账单申请、签名下载与 SHA1 核验 |

## 边界

binder 不依赖 `payment-channel-core` 或消费应用类型；不把预支付受理、退款受理当资金成功。付款码撤销补偿、异常退款处置和
真实渠道验收仍未完成。
