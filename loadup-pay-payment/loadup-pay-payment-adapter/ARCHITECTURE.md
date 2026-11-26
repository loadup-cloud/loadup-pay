# Payment 付款 · Web 适配架构

## 职责与运行边界

[`PaymentController`](src/main/java/io/github/loadup/payment/adapter/web/PaymentController.java)、[`PaymentChannelCallbackController`](src/main/java/io/github/loadup/payment/adapter/web/PaymentChannelCallbackController.java) 将 HTTP 请求、可信身份和成功或失败报文映射到应用契约。内部指令与渠道回调各走所属安全链。控制器负责将输入交给 Facade，回调按渠道协议返回。 业务状态与本地事务由应用服务维护。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
