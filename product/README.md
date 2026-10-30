# Product 订单准入与额度发布

> 当前开发基线正切换至 PostgreSQL；各服务业务迁移已合并为单文件，RPC 协议位于各服务
> client。下方历史验收描述保留其当时的数据库和运行状态；新基线尚需编译与真实 PostgreSQL 验证。

应用间传输已于2026-10-23统一为 gRPC，见[RPC 规范](../design/RPC.md)。本文件旧批次中的内部 HTTP 描述与 `*_BASE_URL`
要求由该规范取代；商户/运营 HTTP 入口继续保留。实际业务 gRPC 随本应用 `*-business` 构建和运行 profile 装配，端口50051；本批通过
IDEA MCP 完成六业务应用及依赖的 `clean test-compile`；未执行测试或部署。

本批补齐 COLA 的 product-app、product-infrastructure、product-adapter，落地 OrderAdmissionFacade 的持久化准入/原决定查询，以及
LimitPolicyPublicationFacade 的初始额度可靠发布、ACK与失败重放。复用框架合约主档、不可变
revision、ContractCodec、ContractRuntimeResolver、Spring JDBC、ID、HTTP、JWT 与统一响应；不复制 merchant_contract 主档。

## 能力与边界

| 能力       | 本批源码行为                                                                                                  |
|------------|---------------------------------------------------------------------------------------------------------------|
| 原决定回放 | 先查原 orderId；同主体、同完整意图回原 ADMITTED/DENIED，不刷新条款、截止或经营状态                            |
| 意图比对   | 固定 payee、产品范围、requestDigest、金额/币种、requestedDueAt 和调用主体范围；不能保持 digest 却更改其他参数 |
| 新准入     | customer 远程查询在本地事务外；锁本地合约后重查原决定，校验状态、有效期、框架使用条件和额度激活回执           |
| 停单门禁   | 准入持有框架 merchant_contract 行锁；框架 updateStatus 更新同一行并校验 generation，使状态变更与新准入串行    |
| 独立快照   | 冻结原合约 hash、单独 compiledTermsHash、强类型条款及字段来源；回放不依赖当前合约                             |
| 竞争处理   | 唯一键冲突逃出事务，回滚后再读胜出记录；网络超时/认证失败不持久化为允许或业务拒绝                             |
| 权限       | JWT 的主体/租户绑定，admit/read 独立权限，只允许原主体查询该准入；不接受请求体或普通 header 冒充主体          |

默认 bootstrap 仍为基础启动骨架。业务装配通过 Maven 与 Spring 的 product-business profile
同时选择。本批尚未执行编译、JUnit、迁移、启动或部署，不能据此宣称业务已经运行。

limitcenter
激活消费者与本服务初始额度发布/ACK工作流已有源码。只有冻结v1载荷、绑定Outbox且经过匹配ACK的ACTIVE回执用于新准入；缺回执仍持久拒绝，原DENIED不自动改判。没有手工设置ACTIVE入口。真实渠道适配与受控引用的可执行性验收仍待完成，额度ACK只代表规则就绪，编译后的渠道组合只表达合约允许范围，不能据此认定主交易链路已运行。

## HTTP 与身份

| POST JSON 路径                      | Command/Query                  | 权限              |
|-------------------------------------|--------------------------------|-------------------|
| `/api/product/admissions/authorize` | OrderAdmissionAuthorizeCommand | pay:product:admit |
| `/api/product/admissions/detail`    | IdQuery，id为admissionId       | pay:product:read  |

Controller 返回 SuccessResponse，业务失败由框架 FailureResponse 处理，HTTP业务报文使用200。DENIED 是成功获取到的持久业务决定，其
terms、contract/revision/generation、时间窗口均为空；依赖异常使用固定 PRODUCT_DEPENDENCY_UNAVAILABLE 错误，未完成的意图可恢复。

requestDigest 为64位小写 SHA-256。amountMinor 使用 CNY 分；requestedDueAt 必须带偏移且不超过微秒精度，归一为上海时间，首次准入时须晚于当前时间且不超过合约
maxWaitSeconds。NULL表示由后续 acquire 按原等待条款生成默认检查点。

