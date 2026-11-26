# Reconciliation 对账 · 启动装配接入

[`ReconciliationApplication`](src/main/java/io/github/loadup/reconciliation/ReconciliationApplication.java) 装配 Reconciliation 对账的 Web、应用和持久化工件，并提供 Spring Boot 启动入口。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-reconciliation-bootstrap`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

以 `local` profile 运行 [`ReconciliationApplication`](src/main/java/io/github/loadup/reconciliation/ReconciliationApplication.java)，本地端口为 `8087`。本地参数见 [application-local.yml](src/main/resources/application-local.yml)。部署时按 [application.yml](src/main/resources/application.yml) 提供 `RECONCILIATION_JDBC_URL`、数据库用户名和密码、部署代码与固定 schema。配置 Payment HTTP 目标、S3/文件能力、Transfer 扫描与 Task Worker。Flyway 另外纳入 Framework File 和 Transfer 目录。

打包或部署时按 [服务接入说明](../README.md) 选择依赖和 profile，环境发布流程见 [配置与发布](../../deploy/CONFIGURATION.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
