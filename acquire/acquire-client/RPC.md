# RPC contracts

本 client 由 acquire 服务维护，包含 Java Facade、Command/Query/DTO、结果码，以及 Protobuf IDL
（`.proto`、生成存根和 Wire↔DTO mapper）。IDL 代码位于 `rpc` 子包，与业务契约隔离；domain/app 不直接引用
rpc 类型。

- 内部业务契约：`PaymentOutcomeFacade`、`FeeRefundOutcomeFacade`、`RefundNotificationFacade`。
  `AcquireOrderFacade`、`CashierFacade`、`CheckoutSessionFacade` 等只用于商户/运营 HTTP 与收银台入口。
- 跨进程 IDL：`acquire-client/src/main/proto/acquire.proto`，service
  `AcquireRpc.PaymentOutcome/FeeRefund/RefundNotification`。event 信封由 payment-client 的源类型映射，
  `acquire-client` 依赖 `payment-client` 复用其 Wire 类型。

提供方在 `acquire-adapter` 实现生成的 `ImplBase` 并标注 `@DubboService(interfaceClass = AcquireRpc.class)`；
消费方在 `*-infrastructure` 用 `@DubboReference(url = "tri://acquire-headless:50051")` 引用生成的 `AcquireRpc`。
Wire↔DTO 转换由 `acquire-client` 的 `AcquireRpcMapper` 负责。通用 channel/JWT 支持见
[RPC 规范](../../design/RPC.md)。
