# Alipay Binder Architecture

## 协议边界

- OpenAPI v2 form 请求，RSA2 签名；私钥通过冻结的 KMS key/version 解析。
- 响应先验签原始节点字节，再按操作严格解析；未知非空字段参与通知验签。
- 凭证与授权按 `reference -> version -> absolute path` 映射读取，内容不可变。

## 适配器

| 适配器 | 职责 |
|---|---|
| `AlipaySubmitAdapter` | 付款码/JSAPI/H5 提交、本地动作构造 |
| `AlipayPaymentAdapter` | 原号查询、关闭、结果核验 |
| `AlipayRefundAdapter` | 退款提交/查询、原号恢复 |
| `AlipayNotificationProvider` | 通知识别、验签、原账户/金额核验与固定 ACK |

## 边界

binder 不读取消费应用数据库，不决定支付/退款终态；所有资金事实必须由 payment 的原状态机和查询收敛流程应用。
服务商 H5 授权承载、证书模式和真实渠道验收仍未完成。
