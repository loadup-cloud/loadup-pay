# Acquire 收单 HTTP 契约

client 持有 Facade、Command/Query/DTO、事件与错误码；client.http.AcquireHttpService 定义标准 POST JSON 契约。
/api/internal/v1/acquire/events/{payment-outcome,fee-refund,refund-notification} 接收源服务 Java DTO 的 EventEnvelope，
分别返回 PaymentOutcomeAckDTO、FeeRefundAckDTO、RefundNotificationAckDTO，使用 SuccessResponse/FailureResponse。

AcquireEventController 只委托既有 Facade。Resource Server 完整验证 JWT；业务层检查可信 SERVICE、租户、来源身份与原可靠投递 ACK。
payment-infrastructure 注册标准代理，每次读取目标 audience 的服务令牌，在事务外调用；不重试、不回退协议。
acquire-client 复用独立 contracts-events 中的 PaymentOutcomeEventDTO、OrderRefundSummaryEventDTO、RefundMerchantEventDTO，不依赖 payment-client 承载事件；原 proto、Wire mapper 和 Dubbo 装配已删除。

付款结果携带完整证据时按证据落账，旧冻结载荷与费用退款按原规则在本地事务外查证。匹配的 delivery receipt 可直接回放首次 ACK。refund-notification 登记待送商户通知，商户送达由独立记录管理。见[收单设计](../ARCHITECTURE.md)。

运行验收状态与完整路径见 [总体架构](../../ARCHITECTURE.md)，构建和测试未执行。

[客户端接入](README.md)
