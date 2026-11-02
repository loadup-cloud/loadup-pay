# Customer HTTP contracts

client 持有 Facade、Command/Query/DTO、结果码和标准声明式接口 `client.http.CustomerHttpService`，不包含 Protobuf、Dubbo 存根或 Wire mapper。

`POST /api/internal/v1/customer/eligibility/authorize` 接收 MerchantActionAuthorizeCommand，成功返回
SuccessResponse<MerchantEligibilityDTO>，失败返回现有 FailureResponse（HTTP 200）。消费端必须核对 result，不能将认证错误解释为无资格。

customer-adapter 的 MerchantEligibilityController 调用 MerchantEligibilityFacade；身份和租户来自 Resource Server 校验后的 JWT。
product-infrastructure 用现有 HttpTemplate.serviceClient 注册标准接口，按服务配置 base-url，逐次传入符合 customer audience 的 bearer。

所有资格事实校验、事务边界与授权语义保留，见 [业务架构](../ARCHITECTURE.md) 和 [迁移清单](../../HTTP_MIGRATION.md)。
