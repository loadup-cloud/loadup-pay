# Reconciliation 对账 · Web 适配架构

## 职责与运行边界

[`ReconciliationController`](src/main/java/io/github/loadup/reconciliation/adapter/web/ReconciliationController.java)、[`ReconciliationInternalController`](src/main/java/io/github/loadup/reconciliation/adapter/web/ReconciliationInternalController.java) 将 HTTP 请求、可信身份和成功或失败报文映射到应用契约。运营入口处理批次和差错等操作，内部入口复用同一批次 Facade。权限和账户范围以服务端校验为准。 业务状态与本地事务由应用服务维护。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
