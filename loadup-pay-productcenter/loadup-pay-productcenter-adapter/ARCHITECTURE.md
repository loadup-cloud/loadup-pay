# Product Center 产品中心 · Web 适配架构

## 职责与运行边界

[`OrderAdmissionController`](src/main/java/io/github/loadup/productcenter/adapter/web/OrderAdmissionController.java)、[`ContractController`](src/main/java/io/github/loadup/productcenter/adapter/web/ContractController.java) 将 HTTP 请求、可信身份和成功或失败报文映射到应用契约。按交易与管理用途划分接口。保留原商户或管理操作者身份，目标服务执行验证与授权。 业务状态与本地事务由应用服务维护。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
