# LimitCenter 商户额度

> 当前数据库基线为 PostgreSQL；运行验收状态不能由历史 MySQL 记录推断。

额度提供端与 payment 证据查证均使用 HTTP；服务仅暴露 8080，已移除 Dubbo binder 与 Nacos profile。见[迁移清单](../HTTP_MIGRATION.md)。

本批补齐 COLA 的 app、infrastructure、adapter，以及“规则激活 → 全部桶预占 → 执行许可/校验 → 未执行预占过期”的持久化源码。复用
LoadUp Money、BaseDO、数据库/ID、生成 Mapper、MapStruct、JSON、JWT resource-server 和 HTTP 响应。通过标准 HTTP 接口暴露额度契约，与 payment 的付款执行生命周期联动。


实现边界与内部流程见 [ARCHITECTURE](ARCHITECTURE.md)。

## 能力与入口

所有接口为 POST JSON，成功使用 SuccessResponse，业务失败使用 FailureResponse。认证失败由资源服务器安全链处理。

| `/api/internal/v1/limitcenter/operations` 路径 | Java Facade | 权限 |
| --- | --- | --- |
| `/activate` | LimitPolicyFacade.activate | `pay:limit:activate` |
| `/reserve` | LimitReservationFacade.reserve | `pay:limit:reserve` |
| `/detail` | LimitReservationFacade.detail | `pay:limit:read` |
| `/begin` | LimitExecutionFacade.beginExecution | `pay:limit:execute` |
| `/verify` | LimitExecutionFacade.verifyExecution | `pay:limit:verify` |
| `/expire` | LimitExecutionFacade.expire | `pay:limit:expire` |
| `/confirm` | LimitSettlementFacade.confirm | `pay:limit:confirm` |
| `/release` | LimitSettlementFacade.release | `pay:limit:release` |
| `/restore-refund` | LimitRefundFacade.restoreRefund | `pay:limit:refund:restore` |

现有业务 Facade 分别实现九个操作；MerchantLimitFacade 保留进程内组合视图。详情以
operationId 查询，限定原调用 service_id；令牌轮换不改变 service_id。

## 身份与策略来源

资源服务器验证 JWT 签名、issuer、audience、有效期及 token_use=access。adapter 还要求签名
claim：principal_type=SERVICE、tenant_id、service_id，以及 sub=service_id；tenant/service 标识最长64字符且不得首尾空白。权限取自框架认证结果。请求体与
X-Tenant-Id 不能指定可信调用身份；本地事务临时绑定并恢复租户上下文。

认证方只向受控 product 发布流程授予激活权限，只向 payment 授予预占、执行、校验、过期和查询权限。这些内部服务接口不直接向商户开放。payment
必须从首次受理事实提供 operationId/paymentId、initiatedAt、金额及原准入 policyId/version/hash，不能接受商户或浏览器任填日期来选择额度窗口。

product 已补初始规则的可靠发布/回执落库源码，见[product说明](../product/README.md)
。只有product接收并校验本接口的真实ACK后才写本地ACTIVE；本服务不直接写product数据库。两端尚未运行集成验收，不能据此宣称新单链路已贯通。

## 构建与运行

默认 bootstrap 包含 limitcenter 业务装配。下列命令供开发者执行：

```bash
# 在 loadup-pay 根目录，编译并执行所选模块及依赖的测试。
mvnd clean verify -pl limitcenter/limitcenter-bootstrap -am

# 沿用既有脚本打包 JAR 与本地镜像，脚本默认跳过测试。
python3 scripts/build.py limitcenter

java \
  -jar limitcenter/limitcenter-bootstrap/target/limitcenter-bootstrap-0.0.1-SNAPSHOT.jar
```

| 环境变量                                              | 用途                                                                |
|-------------------------------------------------------|---------------------------------------------------------------------|
| LIMITCENTER_JDBC_URL                                  | 独立 PostgreSQL 业务库                            |
| LIMITCENTER_JDBC_USERNAME / LIMITCENTER_JDBC_PASSWORD | 外部 Secret 提供的凭证                                              |
| LIMITCENTER_JWT_ISSUER_URI                            | 服务令牌的可信 issuer                                               |
| —                                                     | 入站 audience 取 `spring.application.name`（limitcenter）           |
| LIMITCENTER_JWT_JWK_SET_URI                           | 签发方公钥地址                                                      |
| LIMITCENTER_RESERVE_SECONDS                           | 首次预占执行窗口，默认120秒                                         |
| LIMITCENTER_PERMIT_SECRET                             | v1许可HMAC密钥，至少32个随机UTF-8字节，外部Secret注入；无开发默认值 |
| LIMITCENTER_PERMIT_ACTIVE_KEY_ID                      | 默认v1；切换其他版本须同时提供对应keys配置                          |

