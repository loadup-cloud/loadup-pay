# Acquire HTTP contracts

client 持有 Facade、Command/Query/DTO、事件与错误码；client.http.AcquireHttpService 定义标准 POST JSON 契约。
/api/internal/v1/acquire/events/{payment-outcome,fee-refund,refund-notification} 接收源服务 Java DTO 的 EventEnvelope，
分别返回 PaymentOutcomeAckDTO、FeeRefundAckDTO、RefundNotificationAckDTO，使用 SuccessResponse/FailureResponse。

AcquireEventController 只委托既有 Facade。Resource Server 完整验证 JWT；业务层检查可信 SERVICE、租户、来源身份与原可靠投递 ACK。
payment-infrastructure 注册标准代理，每次读取目标 audience 的服务令牌，在事务外调用；不重试、不回退协议。
acquire-client 复用 payment-client 的原事件 DTO，不复制消息；原 proto、Wire mapper 和 Dubbo 装配已删除。

运行验收状态与完整路径见 [迁移清单](../../HTTP_MIGRATION.md)，构建和测试未执行。