认证方签发的 access JWT 需要 tenant_id、principal_type、sub、merchant_id 等可信绑定，语义同[customer](../customer/README.md)
。MERCHANT_APP 必须绑定商户，MERCHANT 的 sub 必须与 merchant_id 一致。权限由框架的 permissions claim 转换，普通角色不自动授予准入权限。

新准入调用 customer 时转发原已验证 JWT；令牌必须同时满足 product 和 customer 的 audience、issuer 与有效期要求。product 固定请求
CREATE_ORDER，customer 再验证该主体对目标商户的显式授权。未携带 customer audience 的凭证不能发起新查询；不将 product
自身的系统主体当作原商户主体。若部署采用独立签发方或单 audience，后续须接入可信 token
exchange；本批未实现交换，也不自行签发令牌。工作线程恢复原意图时，应由认证系统重新建立同一稳定主体范围，而不是持久保存旧 JWT
或伪造 header。

已存在的原决定无需再次远程查询；查询/回放仍需当前有效的 product 凭证和对应权限。回放不延长 materializeBefore：acquire 在 A1
必须检查原恢复期限，已落库的 INIT/PENDING 订单按原快照履约。这一行为不能当作当前合约的新单通行证。

## 合约编译与持久化

- 读取同一 product 数据源中的框架 merchant_contract 和 merchant_contract_revision。当前框架签约只产生不可变 revision
  1，本适配明确支持该形态，不臆造在线改版接口。
- 从 revision 中要求恰好一个包含 trade.amountModel 参数的收单条目，并复用 ContractRuntimeResolver
  判断签约使用条件；不任意挑选多条收单能力中的第一条。
- 可信条件事实沿用框架 merchant.code/type/industry/country，并提供 merchant.id/active/province/city，以及
  transaction.amountMinor/currency。未知事实由框架拒绝或判为不确定，不从商户 JSON 补充。
- 编译严格读取 CONTRACT-SCHEMA 的标量类型和已知路径，未知字段/枚举或不支持的 LIFO、自动迟到收款退款等策略拒绝。固定
  CNY、总额、全额计费、FIFO顺序退款语义不可配置绕过。
- AcquiringTermsDTO 新增 routingProfileRef、returnTargetCode，LimitPolicyRefDTO 新增 parentPolicyRef，避免编译丢失对应受控引用。两类
  hash 保持区分：原 snapshotHash 来自框架，compiledTermsHash 绑定 compilerVersion、contractId/revision、原 hash、typed条款与来源。
- 对 capability 组和 methods 集合排序后，使用框架 ContractCodec 的递归 map 规范化计算 SHA-256。私有 mapper 的 untyped
  数值转换使用 BigDecimal/BigInteger，不经过 double。compiled_terms 以 LONGTEXT 保存应用产生的原始
  JSON；读取时再次校验身份、版本、hash与Bean Validation。
- 新增 order_admission、contract_limit_publication 实际 Flyway
  迁移。前者保存完整意图和商户/授权事实版本；后者由下述可靠发布工作流写入并供准入读取。框架表及序列仍由框架迁移维护，设计SQL草案不能重复应用。
- READ_COMMITTED 本地短事务；同一合约等待锁后重读原 admission；orderId 唯一键兜底跨范围竞争。显式禁止本地外层事务包裹
  customer 网络调用。商户经营状态是读取到的可信事实，不承诺与 customer 状态变更的跨库全局零窗口。

materialize-seconds 默认300，允许1..86400；它是平台创建意图恢复窗口，不是商户订单寿命或存量续付期限。订单等待、延期、费率等仍取合约，不使用该配置代替商业条款。

## 初始额度可靠发布

| POST JSON 路径                            | Command/Query              | 权限                             |
|-------------------------------------------|----------------------------|----------------------------------|
| `/api/product/limit-publications/publish` | LimitPolicyPublishCommand  | `pay:product:publish`            |
| `/api/product/limit-publications/detail`  | IdQuery，id为publicationId | `pay:product:publication:read`   |
| `/api/product/limit-publications/replay`  | LimitPolicyReplayCommand   | `pay:product:publication:replay` |

