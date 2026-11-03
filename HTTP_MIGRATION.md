# 内部 HTTP 迁移清单

更新：2026-11-03。以 [Roadmap](ROADMAP.md) 为总计划。44/44 个旧操作均已迁移 HTTP 源码，运行验收待完成。
所有七应用已无 Triple 运行入口、协议生成和必需 Nacos；Framework 可选 binder 不受影响。

## 当前调用与身份

| 调用方 → 提供方 | 消费端 | 身份与行为 |
| --- | --- | --- |
| product → customer | CustomerEligibilityHttpLookup | 当次商户/操作员 JWT，必须含 customer 受众；从签名 claim 建立租户；不在本地事务中联网 |
| acquire → product | OrderAdmissionHttpClient | 当前商户委托 JWT，要求 product 受众 |
| product → limitcenter | LimitPolicyActivationHttpClient | 按次读取服务凭证；持久发布意图/Outbox 保留原版本、hash、幂等 |
| acquire → payment | PaymentAcceptanceHttpClient | 逐次读取 acquire 服务凭证；受理未知不能认定付款失败 |
| acquire → realtime | AcquireRealtimeHttpClient | 服务凭证；事件 ID/来源版本、receipt 和 ACK |
| payment → limitcenter | PaymentLimitHttpClient | 服务凭证；reserve/execution/settlement/refund 原幂等与证据 |
| payment → acquire | PaymentAcquireHttpClient | 服务凭证；可靠通知及业务 ACK |
| payment → realtime | PaymentRealtimeHttpClient | 服务凭证；事件 ID/来源版本、receipt 和 ACK |
| limitcenter → payment | PaymentEvidenceHttpClient | 服务凭证；只读可信执行/退款证据 |
| reconciliation → payment | PaymentCaptureHttpClient / PaymentStatementHttpClient / PaymentVerificationHttpClient | 服务凭证；capture 冻结快照、statement 来源完整性、verification 只读证据 |
| 外部受控集成 → reconciliation | ReconciliationHttpService | 仓库内无消费者；需同步受控集成，不以无内部引用为由删除业务契约 |

普通 HTTP 调用读取超时为 3s，Statement Download 独立组为 60s；均无自动重试。每组地址和超时见调用方 application.yml。
连接/池等待各 1s；读取超时不是整个调用的总 deadline。
商户委托不换成高权限 SERVICE token；服务令牌文件逐次读取，不缓存 token。

## 统一协议约束

- 内部路径使用 `/api/internal/v1/`，由现有 Resource Server 对签名、issuer、audience、有效期和 token_use 完整校验。
  不沿用 Triple 内部仅解析 exp 的信任模型，不以 X-Tenant-Id 或客户端自报主体建立身份。每次调用显式传 Authorization，不写入全局默认头。
- 统一 POST JSON，读取操作仍保留 Query 类型；复用业务 Command/Query/DTO，DTO 不增加租户参数。业务失败保持 HTTP 200 的
  `FailureResponse`，成功保持 `SuccessResponse`。客户端必须同时核对 result.code、result.status 和 data，不能只看 HTTP 200。
- HTTP 非 2xx、认证失败、超时、无效 JSON 和未知业务码均按本地依赖失败处理；远端文案、载荷和凭证不进入异常响应。
  只按本地明确的码白名单恢复业务语义，例如 customer NOT_FOUND → 无资格；认证失败不能降级为无资格。
- `BigDecimal` 保持十进制精度，不经 double；分单位 long 金额保留范围检查。时间、枚举、可空字段复用现有 Jackson 契约，
  epoch/Instant 是绝对时间，其余业务时间沿用系统 zone。每批补 DTO 序列化验收，Wire optional 不机械变成 JSON 默认值。
- 金融写操作沿用原 requestKey/业务请求号、版本、hash 和未知结果恢复；HTTP 客户端无自动重试或协议回退；待实施的入口网关亦要求禁用自动重试。
  网络调用保持在本地事务之外；Outbox 业务重投仍由原工作流控制。

## 逐操作对照

