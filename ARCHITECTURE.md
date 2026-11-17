# LoadUp Pay 架构

## 阶段 0 静态架构基线

2026-11-15 已按当前工作区核对[五条核心流程时序](design/PHASE0_FLOWS.md)、[POM 依赖基线](design/PHASE0_DEPENDENCIES.md)与[所有权和例外](design/PHASE0_BASELINE.md)。[阶段 1A](design/PHASE1A_IMPLEMENTATION.md)已分离 AcquireActor 与委托凭证并同步调用方/测试源码；未运行构建和测试。旧阶段 1B 已分离 Web/数据库入口并绑定部署 tenant；[1B 重设计](design/PHASE1B_IMPLEMENTATION.md)现已改为固定部署 code、DataSource/schema 与 Header 校验，已有源码实现，旧核心租户类已删除；2026-11-17 修复内部 HTTP 头与 schema 校验并补隔离测试，运行验收未完成；[阶段 1C](design/PHASE1C_IMPLEMENTATION.md)已明确 UPMS 默认持久化与应用层装配归属并同步 Admin；[阶段 2](design/PHASE2_IMPLEMENTATION.md)已拆分产品商业策略解析、条款投影与额度片段编译；[阶段 3](design/PHASE3_IMPLEMENTATION.md)已实施事实/投递边界、自包含财务证据及付款回查替代的源码，退款最新累计查询保留，运行验收仍开放；阶段 4～5 尚未实施，不能将源码调整当作运行验收。

当前查询边界：完整付款证据路径的 acquire 落账与额度 Confirm/Release 不再回查 payment；旧冻结载荷仍按原顺序查证。退款恢复仍先重放本地回执，再读取最新累计，费用退款也保留最新汇总读取，详见[财务证据记录](design/PHASE3_FINANCIAL_EVIDENCE.md)。新业务事件类型已与目标路由分开；旧冻结标签仅保留指定读取。不能提前按目标架构删除查证、幂等或执行围栏。

本文描述当前工作区源码边界；接入入口见 [README](README.md)，后续工作见 [ROADMAP](ROADMAP.md)。构建、测试与真实环境验收分别记录。

## 应用与事实归属

| 应用 | 权威事实与职责 |
|---|---|
| admin | 自有 UPMS 账号、权限与审计；商户/产品管理及 OAuth 客户端管理 BFF |
| oauth2-server | 机器/商户客户端注册、授权及 client_credentials 令牌签发 |
| merchantcenter | 商户主档、资格、关系和授权 |
| productcenter | 产品目录、商业合约、条款快照、准入与额度准备 |
| acquire | 收单订单、付款金额占用、订单终态、商户通知 |
| payment | 付款、attempt、退款、渠道绑定及证据 |
| limitcenter | 周期限额、预占、执行凭证、消耗、释放及退款恢复 |
| realtime | 订单/付款/退款投影与来源版本 |
| reconciliation | 封存账单、交易快照、批次、差错及复核记录 |
| notificationcenter | 站内信受理与收件箱；Gotone 渠道投递装配 |

merchantmng/productmng 已合并进 admin，不再作为独立部署单元。admin 不直接访问两个 center 或 OAuth server 的数据库。
签约前的商户事实查询在 productcenter 本地事务外完成，合约写入与状态锁由 productcenter 负责。
realtime 和 reconciliation 不裁定支付结果、不修改交易资金事实。渠道协议适配由 Framework payment-channel 提供。
网关仅发布显式列出的公开路由，内部接口和 Actuator 不公开，具体以 [网关配置](deploy/gateway/README.md) 为准。

## 统一术语与运行边界

- 应用名使用上表十个名称；Maven 目录使用 `loadup-pay-*`。`CustomerHttpService` 等现存 Java 类型、迁移文件名与错误码按源码保留，不据此推导额外服务。
- `/api/merchantmng/**`、`/api/productmng/**` 是 admin 当前 Controller 的 URL 前缀，不是独立应用；网关模板尚存旧 backend 引用，属于部署适配缺口，见路线图。
- Pay 数据库基线为 PostgreSQL。业务时间列使用 `timestamp(6) without time zone`，JSON 文本使用 `TEXT`；JVM、业务 Clock 与数据库会话采用一致的本地时区。商户主档归 merchantcenter，目录与合约归 productcenter。
- 一套部署服务一个租户，`loadup.database.schema` 固定连接 schema，`loadup.deployment.code` 固定部署标识；JWT 提供主体、audience 与权限，不要求也不携带 `tenant_id`。业务表无租户列，不存在运行时动态 schema 路由。
- admin/UPMS 负责操作员登录；oauth2-server 负责 SERVICE/MERCHANT_APP/MERCHANT 的 client_credentials。内部调用通过 OAuth2 客户端获取短期令牌，不挂载预签发服务 JWT 文件。
- 当前能力说明以源码为依据；未完成能力集中在模块限制和 ROADMAP，运行状态只由对应版本的验收证据确认。日期化发布记录单独保存在 LOCAL_DEVELOPMENT，不作为当前架构描述。

