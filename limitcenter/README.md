# LimitCenter 商户额度

应用间传输已于2026-10-23统一为 gRPC，见[RPC 规范](../design/RPC.md)。本文件旧批次中的内部 HTTP 描述与 `*_BASE_URL` 要求由该规范取代；商户/运营 HTTP 入口继续保留。实际业务 gRPC 随本应用 `*-business` 构建和运行 profile 装配，端口50051；本批通过 IDEA MCP 完成六业务应用及依赖的 `clean test-compile`；未执行测试或部署。

本批补齐 COLA 的 app、infrastructure、adapter，以及“规则激活 → 全部桶预占 → 执行许可/校验 → 未执行预占过期”的持久化源码。复用
LoadUp Money、BaseDO、数据库/ID、生成 Mapper、MapStruct、JSON、JWT resource-server 和 HTTP 响应。业务 gRPC 与付款执行生命周期继续按
ROADMAP 实施。

## 能力与入口

所有接口为 POST JSON，成功使用 SuccessResponse，业务失败使用 FailureResponse。认证失败由资源服务器安全链处理。

| `/api/limitcenter` 路径 | Java Facade                          | 权限                 |
|-------------------------|--------------------------------------|----------------------|
| `/policies/activate`    | LimitPolicyFacade.activate           | `pay:limit:activate` |
| `/reservations/reserve` | LimitReservationFacade.reserve       | `pay:limit:reserve`  |
| `/reservations/detail`  | LimitReservationFacade.detail        | `pay:limit:read`     |
| `/executions/begin`     | LimitExecutionFacade.beginExecution  | `pay:limit:execute`  |
| `/executions/verify`    | LimitExecutionFacade.verifyExecution | `pay:limit:verify`   |
| `/reservations/expire`  | LimitExecutionFacade.expire          | `pay:limit:expire`   |

LimitReservationFacade 实现预占/查询，LimitExecutionFacade 实现执行许可/校验/显式过期；原 MerchantLimitFacade 继承二者并保留
confirm、release、restoreRefund 目标契约。当前不注册完整 MerchantLimitFacade 实现，不暴露尚未实现的方法或伪造付款结果。详情以
operationId 查询，限定原调用 service_id；令牌轮换不改变 service_id。

## 身份与策略来源

资源服务器验证 JWT 签名、issuer、audience、有效期及 token_use=access。adapter 还要求签名
claim：principal_type=SERVICE、tenant_id、service_id，以及 sub=service_id；tenant/service 标识最长64字符且不得首尾空白。权限取自框架认证结果。请求体与
X-Tenant-Id 不能指定可信调用身份；本地事务临时绑定并恢复租户上下文。

认证方只向受控 product 发布流程授予激活权限，只向 payment 授予预占、执行、校验、过期和查询权限。这些内部服务接口不直接向商户开放。payment
必须从首次受理事实提供 operationId/paymentId、initiatedAt、金额及原准入 policyId/version/hash，不能接受商户或浏览器任填日期来选择额度窗口。

product 已补初始规则的可靠发布/回执落库源码，见[product说明](../product/README.md)
。只有product接收并校验本接口的真实ACK后才写本地ACTIVE；本服务不直接写product数据库。两端尚未运行集成验收，不能据此宣称新单链路已贯通。

## 激活协议

LimitPolicyActivateCommand 包含 policyId、policyVersion、policyHash、payeeMerchantId、refundAmountRestore、refundCountRestore
和完整 rules。规则最多64条，AMOUNT 单位为分，PAYMENT_COUNT 单位为笔；CNY、DAY/WEEK/MONTH/LIFETIME，weekStart 按 ISO
1至7。meteringEnabled 必须为 true；enforcementEnabled=false 仍持续累计，仅跳过新预占的阈值检查。退款配置值为
NO_RESTORE/ORIGINAL_BUCKETS 和 NO_RESTORE/FULL_PAYMENT_REFUNDED_ONCE，本批仅冻结其解释。

