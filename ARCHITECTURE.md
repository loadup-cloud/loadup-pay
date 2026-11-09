# LoadUp Pay 架构

## 商户与产品核心／管理应用

| 应用 | 角色 | 数据所有权 |
| --- | --- | --- |
| merchantcenter | 商户核心查询、状态、关系和动作授权；仅内部 HTTP | 商户数据库 |
| merchantmng | 商户后管及商户签约入口 | 无数据库，通过中心业务接口操作 |
| productcenter | 产品目录、合约、冻结条款、交易准入及额度准备；仅内部 HTTP | 产品合约数据库 |
| productmng | 产品、销售方案和合约运营后管 | 无数据库，通过中心业务接口操作 |

merchantcenter 合并原 merchant 模块；productcenter 合并原 productcore。框架 BOM/验证应用不再提供这些 Pay 业务模块。共享参数枚举位于对应 client.enums，domain 可依赖 client，client 不依赖 domain。

merchantmng 的签约动作调用 productcenter，商户事实由 productcenter 通过独立 SERVICE 凭证查询 merchantcenter。签约在本地事务外获取商户事实，合约写入与状态锁留在 productcenter 内，业务交易从不依赖 mng。没有跨域数据库直连、跨库事务或审批引擎。

网关只开放两个 mng 的管理前缀，两个 center 无网关 backend。集群策略区分 acquire → productcenter、productcenter → merchantcenter、mng → center；管理和交易请求均验证 JWT。操作员令牌需要受控签发和相应多 audience，本次不新增认证协议或模拟令牌。

构建和发布清单现在包含十一个应用，all 为 Namespace 加十一组 Policy/Deployment/Service，共34个资源。原 customer/product 集群资源不会自动删除；需在新版本验收后单独退役，避免误删正在使用的工作负载。


本文描述当前源码边界；接入入口见 [README](README.md)。数据库基线为 PostgreSQL，运行验收需由开发者另行执行。

## 服务与事实归属

| 模块 | 权威事实 | 主要下游 |
|---|---|---|
| customer | 商户/子商户资格、关系和授权，主档复用框架商户模块 | 框架 merchant |
| product | 产品、合约条款、准入和额度策略发布 | customer、limitcenter |
| acquire | 收单订单、付款金额占用、订单终态、商户通知 | product、payment、realtime |
| payment | 独立付款、attempt、退款、冻结渠道绑定及渠道证据 | Framework payment-channel、limitcenter、acquire、realtime |
| limitcenter | 周期限额、预占、执行凭证、消耗、释放及退款恢复 | payment 查证 |
| realtime | 订单/付款/退款投影与来源版本 | 接收可靠事件 |
| reconciliation | 封存账单、交易快照、批次、差错及复核记录 | payment、框架 file/transfer |

realtime 和 reconciliation 不裁定支付结果，不直接修改交易资金事实。支付宝/微信协议适配由 Framework
`loadup-components-payment-channel` 组件族提供；payment 仅通过其 API 调用，不拥有渠道协议实现，也不引入独立渠道数据库。

## 分层与入口

跨服务契约按提供方归属，详细规则见 [契约架构](pay-contracts/ARCHITECTURE.md)。client 不依赖 domain 或其他 Pay 服务 client；
消费方 app/domain 使用本地模型，远端 client 仅由 infrastructure 依赖，通过 Spring 管理的 MapStruct Converter 转换。
`pay-contracts-events` 只保留按发布方分包的事件及公开事件生命周期枚举，不再承载付款请求响应或产品条款树。

merchantcenter、realtime 等小型服务按 `client/service/bootstrap` 拆分：client 保存 Facade、DTO、错误码及传输契约；service 在同一 JAR 内保留 domain/app/infrastructure 包边界，实现领域模型、用例编排和持久化；bootstrap 承载启动类与 HTTP adapter。payment、acquire 等复杂服务继续按 `client/domain/app/infrastructure/adapter/bootstrap` 分层发布。所有 client 不依赖 service 实现，service 是可复用的普通 JAR。

渠道组件按 Framework 的 `loadup-components-payment-channel-api/core/alipay/wechat` 拆分，Pay 不再包含
`channel/` reactor。payment 的 client/domain/app/infrastructure 只编译依赖 channel API，bootstrap 以 runtime 依赖装配 core
和厂商 binder。各服务的配置以 `*-bootstrap/src/main/resources/application.yml` 及其附属配置为准，数据库迁移位于各
service（保留完整分层的服务为 infrastructure）的 `src/main/resources/db/migration/pay/<app>`。

## 金额、状态与执行边界

