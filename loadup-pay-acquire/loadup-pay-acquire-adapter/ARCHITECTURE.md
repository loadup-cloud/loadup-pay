# Acquire 收单 · Web 适配架构

## 职责与运行边界

[`OrderCreationController`](src/main/java/io/github/loadup/acquire/adapter/web/OrderCreationController.java)、[`CashierPaymentController`](src/main/java/io/github/loadup/acquire/adapter/web/CashierPaymentController.java) 将 HTTP 请求、可信身份和成功或失败报文映射到应用契约。分别处理商户 API 和持托管会话的买方请求。内部事件由 AcquireEventController 转给已装配的 Facade。 业务状态与本地事务由应用服务维护。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