发布方使用 client 的 LimitPolicyHash.compute (trustedTenant, command) 计算规范化 SHA-256，再构造携带该 hash 的命令。hash
自身不进入输入；算法 v1 包含协议名、可信 tenant、policyId、version、payee、两项退款策略、规则数量和按稳定身份排序后的全部规则字段。每字段为
UTF-8，前置四字节大端字节长度；整数使用十进制字符串、布尔使用 true/false。JSON 格式或规则输入顺序不影响结果，long 不经过
double。服务端重新计算，读库存量解释也重新验证 hash、行身份和字段约束。

- policyId 和 ruleScopeId 由可信发布方保持稳定；不得以换 ID 规避累计额度。新版本必须从1开始逐次递增。
- policy_head 行锁序列化同一策略的激活与新预占。相同版本同 hash 返回原 activatedAt；不同 hash 冲突。重放旧 ACK 不会回写旧
  runtime 阈值。
- 同一策略后续版本只能调整阈值、是否执行阈值门禁及退款恢复解释；不得改收款商户、增删/改名规则、改变指标/币种/窗口/周起点。runtime
  稳定键归属于原策略，其他策略不得接管。此保守约束避免把规则迁移伪装成累计清零；新规则/改历法需要后续显式迁移流程。
- 当前批次只接受规则 merchantId 等于 policy 的 payeeMerchantId。父商户共享桶和规则迁移需要单独可靠发布协议，暂不支持；遇到父级规则明确拒绝。
- 不可变 revision 保存原规则；runtime 保存最新阈值。原 pending 订单使用旧 policy 引用仍可预占，但按当前 runtime 阈值判断，既有
  used/held 不清零。降阈值允许历史 used+held 超过新阈值，此时后续新预占拒绝。

## 预占与事务

原始 initiatedAt 统一上海时区，要求微秒精度、年份1970至9998且首次预占不能晚于当前时间。DAY/WEEK/MONTH 使用该原始时间归桶，LIFETIME
使用固定窗口。桶键不含 policyVersion。每个 AMOUNT 桶占本笔 amountMinor，每个 PAYMENT_COUNT 桶占1；一次请求命中的全部规则一起成功或一起回滚。

写操作使用主库 READ_COMMITTED 独立事务，拒绝已有本地外层事务。预占锁顺序为 operation_gate → policy_head → runtime
按稳定身份 → bucket 按稳定身份与窗口 → reservation/items；激活为 policy_head →
runtime。纯领域模型先生成全部桶变更提案，再以行版本条件写回所有桶、reservation/items 和 OPEN
gate，同一事务提交。缺行插入由唯一约束兜底；唯一键竞争或瞬时数据库冲突整事务回滚后重试，最多3次，耗尽返回
LIMIT_CONCURRENT_UPDATE，调用方以相同意图重试。

同一 tenant+operationId/paymentId 唯一；重复请求逐项核对付款、订单、商户、金额/币种、policyId/version/hash、原
initiatedAt、requestDigest 与调用服务。相同请求返回已存 reservation，不重复占额或刷新过期时间。预占有效期默认首次成功建单时间加120秒，可配置1至86400秒；该值用于
BeginExecution 门禁；当前提供显式 expire 入口，自动扫描器尚待接入。FENCED gate 不得被迟到 Reserve 重新打开。

实际迁移 `V20261021180000__limitcenter_policy_and_reservation.sql`
新增7表：policy_head、policy_revision、rule_runtime、bucket、operation_gate、reservation、reservation_item（表名前缀均为 limit_
）。后续加法迁移 V20261021190000__limitcenter_execution_grant.sql 再新增 limit_execution_grant，实际合计8张表。BaseDO
与数据库组件管理审计/ID。MyBatis-Flex Mapper/Tables 通过框架 wrapper processor 生成；无手写空 Mapper、JdbcTemplate
或内存生产实现。compiled_rules 使用 LONGTEXT 保存完整 payload，并在诊断输出中隐藏。

## 执行门禁与过期

BeginExecution 首次要求原记录为 RESERVED，服务端当前时间严格早于 reserveExpiresAt，且无 previousAttemptEvidenceId。它与
expire 争用同一 operation_gate 行锁；先读取定位并校验服务归属，取得门禁锁后重读状态，再锁 policy_head。成功时在同一事务写
immutable execution_grant（operation/payment/attempt/epoch/requestKey/keyId/permitHash）并把 reservation 转为
EXECUTING，epoch从1开始。依赖签名失败、唯一键冲突、CAS或提交失败均不会部分提交。

