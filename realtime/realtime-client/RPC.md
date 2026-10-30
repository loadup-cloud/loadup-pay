# RPC contracts

本 client 由 realtime 服务维护，包含 Java Facade、Command/Query/DTO、结果码，以及 Protobuf IDL
（`.proto`、生成存根和 Wire↔DTO mapper）。IDL 代码位于 `rpc` 子包，与业务契约隔离；domain/app 不直接引用
rpc 类型。

- 内部业务契约：`TradeProjectionFacade.applyOrder/applyPayment/applyRefund/applyRefundSummary`；
  `MerchantTradeQueryFacade` 只用于商户 HTTP 查询。
- 跨进程 IDL：`realtime-client/src/main/proto/realtime.proto`，service
  `RealtimeRpc.Order/Payment/Refund/RefundSummary`。event 信封由 acquire-client 与 payment-client 的源类型映射，
  `realtime-client` 依赖 `acquire-client` + `payment-client` 复用其 Wire 类型。

提供方在 `realtime-adapter` 实现生成的 `ImplBase` 并标注 `@DubboService(interfaceClass = RealtimeRpc.class)`；
消费方在 `*-infrastructure` 用 `@DubboReference(url = "tri://realtime-headless:50051")` 引用生成的 `RealtimeRpc`。
Wire↔DTO 转换由 `realtime-client` 的 `RealtimeRpcMapper` 负责。通用 channel/JWT 支持见
[RPC 规范](../../design/RPC.md)。
