# Acquire 收单订单

> 当前开发基线正切换至 PostgreSQL；各服务业务迁移已合并为单文件，RPC 契约位于各服务
> client。下方历史验收描述保留其当时的数据库和运行状态；新基线尚需真实 PostgreSQL 与运行验收。

应用间传输已统一为 Dubbo Triple，见[RPC 规范](../design/RPC.md)。本文件旧批次中的内部 HTTP、gRPC 与 `*_BASE_URL`
描述由该规范取代；商户/运营 HTTP 入口继续保留。Triple 随本应用 `*-business` 构建和运行 profile 装配，HTTP 端口 8080、
Triple 端口 50051。本轮使用 Java 25 完成七应用及依赖的 `clean test-compile`，RPC 适配器单测通过；未启动服务或部署。

已实现普通创单/原调用主体查询、原单会话签发/能力认证/收银台只读摘要，并补齐商户付款发起、金额占用及权威结果刷新落账的 COLA
源码。复用框架 Money、JSON、BaseDO、ID、数据库/事务、生成 Mapper、Spring MapStruct、HTTP 和 JWT 资源服务器。业务默认币种为
CNY，订单、数据库会话与业务 Clock 使用 系统默认时区。

## 接口与身份

| POST JSON 路径                  | Facade                               | 权限                          |
|---------------------------------|--------------------------------------|-------------------------------|
| `/api/acquire/orders/create`    | AcquireOrderCreationFacade.create    | `pay:acquire:create`          |
| `/api/acquire/orders/detail`    | AcquireOrderCreationFacade.detail    | `pay:acquire:read`            |
| `/api/acquire/session/create`   | CheckoutSessionFacade.create         | `pay:acquire:session`         |
| `/api/acquire/checkout/create`  | CheckoutOrderFacade.checkout         | `pay:acquire:checkout`        |
| `/api/acquire/payments/start`   | PaymentInitiationFacade.startPayment | `pay:acquire:payment:start`   |
| `/api/acquire/payments/refresh` | PaymentProgressFacade.refresh        | `pay:acquire:payment:refresh` |
| `/api/acquire/orders/close`     | AcquireOrderClosureFacade.close      | `pay:acquire:close`           |
| `/api/acquire/orders/extend`    | AcquireOrderExtensionFacade.extend   | `pay:acquire:extend`          |
| `/api/cashier/order/summary`    | CashierSummaryFacade.summary         | 已验证的订单会话能力          |

detail 使用 IdQuery，id 为 orderId。Controller 返回 SuccessResponse/FailureResponse；完整 AcquireOrderFacade
继承创单、Checkout、付款发起和进度刷新切片，CashierFacade 继承独立摘要切片。关闭、延期均提供独立已实现切片；DirectPay 及
cashier 付款方法尚未注册实现。

资源服务器负责签名、issuer/audience、期限及 access-token 校验。入口额外要求签名
tenant_id、principal_type（MERCHANT_APP/MERCHANT/OPERATOR）及 sub；商户主体还需 merchant_id，MERCHANT 的 sub
必须等于该商户。身份不能由请求头或请求体覆盖。订单固定原调用主体范围；同商户其他应用和其他操作员也不能直接重用或查询该范围的订单。后续更宽的商户读权限需要独立授权设计。

当前采用原已验证 JWT 委托到 product，再由 product 委托 customer；签发方需同时授予 acquire/product/customer 所需 audience
与对应权限，并在 customer 建立实际收款商户授权。不会用统一服务身份替代原商户授权。令牌不进入数据库或诊断输出；令牌轮换不改变原主体范围。

## A0/A1 与恢复

1. A0 在主库 READ_COMMITTED 独立事务保存 order_create_intent，按 tenant+payee+merchantOrderNo 唯一，分配重试不变的
   orderId。保存完整非敏感命令，摘要 v1 使用固定字段序与 UTF-8 长度分帧，绑定租户、调用者、ORDER 入口、商户订单号、总额、币种、产品
   scope、标题和请求截止时间。
2. 提交 A0 后才向 product 的 authorize 发起有界 HTTP 请求。本地事务不跨网络；请求或传输失败保留 INIT，调用方以原命令重试。product
   根据稳定 orderId 回放原准入；首次准入检查当前合约及 customer/额度策略状态，原准入不因普通停单被重新判定。
3. A1 锁原 intent 并重读，校验准入绑定、明确 ADMITTED/DENIED、合约引用、typed 条款及时间。到达 materializeBefore
   的精确时刻也禁止首次落单。拒绝/过期先提交 REJECTED/EXPIRED，再返回稳定错误；不能因以后合约恢复而用同号创建另一单。
4. 对有效准入，同一事务保存 INIT acquire_order、不可变 order_contract_snapshot、INIT order_fee，并把意图改为
   COMPLETED。ADMITTED 不另开中间事务，避免订单和意图部分提交。费用以固定订单总额计算并冻结，尚未确认扣费；每单只有一个
   ORDER_AMOUNT 记录。
5. 已完成请求直接回放原订单，不再访问 product/customer 或重新计算费用；查询按当前订单字段返回余额进度和是否仍可付款，不因读取而推进生命周期。订单到期默认值以原
   admittedAt+waitSeconds 计算，请求值须落在原 maxWaitSeconds 范围。

A0/A1 拒绝外层本地事务。唯一键竞争或瞬态数据库冲突最多整事务重试3次；耗尽返回
ACQUIRE_CONCURRENT_UPDATE，客户端保留原意图。CAS、插入或提交失败依赖真实数据库回滚，不以进程内补偿假装事务成功。业务键不包含
deleted，不能通过逻辑删除复用商户订单号。

## 持久化与快照

实际迁移 `V20261021210000__acquire_order_creation.sql`
只创建4张本服务表：order_create_intent、acquire_order、order_contract_snapshot、order_fee。后续 V20261021220000 新增
checkout_session，随后 V20261022040000 新增 order_payment_allocation、V20261022050000 新增
order_payment_outcome，V20261022060000 再新增 order_close_intent，V20261022070000 新增
order_extension_receipt，实际合计9张表；退款调整表尚未创建。

configuration 保存 product 当前返回的完整 OrderAdmissionDTO 和强类型条款，使用 LONGTEXT，配套 payload_hash
校验本地持久副本，读取时逐项核对 intent、金额、主体、准入及合约元数据。product 的 compiled_terms_hash 作为可信上游编译结果引用原样保留；它包含
product 内部字段来源，不能由当前 DTO 独立重算，因此不将本地 payload_hash 冒充原编译哈希。字段来源仍由 product 的原
compiled_terms 保存。快照格式当前为1；前期按当前模型直接演进。

仓储使用参数化 Spring JDBC SQL，显式维护 tenant/deleted 条件以及业务状态、版本和审计字段。

## 构建与运行

默认 acquire-bootstrap 仍为基础骨架；业务包需要 Maven 和 Spring 两端启用 acquire-business。以下供开发者按需执行：

```bash
# 在 loadup-pay 根目录，按需执行窄范围构建及相关测试。
mvnd clean verify -pl acquire/acquire-bootstrap -am -Pacquire-business

# 既有脚本构建 JAR 和镜像，默认跳过测试。
./acquire/build.sh -Pacquire-business

java \
  -jar acquire/acquire-bootstrap/target/acquire-bootstrap-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=acquire-business
```

| 环境变量                                                                | 用途                                                            |
|-------------------------------------------------------------------------|-----------------------------------------------------------------|
| ACQUIRE_JDBC_URL / ACQUIRE_JDBC_USERNAME / ACQUIRE_JDBC_PASSWORD        | 独立 MySQL 业务库及外部 Secret 凭证                             |
| ACQUIRE_JWT_ISSUER_URI / ACQUIRE_JWT_AUDIENCE / ACQUIRE_JWT_JWK_SET_URI | 本服务已验证资源服务器                                          |
| PRODUCT_BASE_URL / PRODUCT_JWT_AUDIENCE                                 | 固定 product HTTP 目标及委托 audience                           |
| PAYMENT_BASE_URL / PAYMENT_JWT_AUDIENCE                                 | 固定 payment HTTP 目标与服务凭证 audience                       |
| ACQUIRE_PAYMENT_TENANT_ID                                               | 当前出站服务凭证所属租户；其他租户在占用前拒绝                  |
| ACQUIRE_SERVICE_ID                                                      | 出站 SERVICE 身份，默认 acquire，须匹配 payment 的受信配置      |
| ACQUIRE_PAYMENT_TOKEN_FILE                                              | 外部 Secret 挂载的服务 JWT 文件；每次请求重新读取以支持轮换     |
| ACQUIRE_CHECKOUT_PAGE_URL                                               | 同源托管 `/checkout` 的公开 HTTPS 地址；本地 loopback 可用 HTTP |
| ACQUIRE_CHECKOUT_SECRET                                                 | v1会话 HMAC 密钥，至少32个随机 UTF-8 字节，由外部 Secret 注入   |
| ACQUIRE_CHECKOUT_ACTIVE_KEY_VERSION                                     | 默认v1；切换版本须同时配置 checkout.keys 对应条目               |

