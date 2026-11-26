# Acquire 收单 · 客户端契约接入

发布 Acquire 收单的消费方契约。创单和原订单操作的命令、DTO 及 Facade，以及付款结果、费用退款和商户通知事件入口。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-acquire-client`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

通过 [`AcquireOrderCreationFacade`](src/main/java/io/github/loadup/acquire/client/facade/AcquireOrderCreationFacade.java)、[`AcquireHttpService`](src/main/java/io/github/loadup/acquire/client/http/AcquireHttpService.java) 查阅本地 Facade 或声明式 HTTP 契约。跨服务调用方在自己的基础设施中注册 HTTP 代理，为声明的目标提供基础地址和对应认证。服务路径与权限见 [服务接入说明](../README.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
