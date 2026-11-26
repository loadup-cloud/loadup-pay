# Acquire 收单 · 基础设施架构

## 职责与运行边界

[`AcquirePersistenceConfiguration`](src/main/java/io/github/loadup/acquire/infrastructure/config/AcquirePersistenceConfiguration.java)、[`OrderAdmissionHttpClient`](src/main/java/io/github/loadup/acquire/infrastructure/client/OrderAdmissionHttpClient.java) 属于基础设施边界。装配订单、占用、会话及回执的本地持久化，将准入、付款和实时投影的 HTTP 交互封装在基础设施。 本地持久化遵循固定 schema 和 Spring 事务，网络交互遵循应用层定义的接口及失败语义。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
