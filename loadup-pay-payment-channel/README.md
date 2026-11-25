# 支付渠道组件

Pay 的支付渠道模块面向收单场景，定义统一契约并将调用路由到独立渠道 binder。消费应用拥有业务状态、商户路由、持久化和资金证据；binder
拥有厂商协议和凭证。共享词汇与金额策略由独立的 `loadup-pay-payment-channel-contracts` 提供。

## 模块与能力

| Maven 模块 | 职责 | 能力与限制 |
|---|---|---|
| `loadup-pay-payment-channel-contracts` | Channel、AccessMode、PaymentMethod、金额等共享词汇 | 多模块共享类型 |
| `loadup-pay-payment-channel-api` | Facade、Provider SPI、不可变请求/结果、绑定契约、动作编解码 SPI | 不依赖 Spring、数据库或消费应用业务包 |
| `loadup-pay-payment-channel-core` | Facade 实现、Provider 注册表、结果核验、动作 codec 注册表 | 重复 Provider/kind/type 明确失败 |
| `loadup-pay-payment-channel-alipay` | 支付宝协议、验签、凭证及支付/退款/通知适配 | 服务商 H5 未支持；账单下载、解析与统一来源未完成 |
| `loadup-pay-payment-channel-wechat` | 微信协议、验签、凭证及支付/退款/通知适配 | 付款码撤销补偿未完成；账单来源和消费应用接线待验收 |

先导入 `loadup-pay-bom`。只共享枚举和金额策略时引入 `-contracts`。执行渠道调用的模块依赖 `-api`，应用 bootstrap
以 runtime 依赖装配 core 与所需 binder。

```xml
<dependency>
  <groupId>io.github.loadup-cloud</groupId>
  <artifactId>loadup-pay-payment-channel-api</artifactId>
</dependency>
```

## Facade 操作

`ChannelPaymentFacade` 提供预检、支付提交/查询/恢复/关闭，以及退款能力检查、提交/查询/恢复。Facade 根据执行上下文中的不可变渠道和绑定身份
路由；支付和退款查询/恢复必须沿用原请求身份及金额。

- `validate` 只做本地能力与输入检查，不调用渠道，也不授权提交。
- `submit` 表示一次原始支付请求，结果将受理观察与可选的客户唤起动作分开表达。
- `query`、`recover`、`close` 针对原渠道、账户和支付身份；`recover` 不创建新支付。
- `canRefund` 是本地能力查询。退款提交/查询/恢复保留原退款身份和金额；仅在渠道幂等与核验条件满足时才允许恢复原请求。
- 网络调用必须位于消费应用数据库事务之外。

## 新增渠道

新增 `loadup-pay-payment-channel-{vendor}` binder，依赖 API 和所需 LoadUp 组件。实现 `ChannelProvider`，并为每种动作类型注册一个
`ChannelActionTypeCodec` Bean，通过 binder 自身的 Spring Boot auto-configuration 装配。Core 收集 Provider 与 codec 并拒绝重复注册；应用
bootstrap 负责引入 core 和 binder。Core 不维护厂商选路 switch。

Provider 必须验证厂商响应，并保持原身份与金额。未注册或不支持的能力要明确失败；未知结果保持 UNKNOWN，不能换渠道、账户或请求身份来恢复。

## 交易约束与验收边界

- 消费应用先校验合约和商户绑定，并冻结 channel、method、binding revision 与账户作用域；调用方不能直接指定密钥或绑定版本。
- 查询、关闭、退款和恢复使用原绑定，不因当前商户路由变化而重定向在途交易。
- Provider 异常或网络超时不能视为未支付，也不能触发新身份重提；只允许在原请求和渠道幂等条件下恢复。
- 并发恢复预算（如消费应用配置）按渠道、按 JVM 生效，不是集群配额或请求速率限制。
- 源码接入或单元测试源码不等于真实渠道及资金链路验收通过。真实渠道调用、签名回调、资金状态收敛和账单来源需分别验收。

各 binder 的具体操作能力见对应 README；架构边界见[架构文档](ARCHITECTURE.md)。
