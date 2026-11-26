# Payment 付款 · 客户端契约接入

发布 Payment 付款的消费方契约。付款与退款命令、结果、证据契约及内部操作。采集、账单与查证分别提供 PaymentCaptureHttpService、PaymentStatementHttpService 和 PaymentVerificationHttpService。

## Maven 工件

`io.github.loadup-cloud:loadup-pay-payment-client`。版本由 [Pay BOM](../../loadup-pay-bom/README.md) 管理。

## 使用入口

通过 [`PaymentAcceptanceFacade`](src/main/java/io/github/loadup/payment/client/facade/PaymentAcceptanceFacade.java)、[`PaymentHttpService`](src/main/java/io/github/loadup/payment/client/http/PaymentHttpService.java) 查阅本地 Facade 或声明式 HTTP 契约。跨服务调用方在自己的基础设施中注册 HTTP 代理，为声明的目标提供基础地址和对应认证。服务路径与权限见 [服务接入说明](../README.md)。

[本层架构](ARCHITECTURE.md) · [服务接入](../README.md)
