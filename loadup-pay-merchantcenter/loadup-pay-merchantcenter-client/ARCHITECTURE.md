# Merchant Center HTTP 契约

client 持有 Facade、Command/Query/DTO、结果码和标准声明式接口 `client.http.CustomerHttpService`，不包含 Protobuf、Dubbo 存根或 Wire mapper。

`POST /api/internal/v1/merchantcenter/eligibility/authorize` 接收 MerchantActionAuthorizeCommand，成功返回
SuccessResponse<MerchantEligibilityDTO>，失败返回现有 FailureResponse（HTTP 200）。消费端必须核对 result，不能将认证错误解释为无资格。

merchantcenter bootstrap 中 的 MerchantEligibilityController 调用 MerchantEligibilityFacade；主体来自 Resource Server 校验后的 JWT，部署租户来自本地 DatabaseTenant。
productcenter infrastructure 用现有 HttpTemplate.serviceClient 注册标准接口，按服务配置 base-url，逐次传入符合 merchantcenter audience 的 bearer。

所有资格事实校验、事务边界与授权语义保留，见 [业务架构](../ARCHITECTURE.md) 和 [总体架构](../../ARCHITECTURE.md)。

`CustomerHttpService` 是当前代码中保留的 Java 类型名，所属应用为 merchantcenter；它不表示存在独立 customer 服务。
