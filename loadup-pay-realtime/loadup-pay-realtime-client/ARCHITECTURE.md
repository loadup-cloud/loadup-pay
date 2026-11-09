# Realtime HTTP contracts

client.http.RealtimeHttpService 定义 POST /api/internal/v1/realtime/projections/{order,payment,refund,refund-summary}，
接收 acquire/payment 源服务的 EventEnvelope<具体快照 DTO>，返回 SuccessResponse<ProjectionAckDTO> 或 FailureResponse。
复用源服务 Java DTO，不复制消息；原 proto、Wire mapper 和 Dubbo 自动装配已删除。

TradeProjectionController 委托既有 TradeProjectionFacade；Resource Server 完整 JWT 验证后，业务层继续检查 SERVICE、租户、producer/aggregate/version。
producer 不是认证身份。显式来源版本 0 合法，缺失/null 版本拒绝，嵌套 DTO 继续验证。
APPLIED、DUPLICATE、STALE 的 ACK 与 source/projected version、accepted 不改写；原 receipt/投影在本地事务中提交。

acquire/payment 的 infrastructure 注册标准代理，目标按应用名约定 http://realtime:8080，逐次获取服务凭证，在事务外发送原完整快照。
没有同步源查询、自动重试或协议回退。运行验收见 [迁移清单](../../HTTP_MIGRATION.md)，构建和测试未执行。
