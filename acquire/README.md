# Acquire 收单订单

已实现普通创单/原调用主体查询，以及原单会话签发、能力认证和收银台只读摘要的 COLA app、infrastructure、adapter。复用框架 Money、JSON、BaseDO、ID、数据库/事务、生成 Mapper、Spring MapStruct、HTTP 和 JWT 资源服务器。业务默认币种为 CNY，订单、数据库会话与业务 Clock 使用 Asia/Shanghai。

## 接口与身份

| POST JSON 路径 | Facade | 权限 |
|----------------|--------|------|
| `/api/acquire/orders/create` | AcquireOrderCreationFacade.create | `pay:acquire:create` |
| `/api/acquire/orders/detail` | AcquireOrderCreationFacade.detail | `pay:acquire:read` |
| `/api/acquire/session/create` | CheckoutSessionFacade.create | `pay:acquire:session` |
| `/api/cashier/order/summary` | CashierSummaryFacade.summary | 已验证的订单会话能力 |

detail 使用 IdQuery，id 为 orderId。Controller 返回 SuccessResponse/FailureResponse；完整 AcquireOrderFacade 继承创单切片，CashierFacade 继承独立摘要切片。两者其余方法未注册实现，不暴露占位付款入口。

资源服务器负责签名、issuer/audience、期限及 access-token 校验。入口额外要求签名 tenant_id、principal_type（MERCHANT_APP/MERCHANT/OPERATOR）及 sub；商户主体还需 merchant_id，MERCHANT 的 sub 必须等于该商户。身份不能由请求头或请求体覆盖。订单固定原调用主体范围；同商户其他应用和其他操作员也不能直接重用或查询该范围的订单。后续更宽的商户读权限需要独立授权设计。

当前采用原已验证 JWT 委托到 product，再由 product 委托 customer；签发方需同时授予 acquire/product/customer 所需 audience 与对应权限，并在 customer 建立实际收款商户授权。不会用统一服务身份替代原商户授权。令牌不进入数据库或诊断输出；令牌轮换不改变原主体范围。

## A0/A1 与恢复

1. A0 在主库 READ_COMMITTED 独立事务保存 order_create_intent，按 tenant+payee+merchantOrderNo 唯一，分配重试不变的 orderId。保存完整非敏感命令，摘要 v1 使用固定字段序与 UTF-8 长度分帧，绑定租户、调用者、ORDER 入口、商户订单号、总额、币种、产品 scope、标题和请求截止时间。
2. 提交 A0 后才向 product 的 authorize 发起有界 HTTP 请求。本地事务不跨网络；请求或传输失败保留 INIT，调用方以原命令重试。product 根据稳定 orderId 回放原准入；首次准入检查当前合约及 customer/额度策略状态，原准入不因普通停单被重新判定。
3. A1 锁原 intent 并重读，校验准入绑定、明确 ADMITTED/DENIED、合约引用、typed 条款及时间。到达 materializeBefore 的精确时刻也禁止首次落单。拒绝/过期先提交 REJECTED/EXPIRED，再返回稳定错误；不能因以后合约恢复而用同号创建另一单。
4. 对有效准入，同一事务保存 INIT acquire_order、不可变 order_contract_snapshot、INIT order_fee，并把意图改为 COMPLETED。ADMITTED 不另开中间事务，避免订单和意图部分提交。费用以固定订单总额计算并冻结，尚未确认扣费；每单只有一个 ORDER_AMOUNT 记录。
5. 已完成请求直接回放原订单，不再访问 product/customer 或重新计算费用；查询按当前订单字段返回余额进度和是否仍可付款，不因读取而推进生命周期。订单到期默认值以原 admittedAt+waitSeconds 计算，请求值须落在原 maxWaitSeconds 范围。

A0/A1 拒绝外层本地事务。唯一键竞争或瞬态数据库冲突最多整事务重试3次；耗尽返回 ACQUIRE_CONCURRENT_UPDATE，客户端保留原意图。CAS、插入或提交失败依赖真实数据库回滚，不以进程内补偿假装事务成功。业务键不包含 deleted，不能通过逻辑删除复用商户订单号。