这三个管理入口仅接受签名JWT中的OPERATOR或SERVICE；SERVICE要求sub=service_id，tenant_id禁止首尾空白，主体最长64字符。独立权限不会由商户角色继承；MERCHANT/MERCHANT_APP即使误配发布权限也被拒绝。现有订单准入JWT契约与customer委托保持独立。

publish要求payeeMerchantId、productScopeKey、expectedContractId、expectedRevision=1、expectedSnapshotHash。它锁框架合约行，核对预期不可变快照，拒绝停止或已到期合约；尚未到生效时间可预先准备额度，订单准入仍检查原生效时间。额度从该revision唯一收单条目的冻结配置生成，不接受HTTP提交任意阈值/规则或“已经激活”。完整收单标量配置必须通过既有编译器验证。

首批固定contractRevision=1、policyVersion=1，每个商户只有一个初始发布绑定；同policy也不能给另一合约重新绑定。limit.policyRef必须是该商户独占且稳定的策略ID，不同商户不能共用同一个ID。当前不提供改版、重签迁移、多产品共享发布或父级聚合的管理协议。八个DAY/WEEK/MONTH/LIFETIME ×
AMOUNT/PAYMENT_COUNT规则始终存在，未启用的规则enforcement=false、metering=true，阈值未配时为0。stable
ruleScope只绑定协议v1、可信tenant和payee（UTF-8长度分帧SHA-256），不含policyId/version或contractId，避免换策略引用重新生成商户终生计数范围。后续规则升级须沿用稳定身份并走独立显式版本协议。

本地READ_COMMITTED事务中一起保存INIT意图、框架Outbox事件并更新为PUBLISHING；Outbox只带publicationId，不带令牌或完整商业载荷。原命令重试校验源contract/revision/hash/merchant/scope，返回原发布和event，不重发新意图；合约锁等待后重查，数据库唯一键作为最终约束。唯一键/瞬时冲突整事务回滚后最多重试3次。初始占位INIT不会单独提交。

框架Outbox提交后领取，处理器验证type/schemaVersion/tenant/businessId及本地event绑定，读取并重新校验冻结的激活命令/规范化hash。限额HTTP调用在本地事务之外，使用独立服务身份；不是转发发布请求的操作者JWT。只接受成功响应信封，且ACK的policyId/version/hash与原意图完全相同、激活时间有效，随后短事务CAS写ACTIVE。远端成功后、本地ACK提交前崩溃，可重投相同版本/hash获得原激活时刻；并发重复ACK幂等。ACK不更改框架合约经营状态，暂停/到期仍禁止新单。

本地state与deliveryStatus独立：state=PUBLISHING表示尚无可信ACK，即使Outbox已FAILED也不放行。detail返回队列状态及本轮次数，不暴露载荷/令牌。Outbox默认最多8次、5秒起指数退避、2分钟租约；失败后只有replay权限可带审核理由重放FAILED事件，沿用原ID/载荷并由框架记录操作者和原因。不会在详情查询中自动重试或伪造ACK。ACTIVE且投递完成前崩溃的重放跳过HTTP，直接让框架完成事件。

新迁移V20261021200000为contract_limit_publication增加来源字段、冻结载荷、Outbox绑定、请求时刻和publication_schema_version，以及每商户初始绑定/Outbox唯一键。旧迁移保持原样；既有行保留schemaVersion=0，既不删除也不补造源信息，准入不再信任其ACTIVE。遗留记录须后续受控迁移，不通过直接改schemaVersion/ACTIVE解决。

## 业务装配与配置

在 Java 25 环境下，由开发者按需执行：

```bash
mise exec -- mvnd clean verify -pl product/product-bootstrap -am -Pproduct-business \
  -Dskip.spotless=true -Dskip.spotbugs=true

# 仅打包JAR与本地镜像；既有脚本默认跳过测试。
./product/build.sh -Pproduct-business

java \
  -jar product/product-bootstrap/target/product-bootstrap-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=product-business
```

