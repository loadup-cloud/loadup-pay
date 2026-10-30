# RPC contracts

本 client 由 product 服务维护，包含 Java Facade、Command/Query/DTO、结果码，以及 Protobuf IDL
（`.proto`、生成存根和 Wire↔DTO mapper）。IDL 代码位于 `rpc` 子包，与业务契约隔离；domain/app 不直接引用
rpc 类型。

- 内部业务契约：`OrderAdmissionFacade.authorize/detail`；`LimitPolicyPublicationFacade` 只用于运营 HTTP 接口。
- 跨进程 IDL：`product-client/src/main/proto/product.proto`，service `ProductRpc.Authorize/Detail`，由
  `dubbo-maven-plugin` 生成消息和 `DubboProductRpcTriple` 存根。

提供方在 `product-adapter` 实现生成的 `ImplBase` 并标注 `@DubboService(interfaceClass = ProductRpc.class)`；
消费方在 `*-infrastructure` 用 `@DubboReference(url = "tri://product-headless:50051")` 引用生成的 `ProductRpc`。
Wire↔DTO 转换由 `product-client` 的 `ProductRpcMapper` 负责。通用 channel/JWT 支持见
[RPC 规范](../../design/RPC.md)。
