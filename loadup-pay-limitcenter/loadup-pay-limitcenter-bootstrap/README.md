# LimitCenter 额度 · 启动装配接入

[`LimitCenterApplication`](src/main/java/io/github/loadup/limitcenter/LimitCenterApplication.java) 装配 LimitCenter 额度的 Web、应用和持久化工件，并提供 Spring Boot 启动入口。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-limitcenter-bootstrap`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

以 `local` profile 运行 [`LimitCenterApplication`](src/main/java/io/github/loadup/limitcenter/LimitCenterApplication.java)，本地端口为 `8084`。本地参数见 [application-local.yml](src/main/resources/application-local.yml)。部署时按 [application.yml](src/main/resources/application.yml) 提供 `LIMITCENTER_JDBC_URL`、数据库用户名和密码、部署代码与固定 schema。配置本服务服务令牌、签发 HMAC 执行许可所需的 `loadup.modules.limitcenter.permit` 属性 及到 Payment 的证据查询身份。

打包或部署时按 [服务接入说明](../README.md) 选择依赖和 profile，环境发布流程见 [配置与发布](../../deploy/CONFIGURATION.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