## 分层与入口

跨服务契约按提供方归属，详细规则见 [契约架构](loadup-pay-contracts/ARCHITECTURE.md)。client 不依赖 domain 或其他 Pay 服务 client；
消费方 app/domain 使用本地模型，远端 client 仅由 infrastructure 依赖，通过 Spring 管理的 MapStruct Converter 转换。
`loadup-pay-contracts-events` 只保留按发布方分包的事件及公开事件生命周期枚举，不再承载付款请求响应或产品条款树。

merchantcenter、realtime 等小型服务按 `client/service/bootstrap` 拆分：client 保存 Facade、DTO、错误码及传输契约；service 在同一 JAR 内保留 domain/app/infrastructure 包边界，实现领域模型、用例编排和持久化；bootstrap 承载启动类与 HTTP adapter。payment、acquire 等复杂服务继续按 `client/domain/app/infrastructure/adapter/bootstrap` 分层发布。所有 client 不依赖 service 实现，service 是可复用的普通 JAR。

渠道组件按 Framework 的 `loadup-components-payment-channel-api/core/alipay/wechat` 拆分，Pay 不再包含
`channel/` reactor。payment 的 client/domain/app/infrastructure 只编译依赖 channel API，bootstrap 以 runtime 依赖装配 core
和厂商 binder。分层服务配置以 `*-bootstrap/src/main/resources/application.yml` 为准，admin/notificationcenter/oauth2-server 使用本模块 `src/main/resources/application.yml`，数据库迁移位于各
service（保留完整分层的服务为 infrastructure）的 `src/main/resources/db/migration/pay/<app>`。

## 金额、状态与执行边界

- acquire 管理应收、已付和在途占用；payment 管理实际付款与退款；limitcenter 管理额度。三者不可互相替代。
- 订单遵守 `0 <= paid + reserved <= amount`；UNKNOWN 保留占用，超时不能作为未收款的证据。
- PAID/CLOSED 订单不重新开放。退款使用独立请求和子退款，不减少原订单累计付款金额，也不修改原成功付款事实。
- 首次渠道执行前提交本地资格/attempt，再在事务外发起外部调用，独立事务应用结果。查询/恢复沿用原金融请求身份，不因任务重试创建新付款或退款。
- 本地业务变更、Task、receipt 和 Task 按业务边界同事务提交；跨服务调用不共享本地事务。
- `guarded` 只做业务错误映射，`readSnapshot` 提供只读快照，`writeLocal` 管理本地写事务。不能把错误映射 helper 当成只读事务。
- 来源版本、事件身份和接收 receipt 防止重复投递与旧快照覆盖；退款额度、商户通知、费用和 realtime 投递分别保留目标 ACK。

## 对账边界

对账比较两侧完整、封存的输入。账户身份来自受控配置，普通文件上传或请求自报 hash 不构成来源证明。比较任务与结果提交独立于支付主链路；失败重试保留原 run、task 和封存输入。差错复核、审批及源端只读查证不会自动产生金融处置。

详细模块行为见各模块 README；支付恢复约束见 [payment 架构](loadup-pay-payment/ARCHITECTURE.md)。

## 应用间 HTTP

应用间使用 Spring HTTP Service Clients + RestClient。提供方 client.http 与对应 Controller 是当前接口清单；旧 HTTP 迁移过程清单已移除。运行验收见 [最终验收](deploy/ACCEPTANCE.md)。

### 契约与分层

各提供方的 client 持有纯 Java Facade、Command/Query/DTO、结果码及 client.http 下的标准声明接口。
标准接口只使用 Spring HTTP 注解/头类型；HttpTemplate、RestClient 实现和连接池配置属于消费方 infrastructure。
app/domain 不直接引用 client.http。MVC Controller 将请求交给既有 app Facade，身份、事务和业务判断继续由原负责层承担。
Pay 已删除内部 proto、Wire mapper、Dubbo 生成/自动装配、运行 binder 和 nacos profile；Framework 的可选 binder 独立保留。

远端请求响应经消费方基础设施转换为本地模型；共享事件由独立 contracts-events 持有，不直接复用源服务的内部模型。无跨服务数据库依赖。

### 身份与业务结果

