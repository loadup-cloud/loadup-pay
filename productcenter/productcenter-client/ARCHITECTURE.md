# Product HTTP contracts

client 持有业务 Facade、Command/Query/DTO、错误码及标准 `ProductHttpService`，提供 POST
`/api/internal/v1/product/admissions/authorize` 与 `/detail`。成功为 SuccessResponse<OrderAdmissionDTO>，失败为现有 FailureResponse，均使用 HTTP 200。

product-adapter 的 OrderAdmissionController 调用现有 OrderAdmissionFacade，继续验证原调用主体和权限、幂等创建意图、请求摘要、合约与已激活额度。
acquire-infrastructure 注册标准 HTTP 代理，逐请求传递含 product audience 的商户委托，基地址来自 PRODUCT_BASE_URL。
完整签名/issuer/audience/有效期/token_use 校验由 product Resource Server 执行，不使用 Triple 的 exp-only 内部解析。

原共享 proto、Wire mapper、消息生成与自动装配已删除；应用不再依赖 Dubbo binder 或 nacos profile。跨服务复用原 Java DTO。

迁移状态、序列化与发布验收见 [HTTP 迁移清单](../../HTTP_MIGRATION.md)。