业务 profile 启用 Flyway，首次启动会应用未运行的迁移；数据库会话 +08:00。还需按框架约定为部署配置稳定 ID sequence site。现有
deploy/pay.yml 未包含业务 DB/JWT 配置，业务上线需覆盖层显式注入 Secret/ConfigMap 和 SPRING_PROFILES_ACTIVE。

## 验证与待办

新增4类23个测试方法源码，覆盖 A0 后调用网络、稳定 ID 恢复、原完成回放、拒绝/截止持久化、参数冲突、主体隔离、竞争重读、精确数字/金额、规范化时间、JWT
委托及传输失败。测试事务管理器只记录边界，不证明真实 MySQL 回滚或行锁。

IDE 已检查关键编排、Converter、适配和测试源码；仓储因未在业务 target 生成 Mapper/Tables 仍有未解析类型。独立临时 Maven 目录中，以
Java 25 执行 javac -proc:only 验证两个 Converter、四个 Mapper、四个 TableDef 和 Tables 的注解生成；不等于业务编译通过。未运行
Maven、JUnit、DDL 或部署。

DirectPay 受理、付清费用确认与延期源码已补；后续继续完整渠道执行、结果后台收敛、自动关单、Outbox/等待任务及商户通知。当前收银台只展示订单，不发起付款或自动关单，不能视为可对外收款的完整主链路。

## 会话签发、重放与托管页面

可直接 checkout → 打开 checkoutUrl，也可普通 create → session.create → 打开 checkoutUrl。一步创单与独立续发的事务边界见下节。

session.create 锁定原订单并校验原调用者，读取原持久条款，完全不访问当前 product/customer。新会话仅允许可续付的
INIT/PENDING；CLOSE 策略期限取 sessionSeconds 与原 orderDueAt 的较早值，KEEP_PENDING 按原 sessionSeconds
签发，统一向下对齐到秒。期限不能等于或早于当前时刻。

幂等键为 tenant+orderId+requestKey；摘要 v1 固定租户、原主体、订单、requestKey 与归一化的 returnTargetCode。省略回跳 code
等于原合约默认值，只允许该已审核 code；不接收任意回跳 URL。相同键先回放原
sessionId/token/expiry，过期、取消或订单已经终态也不改写会话；换键只给仍可续付订单签发新会话，不取消其他已签会话，也不迁移任何付款动作。

会话 scope 版本1仅含 ORDER_SUMMARY，不能借它调用商户创建、退款或付款接口。HMAC-SHA256 规范输入沿用 CLIENT-API 2.5 的
LF/epoch/nonce 协议，输出 sessionId.base64url (mac)，nonce 为安全随机32字节。DB只保存
token_hash、nonce、key_ref/version。幂等重放用原密钥版本重建并核对原摘要；旧版本密钥缺失或被同版本替换返回
SESSION_UNAVAILABLE，不偷偷换 token 或续期。多副本须装载相同键配置；轮换先部署新旧版本，再切
active-key-version，并保留旧键到重放窗口结束。删除旧键只阻止凭证重建，已有 token 的摘要认证仍受原期限/状态及 revoked_at
控制，不能把删键当作撤销会话。

公开 URL 固定为配置的 `/checkout`，tenant/order/token 只在 fragment，不进入 query、HTTP access log 或渠道回跳。托管页面将
fragment 从历史记录移除，token 只保存在当前页面内存，以 Authorization: Bearer 发送；刷新按钮可查询原订单，浏览器整页重载需重新打开原链接。页面无第三方资源、无
localStorage/cookie token，设置 no-store/no-referrer/CSP，标题只用 textContent。人民币分值按精确整数展示；浏览器无法保留大整数时拒绝展示金额，不静默舍入。

页面与 `/api/cashier/order/summary` 需要同一公开 origin，可由网关将它们代理到 acquire。当前不开放跨域
CORS。返回摘要不含费率、合约/策略、内部凭证或其他订单，商户显示名暂未持久化，返回 null；支付 choices 为空，页面仅展示状态与刷新按钮。

## 会话认证边界

cashier 使用独立优先级90安全链，早于框架优先级100的商户 JWT 链。仅 POST summary 进入能力认证服务，其余 cashier API 全拒绝；商户
API 继续走框架已验证 JWT。摘要 Facade 每次调用都通过 request-backed provider 检查能力，不能仅凭 permitAll 路由或 IdQuery
读订单。

X-Checkout-Tenant 只是有界的数据库定位值，不是可信商户身份。定位后必须恒定时间匹配 token 摘要，检查状态
ACTIVE/COMPLETED、当前时间严格早于 expiresAt、ORDER_SUMMARY 范围及浏览器 Origin，再建立只能读该 orderId 的作用域；重复
Authorization/租户头拒绝。summary 还核对 IdQuery 与该 scope 一致，并在加载订单后再次检查期限。PAID/CLOSED
可只读查看；INIT/CANCELLED/EXPIRED 会话拒绝。到期即拒绝，不依赖扫描器提前将数据库状态改为 EXPIRED。撤销管理使用专门商户 JWT
权限与永久 revoked_at 标记，见本文末节。

本批新增5类29个测试方法源码，连同前批业务切片累计9类52个方法源码，均未运行 JUnit。关键业务/安全/转换及测试源码的 IDEA
错误检查完成；临时 Maven 样式目录的 javac -proc:only 成功生成两个新 Converter 和 session Mapper/TableDef/Tables。Node
检查真实页面脚本语法，并在无网络 VM 样例中核对 fragment 清除、大整数分值、纯文本标题、缺失凭证及旧解析器精度保护；未做真实浏览器视觉验收或真实
JWT/MySQL/HTTP 集成验收，未构建/部署业务镜像。

## Checkout 一步创单

POST `/api/acquire/checkout/create` 调用 CheckoutOrderFacade.checkout，要求已验证原商户 JWT 的 `pay:acquire:checkout`
权限，以及 product/customer 准入所需权限及 audience。返回 SuccessResponse<CheckoutDTO>，含订单、首次 sessionId、checkoutUrl
和原期限；响应 no-store。当前链接只能查看订单摘要，付款按钮尚未接入。

A0 在 tenant+payeeMerchantId+merchantOrderNo 唯一键下固定 CHECKOUT 入口、原始 returnTargetCode（包括省略值）、orderId 和
initialSessionId。普通 create 的 ORDER v1 摘要与 JSON 载荷不变；两种入口不能同商户订单号互换。Checkout 的独立 v1 摘要增加回跳
code，省略后再显式传入默认 code 也属于修改创单意图；独立 session.create 则仍按归一化默认值比较。

product 网络请求在事务外。A1 先锁意图并复核准入，再准备会话签名/URL；订单、冻结条款、整单 INIT 费用、首次 ACTIVE 会话及
COMPLETED 标记在同一事务提交。会话在领域内先 INIT 后激活。首次会话 requestKey 固定为 `initial/<initialSessionId>`
，只用于标识该已固定会话，不能据此创建另一笔付款。签名/存储失败使 A1 回滚，保留 A0 稳定 ID；准入拒绝、materializeBefore 截止、回跳
code 不匹配或落单时已无法签发有效会话，分别持久化原拒绝/到期结果后返回固定错误。

完成重试只读取原订单及 initialSessionId 指向的会话，返回同一 token/期限；不重新准入、不重算费用、不另建会话，过期或终态也不续期。原会话缺失返回
SNAPSHOT_INVALID，旧密钥不可用返回 SESSION_UNAVAILABLE。需要续发时使用独立 session.create 的新 requestKey，受原订单和条款限制。

