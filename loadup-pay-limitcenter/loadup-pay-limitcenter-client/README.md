# LimitCenter 额度 · 客户端契约接入

发布 LimitCenter 额度的消费方契约。策略激活、预占、执行许可验证、额度结算及退款恢复的标准契约。发布方用 LimitPolicyHash 计算策略摘要。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-limitcenter-client`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

通过 [`LimitReservationFacade`](src/main/java/io/github/loadup/limitcenter/client/facade/LimitReservationFacade.java)、[`LimitCenterHttpService`](src/main/java/io/github/loadup/limitcenter/client/http/LimitCenterHttpService.java) 查阅本地 Facade 或声明式 HTTP 契约。跨服务调用方在自己的基础设施中注册 HTTP 代理，为声明的目标提供基础地址和对应认证。服务路径与权限见 [服务接入说明](../README.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