内部业务入口使用 POST /api/internal/v1/，Resource Server 校验 JWT 签名、issuer、audience、有效期与 token_use。
主体来自验证后的 claims，producer、请求体和普通 Header 不是可信身份；部署租户由本地配置绑定。商户委托保留原主体和受众，不能替换成更高权限 SERVICE token。
SERVICE 凭证通过 loadup-components-service-auth 的标准 OAuth2 客户端获取，按 service/audience 隔离缓存与续取，再显式传入 Authorization；资源端经 JWKS 本地验签及授权。
不采用原内部 Triple 的 exp-only 解析方式。网络调用位于本地事务外，未知受理结果、幂等身份和 Task 工作流保持原语义。

普通成功/业务失败使用 HTTP 200 的 SuccessResponse/FailureResponse，capture 明细为平面 PageResponse。
HttpServiceResponses 同时核对 code/status/data；仅本地白名单恢复业务错误，未知码、认证/传输/JSON 失败按依赖失败处理。
远端文案、凭证和载荷不进入错误或日志。JSON 严格整数/枚举、BigDecimal、缺字段与显式零值、状态/金额/字节哈希约束见各 client 的契约校验类型与模块架构文档。

### 地址、资源与发布

Service Client 以目标应用名命名，默认地址 `http://<目标应用名>:8080`，共享已有 HttpTemplate 命名连接池与 Boot Observation。
K8s 普通 ClusterIP Service DNS 直接适用；本地 profile 只覆盖对应 localhost 端口，环境变量仍可按需覆盖特殊环境。
无需 K8s API 权限或独立注册中心。超时、连接池和大小限制使用框架默认值；只有偏离约定的客户端才单独配置，例如对账下载
`payment-statements` 保留 60s 读取和独立有界连接池。客户端不自动重试、不跟随重定向；业务可靠重投由原 Task/任务负责，
未来网关也不得叠加隐式重试。

共享配置默认由 HttpClientDefaults 绑定 loadup.http.client；模块显式配置优先。service-auth 注册项只声明 scopes，调用方、租户、
目标 audience 和 client-id 按应用名约定推导，个别环境可覆盖。helper 不负责认证，也不缓存 token。旧 loadup.rpc.client 配置不再读取。
入站资源服务器 audience 取 `spring.application.name`，ConfigMap 不再维护 `<APP>_JWT_AUDIENCE` 和 `*_BASE_URL`。

当前 reactor 包含九个业务/管理应用和独立 oauth2-server；部署清单仍需按当前目录与签名材料复核。版本化 ConfigMap/环境 Secret 与 AuthServer KeyStore 文件 Secret 已接入发布渲染；
显式版本写入 Pod template，预检只读取 immutable 元数据，归档镜像/引用组合。滚动与排空源码已补，运行待验收；
HTTP NetworkPolicy 与 Envoy Gateway 配置已补，CNI/控制器/流量验收与虚拟线程仍需后续完成。详见 [配置与发布](deploy/CONFIGURATION.md)。
不承诺 ConfigMap 更新自动刷新 Bean。各应用同步匹配镜像与配置；回退旧协议也需匹配整组镜像/配置/Service，不能混用新旧协议。
现有集群遗留资源不会被 apply 自动删除。本次未部署、未发送金融请求。

### 验收边界

源码覆盖、IDE 检查、编译、单测与真实环境验收分别报告。Boot 4.1.x、Java 25 保持不变。
验收以当前镜像、配置和原始结果为准；源码、IDE 检查和历史运行记录不替代当前证明。

## PostgreSQL 开发基线与验收

数据库组件当前只装配配置的默认 schema，尚未实现运行时租户路由。下述独立数据库/schema 是服务所有权边界，不代表动态多租户隔离已经启用；详见[框架数据库组件](../loadup-framework/loadup-components/data/loadup-components-database/README.md)。

状态：迁移源码已整理；真实 PostgreSQL 初始化、并发和故障恢复验收尚待执行。不要把 SQL 语法检查、编译或已有测试源码视为运行通过。

## 配置与所有权

acquire、payment、merchantcenter、productcenter、limitcenter、realtime、reconciliation、admin、notificationcenter 和 oauth2-server 各自使用独立数据库或隔离
schema；一个服务不得依赖另一个服务的表或事务。各服务使用对应的 `<SERVICE>_JDBC_URL`、
`<SERVICE>_JDBC_USERNAME` 和 `<SERVICE>_JDBC_PASSWORD`，URL 应为 PostgreSQL JDBC URL。启动时显式选择 `classpath:db/migration/pay/<app>` 及获准的 `classpath:db/migration/framework/<module>`；oauth2-server 另按自身配置选择框架 OAuth 专用迁移目录；
共享根 `classpath:db/migration` 会递归扫描 classpath，不能用于隔离应用与依赖 JAR。只在新建的可丢弃开发库上执行初次验证，不对已有库自动执行
Flyway clean、repair 或数据搬迁。

