# Reconciliation 对账 · 客户端契约接入

发布 Reconciliation 对账的消费方契约。批次创建、绑定输入、启动、重试与查询契约。导入、账本采集、复核和查证由相关 Facade 提供。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-reconciliation-client`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

通过 [`ReconciliationFacade`](src/main/java/io/github/loadup/reconciliation/client/facade/ReconciliationFacade.java)、[`ReconciliationHttpService`](src/main/java/io/github/loadup/reconciliation/client/http/ReconciliationHttpService.java) 查阅本地 Facade 或声明式 HTTP 契约。跨服务调用方在自己的基础设施中注册 HTTP 代理，为声明的目标提供基础地址和对应认证。服务路径与权限见 [服务接入说明](../README.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
