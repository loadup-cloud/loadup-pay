# Acquire 收单 · 应用编排接入

承载 Acquire 收单的业务用例。宿主通过已有 Facade 或适配层调用。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-acquire-app`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

查阅 [`OrderCreationService`](src/main/java/io/github/loadup/acquire/app/service/OrderCreationService.java)、[`PaymentInitiationService`](src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationService.java) 对应的业务接口。接入实现时由服务启动配置提供领域、持久化、身份及必要的远端端口。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
