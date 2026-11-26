# Payment 付款 · 基础设施架构

## 职责与运行边界

[`PaymentPersistenceConfiguration`](src/main/java/io/github/loadup/payment/infrastructure/config/PaymentPersistenceConfiguration.java)、[`PaymentLimitHttpClient`](src/main/java/io/github/loadup/payment/infrastructure/client/PaymentLimitHttpClient.java) 属于基础设施边界。装配付款与退款持久化、渠道和风控适配。对 Acquire、LimitCenter 和 Realtime 的 HTTP 调用按命名客户端隔离。 本地持久化遵循固定 schema 和 Spring 事务，网络交互遵循应用层定义的接口及失败语义。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