| 环境变量                                                                | 用途                                                                    |
|-------------------------------------------------------------------------|-------------------------------------------------------------------------|
| PRODUCT_JDBC_URL / PRODUCT_JDBC_USERNAME / PRODUCT_JDBC_PASSWORD        | 独立product MySQL库和外部Secret凭证                                     |
| PRODUCT_JWT_ISSUER_URI / PRODUCT_JWT_AUDIENCE / PRODUCT_JWT_JWK_SET_URI | product资源服务器信任配置                                               |
| CUSTOMER_JWT_AUDIENCE                                                   | 原凭证委托给customer所需audience                                        |
| CUSTOMER_BASE_URL                                                       | 受控customer业务地址；需运行已完成配置的customer-business版本           |
| PRODUCT_MATERIALIZE_SECONDS                                             | 可选，平台恢复窗口，默认300秒                                           |
| PRODUCT_PUBLICATION_TENANT_ID                                           | 本部署允许管理/投递额度发布的唯一可信租户；准入接口仍按原多租户身份运行 |
| PRODUCT_LIMITCENTER_TOKEN_FILE                                          | 绝对路径，挂载独立服务access JWT的文件，不是token原文                   |
| LIMITCENTER_BASE_URL                                                    | 固定受控limitcenter-business地址                                        |

配置复用 loadup-components-http
的固定命名客户/操作，连接、池等待和读取超时分别为2/1/3秒，customer响应限制64KiB、limitcenter响应16KiB，无HTTP自动重试或重定向（额度重试由Outbox统一负责）。显式允许可信集群私网地址，目标origin来自配置，不来自业务请求；响应的生产信任还需部署配置提供受控TLS/网络保障。没有配置出站credential-ref，避免覆盖原主体Bearer凭证。

为只装配需要的框架存储/codec，业务配置关闭
loadup.modules.contract.enabled，并显式导入相应bean与Mapper。产品目录、签约和状态管理继续通过受控的框架管理集成维护同一数据库；本批未开放通用产品/签约管理HTTP入口。

额度投递令牌由外部认证方/代理维护，需满足limitcenter的issuer/audience/token_use=access、principal_type=SERVICE、sub=service_id、tenant_id=PRODUCT_PUBLICATION_TENANT_ID及pay:
limit:activate权限。每次尝试重新打开挂载文件读取，最多16KiB，支持Kubernetes
Secret/代理轮换；令牌过期/缺失仅导致投递失败，绝不默认放行。当前不实现OAuth取token或持久缓存旧JWT，不自行签发SERVICE令牌。单部署只配置一个出站租户，其他租户的发布入口拒绝；多租户出站凭证路由后续实现。不要把商户凭证用作后台投递身份。

业务 profile 开启框架Outbox和Flyway，迁移框架合约/序列/Outbox及product业务表和本次加法迁移；启动可能执行尚未应用的迁移。JVM/Clock/数据库会话使用上海本地时间，readiness
包含自身数据库状态。现有 deploy/pay.yml 尚未注入这些数据库、JWT、customer、limitcenter及挂载服务凭证配置，业务发布需准备相应配置覆盖层。

## 验证状态

准入首批新增6个JUnit类、33个测试方法源码，覆盖完整意图比对、原决定与原期限回放、停单/额度门禁、远程异常不落决定、锁后重查、回滚后读胜出者、本地事务边界、编译规则、可信响应、JWT委托、精确费率和快照完整性。没有执行构建或测试；单元事务替身与端口mock不能代替真实MySQL并发验收。

额度发布批次另新增7类31个测试方法源码，覆盖全八桶/稳定身份、源绑定与ACK幂等、同事务入队、远程调用事务边界、远端成功本地失败重试、详情并发读、失败重放审计、服务令牌轮换、信封绑定和载荷完整性；同样未编译/执行。单元事务替身只验证边界，不证明真实数据库与框架Outbox原子回滚。

本批IDE已检查部分编排/转换/HTTP/测试源码，发现的字符串语法错误已修正；生成Mapper/Converter尚未执行注解处理，不能认定编译通过。35个POM
XML解析和git diff
--check完成；未运行Maven、JUnit、DDL或部署。真实验收需覆盖：同数据源回滚、并发重复发布、Outbox租约重复、远端成功后响应丢失/ACK事务失败、错误签名/租户/audience、停合约与新准入竞态，以及遗留schema0记录拒绝。更多记录见[实施说明](../IMPLEMENTATION.md)
。下一步连接 acquire A0/A1与payment主链路；尚不具备完整外部收单交易链路。
