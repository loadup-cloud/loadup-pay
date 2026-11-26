# Payment 付款 · 应用编排架构

## 职责与运行边界

[`PaymentAcceptanceService`](src/main/java/io/github/loadup/payment/app/service/PaymentAcceptanceService.java)、[`PaymentExecutionService`](src/main/java/io/github/loadup/payment/app/service/PaymentExecutionService.java) 组合领域规则和 Gateway。受理时持久化付款、门禁与恢复任务。后续分支获取额度许可、提交渠道并应用可信观察。未知结果沿原请求恢复。 HTTP 报文与具体 JDBC 适配分别由外层模块处理。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
