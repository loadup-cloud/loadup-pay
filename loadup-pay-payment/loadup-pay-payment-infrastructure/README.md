# Payment 付款 · 基础设施接入

提供 Payment 付款的数据库与远端系统适配。生产或本地集成建议沿用服务已有的启动装配。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-payment-infrastructure`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

由启动模块组装 [`PaymentPersistenceConfiguration`](src/main/java/io/github/loadup/payment/infrastructure/config/PaymentPersistenceConfiguration.java)、[`PaymentLimitHttpClient`](src/main/java/io/github/loadup/payment/infrastructure/client/PaymentLimitHttpClient.java) 等适配器。按 [服务配置](../README.md) 启用该应用的 Flyway 目录，设置固定 schema，并为外部调用配置地址和身份。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
