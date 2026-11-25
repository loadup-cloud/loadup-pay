# LoadUp Components Payment Channel API

支付渠道组件的公开契约：统一 Facade、Provider SPI、不可变请求/结果模型、动作编解码接口和渠道错误码。

```xml
<dependency>
  <groupId>io.github.loadup-cloud</groupId>
  <artifactId>loadup-pay-payment-channel-api</artifactId>
</dependency>
```

## 能力

- `ChannelPaymentFacade`：预检、提交、查单、恢复、关单和退款。
- `ChannelNotificationFacade`：支付/退款异步通知的定位、验签与固定 ACK。
- `ChannelStatementFacade`：官方账单来源读取。
- `ChannelProvider` / `ChannelNotificationProvider` / `ChannelStatementProvider`：厂商实现 SPI。
- `ChannelActionCodec`：客户端动作快照的严格类型编解码门面。
- `ChannelActionTypeCodec`：厂商 binder 实现的单动作编解码 SPI；core 通过注册表分发。
- 全部模型为不可变 `record` 或 sealed interface，敏感字段使用 `@DiagnosticHidden`。

## 边界

- API 不依赖 Spring、数据库、HTTP/KMS 实现或任何 Pay 业务包。
- 厂商类型（支付宝/微信账户身份、JSAPI/H5 动作）属于本组件的表示契约；新增厂商需要同步扩展 API。
- Provider 实例由消费应用在启动模块通过 runtime 依赖装配，不在 API 中注册。

## 基础词汇

渠道、接入模式、支付方式和固定 CNY 金额约束来自平级 `loadup-pay-payment-channel-contracts`。
只共享这些词汇的事件或 DTO 可直接依赖 contracts，不必引入本 API 的 Facade / Provider SPI。
