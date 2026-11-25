# Payment Channel API Architecture

## 契约分层

```text
facade/      业务入口：payment / notification / statement
spi/         厂商策略：ChannelProvider 等
model/       不可变请求、结果、身份、动作与通知输入
codec/       动作快照门面 ChannelActionCodec + 厂商实现 SPI ChannelActionTypeCodec
error/       渠道 ResultCode
```

## 关键约束

- Facade 入参包含可信 `ChannelExecutionContext`（租户 + 不可变绑定 revision），调用方不能传入任意渠道地址或密钥路径。
- 资金结果必须匹配原身份、原金额和原请求号；NOT_FOUND、超时和未验签响应不能证明未收款。
- `PaymentObservation` / `RefundObservation` 只表达验签后的渠道事实，不包含 HTTP 绑定。
- `ChannelPaymentAction` 只表达付款人交互动作，不是资金成功证据。
- API 源码门禁禁止 Spring、数据库和 `io.github.loadup.pay.*` 依赖。

## 基础词汇

渠道、接入模式、支付方式和固定 CNY 金额约束来自平级 `loadup-pay-payment-channel-contracts`。
只共享这些词汇的事件或 DTO 可直接依赖 contracts，不必引入本 API 的 Facade / Provider SPI。
