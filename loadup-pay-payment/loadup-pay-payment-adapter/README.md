# Payment 付款 · Web 适配接入

提供 Payment 付款的 Web 适配。公开入口和授权方式见 [服务 API](../README.md)。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-payment-adapter`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

宿主按自己的安全链装配此工件。[`PaymentController`](src/main/java/io/github/loadup/payment/adapter/web/PaymentController.java)、[`PaymentChannelCallbackController`](src/main/java/io/github/loadup/payment/adapter/web/PaymentChannelCallbackController.java) 是典型入口，路由包含 `/api/payment 与 /api/internal/v1/payment`。内部指令与渠道回调各走所属安全链。控制器负责将输入交给 Facade，回调按渠道协议返回。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
