# LimitCenter 额度 · Web 适配接入

提供 LimitCenter 额度的 Web 适配。公开入口和授权方式见 [服务 API](../README.md)。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-limitcenter-adapter`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

宿主按自己的安全链装配此工件。[`LimitCenterController`](src/main/java/io/github/loadup/limitcenter/adapter/web/LimitCenterController.java)、[`LimitCenterSecurityConfiguration`](src/main/java/io/github/loadup/limitcenter/adapter/security/LimitCenterSecurityConfiguration.java) 是典型入口，路由包含 `/api/internal/v1/limitcenter/operations`。统一处理九个内部 POST，由资源端验证服务令牌，再按操作检查 scope 与可信主体。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
