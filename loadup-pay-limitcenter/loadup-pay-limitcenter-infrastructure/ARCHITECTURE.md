# LimitCenter 额度 · 基础设施架构

## 职责与运行边界

[`LimitCenterPersistenceConfiguration`](src/main/java/io/github/loadup/limitcenter/infrastructure/config/LimitCenterPersistenceConfiguration.java)、[`PaymentEvidenceHttpClient`](src/main/java/io/github/loadup/limitcenter/infrastructure/client/PaymentEvidenceHttpClient.java) 属于基础设施边界。通过本地持久化维护策略、额度桶、预占、执行门禁和回执。付款证据端口通过标准内部 HTTP 实现。 本地持久化遵循固定 schema 和 Spring 事务，网络交互遵循应用层定义的接口及失败语义。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
