# LimitCenter HTTP contracts

client 持有 Facade、Command/Query/DTO、结果码与标准 `LimitCenterHttpService`，不包含 Dubbo/Protobuf 或 Wire mapper。
九个 POST JSON 操作统一位于 `/api/internal/v1/limitcenter/operations/`：activate、reserve、detail、begin、verify、expire、confirm、release、restore-refund。

Controller 调用现有 LimitPolicy/LimitReservation/LimitExecution/LimitSettlement/LimitRefund Facade；身份由 Resource Server 完整 JWT 验证，
SERVICE 的 service_id、sub、租户和权限仍由既有 ActorProvider/业务层检查。客户端不会把认证失败当作已结算或限额拒绝。

product 使用 activate；payment 使用 reserve/begin/verify/confirm/release/restoreRefund；其余操作保留为明确的完整业务契约。
令牌逐次读取，按提供方配置 LIMITCENTER_BASE_URL，共享现有 HTTP 管理资源，写调用不自动重试。
release 先于 reserve 的空 reservation、执行 epoch/permit、退款身份与来源版本、ACK 的 policy/version/hash 语义不变。

limitcenter 应用的 payment 证据查证也已迁移 HTTP，出站 Dubbo 装配和 Nacos profile 已删除。
运行与发布验收见 [HTTP 迁移清单](../../HTTP_MIGRATION.md)。
