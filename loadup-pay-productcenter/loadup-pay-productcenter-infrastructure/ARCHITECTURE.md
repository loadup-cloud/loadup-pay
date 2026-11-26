# Product Center 产品中心 · 基础设施架构

## 职责与运行边界

[`ProductPersistenceConfiguration`](src/main/java/io/github/loadup/productcenter/infrastructure/config/ProductPersistenceConfiguration.java)、[`LimitPolicyActivationHttpClient`](src/main/java/io/github/loadup/productcenter/infrastructure/client/LimitPolicyActivationHttpClient.java) 属于基础设施边界。维护目录、合约、准入与策略发布事实，通过外部端口查询商户并将额度发布交给 LimitCenter。 本地持久化遵循固定 schema 和 Spring 事务，网络交互遵循应用层定义的接口及失败语义。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
