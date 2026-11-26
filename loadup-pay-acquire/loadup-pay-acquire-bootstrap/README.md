# Acquire 收单 · 启动装配接入

[`AcquireApplication`](src/main/java/io/github/loadup/acquire/AcquireApplication.java) 装配 Acquire 收单的 Web、应用和持久化工件，并提供 Spring Boot 启动入口。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-acquire-bootstrap`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

以 `local` profile 运行 [`AcquireApplication`](src/main/java/io/github/loadup/acquire/AcquireApplication.java)，本地端口为 `8082`。本地参数见 [application-local.yml](src/main/resources/application-local.yml)。部署时按 [application.yml](src/main/resources/application.yml) 提供 `ACQUIRE_JDBC_URL`、数据库用户名和密码、部署代码与固定 schema。需对齐 productcenter、payment 和 realtime 目标。签发与校验托管收银台会话所需的密钥由宿主提供。

打包或部署时按 [服务接入说明](../README.md) 选择依赖和 profile，环境发布流程见 [配置与发布](../../deploy/CONFIGURATION.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
