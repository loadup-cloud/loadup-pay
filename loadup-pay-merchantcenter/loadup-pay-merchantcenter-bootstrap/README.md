# Merchant Center Bootstrap

Maven 坐标：`io.github.loadup-cloud:loadup-pay-merchantcenter-bootstrap:0.0.1-SNAPSHOT`。

可执行应用入口为 `io.github.loadup.merchantcenter.MerchantcenterApplication`。默认 HTTP 端口为 8080；`local` profile 使用 8083。主要配置位于 `src/main/resources/application.yml`，本地覆盖位于 `application-local.yml`。

启动前需提供 PostgreSQL 的 `MERCHANTCENTER_JDBC_URL`、`MERCHANTCENTER_JDBC_USERNAME`、`MERCHANTCENTER_JDBC_PASSWORD`，JWT 验证配置 `JWT_ISSUER_URI`、`JWT_JWK_SET_URI`，以及 `DEPLOYMENT_CODE` 和 `DEPLOYMENT_DATABASE_SCHEMA`。资源服务器 audience 使用应用名 `merchantcenter`。Flyway 仅扫描 `db/migration/pay/merchantcenter`。

应用提供的接口均为 POST JSON：商户管理 `/api/internal/v1/merchantcenter/management/merchants/{create,update,page,detail,status}`、关系和授权管理 `/api/internal/v1/merchantcenter/management/{relationships,grants}/{create,revoke}`、商户摘要 `/api/internal/v1/merchantcenter/profiles/detail`、资格校验 `/api/internal/v1/merchantcenter/eligibility/authorize`。管理和内部接口需按对应权限认证；此应用不提供浏览器管理页面。管理 BFF 的真实 issuer/audience 联调仍需运行验收。

服务职责见 [Merchant Center 架构](../ARCHITECTURE.md)和[bootstrap 架构](ARCHITECTURE.md)。
