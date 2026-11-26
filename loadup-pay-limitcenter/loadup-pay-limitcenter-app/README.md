# LimitCenter 额度 · 应用编排接入

承载 LimitCenter 额度的业务用例。宿主通过已有 Facade 或适配层调用。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-limitcenter-app`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

查阅 [`LimitReservationService`](src/main/java/io/github/loadup/limitcenter/app/service/LimitReservationService.java)、[`LimitExecutionService`](src/main/java/io/github/loadup/limitcenter/app/service/LimitExecutionService.java) 对应的业务接口。接入实现时由服务启动配置提供领域、持久化、身份及必要的远端端口。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
