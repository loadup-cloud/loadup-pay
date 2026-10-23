# Acquire 收单订单

应用间传输已于2026-10-23统一为 gRPC，见[RPC 规范](../design/RPC.md)。本文件旧批次中的内部 HTTP 描述与 `*_BASE_URL` 要求由该规范取代；商户/运营 HTTP 入口继续保留。实际业务 gRPC 随本应用 `*-business` 构建和运行 profile 装配，端口50051；本批通过 IDEA MCP 完成六业务应用及依赖的 `clean test-compile`；未执行测试或部署。

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

DO 继承 BaseDO，仓储仅使用生成 Mapper 和 Tables 常量，显式添加 tenant/deleted 条件。各类 DO 的 Mapper/TableDef/Tables 由
LoadUp 包装处理器生成。POM 显式提供其上游 mybatis-flex-processor 的隔离处理器路径，仅执行包装处理器一次，不增加业务
mybatis-flex.config。

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
active-key-version，并保留旧键到重放窗口结束。删除旧键只阻止凭证重建，已有 token 的摘要认证仍受原期限/状态控制，不能把删键当作撤销会话。

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
可只读查看；INIT/CANCELLED/EXPIRED 会话拒绝。到期即拒绝，不依赖扫描器提前将数据库状态改为 EXPIRED。尚未新增撤销会话的管理入口。

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
权限。当前受理支持 H5 + NONE、PAYMENT_CODE + 付款码、JSAPI + 同渠道身份票据；选择必须属于原冻结合约能力。敏感输入由 payment
加密准备，不写入 acquire 数据库。商户手工指定正整数人民币分值，不改变订单固定总额。

A2 在主库独立 READ_COMMITTED 事务中先锁原订单，以 tenant+orderId+merchant/原主体hash+actionKey 定位动作。新动作固定
allocationId/paymentId，领域校验可付款窗口、剩余金额、单笔上下限/尾款例外和成功笔数上限（包含已占用笔数），同事务增加订单占用/笔数并插入
HELD allocation 与固定付款意图。H5 命令可直接受理；敏感方式先保存无原始输入的固定标识/条款，再在独立步骤附加已加密引用。订单进入
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
使用 NONE。JSAPI/H5 后续执行返回的 action 仍需商户前端交互，不能将 API 返回 INIT 当作收款成功。SERVICE 出站 JWT 新增
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