- acquire 管理应收、已付和在途占用；payment 管理实际付款与退款；limitcenter 管理额度。三者不可互相替代。
- 订单遵守 `0 <= paid + reserved <= amount`；UNKNOWN 保留占用，超时不能作为未收款的证据。
- PAID/CLOSED 订单不重新开放。退款使用独立请求和子退款，不减少原订单累计付款金额，也不修改原成功付款事实。
- 首次渠道执行前提交本地资格/attempt，再在事务外发起外部调用，独立事务应用结果。查询/恢复沿用原金融请求身份，不因任务重试创建新付款或退款。
- 本地业务变更、Outbox、receipt 和 DelayedTask 按业务边界同事务提交；跨服务调用不共享本地事务。
- `withTenantContext` 只安装可信租户上下文，`readSnapshot` 提供只读快照，`writeLocal` 管理本地写事务。不能把上下文 helper 当成只读事务。
- 来源版本、事件身份和接收 receipt 防止重复投递与旧快照覆盖；退款额度、商户通知、费用和 realtime 投递分别保留目标 ACK。

## 对账边界

对账比较两侧完整、封存的输入。账户身份来自受控配置，普通文件上传或请求自报 hash 不构成来源证明。比较任务与结果提交独立于支付主链路；失败重试保留原 run、task 和封存输入。差错复核、审批及源端只读查证不会自动产生金融处置。

详细模块行为见各模块 README；支付恢复约束见 [payment 架构](payment/ARCHITECTURE.md)。

## 应用间 HTTP

七服务的 44 个旧内部操作均已迁移为 Spring HTTP Service Clients + RestClient 源码；运行验收待完成。
完整路径/类型、调用关系与验收状态见 [HTTP 迁移清单](HTTP_MIGRATION.md)，后续阶段见 [Roadmap](ROADMAP.md)。

### 契约与分层

各提供方的 client 持有纯 Java Facade、Command/Query/DTO、结果码及 client.http 下的标准声明接口。
标准接口只使用 Spring HTTP 注解/头类型；HttpTemplate、RestClient 实现和连接池配置属于消费方 infrastructure。
app/domain 不直接引用 client.http。MVC Controller 将请求交给既有 app Facade，身份、事务和业务判断继续由原负责层承担。
Pay 已删除内部 proto、Wire mapper、Dubbo 生成/自动装配、运行 binder 和 nacos profile；Framework 的可选 binder 独立保留。

| 提供方 | client.http 标准接口 | 操作数 |
| --- | --- | --- |
| customer | CustomerHttpService | 1 |
| product | ProductHttpService | 2 |
| limitcenter | LimitCenterHttpService | 9 |
| payment | PaymentHttpService、PaymentCaptureHttpService、PaymentStatementHttpService、PaymentVerificationHttpService | 19 |
| acquire | AcquireHttpService | 3 |
| realtime | RealtimeHttpService | 4 |
| reconciliation | ReconciliationHttpService | 6 |

跨服务复用源服务 Java DTO：payment 复用 product 条款，acquire 复用 payment 事件，realtime 复用 acquire/payment 完整快照。
不复制消息定义，不增加跨服务数据库依赖。

### 身份与业务结果

内部入口统一 POST /api/internal/v1/，Resource Server 校验 JWT 签名、issuer、audience、有效期与 token_use。
租户和主体来自验证后的 claims，producer、请求体和普通 Header 不是可信身份。商户委托保留原主体和受众，不能替换成更高权限 SERVICE token。
SERVICE 凭证通过 loadup-components-service-auth 的标准 OAuth2 客户端获取，按 tenant/service/audience 隔离缓存与续取，再显式传入 Authorization；资源端经 JWKS 本地验签及授权。
不采用原内部 Triple 的 exp-only 解析方式。网络调用位于本地事务外，未知受理结果、幂等身份和 Outbox 工作流保持原语义。

普通成功/业务失败使用 HTTP 200 的 SuccessResponse/FailureResponse，capture 明细为平面 PageResponse。
HttpServiceResponses 同时核对 code/status/data；仅本地白名单恢复业务错误，未知码、认证/传输/JSON 失败按依赖失败处理。
远端文案、凭证和载荷不进入错误或日志。JSON 严格整数/枚举、BigDecimal、缺字段与显式零值、状态/金额/字节哈希约束见迁移清单。

### 地址、资源与发布

Service Client 以目标应用名命名，默认地址 `http://<目标应用名>:8080`，共享已有 HttpTemplate 命名连接池与 Boot Observation。
K8s 普通 ClusterIP Service DNS 直接适用；本地 profile 只覆盖对应 localhost 端口，环境变量仍可按需覆盖特殊环境。
无需 K8s API 权限或独立注册中心。超时、连接池和大小限制使用框架默认值；只有偏离约定的客户端才单独配置，例如对账下载
`payment-statements` 保留 60s 读取和独立有界连接池。客户端不自动重试、不跟随重定向；业务可靠重投由原 Outbox/任务负责，
未来网关也不得叠加隐式重试。

