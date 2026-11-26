# Payment 付款 · 启动装配接入

[`PaymentApplication`](src/main/java/io/github/loadup/payment/PaymentApplication.java) 装配 Payment 付款的 Web、应用和持久化工件，并提供 Spring Boot 启动入口。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-payment-bootstrap`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

以 `local` profile 运行 [`PaymentApplication`](src/main/java/io/github/loadup/payment/PaymentApplication.java)，本地端口为 `8081`。本地参数见 [application-local.yml](src/main/resources/application-local.yml)。部署时按 [application.yml](src/main/resources/application.yml) 提供 `PAYMENT_JDBC_URL`、数据库用户名和密码、部署代码与固定 schema。按业务启用渠道 profile，配置渠道身份材料与依赖方地址。服务使用自身数据库 schema 中的业务与任务表。

打包或部署时按 [服务接入说明](../README.md) 选择依赖和 profile，环境发布流程见 [配置与发布](../../deploy/CONFIGURATION.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
