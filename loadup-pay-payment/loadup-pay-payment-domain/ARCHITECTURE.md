# Payment 付款 · 领域模型架构

## 职责与运行边界

[`PaymentOrder`](src/main/java/io/github/loadup/payment/domain/model/PaymentOrder.java)、[`PaymentExecutionGate`](src/main/java/io/github/loadup/payment/domain/model/PaymentExecutionGate.java) 维护核心领域状态。付款单、尝试、执行门禁和退款容量表达资金执行规则。受理和可信终态由各自业务状态表达。 数据库访问由 Gateway 端口隔离，HTTP 和 JDBC 实现位于外层。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
