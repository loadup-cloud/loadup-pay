# Product Center 产品中心 · Web 适配接入

提供 Product Center 产品中心的 Web 适配。公开入口和授权方式见 [服务 API](../README.md)。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-productcenter-adapter`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

宿主按自己的安全链装配此工件。[`OrderAdmissionController`](src/main/java/io/github/loadup/productcenter/adapter/web/OrderAdmissionController.java)、[`ContractController`](src/main/java/io/github/loadup/productcenter/adapter/web/ContractController.java) 是典型入口，路由包含 `/api/internal/v1/productcenter`。按交易与管理用途划分接口。保留原商户或管理操作者身份，目标服务执行验证与授权。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
