# Realtime Bootstrap

Maven 坐标：`io.github.loadup-cloud:loadup-pay-realtime-bootstrap:0.0.1-SNAPSHOT`。

可执行入口为 `io.github.loadup.realtime.RealtimeApplication`。默认 HTTP 端口为 8080；`local` profile 使用 8086。主配置为 `src/main/resources/application.yml`，本地配置为 `application-local.yml`。

启动需提供 `REALTIME_JDBC_URL`、`REALTIME_JDBC_USERNAME`、`REALTIME_JDBC_PASSWORD`、`JWT_ISSUER_URI`、`JWT_JWK_SET_URI`、`DEPLOYMENT_CODE` 和 `DEPLOYMENT_DATABASE_SCHEMA`。入站 audience 默认取应用名 `realtime`。Flyway 仅扫描 `db/migration/pay/realtime`。`REALTIME_ACQUIRE_SERVICE_ID` 和 `REALTIME_PAYMENT_SERVICE_ID` 默认分别为 `acquire`、`payment`，用于限制投影生产方身份。

内部投影 POST 接口：`/api/internal/v1/realtime/projections/{order,payment,refund,refund-summary}`；商户读接口：`/api/realtime/trades/{detail,orders,payments,refunds}`。投影方使用服务凭证，不转发商户令牌；查询使用真实认证商户主体。此读模型不提供资金裁定或可付余额。

运行和 HTTP 边界见 [bootstrap 架构](ARCHITECTURE.md)、[Realtime 服务架构](../loadup-pay-realtime-service/ARCHITECTURE.md)及[Pay 总体架构](../../ARCHITECTURE.md)。
