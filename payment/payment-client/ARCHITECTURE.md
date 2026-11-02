# Payment HTTP contracts

payment-client 持有 Facade、Command/Query/DTO、结果码和四组标准 HTTP Service 接口；提供端由 MVC Controller 调用既有 app Facade，
消费端在 acquire/limitcenter/reconciliation 的 infrastructure 注册代理，显式逐次传递 SERVICE bearer。完整 JWT 和业务权限继续校验。

| 标准接口 | `/api/internal/v1/payment/` 路径 | 数据 |
| --- | --- | --- |
| PaymentHttpService | operations 下 13 个操作 | 复用原 Command/Query/DTO，普通 SuccessResponse / FailureResponse |
| PaymentCaptureHttpService | captures/create、retry、detail、lines | lines 返回标准平面 PageResponse，读取视图可表达 HTTP 200 失败 |
| PaymentStatementHttpService | statements/download | PaymentStatementDTO，JSON Base64 原文/证据，16 MiB 原文与 32 KiB 证据保持原上限 |
| PaymentVerificationHttpService | verifications/verify | 只读观察，Money 和原查询绑定；不是执行许可或收款证明 |

普通调用读取 3s，statement download 保留独立 60s/32 MiB 有界组，复用同一个 PAYMENT_BASE_URL。
客户端不自动重试，未知结果按原工作流恢复；未知错误不变成业务拒绝，异常不保留令牌或原报文。

PaymentCaptureContracts 保留旧 Wire 验证的状态/字段存在性、金额/计数、scope、时间与分页/序号约束；
PaymentEvidenceContracts 与 DTO 构造器保留 statement 字节哈希和 verification 状态/证据约束。
敏感 interaction 使用现有显式 subtype 白名单，DiagnosticHidden 只控制诊断输出，不抹除实际内部请求内容。

payment.proto 已无 service，仍暂存 acquire/realtime 使用的共享事件消息及 PaymentRpcMapper，包含 product.proto 依赖。
其余三份特殊对账 IDL 和 mapper 已删除；共享消息与生成装配随 acquire/realtime 迁移一起清理，不能提前删除导致剩余调用缺类型。

真实运行与发布状态见 [迁移清单](../../HTTP_MIGRATION.md)，当前源码迁移不代表构建、测试、数据库或部署已验收。
