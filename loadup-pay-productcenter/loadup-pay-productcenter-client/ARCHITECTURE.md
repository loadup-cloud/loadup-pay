# Product HTTP contracts

client 持有业务 Facade、Command/Query/DTO、错误码及标准 `ProductHttpService`，提供 POST
`/api/internal/v1/productcenter/admissions/authorize` 与 `/detail`。成功为 SuccessResponse<OrderAdmissionDTO>，失败为现有 FailureResponse，均使用 HTTP 200。

product-adapter 的 OrderAdmissionController 调用现有 OrderAdmissionFacade，继续验证原调用主体和权限、幂等创建意图、请求摘要、合约与已激活额度。
acquire-infrastructure 注册标准 HTTP 代理，逐请求传递含 productcenter 委托 audience 的商户令牌，目标按应用名约定 http://productcenter:8080。
acquire 的阶段 1A 将原令牌解析移至基础设施 DelegatedCredentialResolver，业务 Actor 不再携带凭证；入站与本目标的 JWT 校验及远端失败分类不变。该解析器不新增本地 audience 检查或机器身份回退。
完整签名/issuer/audience/有效期/token_use 校验由 productcenter Resource Server 执行，不使用 Triple 的 exp-only 内部解析。

原共享 proto、Wire mapper、消息生成与自动装配已删除；应用不再依赖 Dubbo binder 或 nacos profile。消费方 infrastructure 使用提供方 Java 契约并通过 RemoteContractConverter 映射本地模型，app/domain 不直接依赖 client.http 或远端 DTO。

迁移状态、序列化与发布验收见 [HTTP 架构](../../ARCHITECTURE.md)。
