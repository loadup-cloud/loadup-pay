# Payment 付款与退款执行

payment 拥有独立付款、attempt、退款、渠道绑定与路由事实。订单应收和付款占用由 acquire 管理，商户额度由 limitcenter 管理。内部调用契约见 [client 架构](payment-client/ARCHITECTURE.md)，执行与恢复约束见 [ARCHITECTURE](ARCHITECTURE.md)。

## 能力与入口

- 内部 HTTP：安全输入、付款受理/查证/关闭、父订单围栏、attempt 启动、退款汇总和结算事实；主路径为 `/api/internal/v1/payment/operations/`，另有 captures/statements/verifications。见 client 架构。
- 退款 HTTP：POST `/api/payment/refunds/create` 创建固定原路由退款计划或重放商户退款号；POST `/api/payment/refunds/detail` 查询当前认证商户的原退款请求与分配。
- 运维 HTTP：POST `/api/payment/operations/recovery/tasks/detail` 检查原任务，`tasks/retry` 在准确观察前提下审计并重试 FAILED 任务；POST `/api/payment/operations/recovery/unknown/list` 查询租户范围内 UNKNOWN 付款/子退款。需要受信运维 SERVICE 及对应权限。
- `ChannelBindingController`、`RoutingProfileController` 提供绑定/路由管理；`PaymentChannelCallbackController` 接收渠道通知。回调身份与原付款绑定必须校验，不能以公共回调路径替代验签。
- 后置对账通过独立 `PaymentCaptureRpc`、`PaymentStatementRpc`、`PaymentVerificationRpc` 提供封存快照、账单下载及只读查证，不直接执行差错资金处置。

## 装配与配置

入口为 `payment-bootstrap`；当前 PostgreSQL 连接使用 `PAYMENT_JDBC_URL/USERNAME/PASSWORD`，认证使用 `PAYMENT_JWT_ISSUER_URI/AUDIENCE/JWK_SET_URI`。

- Triple 默认 50051、HTTP 8080；消费者默认 3 秒、零自动重试，账单下载单独配置较长超时。
- 向 acquire、realtime、limitcenter 调用分别配置可信租户、服务身份、目标 audience 和 token-file，详见 bootstrap 的 `application.yml`。
- 安全输入依赖 KMS：`PAYMENT_KMS_ENDPOINT`、`PAYMENT_KMS_TOKEN`，以及输入加密键、指纹键和版本；凭证不写入日志或公开 DTO。
- Local Scheduler 默认每 30 秒扫描，DelayedTask 默认批量 100、最大 5 次、租约 5 分钟、失败重试间隔 1 分钟。业务正常轮询与 native 失败重试有不同语义。
- 恢复 Bulkhead 按进程/渠道共享，默认并发 16、等待 0；首提不纳入恢复预算。
- UNKNOWN 库存采样默认关闭，通过 `PAYMENT_RECOVERY_INVENTORY_ENABLED` 和可信租户范围显式启用。
- `METRICS_ENABLED` 默认 false；监控部署见 [deploy](../deploy/README.md)。

完整参数及默认值以 [应用配置](payment-bootstrap/src/main/resources/application.yml) 为准。当前迁移位于 payment-infrastructure；共享组件迁移随实际依赖装配。

## 验证边界

付款/退款未知结果不会自动释放占用；原任务失败不等于金融失败。源码、测试输入和监控规则已存在，当前 PostgreSQL 初始化、并发回滚、恢复、渠道联调和告警仍需运行验收。本次文档整理没有运行构建或测试。

## HTTP 迁移进度

payment → limitcenter 额度生命周期调用已迁移为标准 HTTP Service Client，按目标服务配置基础地址，逐次传递原凭证；
原事务、幂等和错误语义保留。其他未迁移链路继续使用 Triple，源码与运行验收状态见 [迁移清单](../HTTP_MIGRATION.md)。

第三批已迁移 payment 全部 19 个提供操作及 acquire/limitcenter/reconciliation 的消费端。服务仅提供 HTTP 8080；
当前对 acquire/realtime 的出站调用暂留 Triple。capture 明细为标准平面 PageResponse；statement 内容经 JSON Base64 传递，
调用方保留独立 60s/32 MiB 有界组。旧 mapper 的验证已移至协议无关 client.support，真实运行尚待验收。
