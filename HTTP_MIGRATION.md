# 内部 HTTP 迁移清单

2026-11-02 首批。以 [Roadmap](ROADMAP.md) 为总计划。以下逐项覆盖迁移前的 44 个 RPC 操作；
customer、limitcenter 的原 IDL 已删除，product 已删除 service 定义、暂留 payment 所需消息与 mapper。已迁移行保留作对照。其余路径为迁移建议，需在对应批次结合 Facade 冻结 JSON 契约，不能作为已提供的接口使用。

## 当前调用与身份

| 调用方 → 提供方 | 消费端 | 身份与行为 |
| --- | --- | --- |
| product → customer | CustomerEligibilityHttpLookup | 当次商户/操作员 JWT，必须含 customer 受众；从签名 claim 建立租户；不在本地事务中联网 |
| acquire → product | OrderAdmissionHttpClient | 当前商户委托 JWT，要求 product 受众 |
| product → limitcenter | LimitPolicyActivationHttpClient | 按次读取服务凭证；持久发布意图/Outbox 保留原版本、hash、幂等 |
| acquire → payment | PaymentAcceptanceHttpClient | 逐次读取 acquire 服务凭证；受理未知不能认定付款失败 |
| acquire → realtime | AcquireRealtimeRpcClient | 服务凭证；事件 ID/来源版本、receipt 和 ACK |
| payment → limitcenter | PaymentLimitHttpClient | 服务凭证；reserve/execution/settlement/refund 原幂等与证据 |
| payment → acquire | PaymentAcquireRpcClient | 服务凭证；可靠通知及业务 ACK |
| payment → realtime | PaymentRealtimeRpcClient | 服务凭证；事件 ID/来源版本、receipt 和 ACK |
| limitcenter → payment | PaymentEvidenceHttpClient | 服务凭证；只读可信执行/退款证据 |
| reconciliation → payment | PaymentCaptureHttpClient / PaymentStatementHttpClient / PaymentVerificationHttpClient | 服务凭证；capture 冻结快照、statement 来源完整性、verification 只读证据 |
| 外部受控集成 → reconciliation | ReconciliationRpc | 仓库内无消费者；需同步受控集成，不以无内部引用为由删除业务契约 |

剩余 Triple 的默认调用超时为 3s，自动重试为 0；Statement Download 消费端显式 60s。HTTP 每组地址和超时见调用方 application.yml。
首批 customer 配置连接/池等待各 1s、读取 3s，读取超时不是整个调用的总 deadline。
商户委托不换成高权限 SERVICE token；原有服务令牌文件逐次读取机制在后续批次保留。

## 统一协议约束

- 内部路径使用 `/api/internal/v1/`，由现有 Resource Server 对签名、issuer、audience、有效期和 token_use 完整校验。
  不沿用 Triple 内部仅解析 exp 的信任模型，不以 X-Tenant-Id 或客户端自报主体建立身份。每次调用显式传 Authorization，不写入全局默认头。
- 统一 POST JSON，读取操作仍保留 Query 类型；复用业务 Command/Query/DTO，DTO 不增加租户参数。业务失败保持 HTTP 200 的
  `FailureResponse`，成功保持 `SuccessResponse`。客户端必须同时核对 result.code、result.status 和 data，不能只看 HTTP 200。
- HTTP 非 2xx、认证失败、超时、无效 JSON 和未知业务码均按本地依赖失败处理；远端文案、载荷和凭证不进入异常响应。
  只按本地明确的码白名单恢复业务语义，例如 customer NOT_FOUND → 无资格；认证失败不能降级为无资格。
- `BigDecimal` 保持十进制精度，不经 double；分单位 long 金额保留范围检查。时间、枚举、可空字段复用现有 Jackson 契约，
  epoch/Instant 是绝对时间，其余业务时间沿用系统 zone。每批补 DTO 序列化验收，Wire optional 不机械变成 JSON 默认值。
- 金融写操作沿用原 requestKey/业务请求号、版本、hash 和未知结果恢复；HTTP 客户端与入口网关均无自动重试或协议回退。
  网络调用保持在本地事务之外；Outbox 业务重投仍由原工作流控制。