共享配置默认由 HttpClientDefaults 绑定 loadup.http.client；模块显式配置优先。service-auth 注册项只声明 scopes，调用方、租户、
目标 audience 和 client-id 按应用名约定推导，个别环境可覆盖。helper 不负责认证，也不缓存 token。旧 loadup.rpc.client 配置不再读取。
入站资源服务器 audience 取 `spring.application.name`，ConfigMap 不再维护 `<APP>_JWT_AUDIENCE` 和 `*_BASE_URL`。

源清单包含十个业务应用和独立 AuthServer 的 HTTP Deployment/Service。版本化 ConfigMap/环境 Secret 与 AuthServer KeyStore 文件 Secret 已接入发布渲染；
显式版本写入 Pod template，预检只读取 immutable 元数据，归档镜像/引用组合。滚动与排空源码已补，运行待验收；
HTTP NetworkPolicy 与 Envoy Gateway 配置已补，CNI/控制器/流量验收与虚拟线程仍需后续完成。详见 [配置与发布](deploy/CONFIGURATION.md)。
不承诺 ConfigMap 更新自动刷新 Bean。各应用同步匹配镜像与配置；回退旧协议也需匹配整组镜像/配置/Service，不能混用新旧协议。
现有集群遗留资源不会被 apply 自动删除。本次未部署、未发送金融请求。

### 验收边界

源码覆盖、IDE 检查、编译、单测与真实环境验收分别报告。Boot 4.1.x、Java 25 保持不变。
未执行构建或测试；IDE 的部分依赖解析和 Bean 提示仍需重载 Maven 模型后复核，不代表整体检查通过。

## PostgreSQL 开发基线与验收

数据库组件当前只装配配置的默认 schema，尚未实现运行时租户路由。下述独立数据库/schema 是服务所有权边界，不代表动态多租户隔离已经启用；详见[框架数据库组件](../loadup-framework/loadup-components/data/loadup-components-database/README.md)。

状态：迁移源码已整理；真实 PostgreSQL 初始化、并发和故障恢复验收尚待执行。不要把 SQL 语法检查、编译或已有测试源码视为运行通过。

## 配置与所有权

acquire、payment、customer、product、limitcenter、realtime、reconciliation 各自使用独立数据库或隔离
schema；一个服务不得依赖另一个服务的表或事务。各服务使用对应的 `<SERVICE>_JDBC_URL`、
`<SERVICE>_JDBC_USERNAME` 和 `<SERVICE>_JDBC_PASSWORD`，URL 应为 PostgreSQL JDBC URL。启动时显式选择 `classpath:db/migration/pay/<app>` 及获准的 `classpath:db/migration/framework/<module>`；
共享根 `classpath:db/migration` 会递归扫描 classpath，不能用于隔离应用与依赖 JAR。只在新建的可丢弃开发库上执行初次验证，不对已有库自动执行
Flyway clean、repair 或数据搬迁。

支付应用保留自己的业务迁移和冻结的组件初始快照；Framework 的 File、Transfer、UPMS、Notification 等业务模块保留各自命名空间的自动迁移，
由应用显式选择，统一纳入该应用的一个迁移历史。组件模板变化通过新的增量版本升级，生成器不会覆盖或删除已登记快照。安装 JAR 时应先 clean：旧构建输出可能仍含已删除的 MySQL
或增量迁移，即使源码目录已经干净也会被 Flyway 扫描。

单数据源装配使用 Spring JDBC 事务管理器，让业务写入、Outbox 和本地 JDBC 使用同一数据源；Outbox 会拒绝不匹配的事务管理器。
GlobalUnique、DelayedTask 和 Inbox 的幂等插入使用指定业务唯一键的 `ON CONFLICT DO NOTHING`，不将其他约束错误当作重复请求。
事务中必须使用绑定到相同数据源的 Spring 管理连接；真实数据库并发、回滚及事务装配仍需验收。

时间字段使用 `timestamp(6) without time zone`，JVM、业务 Clock、JDBC 和数据库会话采用同一部署本地时区；显式偏移的协议时间仍表示绝对时间。字符串列使用
`C` collation；金额、版本和序列保持整数，证据 JSON 保持 TEXT。迁移不承诺与原 MySQL
的区域排序、尾空格和所有字符比较完全相同，必须在目标数据库上验证业务身份及分页语义。

## 必须留存的验收结果

记录 PostgreSQL 服务端和 JDBC/Flyway 版本、数据库或 schema、应用时区、迁移清单、执行命令及结果。现有 Testcontainers 默认镜像为
`postgres:15-alpine`；这是测试默认值，不是部署版本的决定。