| 提供方 / 旧操作 | 当前请求 → 响应类型 | HTTP POST 路径 | 状态 |
| --- | --- | --- | --- |
| customer / `CustomerRpc.Authorize` | `MerchantActionAuthorizeCommand` → `MerchantEligibilityDTO` | `/api/internal/v1/customer/eligibility/authorize` | HTTP 源码已迁移；运行待验收 |
| acquire / `AcquireRpc.PaymentOutcome` | `EventEnvelope<PaymentOutcomeEventDTO>` → `PaymentOutcomeAckDTO` | `/api/internal/v1/acquire/events/payment-outcome` | HTTP 源码已迁移；运行待验收 |
| acquire / `AcquireRpc.FeeRefund` | `EventEnvelope<OrderRefundSummaryEventDTO>` → `FeeRefundAckDTO` | `/api/internal/v1/acquire/events/fee-refund` | HTTP 源码已迁移；运行待验收 |
| acquire / `AcquireRpc.RefundNotification` | `EventEnvelope<RefundMerchantEventDTO>` → `RefundNotificationAckDTO` | `/api/internal/v1/acquire/events/refund-notification` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.Activate` | `LimitPolicyActivateCommand` → `LimitPolicyActivationDTO` | `/api/internal/v1/limitcenter/operations/activate` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.Reserve` | `LimitReserveCommand` → `LimitReservationDTO` | `/api/internal/v1/limitcenter/operations/reserve` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.Detail` | `LimitOperationQuery` → `LimitReservationDTO` | `/api/internal/v1/limitcenter/operations/detail` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.Begin` | `LimitBeginExecutionCommand` → `LimitExecutionPermitDTO` | `/api/internal/v1/limitcenter/operations/begin` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.Verify` | `LimitExecutionVerifyCommand` → `LimitExecutionVerificationDTO` | `/api/internal/v1/limitcenter/operations/verify` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.Expire` | `LimitExpireCommand` → `LimitReservationDTO` | `/api/internal/v1/limitcenter/operations/expire` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.Confirm` | `LimitConfirmCommand` → `LimitReservationDTO` | `/api/internal/v1/limitcenter/operations/confirm` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.Release` | `LimitReleaseCommand` → `LimitReleaseDTO` | `/api/internal/v1/limitcenter/operations/release` | HTTP 源码已迁移；运行待验收 |
| limitcenter / `LimitCenterRpc.RestoreRefund` | `LimitRefundRestoreCommand` → `LimitRestoreDTO` | `/api/internal/v1/limitcenter/operations/restore-refund` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.InputPrepare` | `PaymentInputPrepareCommand` → `PaymentInputDTO` | `/api/internal/v1/payment/operations/input-prepare` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.InputLookup` | `PaymentInputLookupQuery` → `PaymentInputLookupDTO` | `/api/internal/v1/payment/operations/input-lookup` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.InputExpire` | `PaymentInputExpireCommand` → `PaymentInputExpiryDTO` | `/api/internal/v1/payment/operations/input-expire` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.Accept` | `PaymentExecuteCommand` → `PaymentExecutionDTO` | `/api/internal/v1/payment/operations/accept` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.Detail` | `IdQuery` → `PaymentExecutionDTO` | `/api/internal/v1/payment/operations/detail` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.Outcome` | `IdQuery` → `PaymentOutcomeDTO` | `/api/internal/v1/payment/operations/outcome` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.Facts` | `IdQuery` → `PaymentSettlementFactsDTO` | `/api/internal/v1/payment/operations/facts` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.Close` | `PaymentCloseCommand` → `PaymentCloseDTO` | `/api/internal/v1/payment/operations/close` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.FenceParent` | `ParentPaymentFenceCommand` → `ParentFenceDTO` | `/api/internal/v1/payment/operations/fence-parent` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.ParentFenceStatus` | `ParentFenceStatusQuery` → `ParentFenceDTO` | `/api/internal/v1/payment/operations/parent-fence-status` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.StartAttempt` | `PaymentAttemptStartCommand` → `PaymentExecutionDTO` | `/api/internal/v1/payment/operations/start-attempt` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.RefundSummary` | `IdQuery` → `OrderRefundSummaryEventDTO` | `/api/internal/v1/payment/operations/refund-summary` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentRpc.RefundFacts` | `IdQuery` → `RefundSettlementFactsDTO` | `/api/internal/v1/payment/operations/refund-facts` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentCaptureRpc.Create` | `PaymentCaptureCreateCommand` → `PaymentCaptureDTO` | `/api/internal/v1/payment/captures/create` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentCaptureRpc.Retry` | `PaymentCaptureRetryCommand` → `PaymentCaptureDTO` | `/api/internal/v1/payment/captures/retry` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentCaptureRpc.Detail` | `IdQuery` → `PaymentCaptureDTO` | `/api/internal/v1/payment/captures/detail` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentCaptureRpc.Lines` | `PaymentCaptureLinesQuery` → `PageResponse<PaymentLedgerLineDTO>` | `/api/internal/v1/payment/captures/lines` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentStatementRpc.Download` | `PaymentStatementQuery` → `PaymentStatementDTO` | `/api/internal/v1/payment/statements/download` | HTTP 源码已迁移；运行待验收 |
| payment / `PaymentVerificationRpc.Verify` | `PaymentVerificationQuery` → `PaymentVerificationDTO` | `/api/internal/v1/payment/verifications/verify` | HTTP 源码已迁移；运行待验收 |
| product / `ProductRpc.Authorize` | `OrderAdmissionAuthorizeCommand` → `OrderAdmissionDTO` | `/api/internal/v1/product/admissions/authorize` | HTTP 源码已迁移；运行待验收 |
| product / `ProductRpc.Detail` | `IdQuery` → `OrderAdmissionDTO` | `/api/internal/v1/product/admissions/detail` | HTTP 源码已迁移；运行待验收 |
| realtime / `RealtimeRpc.Order` | `EventEnvelope<OrderSnapshotEventDTO>` → `ProjectionAckDTO` | `/api/internal/v1/realtime/projections/order` | HTTP 源码已迁移；运行待验收 |
| realtime / `RealtimeRpc.Payment` | `EventEnvelope<PaymentSnapshotEventDTO>` → `ProjectionAckDTO` | `/api/internal/v1/realtime/projections/payment` | HTTP 源码已迁移；运行待验收 |
| realtime / `RealtimeRpc.Refund` | `EventEnvelope<RefundSnapshotEventDTO>` → `ProjectionAckDTO` | `/api/internal/v1/realtime/projections/refund` | HTTP 源码已迁移；运行待验收 |
| realtime / `RealtimeRpc.RefundSummary` | `EventEnvelope<OrderRefundSummaryEventDTO>` → `ProjectionAckDTO` | `/api/internal/v1/realtime/projections/refund-summary` | HTTP 源码已迁移；运行待验收 |
| reconciliation / `ReconciliationRpc.Create` | `ReconciliationBatchCreateCommand` → `ReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/create` | HTTP 源码已迁移；运行待验收 |
| reconciliation / `ReconciliationRpc.Attach` | `ReconciliationInputAttachCommand` → `ReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/attach` | HTTP 源码已迁移；运行待验收 |
| reconciliation / `ReconciliationRpc.Start` | `ReconciliationBatchStartCommand` → `ReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/start` | HTTP 源码已迁移；运行待验收 |
| reconciliation / `ReconciliationRpc.Retry` | `ReconciliationBatchRetryCommand` → `ReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/retry` | HTTP 源码已迁移；运行待验收 |
| reconciliation / `ReconciliationRpc.Task` | `IdQuery` → `ReconciliationBatchTaskDTO` | `/api/internal/v1/reconciliation/batches/task` | HTTP 源码已迁移；运行待验收 |
| reconciliation / `ReconciliationRpc.Detail` | `IdQuery` → `ReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/detail` | HTTP 源码已迁移；运行待验收 |

