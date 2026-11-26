# Payment 付款 · 领域模型接入

提供 Payment 付款的领域模型、状态和 Gateway 端口。应用服务通过它们表达命令及业务约束。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-payment-domain`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

先从 [`PaymentOrder`](src/main/java/io/github/loadup/payment/domain/model/PaymentOrder.java)、[`PaymentExecutionGate`](src/main/java/io/github/loadup/payment/domain/model/PaymentExecutionGate.java) 确认业务约束，再在应用层编排操作。在基础设施层实现需要的 Gateway，使用项目已有的持久化和远端适配。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
