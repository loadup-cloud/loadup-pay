# LoadUp Components Payment Channel Contracts

渠道组件与消费方共享的轻量契约，包名为 `io.github.loadup.components.payment.channel.contract`。

```xml
<dependency>
    <groupId>io.github.loadup-cloud</groupId>
    <artifactId>loadup-pay-payment-channel-contracts</artifactId>
</dependency>
```

| 类型 | 职责 |
|---|---|
| `Channel` | ALIPAY、WECHAT、UNIONPAY 渠道词汇 |
| `AccessMode` | DIRECT、SERVICE_PROVIDER 接入模式 |
| `PaymentMethod` | PAYMENT_CODE、JSAPI、H5 交互方式 |
| `ChannelAmounts` | 校验非负 CNY 金额，保持领域失败原因与最小单位精度 |

本制品不包含 Facade、Provider SPI、Spring 装配、厂商协议或 Pay 类型。只共享渠道词汇的 DTO、事件与领域数据可以独立消费。
调用渠道操作时另外引入 `loadup-pay-payment-channel-api`。

枚举常量、JSON 表示和金额校验行为保持不变。金额策略回归入口为 `ChannelAmountsTest`；实际运行结果需按当前版本记录。