## 逐操作对照

| 提供方 / 旧操作 | 当前请求 → 响应类型 | HTTP POST 路径 | 状态 |
| --- | --- | --- | --- |
| customer / `CustomerRpc.Authorize` | `MerchantActionAuthorizeCommand` → `MerchantEligibilityDTO` | `/api/internal/v1/customer/eligibility/authorize` | HTTP 源码已迁移；运行待验收 |
| acquire / `AcquireRpc.PaymentOutcome` | `WirePayEventEnvelopeOfPaymentOutcomeEventDTO` → `WirePaymentOutcomeAckDTO` | `/api/internal/v1/acquire/events/payment-outcome` | 待迁移；路径草案 |
| acquire / `AcquireRpc.FeeRefund` | `WirePayEventEnvelopeOfOrderRefundSummaryEventDTO` → `WireFeeRefundAckDTO` | `/api/internal/v1/acquire/events/fee-refund` | 待迁移；路径草案 |
| acquire / `AcquireRpc.RefundNotification` | `WirePayEventEnvelopeOfRefundMerchantEventDTO` → `WireRefundNotificationAckDTO` | `/api/internal/v1/acquire/events/refund-notification` | 待迁移；路径草案 |
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
| realtime / `RealtimeRpc.Order` | `WirePayEventEnvelopeOfOrderSnapshotEventDTO` → `WireProjectionAckDTO` | `/api/internal/v1/realtime/projections/order` | 待迁移；路径草案 |
| realtime / `RealtimeRpc.Payment` | `WirePayEventEnvelopeOfPaymentSnapshotEventDTO` → `WireProjectionAckDTO` | `/api/internal/v1/realtime/projections/payment` | 待迁移；路径草案 |
| realtime / `RealtimeRpc.Refund` | `WirePayEventEnvelopeOfRefundSnapshotEventDTO` → `WireProjectionAckDTO` | `/api/internal/v1/realtime/projections/refund` | 待迁移；路径草案 |
| realtime / `RealtimeRpc.RefundSummary` | `WirePayEventEnvelopeOfOrderRefundSummaryEventDTO` → `WireProjectionAckDTO` | `/api/internal/v1/realtime/projections/refund-summary` | 待迁移；路径草案 |
| reconciliation / `ReconciliationRpc.Create` | `WireReconciliationBatchCreateCommand` → `WireReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/create` | 待迁移；路径草案 |
| reconciliation / `ReconciliationRpc.Attach` | `WireReconciliationInputAttachCommand` → `WireReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/attach` | 待迁移；路径草案 |
| reconciliation / `ReconciliationRpc.Start` | `WireReconciliationBatchStartCommand` → `WireReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/start` | 待迁移；路径草案 |
| reconciliation / `ReconciliationRpc.Retry` | `WireReconciliationBatchRetryCommand` → `WireReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/retry` | 待迁移；路径草案 |
| reconciliation / `ReconciliationRpc.Task` | `WireReconciliationBatchQuery` → `WireReconciliationBatchTaskDTO` | `/api/internal/v1/reconciliation/batches/task` | 待迁移；路径草案 |
| reconciliation / `ReconciliationRpc.Detail` | `WireReconciliationBatchQuery` → `WireReconciliationBatchDTO` | `/api/internal/v1/reconciliation/batches/detail` | 待迁移；路径草案 |

实际清单共 44 项。Wire 类型只用于记录尚未迁移的现状；后续从对应 mapper/Facade 提取实际 Java DTO，
不能直接把 Wire 类型作为 HTTP 请求响应。分页、对账大报文/下载、Bytes、跨服务复用 DTO 的具体契约仍待各批冻结。

## 首批验收与发布