已进入 EXECUTING 的同 attempt + requestKey 重试用原 keyId 重算同一许可，即使预占期限已过也不刷新额度或增加 epoch。更改
requestKey/先前证据字段拒绝；不同 attempt 返回 LIMIT_OUTCOME_UNKNOWN，即使传入任意 evidenceId 也不启用换尝试。可信渠道查证和封禁旧执行的接入后续实施。

许可复用框架
commons-util.DigestUtils.hmacSha256，输出64位小写十六进制；协议v1以UTF-8字节长度加冒号分帧，绑定租户、原调用服务、reservation/operation/payment/order/payee、金额/币种、原policy/version/hash、意图摘要、原initiatedAt、attempt、epoch、requestKey和keyId。数据库只保存
SHA-256 (permit) 与 keyId；不存原许可或HMAC密钥。诊断隐藏输入permit、持久摘要和配置密钥。此为 limitcenter 内部许可，不是 JWT
或渠道扣款凭证，HMAC密钥不得下发给商户/payment/浏览器。

verifyExecution 使用原服务权限，核对当前 EXECUTING 状态、payment/attempt/epoch、金额/币种与许可摘要（恒定时间比较）。返回
valid 与 VALID/NOT_EXECUTING/PERMIT_MISMATCH；认证/资源归属/存储损坏仍为规范业务失败。它是锁内某时刻的观察，不能与跨服务
payment 数据库形成原子事务，也不证明渠道只提交了一次；payment 的 Y2 本地 epoch/attempt CAS、重试和查证仍必须实现。当前尚无真实渠道提交调用方。

expire 只在 RESERVED 且当前时间达到期限时，按原不可变policy/hash/受理时间核对完整明细，锁原桶并同时归还本次 held、保留其他
used/held，再CAS变更为 EXPIRED。重复调用不重复释放；早于期限返回 RESERVED；EXECUTING/CONFIRMED
等状态返回当前状态且绝不按TTL释放。缺桶/缺明细或数量不一致拒绝并回滚，不新建空桶。EXPIRED 后不能 BeginExecution，同operation
Reserve只返回原终态；新付款须使用新的权威payment/operation。

Begin 在过期边界返回 LIMIT_RESERVATION_EXPIRED，不在抛异常的事务中偷偷释放；调用方可显式 expire 并确认
EXPIRED。不存在的operation返回 NOT_FOUND，不等于持久无扣款证明，也不替代后续 Release-before-Reserve 的 FENCED tombstone
协议。自动过期扫描、到期事件/历史与 payment 释放订单占用联动仍待后续实施。

## 构建与运行

默认 bootstrap 仍为基础启动骨架。业务 JAR 同时需要 Maven 的 limitcenter-business profile 和运行时同名 Spring
profile。下列命令供开发者执行，本轮未运行：

```bash
# 在 loadup-pay 根目录，编译并执行所选模块及依赖的测试。
mvnd clean verify -pl limitcenter/limitcenter-bootstrap -am -Plimitcenter-business

# 沿用既有脚本打包 JAR 与本地镜像，脚本默认跳过测试。
./limitcenter/build.sh -Plimitcenter-business

java \
  -jar limitcenter/limitcenter-bootstrap/target/limitcenter-bootstrap-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=limitcenter-business
```

| 环境变量                                              | 用途                                                                |
|-------------------------------------------------------|---------------------------------------------------------------------|
| LIMITCENTER_JDBC_URL                                  | 独立 MySQL 业务库，使用支持 CHECK 的版本                            |
| LIMITCENTER_JDBC_USERNAME / LIMITCENTER_JDBC_PASSWORD | 外部 Secret 提供的凭证                                              |
| LIMITCENTER_JWT_ISSUER_URI                            | 服务令牌的可信 issuer                                               |
| LIMITCENTER_JWT_AUDIENCE                              | limitcenter API audience                                            |
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

## 验证与后续