## 持久化与快照

实际迁移 `V20261021210000__acquire_order_creation.sql` 只创建4张本服务表：order_create_intent、acquire_order、order_contract_snapshot、order_fee。后续 V20261021220000 新增 checkout_session，实际合计5张表；付款占用和退款调整表尚未创建。

configuration 保存 product 当前返回的完整 OrderAdmissionDTO 和强类型条款，使用 LONGTEXT，配套 payload_hash 校验本地持久副本，读取时逐项核对 intent、金额、主体、准入及合约元数据。product 的 compiled_terms_hash 作为可信上游编译结果引用原样保留；它包含 product 内部字段来源，不能由当前 DTO 独立重算，因此不将本地 payload_hash 冒充原编译哈希。字段来源仍由 product 的原 compiled_terms 保存。快照格式当前为1；前期按当前模型直接演进。

DO 继承 BaseDO，仓储仅使用生成 Mapper 和 Tables 常量，显式添加 tenant/deleted 条件。四类 DO 的 Mapper/TableDef/Tables 由 LoadUp 包装处理器生成。POM 显式提供其上游 mybatis-flex-processor 的隔离处理器路径，仅执行包装处理器一次，不增加业务 mybatis-flex.config。

## 构建与运行

默认 acquire-bootstrap 仍为基础骨架；业务包需要 Maven 和 Spring 两端启用 acquire-business。以下供开发者执行，本轮未运行：

```bash
# 在 loadup-pay 根目录，按需执行窄范围构建及相关测试。
mvnd clean verify -pl acquire/acquire-bootstrap -am -Pacquire-business

# 既有脚本构建 JAR 和镜像，默认跳过测试。
./acquire/build.sh -Pacquire-business

TZ=Asia/Shanghai java -Duser.timezone=Asia/Shanghai \
  -jar acquire/acquire-bootstrap/target/acquire-bootstrap-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=acquire-business
```

| 环境变量 | 用途 |
|-----------|------|
| ACQUIRE_JDBC_URL / ACQUIRE_JDBC_USERNAME / ACQUIRE_JDBC_PASSWORD | 独立 MySQL 业务库及外部 Secret 凭证 |
| ACQUIRE_JWT_ISSUER_URI / ACQUIRE_JWT_AUDIENCE / ACQUIRE_JWT_JWK_SET_URI | 本服务已验证资源服务器 |
| PRODUCT_BASE_URL / PRODUCT_JWT_AUDIENCE | 固定 product HTTP 目标及委托 audience |
| ACQUIRE_CHECKOUT_PAGE_URL | 同源托管 `/checkout` 的公开 HTTPS 地址；本地 loopback 可用 HTTP |
| ACQUIRE_CHECKOUT_SECRET | v1会话 HMAC 密钥，至少32个随机 UTF-8 字节，由外部 Secret 注入 |
| ACQUIRE_CHECKOUT_ACTIVE_KEY_VERSION | 默认v1；切换版本须同时配置 checkout.keys 对应条目 |

业务 profile 启用 Flyway，首次启动会应用未运行的迁移；数据库会话 +08:00。还需按框架约定为部署配置稳定 ID sequence site。现有 deploy/pay.yml 未包含业务 DB/JWT 配置，业务上线需覆盖层显式注入 Secret/ConfigMap 和 SPRING_PROFILES_ACTIVE。

## 验证与待办

新增4类23个测试方法源码，覆盖 A0 后调用网络、稳定 ID 恢复、原完成回放、拒绝/截止持久化、参数冲突、主体隔离、竞争重读、精确数字/金额、规范化时间、JWT 委托及传输失败。测试事务管理器只记录边界，不证明真实 MySQL 回滚或行锁。

IDE 已检查关键编排、Converter、适配和测试源码；仓储因未在业务 target 生成 Mapper/Tables 仍有未解析类型。独立临时 Maven 目录中，以 Java 25 执行 javac -proc:only 验证两个 Converter、四个 Mapper、四个 TableDef 和 Tables 的注解生成；不等于业务编译通过。未运行 Maven、JUnit、DDL 或部署。