实际清单共 44 项，均已迁移源码。表中的响应是业务数据类型，普通响应包在 SuccessResponse 中；capture lines 使用标准平面 PageResponse。
旧操作名称仅供迁移对照，不代表仍提供 RPC 入口。接口签名以各 client.http 标准接口为准。

## 传输、JSON 与配置边界

- 所有代理通过既有 HttpTemplate 创建，共享命名 Apache 连接池、TLS Bundle、DNS 策略、Boot Observation 与刷新生命周期。
  非 2xx 不读取远端业务文案；HTTP 200 先验证成功码/状态/非空 data。事务外联网、目标凭证和本地错误白名单保留。
- customer 请求/响应各 64 KiB，product/limitcenter 各 1 MiB，其余调用组各 32 MiB。
  reconciliation 的 payment-statements 独立读取 60s、最大连接 8/每路由 4，仍使用 PAYMENT_BASE_URL。
  这些是客户端限制。七应用另已配置 WebMVC 内部请求上限：customer 64 KiB、product/limitcenter 1 MiB、其余 32 MiB。
  入站 Advice 完整有界读取后才转换 JSON，未知/虚假长度与尾随输入均计入，超限 HTTP 413；源码已补，运行待验证。
- capture 的 INIT/SEALED/BLOCKED presence、显式零值、计数/金额、scope、哈希、时间与分页/连续 ordinal 由
  PaymentCaptureContracts/PaymentEvidenceContracts 和 DTO 构造校验承担。statement 16 MiB 原字节、32 KiB 证据通过 JSON Base64 传输并核对 SHA，不能把原字节限制直接当作整个 JSON 上限。
