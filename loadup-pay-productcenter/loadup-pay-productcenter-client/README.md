# Product Center 产品中心 · 客户端契约接入

发布 Product Center 产品中心的消费方契约。订单准入与条款结果的契约。ProductManagementHttpService 提供目录、合约及策略发布的管理接口。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-productcenter-client`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

通过 [`OrderAdmissionFacade`](src/main/java/io/github/loadup/productcenter/client/facade/OrderAdmissionFacade.java)、[`ProductHttpService`](src/main/java/io/github/loadup/productcenter/client/http/ProductHttpService.java) 查阅本地 Facade 或声明式 HTTP 契约。跨服务调用方在自己的基础设施中注册 HTTP 代理，为声明的目标提供基础地址和对应认证。服务路径与权限见 [服务接入说明](../README.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
