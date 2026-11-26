# Acquire 收单 · 应用编排架构

## 职责与运行边界

[`OrderCreationService`](src/main/java/io/github/loadup/acquire/app/service/OrderCreationService.java)、[`PaymentInitiationService`](src/main/java/io/github/loadup/acquire/app/service/PaymentInitiationService.java) 组合领域规则和 Gateway。先登记原创单意图，再经远端准入落单。发起付款时保留原占用和 actionKey，远端调用独立于本地写事务。 HTTP 报文与具体 JDBC 适配分别由外层模块处理。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