首批新增6个测试类、30个方法源码：规则版本/归属/稳定计数、哈希排序/租户/整数精度、旧 ACK 重放、当前阈值门禁、预占重试/金额冲突/到期不刷新、事务重试边界、JWT
服务绑定及落库解释完整性。第二批新增4类25个测试方法源码，覆盖首次许可与幂等重放、密钥轮换/删除/错误复用、到期边界、锁后重读、未知换尝试拒绝、原桶与多维度归还、执行后TTL不释放，以及事务失败边界。两批合计10类55个新增方法源码，均未编译/执行。测试使用的事务管理器仅记录单测边界，不能证明数据库回滚与行锁。

本轮未执行 Maven、JUnit、DDL 或部署。35个 POM 的 XML 解析及 git diff --check 完成；IDE 已检查部分编排/转换源码，持久化类及测试装配因尚未运行注解处理器而报告生成
Mapper/Converter 不可解析，不能视为编译通过。仍需窄范围 clean 构建验证 MapStruct/Flex 生成，以及真实 MySQL
并发的同意图/异意图竞争、多桶全回滚、策略升级与预占竞态、跨租户隔离和 JWT HTTP 验收。

product初始发布/ACK已有源码，后续继续其版本演进与集成验收；payment
Y2与可信事实适配仍待实施，包括、安全换attempt、Confirm/Release、自动过期扫描、退款原桶恢复、父级共享规则和历史记录。当前须显式调用expire处理未执行预占，不能将本批独立接入真实渠道支付。UNKNOWN/NOT_FOUND/超时均不能作为无扣款证明；执行后不得靠
TTL 回收额度。

## 付款终态确认与安全释放

新增 LimitSettlementFacade 及内部 POST `/api/limit/settlement/confirm`、`/release`，分别要求指定 payment SERVICE 身份和
pay:limit:confirm/release。confirm 接 LimitConfirmCommand 返回 LimitReservationDTO；release 接 LimitReleaseCommand，现返回
LimitReleaseDTO，含 fenced、付款版本/证据及可空 reservation。释放早于预占到达时只有持久 gate/回执，不构造假 reservation。

应用先在本地事务外使用独立挂载 SERVICE JWT 请求 payment `/api/payment/facts/payment`，核对
operation/payment/order/商户/金额/币种/原 initiatedAt 及终结版本/证据。confirm 还须为 SUCCEEDED 且成功 attempt
等于原活动尝试；release 必须为 CLOSED/FAILED 并持久执行封禁。HTTP 超时、空记录或仅提供 evidenceId 不修改额度。

锁序为 operation_gate→policy_head→原桶稳定顺序→reservation/items。使用原 policy/version/hash 和原 initiatedAt
恢复全部窗口，确认将所有桶 held→used 并确认全部明细，释放只减少原 held。策略升级不改变原归桶或重置终生额度；EXPIRED
已释放不重复扣减，EXECUTING 永不依赖 TTL 回收。

新增不可变 limit_settlement_receipt：同 operation 固定 CONFIRM/RELEASE、源版本和证据，业务摘要不含投递 eventId，因此相同语义换投递
ID 不二次记账；改参数或成功/未收款冲突拒绝。release 同事务写 FENCED，迟到 Reserve/BeginExecution
无法再开门禁。原桶、明细、预占状态、门禁和回执原子提交。

V20261022080000 新增本服务第9表。运行需
PAYMENT_BASE_URL/PAYMENT_JWT_AUDIENCE、LIMITCENTER_PAYMENT_TENANT_ID、LIMITCENTER_SERVICE_ID（默认
limitcenter）、LIMITCENTER_PAYMENT_TOKEN_FILE；出站凭证需 pay:payment:limit-facts。LIMITCENTER_PAYMENT_SERVICE_ID 默认
payment，限定入站写者。令牌每次读取文件并预检租户/sub/service/audience/有效期，payment 最终验签。当前一个部署显式配置一个出站租户，未配置租户拒绝。

本批未运行 IDEA 检查、构建、测试、DDL 或部署。退款额度恢复、跨 attempt 安全替换和自动过期扫描仍待实现；confirm 中 succeededAt
仅冻结来源请求元数据，归桶始终使用已核验的原 initiatedAt，不以成功日重建桶。