密钥配置位于 loadup.modules.limitcenter.permit（active-key-id、keys映射）。多副本共享同版本密钥。轮换先在所有副本装载新旧keyId/密钥，再切
active-key-id；保留旧键直至所有在途执行与响应重放不再需要它。不得在同一keyId下换密钥，重放时摘要不匹配会拒绝。移除旧键不会使已存许可通过假签名重发；旧许可的数据库摘要校验可继续工作，但Begin重放会明确报
LIMIT_PERMIT_UNAVAILABLE。不要复用JWT/商户通知密钥。

业务启动启用 Flyway，扫描本服务和所依赖框架的 db/migration；会执行未应用的迁移。数据库会话 +08:00、JVM/业务 Clock
上海本地时区，DATETIME (6) 不使用 UTC。部署时还需按框架数据库约定稳定分配 ID sequence site。现有 deploy/pay.yml
没有这些配置，不直接用于业务上线；需外部覆盖层注入 Secret/ConfigMap 和 SPRING_PROFILES_ACTIVE。

## 验证边界

本次只核对文档与当前配置/源码，未运行构建或测试。当前 PostgreSQL 基线的迁移、并发、回滚及真实服务调用需单独验收，历史数据库测试记录不代表当前版本已通过。

## 成功退款恢复与权威事实

`LimitCenterRpc/RestoreRefund` 实现 `LimitRefundFacade.restoreRefund`，只接受配置的 payment SERVICE 和
`pay:limit:refund:restore`。原 `LimitRefundRestoreCommand` 绑定
operation/payment/refund/version/businessEvent、单笔成功退款金额、原消息付款累计成功退款、币种和成功证据。业务 Facade
继续分切片装配，MerchantLimitFacade 聚合继承该切片。

首次调用在本地事务外调用 `PaymentRpc/RefundFacts(IdQuery(refundId))`
，验证真实成功子退款与原付款、operation/商户/订单/币种/金额/版本/证据/首次受理时间。查询专供 limitcenter SERVICE 和
`pay:payment:refund-facts`，按 payment 的 parent→request→capacity→child 锁顺序复核原付款永久 claim、成功退款永久 claim
与渠道成功记录，返回该付款最新累计已退及 capacity 版本。旧消息的累计可小于最新权威累计，使用已验证的最新累计推进高水位。

本地按 operation gate→原策略 head/revision→原 buckets 加锁；必须先完成付款 Confirm，且存在匹配原成功版本/证据/attempt
的确认回执。原预占尚为 RESERVED/EXECUTING 时返回 DEPENDENCY_NOT_READY，保持出站重试，不提前写退款回执。原已确认但缺确认回执属于损坏。当前合约是否活跃、当前
revision 如何改恢复规则，均不覆盖原 reservation 冻结解释。

根据原 initiatedAt、原规则的 DAY/WEEK/MONTH/LIFETIME 键读取原 items，只减原 buckets 的 used，保留 held
和窗口身份，不创建退款发生日/月的新桶，不改订单可付款额。金额按累计成功退款高水位计算各 item
的正差额；FULL_PAYMENT_REFUNDED_ONCE 只有累计达到原付款全额才恢复该付款的笔数，每 item 最多一次。乱序/重复不再次恢复，NO_RESTORE
明确保存 NO_ACTION。

bucket CAS、item restored 高水位和 `limit_refund_receipt` 同事务保存；失败整体回滚。tenant/refundId 唯一，完整命令按 UTF-8
长度分帧 SHA-256 绑定；同退款改参拒绝，原参数直接返回首次结果，不再次依赖 payment
可用性。回执保存原累计、权威累计/版本、首次恢复金额/笔数、来源服务、acceptedAt。返回的 restoredAmountMinor
是逻辑付款本次恢复额，不把相同金额的日/月/终生规则恢复量相加。

V20261023080000__limit_refund_receipt.sql、BaseDO、Spring MapStruct、生成 Mapper/TableDef 和 QueryWrapper 已补；不手写
Mapper/业务 SQL。复用 `LIMITCENTER_PAYMENT_TOKEN_FILE` 增加上述事实查询权限，payment 的 `PAYMENT_LIMITCENTER_TOKEN_FILE`
增加退款恢复权限；channel/deadline/JWT 沿用业务 profile。没有签发凭证或执行迁移。

已补原月/终生桶、held 不变、全额仅一次笔数、重复/乱序、原规则、Confirm 前重试、NO_ACTION、身份/权威金额拒绝、回执失败传播以及
RPC/客户端测试源码。内存事务夹具只能检查调用边界，不能证明真实数据库回滚；未运行检查、构建、测试、DDL 或部署。

本地启用 `local` profile 时，许可签名使用 `application-local.yml` 中固定的开发密钥（v1），可由 `LIMITCENTER_PERMIT_SECRET` 覆盖。覆盖值至少需要 32 个 UTF-8 字节；开发密钥仅用于本地，生产环境必须单独生成随机密钥。
