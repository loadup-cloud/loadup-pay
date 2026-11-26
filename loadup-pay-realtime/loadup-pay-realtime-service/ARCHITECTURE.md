# Realtime Service 架构

本模块在一个 Maven 工件中保留 domain、app 和 infrastructure 包边界。对外 Java 类型和 HTTP Service 见 [client 契约](../loadup-pay-realtime-client/ARCHITECTURE.md)，安全 HTTP 与启动装配见 [bootstrap 架构](../loadup-pay-realtime-bootstrap/ARCHITECTURE.md)。

## 应用职责

`TradeProjectionService` 接收 acquire/payment 提供的完整订单、付款、退款和退款汇总快照；`MerchantTradeQueryService` 在认证商户范围内读取交易投影。`RealtimeActorProvider` 为应用服务提供认证主体，来源服务身份仍由安全适配器验证，消息中的 producer 字段不能代替认证身份。

## 领域与持久化

Domain 模型包括四种投影、退款汇总、来源版本、投影推进状态及处理回执。每种来源独立比较 aggregate/source version：高版本通过条件更新推进，同版本同内容幂等，旧版本不覆盖新事实，同版本冲突拒绝。领域层不依赖 Spring 或 ORM 注解。

Infrastructure 使用数据对象、Mapper、转换器和 `TradeProjectionCodec` 实现存储。投影记录与 `ProjectionReceipt` 在同一数据库事务内更新，保证重复投递返回可复用的处理 ACK。迁移在 `src/main/resources/db/migration/pay/realtime`，只由 bootstrap 启动时执行。

## 不变量与边界

Realtime 是读模型，不写回 acquire/payment，也不裁定支付或退款资金事实。读取按认证 `merchant_id` 隔离；缺订单返回 NOT_FOUND，尚无退款汇总可返回 null。金额和状态以源快照为准，跨来源暂不同步时保留各自版本并表达不一致，不以消费者时钟或到达顺序改写源事实。

消息认证、HTTP 路由与部署参数见 [bootstrap](../loadup-pay-realtime-bootstrap/ARCHITECTURE.md)；客户端投影和商户查询请求见 [client](../loadup-pay-realtime-client/ARCHITECTURE.md)。跨服务数据所有权见[Pay 总体架构](../../ARCHITECTURE.md)。
