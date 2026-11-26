# Merchant Center Service

Maven 坐标：`io.github.loadup-cloud:loadup-pay-merchantcenter-service:0.0.1-SNAPSHOT`。

本模块是可复用的 Merchant Center 业务 JAR，包含 domain、app、infrastructure 包，不是独立可执行应用。它提供商户主档、经营状态、关系、逐项动作授权、资格判断和安全商户事实查询；持久化使用本服务商户数据库。调用方通过 `loadup-pay-merchantcenter-client` 中的 Facade 使用服务。

迁移资源位于 `src/main/resources/db/migration/pay/merchantcenter`，由 bootstrap 配置的 Flyway 扫描。运行 HTTP 服务请依赖 `loadup-pay-merchantcenter-bootstrap` 并配置 PostgreSQL、部署 schema 与 JWT 资源服务器；本模块本身不启动 HTTP 端口。

职责和事务边界见 [服务架构](ARCHITECTURE.md)、[Merchant Center 总体架构](../ARCHITECTURE.md)及[Pay 总体架构](../../ARCHITECTURE.md)。
