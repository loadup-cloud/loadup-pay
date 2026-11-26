# Realtime Service

Maven 坐标：`io.github.loadup-cloud:loadup-pay-realtime-service:0.0.1-SNAPSHOT`。

本模块是 Realtime 的可复用业务 JAR，提供订单、付款、退款及退款汇总投影的版本校验、写入和商户读模型查询。它包含 domain、app、infrastructure 包，不是独立可执行服务；HTTP 入口由 `loadup-pay-realtime-bootstrap` 提供，跨模块契约由 `loadup-pay-realtime-client` 提供。

投影与接收回执使用本服务 PostgreSQL 数据源并在本地事务内提交。Flyway 迁移位于 `src/main/resources/db/migration/pay/realtime`，由 bootstrap 启动配置扫描。集成时需为 bootstrap 配置 `REALTIME_JDBC_URL/USERNAME/PASSWORD`、schema、部署标识和 JWT 资源服务器参数。

服务职责、版本和事务规则见 [Realtime 服务架构](ARCHITECTURE.md)、[bootstrap 架构](../loadup-pay-realtime-bootstrap/ARCHITECTURE.md)及[Pay 总体架构](../../ARCHITECTURE.md)。
