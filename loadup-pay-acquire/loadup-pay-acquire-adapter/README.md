# Acquire 收单 · Web 适配接入

提供 Acquire 收单的 Web 适配。公开入口和授权方式见 [服务 API](../README.md)。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-acquire-adapter`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

宿主按自己的安全链装配此工件。[`OrderCreationController`](src/main/java/io/github/loadup/acquire/adapter/web/OrderCreationController.java)、[`CashierPaymentController`](src/main/java/io/github/loadup/acquire/adapter/web/CashierPaymentController.java) 是典型入口，路由包含 `/api/acquire 与 /api/cashier`。分别处理商户 API 和持托管会话的买方请求。内部事件由 AcquireEventController 转给已装配的 Facade。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