支付应用保留自己的业务迁移和冻结的组件初始快照；Framework 的 File、Transfer、UPMS、Notification 等业务模块保留各自命名空间的自动迁移，
由应用显式选择，统一纳入该应用的一个迁移历史。组件模板变化通过新的增量版本升级，生成器不会覆盖或删除已登记快照。安装 JAR 时应先 clean：旧构建输出可能仍含已删除或已移动的迁移，即使源码目录已经干净也会被 Flyway 扫描。

单数据源装配使用 Spring JDBC 事务管理器，让业务写入、Task 和本地 JDBC 使用同一数据源；Task 会拒绝不匹配的事务管理器。
GlobalUnique、Task 和 Inbox 的幂等插入使用指定业务唯一键的 `ON CONFLICT DO NOTHING`，不将其他约束错误当作重复请求。
事务中必须使用绑定到相同数据源的 Spring 管理连接；真实数据库并发、回滚及事务装配仍需验收。

时间字段使用 `timestamp(6) without time zone`，JVM、业务 Clock、JDBC 和数据库会话采用同一部署本地时区；显式偏移的协议时间仍表示绝对时间。字符串列使用
`C` collation；金额、版本和序列保持整数，证据 JSON 保持 TEXT。身份和分页比较遵循当前 PostgreSQL collation、索引和查询定义，必须在目标数据库上验证业务身份及分页语义。

## 待验收

当前版本需记录数据库/JDBC/Flyway 版本、schema、时区、迁移清单及运行结果；验证空库和已有库升级、幂等/receipt/Task/任务原子回滚、并发围栏、付款退款与未知结果恢复、对账 RR/封存、时间与金额往返。运行场景与记录模板统一见 [最终验收](deploy/ACCEPTANCE.md)，各模块测试 README 维护窄范围入口。

父 POM 的 Failsafe 在 verify 阶段运行 IT，使用 `-Dit.test` 限定相关类；构建测试由开发者执行，始终 clean。单个测试结果不能证明全部应用通过。

## 平台入口与网络边界

Gateway API + Envoy Gateway v1.9.3 是默认入口参考，见 [网关配置](deploy/gateway/README.md)。
公开路由不包含内部接口或 Actuator；完整 JWT/租户/业务授权仍在服务内执行。协议不重试，Envoy 控制头移除，渠道原文与签名头不重写。
各应用入站按调用图、指定 Gateway 代理与监控 Pod 限定，策略与渲染顺序见 [网络策略](deploy/NETWORK_POLICY.md)。
网络策略对象、标签和 HTTP 健康不证明 CNI 隔离、xDS 以及金融链路已验收。

## 线程执行与验收

各应用基础配置显式关闭虚拟线程，virtual-threads profile 仅作为压测候选，开启原生 Boot 虚拟线程和 keep-alive。
两种模型均使用原生任务上下文传播与现有 LoadUpContextTaskDecorator；不替换全部执行器，不把数据库事务带到其他线程。
同步 HTTP 池、超时、渠道恢复 Bulkhead 与金融围栏保留。Local fixed-delay 扫描不因开关而自动并行。
实际模型/上下文/调度回归源码与容量门槛见 [最终验收](deploy/ACCEPTANCE.md)，未运行；没有新增生产诊断接口。

## 身份与部署租户

admin 通过 UPMS 负责操作员登录，oauth2-server 负责 SERVICE、MERCHANT_APP、MERCHANT 的机器令牌。OAuth 客户端和 JWT 不携带逻辑租户；物理 schema 由 `loadup.database.schema` 固定，一套部署只服务一个租户，动态 schema 路由不存在。

Admin 客户端管理原样转发操作员 JWT；OAuth server 独立验证 Admin issuer、audience 和公钥并检查操作权限。Admin 不用机器身份替代操作员，也不直连 OAuth 数据库。凭证轮换立即替换唯一 secret，既有 JWT 仍有效至到期；操作详情见 [OAuth2 Server](loadup-pay-oauth2-server/README.md)。

## 外部制品版本

Pay BOM 导入 Framework 和 Modules BOM，在 BOM 内通过 `loadup.framework.version` 与 `loadup.modules.version` 固定配套运行时版本。
Pay parent 只导入本仓库同版本的 Pay BOM 和内部测试所需的 Testify BOM；子模块继承依赖管理，实际 Testify 依赖保持 `test` scope。
公开 Pay BOM 不导入 Testify。各 BOM 不继承所属仓库 parent，避免模型循环；构建插件及处理器路径留在 parent。

## Database 编译期处理器

编译期配置由 database processor 内置维护，模块无需同步配置；处理器路径与 MapStruct 一样不单独声明版本，统一通过 Pay BOM → Framework BOM 解析。详见
[framework database processor 文档](../loadup-framework/loadup-components/data/loadup-components-database-processor/README.md)。
