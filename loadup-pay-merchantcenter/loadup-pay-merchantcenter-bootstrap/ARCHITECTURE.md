# Merchant Center Bootstrap 架构

本模块是可执行边界：`MerchantcenterApplication` 启动 Spring Boot，适配器暴露经过认证的 HTTP 入口，并装配可复用的 [Merchant Center Service](../loadup-pay-merchantcenter-service/ARCHITECTURE.md) 与 [Client 契约](../loadup-pay-merchantcenter-client/ARCHITECTURE.md)。应用扫描限制在 Merchant Center 包；Service 通过 Boot auto-configuration imports 被发现，不把 Pay 其他领域包加入扫描范围。

## HTTP 入口

- `MerchantController` 提供商户管理命令与查询。
- `MerchantAuthorizationController` 管理关系和动作授权。
- `MerchantProfileController` 提供供受信服务查询的安全商户摘要。
- `MerchantEligibilityController` 执行商户交易资格校验。

业务 API 使用 JSON 请求和标准成功/失败响应。部署配置要求资源服务器验证 JWT 的签名、issuer、audience 与权限。操作者身份从认证上下文派生；部署标识与 schema 来自本地配置，不信任请求体或任意租户 header。该服务只开放内部 API，不提供公网商户管理界面。

## 启动与持久化装配

`application.yml` 读取 `MERCHANTCENTER_JDBC_*`，启用 Flyway 并将扫描范围限定到 `classpath:db/migration/pay/merchantcenter`，JWT audience 默认为 `spring.application.name`（`merchantcenter`）。`application-local.yml` 配置本地 PostgreSQL 和 8083 端口；默认端口为 8080。就绪探针包含数据库健康状态。

管理 BFF 转发的原操作者令牌仍需由本应用独立验证；issuer/audience 信任关系未通过联调前，不能仅凭 BFF 路由存在认定管理流程可用。更多业务数据与事务边界见[服务架构](../loadup-pay-merchantcenter-service/ARCHITECTURE.md)及[Pay 总体架构](../../ARCHITECTURE.md)。