- 源码：customer 只暴露 HTTP；product → customer 全量改用 CustomerHttpService；移除 customer IDL、mapper、Dubbo 装配、Nacos profile、50051 和 Headless Service。
- 安全：新入口受现有 `/api/**` JWT 校验保护；customer 业务层继续建立租户上下文与授权事实。新增签名、受众、token_use、过期及伪造租户头回归源码。
- 客户端：复用已有 HttpTemplate 的命名连接池、SSL Bundle、DNS 策略、大小上限、Boot Observation 和刷新生命周期；标准 HttpServiceProxyFactory + RestClientAdapter，不新建连接池。
- 错误/资源：新增泛型 JSON、令牌逐次传入、无重试/重定向、双向大小限制、私网策略、超时、刷新后原代理及关闭行为回归源码。
- 待执行：编译、上述测试、真实签发方 JWT、PostgreSQL 租户隔离、多副本/滚动发布及压力验收。本批未运行构建或测试，不代表业务等价已经验收。
- 发布：当前开发阶段同步切换 customer 与 product 镜像/配置；无双栈。回退需一起恢复这两个服务的匹配镜像和配置，
  包括旧 customer Triple Service/端口与 product 引用；不能单独回退其中一端。其余六个应用的剩余链路仍使用 Triple。

静态检查备注：已进行 IDEA 批量检查；新增 Maven 依赖尚未同步到部分模块的 IDE 模型，仍报告 spring-web、HTTP 组件及测试库未解析，
另有接口 Bean 注入歧义提示。声明依赖已同步到 POM，生产实现按源码仅有对应 ActorProvider/Facade；这些提示仍需重载项目后复核。
此记录不代表 IDEA 全通过或编译通过。

## 第二批：Product 与 LimitCenter 提供端

- 迁移 Product Authorize/Detail 与 LimitCenter 的九个操作；acquire 的准入客户端、product 的额度激活客户端及 payment 的额度生命周期客户端同步切换。
  截至本批，44 个旧操作中 12 个已迁移源码，32 个仍待迁移；运行验收均未完成。
- product 运行装配不再包含 Dubbo binder/Nacos profile、Triple 协议或 Headless Service。其 client 暂留 payment Wire 条款需要的
  Protobuf 消息、mapper 和生成任务；product.proto 已无 service，不能据此称协议依赖全部清除。
- limitcenter 已移除自己的 IDL、Wire mapper、自动装配与 Triple 提供端；因其仍调用 payment 查证，暂保留出站 Dubbo binder、payment 引用和可选 Nacos profile。
- 新消费组配置 PRODUCT_BASE_URL / LIMITCENTER_BASE_URL，默认普通 ClusterIP HTTP 8080；连接、池等待各 1s、读取 3s，
  请求/响应上限各 1 MiB。原 customer 64 KiB 配置不变，均无自动重试或协议回退。
- 标准接口共享现有事务外联网边界和当次令牌；HttpServiceResponses 同时检查成功码、状态及非空数据，失败只提供格式受限的码供本地白名单映射。
  远端文案、任意异常消息及认证失败不能伪造成已接受/已结算或持久业务拒绝。
- product 的冻结激活意图、Outbox、ACK identity/version/hash 校验不变，文件凭证每次重开；payment 的四项额度错误映射、围栏、退款恢复及凭证轮换保留。
- 已同步回归源码：商户委托、SERVICE token 轮换、事务禁止、错误白名单与伪造消息拒绝；新增十进制费率、超大整数、可空围栏、时间与 permit 的 JSON 契约回归。
- 发布/回退要同时匹配 acquire、product、limitcenter、payment 及首批 customer 的镜像与配置。源清单不再含 product/limitcenter-headless；
  已存在的集群资源仍需在无旧调用后由发布方明确清理，apply 不会自动删除。未执行任何集群变更。
- 本批仍未构建或运行测试；IDE Maven 模型尚未同步新依赖，需重载后再复核静态提示，真实鉴权/租户/数据库/滚动行为仍待验收。

## 第三批：Payment 提供端与全部消费端

- Payment 的 13 个主操作、4 个 capture 操作、statement download 和 verification 共 19 项已迁移源码，累计 31/44 项。
  余下 13 项是 acquire 三个事件入口、realtime 四个投影、reconciliation 六个操作；仍需后续迁移和全部运行验收。