1. 对所有服务及其实际依赖分别在新库初始化，确认无重复 Flyway 版本、无 MySQL 语法，表、索引和约束均存在。
2. 在同一事务内重复提交 GlobalUnique、Inbox 和 DelayedTask 的业务键，验证去重之后仍能继续写入；不相关唯一键冲突必须报错，外层回滚必须撤销全部本地写入。验证
   sequence 分配并发无重复。
3. 验证 Outbox 领取、重试与过期租约，以及 DelayedTask 的 claim token、attempt、旧 lease 和旧 generation 的围栏。
4. 验证支付幂等、首次提交资格、状态与 receipt/Outbox/task 的同事务回滚、退款上限、realtime 旧版本拒绝覆盖；验证金额、微秒、账单日界和
   HTTP JSON 时间往返。
5. 验证对账读取 RR、双侧输入封存、结果原子提交；使比较任务耗尽并显示 FAILED，修复故障后重试原 run/task，检查过时
   task、跨租户、运行中、已完成和并发重试均受控，最终只提交一次结果。
6. 记录常见查询计划，尤其运营分页和任务扫描；只有数据量与计划证明需要时再优化索引或分页。

测试使用 Testcontainers 隔离环境；支付持久化 IT 使用各 infrastructure 模块的 `postgresql-it` profile。构建和测试由开发者手动执行，统一使用带
`clean` 的窄模块命令，不能以单个测试结果推断七服务全部通过。

## Payment HTTP 边界

payment 的主操作与对账证据均使用 `/api/internal/v1/payment/`，原 Command/Query/DTO 与 app 事务逻辑复用。
capture 明细仍为平面 PageResponse；消费端在读取视图中先校验 result，再校验分页和连续序号。
PaymentCaptureContracts / PaymentEvidenceContracts 与 DTO 构造器保留旧 Wire 的状态、scope、微秒时间、金额及字节哈希检查。

共享事件已全部使用源服务 Java DTO + EventEnvelope，原 payment/product 消息生成链已删除。
各应用已显式配置 WebMVC 内部请求字节上限，完整有界读取后再转换 JSON，超限返回 HTTP 413；未知长度与尾随输入不能绕过。
当前客户端限制与入站限制分别配置；Advice 不覆盖无 @RequestBody 的 Servlet 直读入口，网关/容器的并发和读取时间仍需独立约束。回归源码未运行。

## 平台入口与网络边界

Gateway API + Envoy Gateway v1.9.3 是默认入口参考，见 [网关配置](deploy/gateway/README.md)。
公开路由不包含内部接口或 Actuator；完整 JWT/租户/业务授权仍在服务内执行。协议不重试，Envoy 控制头移除，渠道原文与签名头不重写。
各应用入站按调用图、指定 Gateway 代理与监控 Pod 限定，策略与渲染顺序见 [网络策略](deploy/NETWORK_POLICY.md)。
本地示例已渲染但未部署，网络策略对象、标签和 HTTP 健康不证明 CNI 隔离、xDS 以及金融链路已验收。

## 线程执行与验收

各应用基础配置显式关闭虚拟线程，virtual-threads profile 仅作为压测候选，开启原生 Boot 虚拟线程和 keep-alive。
两种模型均使用原生任务上下文传播与现有 LoadUpContextTaskDecorator；不替换全部执行器，不把数据库事务带到其他线程。
同步 HTTP 池、超时、渠道恢复 Bulkhead 与金融围栏保留。Local fixed-delay 扫描不因开关而自动并行。
实际模型/上下文/调度回归源码与容量门槛见 [最终验收](deploy/ACCEPTANCE.md)，未运行；没有新增生产诊断接口。

## AuthServer 与逻辑租户

独立 AuthServer 使用 SAS + KMS-local、标准 PostgreSQL 客户端注册/授权存储，按审查过的环境授权同步；客户端按逻辑租户、调用方、目标 audience 取令牌。商户/用户委托不改成服务身份。实现及运行边界见 [AuthServer](authserver/README.md)。

当前 TenantFilter 仍绑定默认租户，DatabaseTenant 尚把租户 ID 直接解释成 schema，缺少动态连接路由。已有 ContextHolder/ExecutionContext 基于 ScopedValue，可继续承载逻辑租户；后续应增加受控租户注册和按应用解析的 schema 映射，在验签后、事务前建立上下文。无效/缺失租户拒绝访问；跨线程显式传播，事务内不能切换，连接归还不能残留 schema，缓存/任务身份仍须包含租户。业务表不恢复 tenant_id；JWT 与控制面授权保留逻辑租户，不传播物理 schema。此项尚未实施，不能将当前源码当成动态多租户隔离已完成。
