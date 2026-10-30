# RPC contracts

本 client 由 payment 服务维护，包含 Java Facade、Command/Query/DTO、结果码，以及 Protobuf IDL
（`.proto`、生成存根和 Wire↔DTO mapper）。IDL 代码位于 `rpc` 子包，与业务契约隔离；domain/app 不直接引用
rpc 类型。

- 内部业务契约：`PaymentInputFacade`、`PaymentAcceptanceFacade`、`PaymentOutcomeFacade`、
  `PaymentSettlementFactsFacade`、`PaymentControlFacade`、`PaymentAttemptFacade`、`RefundSummaryFactsFacade`、
  `RefundSettlementFactsFacade`、`PaymentCaptureFacade`、`PaymentStatementFacade`、
  `PaymentVerificationFacade`。`RefundFacade`、`ChannelBindingFacade`、`RoutingProfileFacade` 和渠道回调接口
  只用于 HTTP/渠道入口。
- 跨进程 IDL：`payment-client/src/main/proto/` 下的 4 个文件：
    - `payment.proto` → `PaymentRpc`（InputPrepare/InputLookup/InputExpire/Accept/Detail/Outcome/Facts/Close/
      FenceParent/ParentFenceStatus/StartAttempt/RefundSummary/RefundFacts）
    - `payment_reconciliation.proto` → `PaymentCaptureRpc.Create/Retry/Detail/Lines`
    - `payment_statement.proto` → `PaymentStatementRpc.Download`
    - `payment_verification.proto` → `PaymentVerificationRpc.Verify`

提供方在 `payment-adapter` 为每个 proto service 提供一个 `@DubboService` 实现类；消费方在 `*-infrastructure` 用
`@DubboReference(url = "tri://payment-headless:50051")` 引用对应生成的接口。
`PaymentStatementRpc` 的引用超时为 60 秒，其余按全局 3 秒。Wire↔DTO 转换由 `payment-client` 的 mapper 负责。
`payment-client` 依赖 `product-client` 以复用 AdmittedTerms 的 Wire 映射。通用 channel/JWT 支持见
[RPC 规范](../../design/RPC.md)。
