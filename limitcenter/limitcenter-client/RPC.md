# LimitCenter RPC

本 client 由 limitcenter 服务维护，包含 Java Facade、Command/Query/DTO、结果码，以及 Protobuf IDL
（`.proto`、生成存根和 Wire↔DTO mapper）。IDL 代码位于 `rpc` 子包，与业务契约隔离；domain/app 不直接引用
rpc 类型。

- 内部业务契约：`LimitPolicyFacade`、`LimitReservationFacade`、`LimitExecutionFacade`、`LimitSettlementFacade`、
  `LimitRefundFacade`（`MerchantLimitFacade` 是进程内组合视图）。
- 跨进程 IDL：`limitcenter-client/src/main/proto/limitcenter.proto`，service
  `LimitCenterRpc.Activate/Reserve/Detail/Begin/Verify/Expire/Confirm/Release/RestoreRefund`。

提供方在 `limitcenter-adapter` 实现生成的 `ImplBase` 并标注
`@DubboService(interfaceClass = LimitCenterRpc.class)`；消费方在 `*-infrastructure` 用
`@DubboReference(url = "tri://limitcenter-headless:50051")` 引用生成的 `LimitCenterRpc`。
Wire↔DTO 转换由 `limitcenter-client` 的 `LimitCenterRpcMapper` 负责。通用 channel/JWT 支持见
[RPC 规范](../../design/RPC.md)。
