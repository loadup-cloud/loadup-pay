# LimitCenter 额度 · Web 适配架构

## 职责与运行边界

[`LimitCenterController`](src/main/java/io/github/loadup/limitcenter/adapter/web/LimitCenterController.java)、[`LimitCenterSecurityConfiguration`](src/main/java/io/github/loadup/limitcenter/adapter/security/LimitCenterSecurityConfiguration.java) 将 HTTP 请求、可信身份和成功或失败报文映射到应用契约。统一处理九个内部 POST，由资源端验证服务令牌，再按操作检查 scope 与可信主体。 业务状态与本地事务由应用服务维护。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