- acquire 的受理/查证/关闭/围栏、limitcenter 的付款与退款事实、reconciliation 的 capture/statement/verification 消费端同步改用 HTTP。
  客户端不自动重试，原文件令牌读取、tenant/service/subject/audience/expiry 预检、事务外联网和错误白名单保留；提供端完整 JWT 验证。
- 输入 absent、付款 UNKNOWN、预占围栏、原付款/退款身份与来源证据不改变。网络/认证/不合法响应不能变成已受理、已关闭或无收款证明。
  startAttempt 等原先没有仓库内消费者的操作仍通过 HTTP 完整保留，不能因无当前调用而删除业务能力。
- capture lines 使用标准平面 PageResponse `{result,data:[...],pageInfo}`；PaymentCapturePageResponse 是消费端读取成功页与 FailureResponse 的视图，
  没有削弱共享 PageResponse 的非空约束。消费端先校验 result，再检查页号/大小、连续 ordinal 和行状态。
- 原 mapper 中的 capture INIT/SEALED/BLOCKED 字段存在性、显式零计数、时间范围/微秒、封存计数/金额、scope 和哈希格式检查移至
  PaymentCaptureContracts；statement/verification 还复用 DTO 的构造校验及 PaymentEvidenceContracts，保留字节完整性与观察状态约束。
  capture version 与行 ordinal/amountMinor/sourceVersion 的 JSON creator 显式要求字段且拒绝 null，避免缺字段伪造零值。
- payment 普通调用组读取 3s、请求/响应上限 32 MiB；reconciliation 的 payment-statements 组保留独立 60s 读取和 32 MiB 响应上限，
  连接上限 8、每路由 4。两组共用 PAYMENT_BASE_URL，均由已有 HttpTemplate 管理。长下载独立限流，避免占满普通调用资源。
  16 MiB 原文及 32 KiB 证据通过 JSON Base64 传输，不把 16 MiB 当作整个 JSON 的大小限制。
- payment 已无 Triple 提供端及 Headless Service，暂留 acquire/realtime 的出站 binder。limitcenter 已无任何出站/入站 Triple，删除 Nacos profile 和 binder。
  特殊对账 Wire mapper/proto 已删除；payment.proto 及 PaymentRpcMapper 的共享事件消息仍供 acquire/realtime 使用，随下一批收敛。
- 新增/迁移回归源码涵盖受理与查证令牌轮换、输入不存在/过期错误、平面分页失败映射、缺字段/错序号拒绝、敏感输入 subtype、
  Base64 字节/SHA 校验、Money 精度和时间；架构门禁允许 client.http 中的标准声明注解，继续阻止实现类型和 app/domain 直接引用 HTTP 客户端。
- 待完成的基础边界：为内部 MVC JSON 请求设计并验证入站大小限制，不能把客户端出站上限或 Tomcat 表单限制当作入站 JSON 限制。
- 源清单删除 payment-headless 和容器 Triple 端口；同批匹配 acquire/payment/limitcenter/reconciliation 及既有 customer/product 镜像与配置，回退亦需匹配。
  现有集群遗留 Service 的清理仍需发布方明确执行。本批未执行构建、测试、部署或金融请求；IDE 仍存在 Maven 新依赖未同步的解析提示。

第三批静态复核补充：共享 JSON 禁止浮点数隐式转为 int/long，声明式代理显式绑定同一 JsonMapper。
新增严格整数与客户端 mapper 一致性回归源码；这是对原 IDL 整数类型约束的保留，不能仅靠字段声明为 long 推断 JSON 不会截断。
IDE 检查发现并修正了 Spring 7 JSON converter 需要 JsonMapper 的构造类型问题；整体检查仍受未同步 Maven 模型限制，未执行编译或测试。

本地 profile 同步：已移除 customer/product/limitcenter/payment 的本地 Triple 端口；迁移后的消费组按现有 HTTP 端口
8081(payment)、8083(customer)、8084(limitcenter)、8085(product) 默认指向 localhost，仍允许同名 BASE_URL 环境覆盖。
剩余三组 Triple 提供端尚未迁移，不能将本地地址已补齐视作整个混合协议链路已经运行。
