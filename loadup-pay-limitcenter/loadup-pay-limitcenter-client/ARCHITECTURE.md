# LimitCenter 额度 HTTP 契约

client 持有 Facade、Command/Query/DTO、结果码与标准 `LimitCenterHttpService`，不包含 Dubbo/Protobuf 或 Wire mapper。
九个 POST JSON 操作统一位于 `/api/internal/v1/limitcenter/operations/`：activate、reserve、detail、begin、verify、expire、confirm、release、restore-refund。

Controller 调用现有 LimitPolicy/LimitReservation/LimitExecution/LimitSettlement/LimitRefund Facade；身份由 Resource Server 完整 JWT 验证，
服务身份的 service_id、sub 和权限由 ActorProvider 与业务层校验，部署标识在本地配置。客户端不会把认证失败当作已结算或限额拒绝。

productcenter 使用 activate；payment 使用 reserve/begin/verify/confirm/release/restoreRefund；其余操作保留为明确的完整业务契约。
每次出站调用显式传递目标 audience 的 SERVICE 凭证，获取、缓存与续取由 ServiceAccessTokens 负责，不表示每次读取令牌文件。payment 证据目标按应用名约定 http://payment:8080；payment 调用本服务的目标为 http://limitcenter:8080。共享现有 HTTP 管理资源，写调用不自动重试。
release 先于 reserve 的空 reservation、执行 epoch/permit、退款身份与来源版本、ACK 的 policy/version/hash 语义不变。

limitcenter 应用的 payment 证据查证也已迁移 HTTP，出站 Dubbo 装配和 Nacos profile 已删除。
运行与发布验收见 [HTTP 架构](../../ARCHITECTURE.md)。

[客户端接入](README.md)
