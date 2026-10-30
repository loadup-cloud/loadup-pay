# RPC contracts

本 client 由 reconciliation 服务维护，包含 Java Facade、Command/Query/DTO、结果码，以及 Protobuf IDL
（`.proto`、生成存根和 Wire↔DTO mapper）。IDL 代码位于 `rpc` 子包，与业务契约隔离；domain/app 不直接引用
rpc 类型。

- 内部业务契约：`ReconciliationFacade.create/attach/start/retry/detail/task/differences`；
  `ReconciliationImportFacade`、`ReconciliationLedgerFacade`、`ReconciliationReviewFacade`、
  `ReconciliationStatementFacade`、`ReconciliationVerificationFacade` 只用于 HTTP 入口。
- 跨进程 IDL：`reconciliation-client/src/main/proto/reconciliation.proto`，service
  `ReconciliationRpc.Create/Attach/Start/Retry/Task/Detail`。`differences` 是只读运营查询，随 HTTP 暴露，
  不进入 proto。

提供方在 `reconciliation-adapter` 实现生成的 `ImplBase` 并标注
`@DubboService(interfaceClass = ReconciliationRpc.class)`；消费方在 `*-infrastructure` 用
`@DubboReference(url = "tri://reconciliation-headless:50051")` 引用生成的 `ReconciliationRpc`。
Wire↔DTO 转换由 `reconciliation-client` 的 `ReconciliationRpcMapper` 负责。通用 channel/JWT 支持见
[RPC 规范](../../design/RPC.md)。
