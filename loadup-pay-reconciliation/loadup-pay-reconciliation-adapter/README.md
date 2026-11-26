# Reconciliation 对账 · Web 适配接入

提供 Reconciliation 对账的 Web 适配。公开入口和授权方式见 [服务 API](../README.md)。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-reconciliation-adapter`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

宿主按自己的安全链装配此工件。[`ReconciliationController`](src/main/java/io/github/loadup/reconciliation/adapter/web/ReconciliationController.java)、[`ReconciliationInternalController`](src/main/java/io/github/loadup/reconciliation/adapter/web/ReconciliationInternalController.java) 是典型入口，路由包含 `/api/reconciliation 与 /api/internal/v1/reconciliation`。运营入口处理批次和差错等操作，内部入口复用同一批次 Facade。权限和账户范围以服务端校验为准。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
