# Product HTTP contracts

client 持有业务 Facade、Command/Query/DTO、错误码及标准 `ProductHttpService`，提供 POST
`/api/internal/v1/product/admissions/authorize` 与 `/detail`。成功为 SuccessResponse<OrderAdmissionDTO>，失败为现有 FailureResponse，均使用 HTTP 200。

product-adapter 的 OrderAdmissionController 调用现有 OrderAdmissionFacade，继续验证原调用主体和权限、幂等创建意图、请求摘要、合约与已激活额度。
acquire-infrastructure 注册标准 HTTP 代理，逐请求传递含 product audience 的商户委托，基地址来自 PRODUCT_BASE_URL。
完整签名/issuer/audience/有效期/token_use 校验由 product Resource Server 执行，不使用 Triple 的 exp-only 内部解析。

过渡期 product.proto 仅保留 payment.proto 所需的共享 Wire 消息，ProductRpc service 已删除；ProductRpcMapper、消息生成及其自动装配
随 payment 的条款迁移一起移除。product 运行装配已移除 Dubbo binder 与 Nacos profile，不能在共享消息仍存在时宣称 client 的 Protobuf 清理完成。

迁移状态、序列化与发布验收见 [HTTP 迁移清单](../../HTTP_MIGRATION.md)。
