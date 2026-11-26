# Reconciliation 对账 · 基础设施架构

## 职责与运行边界

[`PaymentCaptureHttpClient`](src/main/java/io/github/loadup/reconciliation/infrastructure/client/PaymentCaptureHttpClient.java)、[`PaymentStatementHttpClient`](src/main/java/io/github/loadup/reconciliation/infrastructure/client/PaymentStatementHttpClient.java) 属于基础设施边界。实现付款源端采集、官方账单和证据查证的 HTTP 边界。将封存输入与差错、复核结果持久化到对账 schema。 本地持久化遵循固定 schema 和 Spring 事务，网络交互遵循应用层定义的接口及失败语义。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