新增迁移 `V20261022010000__checkout_order_creation.sql`
，仅扩展意图，不增加业务表；结构说明见[数据库设计第12节](../design/DATABASE.md#12-checkout-一步创单结构)
。本轮新增14个测试方法源码（创单12、编码2），累计 acquire 9类66个方法。仅做 IDEA
静态错误检查和差异检查；未执行构建、注解生成、JUnit、数据库迁移或部署。真实五表回滚及重复请求竞争仍待环境验收。

## 创单意图映射修复

OrderCreationStorageConverter 显式双向映射 entryMode/initialSessionId，保持严格未映射目标检查。OrderCreateIntent
只保留当前完整构造器；调用方和测试统一显式提供入口与首次会话字段，新增两个持久化往返回归测试方法源码（累计10类68个方法）。

本地排查发现 domain/target 中模型已更新，但 Maven 本地安装的 acquire-domain JAR 仍无这两个字段；单独构建 infrastructure
若解析到旧 JAR，会出现未映射属性或源属性不存在。请在仓库根目录将受影响模块及其依赖放入同一 reactor，避免引用陈旧安装产物：

```bash
mvnd clean compile -pl acquire/acquire-infrastructure -am -Pacquire-business \
  -DskipTests -Dskip.spotless=true -Dskip.spotbugs=true
```

此命令供本地执行，本轮未运行构建或 JUnit。IDE 检查不等于注解处理成功。

## 商户付款发起与 A2 金额占用

POST `/api/acquire/payments/start` 接收 PaymentStartCommand，必须是原订单调用主体且具有 `pay:acquire:payment:start`
权限。当前受理支持 H5 + H5_SCENE（微信要求真实终端 IP 与场景）、PAYMENT_CODE + 付款码、JSAPI + 同渠道身份票据；NONE
只保留空输入语义，不能替代渠道必填场景。选择必须属于原冻结合约能力。敏感输入由 payment
加密准备，不写入 acquire 数据库。商户手工指定正整数人民币分值，不改变订单固定总额。

A2 在主库独立 READ_COMMITTED 事务中先锁原订单，以 tenant+orderId+merchant/原主体hash+actionKey 定位动作。新动作固定
allocationId/paymentId，领域校验可付款窗口、剩余金额、单笔上下限/尾款例外和成功笔数上限（包含已占用笔数），同事务增加订单占用/笔数并插入
HELD allocation 与固定付款意图。H5_SCENE 与其他敏感方式先保存无原始输入的固定标识/条款，再在独立步骤附加已加密引用。订单进入
PENDING，已付金额仍为0。只更新占用/状态/版本四列，不覆盖原截止版本或条款。

提交后才调用 payment.accept；同 actionKey 重试先校验摘要、金额、选择和原条款，始终沿用原
paymentId/命令。原动作恢复不受当前订单截止影响，也不重查当前合约；新动作仍遵守原订单期限。同键改参返回幂等冲突。非 HELD
动作只查询既有付款，不能重新占用或另建付款。网络失败、远端拒绝、响应错配均保留已提交占用，不能据此释放。

跨服务调用复用 LoadUp HttpTemplate，使用独立挂载的 SERVICE JWT，不转发商户令牌；每次读取文件，预检租户/服务身份/sub/audience/exp/nbf，最终签名/issuer
校验由 payment 资源服务器负责。目前单个部署显式配置一个出站租户凭证，其他租户在占用前拒绝；多租户凭证解析后续再实现。令牌和原输入不持久化、不输出诊断。

## 权威付款结果刷新与 A3 落账

POST `/api/acquire/payments/refresh` 接收 `{ "orderId": "...", "paymentId": "..." }`，要求原订单主体及独立
`pay:acquire:payment:refresh` 权限。先读取原订单/占用并核验归属和冻结命令，在本地事务外请求
payment.outcome；不接收商户提交的付款状态、证据或金额作为资金事实。

远端返回 PaymentOutcomeDTO：一致的付款视图和持久执行封禁标志。INIT/PROCESSING/UNKNOWN/CLOSING 不推进订单。成功要求正
sourceVersion、成功 attempt 和终结证据；FAILED/CLOSED 还要求单笔执行门禁已持久 FENCED，才允许释放。不存在记录、HTTP
超时、终态无证据或未封禁，均不能把占用变成可付余额。

A3 锁序为 acquire_order → order_payment_allocation。在锁内重新核验原命令/主体，按领域 applyPaymentOutcome
更新订单和原占用，并插入唯一不可变 order_payment_outcome 回执，三者同事务提交。成功将 HELD→CONSUMED、减少占用并累计 gross
已付金额/成功笔数，恰好付清转 PAID；明确未收款将 HELD→RELEASED，已付金额不减少。订单截止不阻止原在途结果落账。paid_at
为本服务首次确认付清的本地时间，不冒充渠道支付时间。

重复刷新必须与原回执的付款、金额、版本和证据完全一致，只返回当前订单，不重复累计/释放；冲突或倒退的远端结果拒绝覆盖。成功后的
gate 封禁不会改变已经确认的成功资金事实。PAID/CLOSED 订单不重开，退款继续独立。

当前仅请求触发刷新，start 返回本身不落账；仍需调用 refresh，后台扫描/可信事件、Outbox 通知、整单费用确认及 limitcenter
确认/释放后续接入。收银台页面与会话 scope 继续仅摘要，不因商户接口增加而得到付款权限。

## 本批开发状态

付款发起批次新增3类23个测试方法源码；在本次暂停验证指令之前，其 acquire-business 窄范围
`mvnd clean test-compile -pl acquire/acquire-bootstrap -am -Pacquire-business` 已通过10模块，MapStruct 与 allocation
Mapper/TableDef 已真实生成，JUnit 未执行。随后新增的 outcome/refresh/回执落账源码按用户要求暂不做 IDEA
检查、构建或测试，不能沿用前批编译结论。两项新迁移均未执行，业务镜像未打包部署。

## 商户显式关单与可恢复收敛

POST `/api/acquire/orders/close` 接收 AcquireOrderCloseCommand (id/requestKey/reasonCode)，要求原订单主体及
`pay:acquire:close` 权限。C0 在原订单行锁下持久化首个 order_close_intent，并将 INIT/PENDING 转 CLOSING、固定
closeEpoch；新付款/会话不能再进入。关闭请求固定租户、主体、订单、key 和原因；相同请求继续原工作流，换 key 或改原因均冲突。首次请求遇到
PAID/CLOSED 拒绝，已经存在的原关闭请求允许只读回放当前终态。

C0 提交后用独立 SERVICE JWT 调 payment.fenceParent，再逐笔调用 payment.close，所有网络调用都在本地事务外。远端 requestKey
固定为 order-close/closeIntentId，不直接拼接最长128字符的商户 key。服务凭证新增需要 pay:payment:close/fence/fence:read
权限，出站租户及 audience 限制保持不变。

每次最多处理16笔 HELD，按 paymentId 排序并持久推进扫描游标；空字符串代表新一轮扫描。处理中/未知付款保留 HELD 和
CLOSING，仍推进游标，避免第一批未知付款阻塞后面付款。并发重试按 intent.rowVersion 判断，过期响应不能回退游标。网络或数据库失败保留
C0 和已落账结果，原请求可重试；当前尚无自动调度器。

已受理付款复用 PaymentOutcomeOperations 的同一 A3 落账逻辑，成功累计 gross、明确未收款才释放。尚未受理的付款必须由
payment.close 返回 payment=null、持久封禁及 noCollectionEvidenceId，才记录独立 NOT_ACCEPTED 事实并释放原占用。其
paymentSourceVersion=0，表示没有付款记录，不把 fenceEpoch 冒充付款版本，也不构造假 CLOSED payment。

NOT_ACCEPTED 原动作再次 start 返回 ORDER_NOT_PAYABLE；refresh 返回 payment=null 和当前真实订单视图，并核对原回执。已受理的
CLOSED/FAILED 仍使用正付款版本的 NO_COLLECTION 事实。

最终再次获取 parentFenceStatus，再锁订单重读；仅当父门禁覆盖原 closeEpoch、全部远端子付款收敛、金额和笔数占用均为0、全部本地
allocation 均有唯一终态回执时才写 CLOSED/closedAt。否则返回 CLOSING，调用方保留原请求继续推进。在途成功可能先将订单付清为
PAID，关闭流程保留该成功终态；已经累计的支付金额不因关闭或独立退款减少。部分收款后显式关单不自动发起退款。

PaymentProgressService 已将资金事实落账提取为内部 PaymentOutcomeOperations，供刷新和关单复用。刷新会写入资金事实，因此公开入参统一改为
PaymentProgressRefreshCommand，不保留旧 Query 别名。新增 order_close_intent 为本服务第8张表；此前尚未执行的结果回执迁移按当前模型直接加入
NOT_ACCEPTED 和0版本配对约束，execution_fenced 使用 TINYINT (0/1)。

本批按用户要求仅开发，未运行 IDEA 检查、编译、测试、DDL 或部署。真实渠道关闭、未知结果后台收敛、额度确认/释放、费用确认、到期任务和通知仍待接入。

## 原订单延期与不可变回执

POST `/api/acquire/orders/extend`，输入 AcquireOrderExtendCommand (id/requestKey/requestedDueAt/expectedVersion)
，要求原订单调用主体和 `pay:acquire:extend` 权限。requestedDueAt 必须显式带偏移，归一到本地时区后使用微秒精度，超出 MySQL
年份范围或微秒精度的值拒绝，不静默截断。expectedVersion 是原订单视图的 version，区别于内部 deadlineVersion。

同订单行锁内先查 tenant+orderId+requestKey 回执：同参返回当前订单，不能再次延期；改变截止时间或 expectedVersion
均冲突。已有回执重放不检查当前可延期窗口，订单随后到期、CLOSING/PAID/CLOSED 或再次延期也不重新执行；当前订单及
deadlineVersion 必须覆盖回执，否则视为快照损坏。首次请求若 expectedVersion 已落后，返回 VERSION_CONFLICT；修改参数后提交必须使用新
requestKey。

新请求读取本地冻结合约，要求原条款 extensionAllowed=true、订单仍为可续付 INIT/PENDING。CLOSE 策略在精确截止时刻即禁止延期；KEEP_PENDING
可在原截止之后延期，但新截止仍必须晚于当前时刻。CLOSING/PAID/CLOSED 不可创建新延期回执。

原始截止由持久 ORDER/CHECKOUT 创单意图和原准入恢复：商户指定值优先，否则为原 admittedAt+waitSeconds。累计上限始终是
originalDueAt+extensionMaxSeconds，多次延期不把上一次截止当作新基准。新截止必须严格晚于当前截止，不能缩短期限、重置累计额度或修改原创单意图。当前
product/customer/合约停单状态不参与原订单延期重判。

同一事务只更新 orderDueAt、deadlineVersion+1 和 order.version+1，并插入不可变
order_extension_receipt。回执冻结主体、请求摘要、原始/前次/新截止、expectedOrderVersion、新 deadlineVersion 与
appliedAt；两项唯一键保护动作和截止版本。延期与付款/结果落账/关单使用同一订单锁；任一写入失败整事务回滚，不保留半个延期。

本批不调整订单金额、占用、状态、费用、原条款或已有付款执行时限；也不延长原收银台会话 token 的有效期。需新链接时仍通过
session.create 的新 requestKey 签发。deadlineVersion 已为后续到期任务提供变更代次，自动任务注册与旧任务淘汰尚未实现。

新增 V20261022070000__order_extension_receipt.sql，acquire 实际9表。按用户要求本批未运行 IDEA 检查、构建、测试、DDL 或部署。

## 整单费用确认

PaymentOutcomeOperations 在订单恰好付清的 A3 事务中确认原 order_fee。按订单→allocation→fee 锁顺序，复核原总额、冻结费率/舍入规则与原费用金额，只从
INIT 更新为 CONFIRMED，保存与本次付清确认相同的本地时间；部分付款不分次计费。原占用、订单、终态回执和费用任一写入失败均回滚。

确认使用原合约费用策略，保持 baseAmount/feeAmount/ruleSnapshot 不变，不把退款从 gross
已付金额扣除。重复事实回放不会重复确认。退款累计基数与独立退费调整后续接入。本批未运行检查、构建或测试。

## 扫码枪输入与纯 API DirectPay（新增源码）

`startPayment` 已接入付款码和 JSAPI 输入准备。A2 提交固定 allocationId/paymentId、金额和原条款后，在事务外调用
payment.inputs.prepare；只将返回的不透明 inputRef 在原订单锁下附加到 HELD 意图，CAS 仅更新
intent_payload/payload_hash/row_version，不改变金额或笔数。附加最多一次；关单或释放先完成时不能再附加。远端 prepare/accept
失败保留原占用，原 actionKey 重试；已准备输入的敏感值一致性由 payment 的原 HMAC 指纹核验，不在 acquire
存明文或原始码普通哈希。过期不会自动换码或自动释放；须安全关闭原付款后用新 action 发起。

新增 DirectPaymentFacade 与 POST `/api/acquire/payments/direct-pay`，输入 DirectPayCommand，返回 DirectPayDTO。直接组合已有
create → startPayment → detail，各步骤独立提交，不创建 CheckoutSession，不承诺跨服务原子扣款。要求
`pay:acquire:create/read/payment:start`，首笔金额为正且不超过固定总额；支持首笔部分付款和后续 start。merchantOrderNo
固定创单幂等，actionKey 固定付款幂等，重试保留两者。后续确需另一笔付款应使用不同 actionKey；网络异常不得换 key 盲重扣。返回读取
A2 后的当前订单与实际付款受理结果；当前 payment 仍只受理 INIT，真实扣款执行/动作尚未接通。

普通 create→startPayment 同样是纯 API，付款码由商户扫码枪读取后放入 type=PAYMENT_CODE 的 interaction；JSAPI 传同渠道票据，H5
按渠道要求传 H5_SCENE；微信需要真实终端 IP 与 WAP/IOS/ANDROID 场景。JSAPI/H5 后续执行返回的 action 仍需商户前端交互，不能将
API 返回 INIT 当作收款成功。SERVICE 出站 JWT 新增
`pay:payment:input:prepare` 权限，复用同一 payment HTTP 客户端和租户限制。

该批源码未运行 IDEA 检查、构建、测试或部署；新的敏感输入路径、只附加引用的 CAS、DirectPay 和真实 MySQL 事务/竞争验收仍待执行。

## 2026-10-23 付款事件接收与语义 ACK（新增源码）

新增 PaymentOutcomeFacade.accept 的应用实现与 POST /api/acquire/events/payment-outcome，返回
SuccessResponse<PaymentOutcomeAckDTO>。仅 ACQUIRE_PAYMENT_SERVICE_ID（默认 payment）指定受信 SERVICE 且有 pay:acquire:
payment:outcome 可调用；从已验证 JWT 读取 tenant/service/subject/权限，商户 JWT 不获得该入口。原商户查询/刷新仍使用原
AcquireActorProvider，事件不接收调用方自行声明的 owner scope。

首见事件核对 type/schema/producer/aggregate/sourceVersion/businessEvent/evidence，再从原订单读取 merchant owner、原
allocation 和冻结命令。回查 payment 的 /api/payment/facts/payment（在本地事务外），逐项核对 operation、实际
payee、order/payment/allocation、金额/CNY、原终态/版本/证据及执行封禁。已有成功之后新增 fence 可以兼容原正事实，负终态必须有持久
fence。事件标签或公开 evidenceId 本身不作为资金证明。

金额收敛复用原 PaymentOutcomeOperations：原 converge 事务体抽为 convergeLocked，由本次接收事务调用；原商户
refresh/关单路径继续调用同一实现。order→allocation 锁下核对原命令和既有付款回执，HELD 只会消耗/释放一次；满额转 PAID
并按原订单总金额同事务确认费用，部分付款保持 PENDING。新语义接收回执与订单/占用/付款事实/费用同事务提交，失败不返回成功
ACK。不重开 CLOSED/PAID，真实迟到收款和补偿仍按已有门禁/后续补偿范围处理。

V20261023010000__payment_event_receipt.sql 新增 acquire_payment_event_receipt，tenant/deliveryId
唯一，存原完整事件摘要、来源服务、payment/order/allocation、版本/证据/businessEvent、首次观察到的 orderVersion 和本地接收时间。它是业务
Inbox 和 ACK 证据，能够绑定原参数并回放 ACK；通用 Inbox 只有消费去重标识，不能单独满足本用例的参数冲突/ACK 回放，因此不双写第二份通用
Inbox。

原 delivery 重放校验完整摘要并返回首次 ACK，不依赖 payment 此时可用；改金额/时间/来源等原参数拒绝。新 delivery 指向同资金事实时仍由既有
allocation outcome receipt 幂等，不重复加支付金额/确认费用。即使原商户 refresh 已落账，也能为后到事件生成接收回执。ACK 含
deliveryId/businessEventId/paymentId/paymentVersion/evidenceId/orderVersion/accepted，orderVersion 是首次接收时观察版本，后续订单变化不改旧
ACK。

acquire 原 ACQUIRE_PAYMENT_TOKEN_FILE 的 SERVICE 凭证需新增 pay:payment:acquire-facts；payment 事实入口对该角色额外限制原
callerServiceId=acquire，limitcenter 仍保留原独立 scope。新增命名 HTTP 操作 payment/facts，无商户 token 转发。所有时间
DATETIME (6) 本地时区，BaseDO/MapStruct/生成 Mapper/TableDef 和 QueryWrapper 不变。本批及新增测试源码未执行任何检查/构建/测试/DDL/部署。

## 2026-10-23 订单版本快照与 realtime 投递（新增源码）

OrderSnapshotEvents 已接入创单（ORDER/CHECKOUT）、A2 金额占用、A3 付款成功/释放、关单 CLOSING/CLOSED
和延期事务。各次订单版本写入后，在原可写事务中读取刚落库的完整订单头，校验精确 rowVersion，冻结真实
BaseDO.createdAt/updatedAt、原商户/订单号、固定总额、累计成功额、占用额和状态，写入同库
Outbox。不得用合约准入时间或发送时刻代替原始创建时间。重复请求/既有付款事实回放不重写版本，也不新增快照；会话/输入引用/关单游标变化不修改订单版本，不单独生产快照。延期投影推进源版本和更新时间，目前快照字段不含截止时间。

独立 OrderSnapshotOutboxHandler 处理 pay.acquire.snapshot.realtime.v1，通过命名 HttpTemplate realtime/order POST
/api/realtime/projections/order。worker 校验可信租户、已登记 PROCESSING 队列元数据和完整类型化信封；事务外重放原持久负载，不回查此时订单，也不在交易事务中请求
realtime。ACK 必须匹配原 delivery/businessEvent/producer/aggregate/sourceVersion，projectedVersion 不小于源版本，且
disposition 为 APPLIED/DUPLICATE/STALE。异常沿用框架重试与原事件身份；at-least-once 不作为财务互斥。

acquire-business 新增 REALTIME_BASE_URL、REALTIME_JWT_AUDIENCE、ACQUIRE_REALTIME_TOKEN_FILE；ACQUIRE_REALTIME_TENANT_ID
默认沿用 ACQUIRE_PAYMENT_TENANT_ID。凭证是 acquire SERVICE 专用挂载 JWT，需 pay:realtime:projection:order；realtime 接收端
REALTIME_ACQUIRE_SERVICE_ID 默认 acquire。每次发送重读文件以支持凭证轮换，预检 tenant/service/audience/epoch
exp/nbf，接收资源服务器仍负责实际验签。原 payment 客户端复用 acquire 本地凭证读取器；不同目标凭证文件及 audience 仍独立配置。

新增事务失败/租户/版本、原时间和金额、重复投递/乱序 ACK、严格 JSON 解码及凭证轮换测试源码，并同步既有服务构造器与重放断言。未运行
IDEA 检查、构建、测试、DDL 或部署；真实 MySQL 同事务回滚、Outbox dispatcher、服务鉴权和 realtime
端到端验收待执行。商户通知、退款和付款中间态投影、到期自动调度仍按 BUSINESS-IMPLEMENTATION.md 待办。

## 2026-10-23 退款费用基数与独立调整持久化（新增源码）

OrderFeeGateway 新增内部 applyRefundBasis，调用方必须已验证 payment 的权威累计退款事实并持有原订单锁及同库可写事务。后续
Dubbo Triple 接线见下一节；不公开商户记账端点。

同一 order_fee
锁下核对原订单总额、冻结规则和原费用，再更新累计成功退款依据。相同源版本必须同金额；旧版本只允许不大于最新累计的事实；新版本不能减少累计，均不能超过原总额。费用未确认时仍保存基数，不提前退费。confirmPaid
在原确认事务中读取既有依据并补算应退，退款先到或后到均使用同一累计规则。

仅对正差额写独立 fee_adjustment，然后 CAS 保存费用状态、原累计基数、adjustedAmountMinor 和行版本。退费额按原整单费用及原合约累计规则计算，原
baseAmount/feeAmount/ruleSnapshot、订单状态和 gross 已付金额不变。无应退差额不写零记录；唯一触发键 FEE_CONFIRMED 或
REFUND_SUMMARY:源版本，重复重放不再次调整。确认补算的 source_event_id 使用原 feeId，配合 FEE_CONFIRMED
识别本地确认事实；退款触发保留经验证的源事件标识。

V20261023060000__fee_adjustment.sql 新增调整表，存原费用/订单、触发键/源版本/事件、本次正差额、成功退款累计、原规则与本地
appliedAt；审计字段由 BaseDO 监听器生成。插入调整或费用 CAS 任一失败整事务回滚。生成 Mapper/TableDef 和 Spring MapStruct
负责持久映射，未手写业务 SQL。

新增领域冲突/乱序和持久层早到基数/确认补算/差额/失败传播测试源码，未运行构建、检查、测试、DDL 或部署。实际 MySQL 回滚/竞争与
Dubbo Triple 链路仍待验收。

## 2026-10-23 退款费用 gRPC 与接收回执（新增源码）

`AcquireRpc/FeeRefund` 接收 `EventEnvelope<OrderRefundSummaryEventDTO>`，要求配置的 payment SERVICE 和
`pay:acquire:fee:refund`。acquire-client 通过 proto import 引用 payment 拥有的六字段金额消息，不另造金额模型。商户凭证不能调用。

首次接收在本地事务外，经 `PaymentRpc/RefundSummary` 查询权威累计退款；查询要求原 acquire SERVICE 及
`pay:payment:refund-summary:read`。先校验来源、订单/商户/币种、源版本和总额边界。相同版本必须完整金额一致；旧消息允许采用已验证的新版本累计值，ACK
同时保留 incoming sourceVersion 与 verified appliedVersion。已保存费用可能更晚，此时按领域单调规则忽略较旧依据。退款占用不作为成功退款计费依据。

然后在 acquire 可写事务中按订单→费用加锁，验证原准入快照和冻结费率，保存费用基数/应退正差额，并插入 `fee_refund_receipt`
，最后返回 `FeeRefundAckDTO`。退款事实早于付款落账也可先保存依据；订单终态不影响独立退费，但退款不改变原订单总额、gross
已付金额或终态。费用确认仍在付款落账原事务中补算早到依据。

V20261023070000__fee_refund_receipt.sql 保存 delivery/businessEvent/order/sourceVersion/appliedVersion、可信来源服务、完整请求摘要和首次
acceptedAt；tenant/delivery 唯一。摘要按 UTF-8 长度分帧，协议时间转绝对时刻，防止字段串联歧义及 wire 时区归一化误判。相同
delivery 改参拒绝；原参数重放返回首次 ACK，无须远端再次可用。回执不能先于费用提交；任一步失败整事务回滚。

复用 `ACQUIRE_PAYMENT_TOKEN_FILE` 和 `PAYMENT_ACQUIRE_TOKEN_FILE`，分别增发上述最小权限，无新目标地址/凭证配置。业务
profile 已有双向 gRPC channel/JWT 装配。新增权限与首次部署 DDL 需随发布提供；本批没有签发令牌或执行迁移。

已补首次/重放、旧版本采用新事实、同版本冲突、服务与权限隔离、锁外查询/事务内落账、失败传播及 RPC 精确 int64/客户端调用测试源码。未运行
IDEA 检查、构建、测试、DDL 或部署；真实数据库原子回滚、多副本竞争及真实 gRPC/Outbox 验收仍待执行。

## 商户异步通知（2026-10-24）

已接通 acquire 的 PaymentSucceeded、OrderFullyPaid、OrderClosed 三类源码。PaymentSucceeded 在原付款结果的 acquire
收敛事务中登记；两个订单终态在原订单快照事务中登记。成功付款可能同时产生单笔成功与整单付清事件，eventId/type
各自独立，不保证送达顺序。OrderClosed 可包含部分已付金额，不表示退款完成。停用通知的原合约不生产投递记录；在途事件只使用订单冻结通知条款，不读取当前新合约。

新增 merchant_notification_delivery 与首轮 DelayedTask 同源事务提交，不跨锁调用网络。eventId 按租户/type/聚合/来源版本确定，固定
UTF-8 JSON body、摘要、occurredAt、金额/汇总及通知策略保持不变。这里直接复用组件原生任务的同库事务登记，不再增加只用于转交的重复
Outbox。源交易的既有 realtime Outbox 保留独立职责。

任务以 deliveryId:已启动次数作为代次身份。短事务先锁投递行、增加尝试计数并登记不同身份的后继任务，再在事务外发送；发送前后中断不会丢失后继。旧代次再次领取不重发。正常未
ACK 由合约的指数退避/上限/窗口推进；框架的原生失败重试只处理基础设施异常，不能替代合约窗口。窗口截止或尝试许可耗尽记
EXHAUSTED。尝试数按已提交发送许可计，进程中断和缺配置也可能消费许可；至少一次投递要求商户按 eventId 去重。重试不生成新事件，不延长原窗口。

传输复用 loadup HttpTemplate 的固定 named client/operation、最终 URI/字节签名回调及 RequestSigner 的
loadup-request-v1/KMS 显式版本。每次签原 body，重新生成 timestamp/nonce；签名头使用
X-App-Id、X-Signature-Audience、X-Key-Version 等框架字段。商户必须返回 2xx 和精确两字段 JSON {"eventId":"原事件","result":
"SUCCESS"}；空响应、额外/重复字段、错误编码、非字符串或 ID 不同均未确认。响应最多4096字节，超过原超时预算或窗口的 ACK 不被确认。

目标目录位于 loadup.modules.acquire.merchant-notifications.targets.<tenant>.<targetRef>，签名目录为
signing-identities.<tenant>.<signingIdentityRef>，两者均绑定收款商户。引用必须是已审核的不可变版本，禁止原地重指向；更改目标或密钥用新引用且仅影响新订单。第一次成功解析配置后冻结
URL/client/operation/应用/受众/KMS key+version/算法/合约超时的路由指纹，后续配置不一致时拒绝发送，不能自动迁移旧事件。第一轮解析前依赖版本目录的不可变性约定，product
发布前引用开通验证与目录生命周期管理仍待补。

只允许固定 HTTPS 443 目标，无 userinfo、fragment、query；HTTP 组件继续限制私网地址/DNS 和自动重定向。固定 operation 必须
POST，实际 URI 必须与审核地址完全相同；不接受订单或通知 payload 提供任意 URL。HTTP client 不可配置
credential-ref、私网放行或代理，响应上限不超过4096，请求容量至少65536；连接/连接池获取/读取超时之和必须在原合约
timeoutMillis 内。签名/KMS 与 HTTP 返回总耗时超预算也不接受 ACK；这不承诺强制取消已在途的网络请求。

可选 [application-acquire-notifications.yml](acquire-bootstrap/src/main/resources/application-acquire-notifications.yml)
提供 RequestSigner/KMS 装配与目录示例；与 acquire-business 一起启用。私钥只由 KMS 管理，显式指定原版本，保留至通知窗口结束。通知不需要
RequestVerifier/Redis nonce store，样例只启用签名并关闭验证；商户接收方仍需验签、防重放和业务去重。acquire-business 已装配
native delayedtask 与 local scheduler，扫描间隔和 native 重试参数可通过 ACQUIRE_TASK_*/ACQUIRE_SCAN_INTERVAL 配置。

DDL 为 V20261024180000__merchant_notification_delivery.sql，只修改源码未执行；DO/Mapper/TableDef 由 LoadUp
数据库处理器生成，状态更新只写游标列并按 rowVersion CAS。正文/URL诊断隐藏，不保存原签名或商户响应明文；SQL 与业务时间使用本地
DATETIME (6)。

本批未运行 IDEA 检查、构建、测试、DDL、部署或实际发送。退款通知、投递查询/审计重放、目标授权迁移/紧急吊销、历史密钥保留与清理、真实数据库竞态/签名/商户
ACK 验收继续待办。

## 退款商户通知接收（2026-10-24）

RefundNotificationService 实现内部 RefundNotificationFacade，要求配置的 payment SERVICE 与
`pay:acquire:refund:notification`，租户来自认证。核对原订单/商户/CNY/已支付 gross；子退款需原 payment allocation 已
CONSUMED，晚到的付款成功事件交内部 Outbox 重试。接收仅登记投影，不修改订单状态、已支付金额或退款账。

原合约通知策略、RefundSucceeded/RefundFailed/RefundRequestCompleted 的固定正文、首轮 native task 和
refund_notification_receipt 同事务登记。相同 delivery 参数重放回首次 ACK，改参拒绝；目标和签名引用来自原订单，不能由 payment
事件任意指定。复用现有投递签名、固定字节、原事件窗口、代次/次数/退避与严格商户 ACK；内部登记 ACK 与商户已送达分开。

新增实际迁移 `V20261024190000__refund_notification_receipt.sql`；payment 侧投递表草案已移除，统一复用 acquire 的通知引擎。数据库
Mapper/TableDef 仍由注解处理器生成。源码及幂等/权限/原付款晚到/部分成功/固定报文测试源码已补；未运行构建、测试、DDL、RPC
或实际通知。通知查询/审计重放、授权迁移/吊销及历史密钥生命周期仍待完成。

## 通知查询与有界运维（2026-10-24）

新增 `/api/acquire/notifications/detail`、`/page` 查询元数据，商户凭证需 pay:acquire:notification:read 且 merchant_id
精确匹配；详情不会暴露其他商户是否有该资源。分页支持订单/事件/状态和源发生时间范围，使用框架
PageQuery/PageDTO/PageResponse，不返回正文、目标/签名引用、路由指纹或 native 错误。

`/wake` 与 `/wakes` 只开放给具有 pay:acquire:notification:manage 的 OPERATOR 或配置的 SERVICE；默认 pay-ops，可用
ACQUIRE_NOTIFICATION_OPERATIONS_SERVICE_ID 修改。唤醒必须带稳定 requestKey、观察到的 rowVersion
和原因。原生任务缺失/FAILED/CANCELLED 可在原业务有效期和剩余次数内登记/重试/恢复；PENDING/RUNNING/未来退避不加速、不抢占。ACKED、EXHAUSTED、已超期不重新授予次数或窗口。

商户/运营主体来自已验证 JWT，不能由参数模拟。不可变 wake receipt
保存决定、主体、原版本/次数与原因，同库事务提交原生调度变化；请求重放直接返回首次回执，变更原因/版本/投递冲突。/wakes
分页只供运营审计，原因在诊断输出隐藏。任务 handler 继续校验原代次、路由指纹和固定字节签名；接口本身不实际发送或改金融状态。

源码、新实际迁移 V20261024200000__merchant_notification_operations.sql、schema 和权限/预算/幂等/调度/DTO
隔离测试源码已补。未运行构建、测试、DDL 或真实调用。跨原窗口的额外人工补发、目标授权迁移/吊销、发布前引用验证及历史密钥生命周期继续待办。

## 托管收银台付款后端（2026-10-24）

CashierFacade 已实现原会话付款发起和进度：`POST /api/cashier/payments/start` 接 orderId/actionKey/amountMinor/choice，
`/progress` 接 IdQuery（paymentId）。每次校验原 capability；新预占持原订单/会话锁并核对权限、归属与期限。与商户 API 复用
PaymentInitiationOperations，不复制金额/次数/尾款规则；幂等 actorScope 为 checkout/sessionId，相同动作重放同
allocation/payment。

入站不接受浏览器自填 IP/OpenID/interaction。当前 RequestCashierInteractionProvider 只构造 H5/WAP 的受信 servlet
remoteAddr 场景，不读取自报转发头。部署须在可信网关/容器还原真实客户端地址，不能盲目信任任意 X-Forwarded-*。JSAPI 等待渠道
OAuth 绑定，付款码/纯商户 API 路径不经过托管接口。

默认托管目录为空；运行 acquire-business 与可选 acquire-cashier profile，并用 ACQUIRE_CASHIER_H5_CHANNELS 启用已接入的渠道，例如微信
H5 环境填 WECHAT。choices 与原冻结合约相交，终态或无可付金额为空；没有渠道硬编码或跨渠道回退。配置不能代替实际产品开通/Provider
验收。

响应使用 CashierPaymentDTO 安全投影，不泄露商户凭证、allocation/evidence/合约信息；进度限本 session 的原
payment，不能横向查询其他会话。同一共享结果事务继续结算 cashier scope 的已接受付款，即使浏览器离开或会话过期。内部结果归属现在使用
OrderPaymentScope，不再构造空权限的商户调用身份。

初期 scope 直接更新为摘要/本会话付款/进度三权限，没有为早期只读快照保留兼容分支或自动提权；旧试验会话需重新签发。没有变更表列或实际执行迁移。源码及授权/幂等/结果收敛/H5
输入测试源码已补，未运行任何验证或真实调用。H5 表单/原动作/有界刷新页面源码已续补；OAuth、进一步恢复与完整收银台验收继续；撤销管理源码见本文末节。

## 托管 H5 页面交互（2026-10-24）

`/checkout` 已补手动金额、方式选择、单笔/尾款提示、固定动作键重试、原支付地址展示、结果轮询与多次付款页面源码。金额用 BigInt
整数分，限制正数/最多两位小数和 signed int64 范围；JSON 数字原文可用时精确解码，不支持原文且数值超出安全整数时拒绝展示，不静默舍入。发起
body 写经过验证的整数分字面量。后台仍是金额、并发、次数与终态约束的唯一权威。

CashierOrderDTO 增加 minimumPaymentMinor/maximumPaymentMinor/allowExactRemainderBelowMin/remainingPaymentCount；来自原
OrderPaymentPolicy 与已成功/预占计数，slots 为空表示不限次数。CashierPaymentDTO 增加必需 accepted 标记：false
必须来自可信未受理回执；页面不把字段缺失/超时当成未付款。金额/次数上限仅作为输入提示，服务端仍在原事务复核。

页面在第一次付款 HTTP 前，以 sessionStorage 写入并读回确认原 actionKey、amountMinor、choice、paymentId 等最小恢复信息；存储键按
tenant/order/session 区分。token、IP/OpenID、商户凭证和完整支付地址不进存储。网络失败保留原动作，字段锁定，重试不换键/金额；已知
paymentId 后仅刷新该付款。终态只有经 /progress 完成订单贡献收敛后才清除记录、允许下一笔手动付款。损坏/不可用存储不会自动丢弃旧动作并新建付款。

默认3秒一次、每轮最多30次查询；后台标签暂停，手动刷新可继续。超时、地址到期、浏览器返回都不置付款成功/失败；地址不自动刷新或重建付款。不确定拒绝/响应丢失仍保留原键；需要改变原请求或会话到期时由商户核对并按原受理事实处理。记录只恢复当前标签页，跨标签/设备不作全局去重承诺；后端幂等与金额占用继续兜底。

页面与 ES module/CSS 四个 GET 资源单独公开，业务 POST 仍逐次校验 capability。CSP 同源脚本/样式/请求，无内联脚本或第三方资源；文本用
textContent。H5 链接只使用原已验证、未到期 HTTPS URL，用户显式打开新标签以保留原页面；不拼 redirect 参数、不保存 URL。页面与
API 默认 no-referrer，支付链接单独 strict-origin，仅发送 origin
且降级不发送，参见 [W3C Referrer Policy](https://www.w3.org/TR/referrer-policy/#referrer-policy-strict-origin)
与 [HTML hyperlink policy](https://html.spec.whatwg.org/multipage/links.html#hyperlink-referrer-policy)
；正式收银域/渠道域配置仍需实网验收。

新增纯 JS 核心测试源码位于
acquire-adapter/src/test/javascript/checkout-core.test.mjs，覆盖整数精度、尾款/次数、存储失败、固定键恢复、受理标记、原地址/期限；同步
Java DTO/MapStruct 和金额摘要测试源码。按用户要求未执行 Node/Maven/IDE 检查、浏览器预览、DDL、部署或实际支付。JSAPI
OAuth/交互和浏览器/真实渠道完整验收继续待办；会话撤销源码见本文末节。

## 收银台链接撤销管理（2026-10-24）

原签发商户可通过 POST `/api/acquire/session/detail` 读取不含凭证的元数据，使用当前 rowVersion，
通过 `/api/acquire/session/revoke` 提交 id/requestKey/expectedVersion/reason；`/revocations` 按 sessionId 分页读取审计。
JWT 分别要求 `pay:acquire:session:read`、`pay:acquire:session:revoke`；原签发权限不自动授予管理权限，
买方 token 无管理能力。原主体 hash/tenant/order/payee 均核对，不能管理同商户其他应用签发的链接。

撤销永久设置 revoked_at，不改变业务状态或金融金额；已完成订单的旧链接也可撤销。标记与原请求回执同事务，
新键显式 CAS，原键同参返回历史结果，改参冲突。先锁订单再锁会话，锁后重查回执以处理原请求并发重放。
新认证/读取/付款预占拒绝已撤销链接，原已提交意图继续履约；不隐含渠道关单/退款，也不承诺在途读的全局零窗口。
原签发键不再重建已撤销链接；另签新链接必须使用新请求键且订单可继续付款。撤销不清密钥、不延长期限，不能恢复原链接。

新增迁移只提供源码，未执行。领域/管理服务/认证/新预占和原意图继续/原链接重放拒绝等测试源码已补，
未执行 IDEA、构建、测试、DDL 或真实调用。完整接口与表设计见 design/CLIENT-API.md 和 design/DATABASE.md。

## 丢失付款回复后的原意图恢复（2026-10-24）

页面持久保存原 order/actionKey/金额/choice，首次 start 回复丢失或重新打开标签页时，
先调用 POST `/api/cashier/payments/recover`，尚未拿到 paymentId 也能定位原会话付款。
恢复按 NOT_OBSERVED/INPUT_REQUIRED/RESOLVED 明确区分；前两者保留原操作，不是未支付证明。
原安全输入已绑定时重放落库的固定执行命令，接通既有结果收敛；不调用当前场景输入提供方、不另做预占、不换付款身份。
非 HELD 意图只进入原结果流程，NOT_ACCEPTED 必须有原证据。RESOLVED 的嵌套 payment/state 才表示当前金融结果。

页面刷新和按原金额重试均先恢复；取得原 id 后使用 progress，A3 收敛终态才清除操作。
缺 id 的恢复轮询也受原次数/可见性边界约束，用户手动重试重启一轮有界查询。
另一个会话、订单或 merchant 范围不能以同 actionKey 查询，改金额/选择会明确冲突。

远端输入已保存但本地引用丢失的恢复已由 InputLookup 接通；未受理输入过期的有证据关闭已接
InputExpire；首次准备并发期间网络地址变更等恢复仍需继续；
已过期/撤销 capability 不绕过鉴权，需商户核对。后端/API/页面和测试源码已补，未运行 Java/Node/IDE/浏览器/数据库或真实渠道验证。

## 原安全输入引用恢复（2026-10-24）

payment 的独立 InputLookup Facade 通过 Dubbo Triple 以 acquire SERVICE+input:read 校验原不可变意图的完整范围，
只返回原 opaque 引用/摘要/期限，不解密、指纹计算、改数据或续期。found 明确存在并与可空 input 一致；
过期/BOUND 仍回原元数据。acquire 恢复缺失引用时事务外查询，再在原订单→会话锁下 CAS 绑定原引用，继续原命令幂等受理和 A3。
不再为已存原输入重新生成当前 IP/场景。异常不当缺失、缺失不当未收款、到期不直接释放占用，金融编号/金额/渠道不变。

新增权限须由服务 JWT 发行方配置，没有签发令牌或修改部署。provider/consumer/MapStruct/proto/接线与测试源码已补，
未运行任何验证；未受理输入到期后的有证据关闭源码已补；第一次准备仍在途的竞态与其余完整业务范围继续。

## 未受理输入到期收敛（2026-10-26）

InputExpire 是完整原输入范围绑定的内部条件操作，要求 acquire SERVICE+input:expire 权限。
在原 parent/child/payment/input 锁序内，已有付款保持原状；有效未受理输入仍可原命令受理；
到期且从未受理时复用单笔 close 原子写门禁与固定控制回执，不封禁父订单或调用渠道。
回复丢失可用原请求重放证明，孤立数据没有证明不能按无付款记录直接释放。

acquire 原 actionKey 恢复据三态处理，关闭证明经通用 applyClose/A3 核对并幂等释放原 allocation。
paid gross、其他占用和订单业务状态不改变，后续 progress 不虚构 payment 行；页面得到明确收敛结果才允许下一笔。
控制 API/DTO/Protobuf/MapStruct/consumer、恢复接线及测试源码已补，未运行验证、令牌签发、部署或真实调用。
首次准备并发/地址变化、JSAPI OAuth、其他渠道、通知/交易重建/风控/对账及完整目标继续。

## 商户原 actionKey 恢复入口（2026-10-26）

POST `/api/acquire/payments/recover` 接收原 orderId/actionKey/amountMinor/choice，专门 merchant JWT 权限为
`pay:acquire:payment:recover`。恢复绑定原 tenant/callerScopeHash 与 merchant scope，不接受自报范围，不读取 checkout 的同名动作。
复用共享原意图恢复/原输入查回与条件到期证明/A3，找不到或缺输入时 accepted 明确 null；
只有 RESOLVED 的已受理原付款或经 A3 的未受理证据分别返回 true/false，不把依赖失败伪装成未受理。

补充原合法 NoInteraction H5 的恢复：以原 v1 摘要精确确认 NONE 模式才跳过输入查询/到期接口，
原字段全量经 MapStruct 重建摘要候选，不依据空引用猜测。敏感输入路径继续查原输入，不能切换到 NONE。
Java 恢复枚举提升为共享 PaymentRecoveryState，商户与 cashier 使用同一组语义，未保留早期兼容类型。

新增源码与权限/隔离/三态/原证据/未知 JSON 判别/无输入与损坏拒绝测试源码，未执行任何验证、部署或实际支付。
DirectPay 整体丢失回复、首次输入准备并发、JSAPI OAuth、其他渠道/通知生命周期/交易重建/风控/对账和完整验证仍需继续。

## DirectPay 首笔绑定与整体恢复（2026-10-26）

DirectPay 改为独立 DIRECT_PAY A0，固定非敏感订单字段和首笔 actionKey/金额/choice/输入种类，禁止与 ORDER/CHECKOUT
同号混用或改变首笔。
raw input 不进入创建库。原 DirectPay 先恢复原首笔，确实尚未提交才用原请求补做 start；结果统一为当前订单和
PaymentRecoveryDTO，
不再保留早期 PaymentStartDTO 的嵌套兼容表示。调用权限增加 payment:recover。

新 POST /api/acquire/payments/direct-pay/recover 接收原非敏感元数据，新增 direct-pay:recover 并要求 create/read/payment:
recover。
未知 orderId 也能按原商户订单号恢复 A0：未观察到不分配 ID；INIT 通过仅续办入口继续原准入；已拒绝/到期返回白名单固定决定；
已完成后恢复原首笔。仅续办在 A0 事务内再次要求行存在，绝不因丢行重建新单；恢复不调用 start 或创建新 A2。

DTO、Facade、编码/摘要、COLA 编排、模式约束、MapStruct、开发期 CHECK 与表草案、测试源码已同步。
未执行 IDEA/构建/测试/DDL/部署或真实调用。首次输入准备并发/地址变动、JSAPI OAuth、其他渠道/通知/交易重建/风控/对账及全部原目标继续。

## A2 后台付款恢复（2026-10-26）

新付款的预占金额、原命令、`allocation_recovery_cursor` 和首轮 native DelayedTask 在同一个 A2 事务中登记，
首轮时间为原 initiatedAt 加轮询间隔；任务登记失败使 A2 回滚，之后不发起准备输入或受理 RPC。
任务类型为 `pay.acquire.payment.intent.progress`，bizId 为 `paymentId.generation`，只携带原身份和恢复代次。
`ACQUIRE_PAYMENT_RECOVERY_POLL_DELAY_SECONDS` 默认 30，允许 1 至 86400 秒；运行 `acquire-business` 已有的
DelayedTask/local Scheduler 装配即可唤醒，不为每笔订单创建平台作业。

执行器从可信持久化原归属恢复内部权限，只允许继续原意图，不伪造商户 JWT、不要求原收银台仍在线或会话仍有效，
不能创建新 A2、换 actionKey、重建付款码/买家身份/IP 或改变渠道。已提交意图经过原输入查回、输入到期门禁、原命令受理和
原结果查询流程继续；INPUT_REQUIRED 保留预占并登记下一代，不因超时直接释放。取得到期关闭证明时交给现有 A3 收敛，
非终态继续查询；只有 CONSUMED/RELEASED 且具有精确绑定的 PaymentOutcomeReceipt 才结束恢复。

本地检查按订单→allocation→恢复游标锁定；RPC 在事务外，之后重新锁定原记录。旧代、已完成、重复任务不重复续排；
后续游标与不同 bizId 的 native 任务原子提交。native 租约只约束唤醒处理，不构成资金执行权或未收款证明。
依赖异常交给 native 有界重试，FAILED 保留原业务未知状态，不能当作支付失败或释放金额。

新增迁移 `V20261026110000__allocation_recovery_cursor.sql`、DO/生成 Mapper 约定下的 Gateway、转换器、领域/编排/事务测试源码。
未运行 IDEA、编译、测试、数据库、调度器或 RPC 验收；迁移没有执行。订单到期关单续办、首次准备仍在途且输入尚不可查回的
最终收敛、FAILED 运维续办另行实现，本任务不声称这些边界已完成。

## 订单到期与关单后台续办（2026-10-26）

A1 同事务登记 `pay.acquire.order.deadline`，bizId 为 `orderId.deadlineVersion`，初始版本为 1、executeAt 为原 orderDueAt。
延期把新截止、订单版本、deadlineVersion、不可变延期回执与新任务一起提交；同键重放不再登记任务。旧版任务不取消远端行为，
仅在订单锁下按当前版本幂等跳过。延长截止与到期处理争用同一订单锁：延期先提交则旧任务无效，到期 C0 先提交则禁止新延期。
ORDER、CHECKOUT、DIRECT_PAY 均使用同一创建登记流程；DirectPay 延期也从原类型化创建元数据校验原截止与累计上限。

`OrderDeadlineService` 校验 native RUNNING/attempts/租约/nextExecuteAt、可信租户、原 orderId、正 deadlineVersion 与原期限。
CLOSE 策略到期通过共享 `OrderClosureOperations.startLocked` 原子登记 C0 与首轮关单任务；服务端请求键为
`order-deadline/{version}`，原理由 `ORDER_EXPIRED` 标识。KEEP_PENDING 到期不会建立关闭门禁：INIT 仅转为 PENDING，
金额、预占和合约不变；已 PENDING 不重复写，仍可按原策略续付。

商户关闭入口保留 `pay:acquire:close` 权限；共享编排不伪造商户身份。所有新 C0 同事务登记
`pay.acquire.order.close.progress`，bizId 为 `orderId.closeIntentRowVersion`，首轮版本为 0、时间为原 requestedAt。
`OrderClosureRecoveryService` 仅续办精确匹配的原关闭意图，沿原 owner、reason、epoch、父门禁、子付款和游标执行有界批次。
游标推进与下一代 native 任务同事务提交，重复/旧代或终态不另起批次；RPC 均在本地事务外。

`ACQUIRE_ORDER_CLOSE_POLL_DELAY_SECONDS` 默认 30（1 至 86400）。未知子付款、未收敛父门禁或本地仍有预占都保持 CLOSING；
只有原权威门禁证明、子付款收敛、本地零预占及完整回执条件满足，才沿原 finishClose 逻辑进入 CLOSED。
任务租约或失败不证明未收款；native FAILED 不释放预占，运维续办仍需后续实现。

复用现有 deadline_version、order_close_intent.row_version/cursor_payment_id 与 native 表，没有新增业务表或 RPC。
源码及测试源码已补；未运行 IDEA、构建、测试、数据库、调度或实际 RPC。旧库已有记录的补登记未实现，当前仍按开发期直接重构规则处理。

## 失败恢复任务的审计重试（2026-10-26）

新增 `AcquireRecoveryOperationsFacade` 与运营 HTTP 入口：

| 接口                                               | 权限                       | 作用                                             |
|----------------------------------------------------|----------------------------|--------------------------------------------------|
| POST /api/acquire/operations/recovery/tasks/detail | pay:acquire:recovery:read  | 查看一个原任务的状态与精确重试前置条件           |
| POST /api/acquire/operations/recovery/tasks/retry  | pay:acquire:recovery:retry | 对当前仍有业务工作的 FAILED 原任务登记审计并重试 |

两者只接受已验证的 SERVICE 身份，service_id/sub 必须匹配
`ACQUIRE_RECOVERY_OPERATIONS_SERVICE_ID`（默认 pay-ops），租户来自 JWT；商户和付款/退款结果服务不获得运营权限。
令牌 issuer、签名、受众及有效期沿既有资源服务器校验；本轮没有签发或配置真实令牌。

kind 仅允许 PAYMENT_ALLOCATION、ORDER_DEADLINE、ORDER_CLOSURE，分别映射到既有三类 native taskType。
subjectId 是原 paymentId（付款恢复）或原 orderId（另两类），generation 为原恢复代次/截止版本/关单游标版本。
查询不会创建任务，且只返回 native id/status/attempts/maxAttempts/executeAt/nextExecuteAt、businessCurrent 和 retryable；
不暴露 lastError、原命令或金融证明。retryable 是可变化的查询快照，不授予执行权。

重试要求原 kind/subject/generation、expectedTaskId、expectedAttempts 和微秒精度 expectedNextExecuteAt 全部匹配，
并使用稳定 requestKey 与必填 reason。只处理 FAILED；PENDING/RUNNING/SUCCEEDED/CANCELLED 不改写，缺任务不补建。
仍有当前业务工作且原 executeAt 已到时调用 native retryFailed，保留原业务期限；原金融身份、actionKey、渠道、执行 epoch、
金额、商户合约及业务尝试预算不变。过期代次或业务已结束，只记录 NO_LONGER_NEEDED，不重新唤醒。

操作回执 `acquire_recovery_retry_receipt` 与 native 重试同数据源/同事务提交，唯一键为 tenant/callerScopeHash/requestKey。
同键完整重放返回首次不可变决定；同键改参冲突，即使原 native 任务再次耗尽，也不会因回复重试而重新清零次数。
原订单锁序列化同订单操作，native 的状态条件更新处理调度竞争；审计或 native 更新失败一同回滚。
RETRIED 只代表唤醒已登记，不代表资金成功、失败、未收款或订单已关闭。

新增 `V20261026130000__acquire_recovery_retry_receipt.sql`、生成 Mapper 约定下的 DO/Gateway/MapStruct 和业务/领域测试源码。
没有新增内部 RPC 或直接执行渠道的入口。未运行 IDEA、编译、测试、数据库/HTTP/JWT/调度器验收；迁移尚未执行。
本入口目前只覆盖 acquire 三类任务，payment/refund 自身的 FAILED 运维处理仍需继续；任务列表/告警与旧库补登记另行推进。

## Review R2：运营详情与重试隔离（2026-10-26）

运营 recovery detail 改为短 `readSnapshot`：REQUIRES_NEW、REPEATABLE_READ、readOnly，订单/allocation/游标均采用普通查询，
不获取交易排他锁。三类任务共用原期限、代次、绑定与回执判定；`businessCurrent/retryable` 仅描述该快照，不获得执行权限。
并发提交后的详情可能过时，retry 必须重新走 `lockForRetry`，按订单→allocation→游标锁序核验当前金融条件和原 native 快照，
原 taskId/attempts/nextExecuteAt 已变化仍拒绝，审计与重试仍同事务。

新 `readSnapshot` 不替换原 `OrderCreationTransactions.read`；后者仍仅绑定租户上下文，不会把既有 RPC 包进事务。
完整命名收敛属于 review R5 的后续批次。已补只读/隔离级别/上下文恢复、详情无 lock 调用及过时详情被写路径拒绝的测试源码；未运行。

## Review R1：A2/A3 与真实 Outbox 的测试源码

新增 `AcquiringFinancialPersistenceIT`：实际订单/合同快照/费用/预占/结果回执/恢复游标 Gateway，实际 DelayedTask、Outbox、
MapStruct 与本地事务；通过真实付款发起/进度编排并发调用。故障在真实 Outbox publish 之后注入，检查 A2 整组回滚及 A3 金额、
回执、费用确认/调整、快照共同回滚；远端成功测试事实保留，可原号再查证收敛。并发相同 actionKey 与成功事实检查只生效一次。

仓储重建补齐 DIRECT_PAY 原元数据/摘要分支，避免把其 payload 交给仅接受 ORDER 的解码器；ORDER/DIRECT_PAY 均有实际仓储重建测试输入。
`postgresql-it` profile 为显式 opt-in，详细范围及命令见 `acquire-infrastructure/src/test/README.md`。没有新增业务迁移或
RPC。
所有新源码未编译、未运行容器/数据库，不等价于 R1 已验收或真实跨服务/资金链路已通过。

### Review R6：付款恢复意图快照

本服务不再直接持久化公开 PaymentExecuteCommand JSON；独立 PaymentIntentSnapshot 及嵌套本地条款由 MapStruct 转换，使用
owner 格式和固定存储 JSON 规则。
恢复不重新执行当前 Command Bean Validation 或查询当前合约填值；仍验证快照自身、哈希及原落库索引，保留金额/期限/规则版本/输入引用。
旧裸 Command/错误
owner/未知格式明确失败，没有双读、回填或兼容分支。未执行编译/测试/数据库或开发数据重建，完整范围与测试源码见 [快照契约](../design/PAYMENT-INTENT-SNAPSHOTS.md)。

## 2026-10-28 JSAPI 客户端动作桥接

新增 checkout-bridge.mjs，消费既有微信/支付宝 INVOKE_JSAPI
动作，固定方法/参数白名单、就绪等待/过期/单在途/中断与超时，原生返回只促使查询原付款。托管页面增加点击按钮与原动作保护，可供纯
API 商户同源部署模块复用。托管后端仍只配置 H5，可信渠道身份/OAuth
接线和真实客户端验收继续待办。详见 [JSAPI 桥接设计](../design/CASHIER-JSAPI-BRIDGE.md)。本批仅源码和测试源码，未运行。
