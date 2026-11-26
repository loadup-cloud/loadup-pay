# Product Center 产品中心 · 应用编排架构

## 职责与运行边界

[`OrderAdmissionService`](src/main/java/io/github/loadup/productcenter/app/service/OrderAdmissionService.java)、[`PolicyPublicationService`](src/main/java/io/github/loadup/productcenter/app/service/PolicyPublicationService.java) 组合领域规则和 Gateway。在应用层组合目录、收款方资格、合约与额度策略，计算准入结果。初始额度发布复用持久化任务和原发布身份。 HTTP 报文与具体 JDBC 适配分别由外层模块处理。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
