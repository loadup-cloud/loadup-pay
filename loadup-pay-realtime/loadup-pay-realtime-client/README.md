# Realtime Client

Maven 坐标：`io.github.loadup-cloud:loadup-pay-realtime-client:0.0.1-SNAPSHOT`。

本模块定义 Realtime 投影接收和商户交易查询所需的 Facade、DTO、查询对象、结果码及 `RealtimeHttpService`。acquire/payment 投影生产方依赖本模块提交完整事件信封；查询调用方依赖本模块请求商户范围内的读模型。调用方需配置 Realtime 服务地址和目标 audience 对应的服务令牌；商户查询须携带经认证且绑定商户的主体。

投影 API 为 POST `/api/internal/v1/realtime/projections/{order,payment,refund,refund-summary}`；商户查询为 POST `/api/realtime/trades/{detail,orders,payments,refunds}`。投影接口返回 `ProjectionAckDTO`，业务事实和来源版本定义见 `loadup-pay-contracts-events`。

客户端请求、响应与 ACK 约束见[HTTP 契约架构](ARCHITECTURE.md)、[Realtime 服务架构](../ARCHITECTURE.md)和[Pay 总体架构](../../ARCHITECTURE.md)。
