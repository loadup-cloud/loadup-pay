# Merchant Center Client

Maven 坐标：`io.github.loadup-cloud:loadup-pay-merchantcenter-client:0.0.1-SNAPSHOT`。

本模块为 Merchant Center 提供可供应用层调用的 Facade、命令、查询、DTO、结果码和 HTTP Service 契约。需要商户主档、关系、动作授权或资格校验的调用方依赖本模块；运行时 HTTP 代理由调用方应用装配，需配置 Merchant Center 服务地址，并使用 audience 为 `merchantcenter`、权限符合目标端点要求的访问令牌。

管理契约定义在 `MerchantManagementHttpService`，资格校验定义在 `CustomerHttpService`，安全商户摘要查询定义在 `MerchantProfileHttpService`。`CustomerHttpService` 是保留的 Java 类型名，不代表独立 customer 服务。

请求响应与安全约束见 [客户端契约架构](ARCHITECTURE.md)、[Merchant Center 服务架构](../ARCHITECTURE.md)和[Pay 总体架构](../../ARCHITECTURE.md)。