后续继续 DirectPay/多次部分付款、订单占用与 payment 可信结果、付清费用确认、延期/自动关单、Outbox/等待任务及商户通知。当前收银台只展示订单，不发起付款或自动关单，不能视为可对外收款的完整主链路。


## 会话签发、重放与托管页面

可直接 checkout → 打开 checkoutUrl，也可普通 create → session.create → 打开 checkoutUrl。一步创单与独立续发的事务边界见下节。

session.create 锁定原订单并校验原调用者，读取原持久条款，完全不访问当前 product/customer。新会话仅允许可续付的 INIT/PENDING；CLOSE 策略期限取 sessionSeconds 与原 orderDueAt 的较早值，KEEP_PENDING 按原 sessionSeconds 签发，统一向下对齐到秒。期限不能等于或早于当前时刻。

幂等键为 tenant+orderId+requestKey；摘要 v1 固定租户、原主体、订单、requestKey 与归一化的 returnTargetCode。省略回跳 code 等于原合约默认值，只允许该已审核 code；不接收任意回跳 URL。相同键先回放原 sessionId/token/expiry，过期、取消或订单已经终态也不改写会话；换键只给仍可续付订单签发新会话，不取消其他已签会话，也不迁移任何付款动作。

会话 scope 版本1仅含 ORDER_SUMMARY，不能借它调用商户创建、退款或付款接口。HMAC-SHA256 规范输入沿用 CLIENT-API 2.5 的 LF/epoch/nonce 协议，输出 sessionId.base64url(mac)，nonce 为安全随机32字节。DB只保存 token_hash、nonce、key_ref/version。幂等重放用原密钥版本重建并核对原摘要；旧版本密钥缺失或被同版本替换返回 SESSION_UNAVAILABLE，不偷偷换 token 或续期。多副本须装载相同键配置；轮换先部署新旧版本，再切 active-key-version，并保留旧键到重放窗口结束。删除旧键只阻止凭证重建，已有 token 的摘要认证仍受原期限/状态控制，不能把删键当作撤销会话。

公开 URL 固定为配置的 `/checkout`，tenant/order/token 只在 fragment，不进入 query、HTTP access log 或渠道回跳。托管页面将 fragment 从历史记录移除，token 只保存在当前页面内存，以 Authorization: Bearer 发送；刷新按钮可查询原订单，浏览器整页重载需重新打开原链接。页面无第三方资源、无 localStorage/cookie token，设置 no-store/no-referrer/CSP，标题只用 textContent。人民币分值按精确整数展示；浏览器无法保留大整数时拒绝展示金额，不静默舍入。

页面与 `/api/cashier/order/summary` 需要同一公开 origin，可由网关将它们代理到 acquire。当前不开放跨域 CORS。返回摘要不含费率、合约/策略、内部凭证或其他订单，商户显示名暂未持久化，返回 null；支付 choices 为空，页面仅展示状态与刷新按钮。

## 会话认证边界

cashier 使用独立优先级90安全链，早于框架优先级100的商户 JWT 链。仅 POST summary 进入能力认证服务，其余 cashier API 全拒绝；商户 API 继续走框架已验证 JWT。摘要 Facade 每次调用都通过 request-backed provider 检查能力，不能仅凭 permitAll 路由或 IdQuery 读订单。

X-Checkout-Tenant 只是有界的数据库定位值，不是可信商户身份。定位后必须恒定时间匹配 token 摘要，检查状态 ACTIVE/COMPLETED、当前时间严格早于 expiresAt、ORDER_SUMMARY 范围及浏览器 Origin，再建立只能读该 orderId 的作用域；重复 Authorization/租户头拒绝。summary 还核对 IdQuery 与该 scope 一致，并在加载订单后再次检查期限。PAID/CLOSED 可只读查看；INIT/CANCELLED/EXPIRED 会话拒绝。到期即拒绝，不依赖扫描器提前将数据库状态改为 EXPIRED。尚未新增撤销会话的管理入口。

