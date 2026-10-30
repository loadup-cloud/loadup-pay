# RPC contracts

本 client 由 customer 服务维护，包含 Java Facade、Command/Query/DTO、结果码，以及 Protobuf IDL
（`.proto`、生成存根和 Wire↔DTO mapper）。IDL 代码位于 `rpc` 子包，与业务契约隔离；domain/app 不直接引用
rpc 类型。

- 内部业务契约：`MerchantEligibilityFacade.authorize`；`MerchantAuthorizationFacade` 只用于 HTTP 管理接口。
- 跨进程 IDL：`customer-client/src/main/proto/customer.proto`，service `CustomerRpc.Authorize`，由
  `dubbo-maven-plugin` 生成消息和 `DubboCustomerRpcTriple` 存根。

提供方在 `customer-adapter` 实现生成的 `ImplBase` 并标注 `@DubboService(interfaceClass = CustomerRpc.class)`；
消费方在 `*-infrastructure` 用 `@DubboReference(url = "tri://customer-headless:50051")` 引用生成的 `CustomerRpc`。
Wire↔DTO 转换由 `customer-client` 的 `CustomerRpcMapper` 负责。通用 channel/JWT 支持见
[RPC 规范](../../design/RPC.md)。
