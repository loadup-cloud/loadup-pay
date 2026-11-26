# Realtime Bootstrap 架构

`RealtimeApplication` 是 Spring Boot 启动入口。本模块依赖 [Realtime Service](../loadup-pay-realtime-service/ARCHITECTURE.md) 执行业务，并通过 [Realtime Client](../loadup-pay-realtime-client/ARCHITECTURE.md) 的请求与响应类型暴露 HTTP API；同时依赖 acquire/payment client 复用来源快照 DTO。

## HTTP 与安全

- `TradeProjectionController` 接收四类投影快照，并委托 `TradeProjectionFacade`。
- `MerchantTradeQueryController` 提供订单详情及订单、付款、退款分页查询。
- Resource Server 校验 JWT 签名、issuer、audience 和有效期。投影服务身份按真实认证主体校验，不能仅信任事件 envelope 的 producer 标签；商户查询由认证主体中的 merchant_id 限定范围。

内部投影请求体上限由配置设为 32 MiB，投影消息 DTO 自身另有大小和字段校验。业务接口使用 JSON 请求及标准响应，Actuator 对外仅暴露 health、prometheus。

## 启动、数据库和迁移

`application.yml` 读取 `REALTIME_JDBC_*`、JWT 和部署配置，启用 Flyway，位置固定为 `classpath:db/migration/pay/realtime`。入站 audience 为 `realtime`；来源服务 ID 默认 `acquire` 和 `payment`。`application-local.yml` 指向本地 `loadup_realtime` 数据库并设置 8086 端口；默认端口为 8080。虚拟线程默认关闭。

本地回执和投影使用同一事务，确保 ACK 与读模型变更一起提交。此服务不查询或修改交易源数据库。数据版本与回执不变量见[服务架构](../loadup-pay-realtime-service/ARCHITECTURE.md)，运行验收要求见[Pay 总体架构](../../ARCHITECTURE.md)。