本批新增5类29个测试方法源码，连同前批业务切片累计9类52个方法源码，均未运行 JUnit。关键业务/安全/转换及测试源码的 IDEA 错误检查完成；临时 Maven 样式目录的 javac -proc:only 成功生成两个新 Converter 和 session Mapper/TableDef/Tables。Node 检查真实页面脚本语法，并在无网络 VM 样例中核对 fragment 清除、大整数分值、纯文本标题、缺失凭证及旧解析器精度保护；未做真实浏览器视觉验收或真实 JWT/MySQL/HTTP 集成验收，未构建/部署业务镜像。


## Checkout 一步创单

POST `/api/acquire/checkout/create` 调用 CheckoutOrderFacade.checkout，要求已验证原商户 JWT 的 `pay:acquire:checkout` 权限，以及 product/customer 准入所需权限及 audience。返回 SuccessResponse<CheckoutDTO>，含订单、首次 sessionId、checkoutUrl 和原期限；响应 no-store。当前链接只能查看订单摘要，付款按钮尚未接入。

A0 在 tenant+payeeMerchantId+merchantOrderNo 唯一键下固定 CHECKOUT 入口、原始 returnTargetCode（包括省略值）、orderId 和 initialSessionId。普通 create 的 ORDER v1 摘要与 JSON 载荷不变；两种入口不能同商户订单号互换。Checkout 的独立 v1 摘要增加回跳 code，省略后再显式传入默认 code 也属于修改创单意图；独立 session.create 则仍按归一化默认值比较。

product 网络请求在事务外。A1 先锁意图并复核准入，再准备会话签名/URL；订单、冻结条款、整单 INIT 费用、首次 ACTIVE 会话及 COMPLETED 标记在同一事务提交。会话在领域内先 INIT 后激活。首次会话 requestKey 固定为 `initial/<initialSessionId>`，只用于标识该已固定会话，不能据此创建另一笔付款。签名/存储失败使 A1 回滚，保留 A0 稳定 ID；准入拒绝、materializeBefore 截止、回跳 code 不匹配或落单时已无法签发有效会话，分别持久化原拒绝/到期结果后返回固定错误。

完成重试只读取原订单及 initialSessionId 指向的会话，返回同一 token/期限；不重新准入、不重算费用、不另建会话，过期或终态也不续期。原会话缺失返回 SNAPSHOT_INVALID，旧密钥不可用返回 SESSION_UNAVAILABLE。需要续发时使用独立 session.create 的新 requestKey，受原订单和条款限制。

新增迁移 `V20261022010000__checkout_order_creation.sql`，仅扩展意图，不增加业务表；结构说明见[数据库设计第12节](../design/DATABASE.md#12-checkout-一步创单结构)。本轮新增14个测试方法源码（创单12、编码2），累计 acquire 9类66个方法。仅做 IDEA 静态错误检查和差异检查；未执行构建、注解生成、JUnit、数据库迁移或部署。真实五表回滚及重复请求竞争仍待环境验收。


## 创单意图映射修复

OrderCreationStorageConverter 显式双向映射 entryMode/initialSessionId，保持严格未映射目标检查。OrderCreateIntent 只保留当前完整构造器；调用方和测试统一显式提供入口与首次会话字段，新增两个持久化往返回归测试方法源码（累计10类68个方法）。

本地排查发现 domain/target 中模型已更新，但 Maven 本地安装的 acquire-domain JAR 仍无这两个字段；单独构建 infrastructure 若解析到旧 JAR，会出现未映射属性或源属性不存在。请在仓库根目录将受影响模块及其依赖放入同一 reactor，避免引用陈旧安装产物：

```bash
mvnd clean compile -pl acquire/acquire-infrastructure -am -Pacquire-business \
  -DskipTests -Dskip.spotless=true -Dskip.spotbugs=true
```

此命令供本地执行，本轮未运行构建或 JUnit。IDE 检查不等于注解处理成功。