- EventEnvelope 的 schemaVersion/aggregateVersion 缺失或 null 拒绝，显式来源版本 0 合法；嵌套 DTO 继续执行 Bean Validation。
  producer 不是认证身份，收到完整 JWT 后仍检查服务主体、租户、来源与 ACK。realtime 的 STALE/DUPLICATE 和原 receipt 不改变。
- ReconciliationInput/Batch/Task 构造器保留原 mapper 的 presence、计数溢出、状态/输入/run/任务关联约束。
  JsonUtil 禁止浮点截断为 int/long 与数字枚举 ordinal；BigDecimal 不经 double。标准代理显式使用同一个 Jackson 3 JsonMapper。
- 共享部署默认配置改为 loadup.http.client.{tenant-id,service-id,audience,token-file}，模块显式配置优先。
  audience/token-file 默认按目标应用取值，service-id 默认取调用方 spring.application.name；helper 只绑定配置，不读取或验证 token。
  不保留 loadup.rpc.client 别名。已有 ACQUIRE/PAYMENT 等模块环境变量继续生效。
- 默认 Service 地址为 http://<app>:8080。本地 profile 按 payment/acquire/customer/limitcenter/product/realtime/reconciliation
  的 8081/8082/8083/8084/8085/8086/8087 配置出站地址；BASE_URL 环境变量仍可覆盖。

## 依赖与部署

七应用已删除 Dubbo binder、内部 proto/存根生成、Wire mapper、RPC 自动装配和 nacos profile；框架可选 RPC/ConfigCenter binder 保留。
deploy/pay.yml 为 Namespace + 七组 NetworkPolicy/Deployment/普通 HTTP ClusterIP Service，共 22 个资源；单应用渲染为 4 个资源。
渲染器拒绝旧 Headless Service 和额外协议端口。已删除旧的仅约束 50051 的策略，新增每应用 HTTP 入站策略；真实 CNI 隔离未验收，见 [网络策略](deploy/NETWORK_POLICY.md)。

当前开发阶段同步匹配七服务镜像、基础地址、JWT 与配置，不保留双栈或自动协议回退。
回退旧协议时需恢复整套匹配镜像/配置/Service。apply 不会删除集群遗留 Headless Service 或旧 NetworkPolicy，发布方应在确认无旧消费者后明确清理。
源码清理后必须 clean 构建，避免旧生成类残留。未执行集群变更或删除资源。

## 验收状态

已补客户端、JWT 入口、泛型 JSON、令牌轮换、错误白名单、事务边界、字节完整性、缺字段、无重试/重定向、超时和生命周期回归源码；
渲染器和架构门禁的源码同步检查已更新。IDE 检查仍报告未同步 Maven 新依赖的 spring-web/HTTP 组件解析及接口 Bean 提示；不代表整体检查通过。

2026-11-03 用户反馈本地编译无问题，测试验证暂缓；本轮助手未运行构建或测试，未将该反馈扩展为测试通过。
真实签发方 JWT、PostgreSQL 隔离/事务、未知结果恢复、多副本分布、滚动排空和压力验收仍待完成。
44/44 是源码覆盖，不是运行验收。版本化配置/排空源码已补，见 [配置与发布](deploy/CONFIGURATION.md)；HTTP 网络策略/Envoy Gateway 已补源码和安装说明，真实流量及虚拟线程压测继续按 Roadmap 验收。

线程候选与全部运行证据按 [最终验收](deploy/ACCEPTANCE.md) 收口；44/44 源码、配置渲染和线程测试源码均不是业务通过记录。
