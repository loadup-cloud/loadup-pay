# LoadUp Components Payment Channel Core

支付渠道组件的默认 Facade 与路由引擎。

```xml
<dependency>
  <groupId>io.github.loadup-cloud</groupId>
  <artifactId>loadup-pay-payment-channel-core</artifactId>
</dependency>
```

## 装配

`PaymentChannelAutoConfiguration` 提供：

- `ChannelPaymentFacade` / `ChannelNotificationFacade` / `ChannelStatementFacade`
- `ChannelActionCodec`（`RegistryChannelActionCodec`，收集 binder 的 `ChannelActionTypeCodec`）
- `ChannelRecoveryConcurrency`

Spring 收集 `ChannelProvider` 等策略并构造不可变注册表；同一渠道重复注册在启动时失败。缺少 Provider 或能力时返回
`CHANNEL_UNSUPPORTED_CAPABILITY`，不回退到其他渠道。binder 只注册 Provider/codec Bean，不声明跨模块 `afterName`；core 通过
构造器依赖 `ResilienceRegistries`，同样不依赖自动配置处理顺序。

## 运行要求

- 应用提供 `ResilienceRegistries`；后者由 `loadup-components-resilience4j-binder-core` 装配。厂商 codec 自行构造注入
  `JsonMapper`。
- 恢复/查询/关单/退款查证按渠道共享 Bulkhead 预算；`max-wait-duration` 必须为 0。
- Facade 拒绝在本地事务内调用；真实渠道调用必须位于事务外。
- `ChannelExecutionContext` 只携带不可变渠道绑定；请求入口校验部署 code，不建立租户上下文，也不做租户比对。
