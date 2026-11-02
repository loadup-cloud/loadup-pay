# LoadUp Pay 架构

本文描述当前源码边界；接入入口见 [README](README.md)。数据库基线为 PostgreSQL，运行验收需由开发者另行执行。

## 服务与事实归属

| 模块 | 权威事实 | 主要下游 |
|---|---|---|
| customer | 商户/子商户资格、关系和授权，主档复用框架商户模块 | 框架 merchant |
| product | 产品、合约条款、准入和额度策略发布 | customer、limitcenter |
| acquire | 收单订单、付款金额占用、订单终态、商户通知 | product、payment、realtime |
| payment | 独立付款、attempt、退款、冻结渠道绑定及渠道证据 | channel、limitcenter、acquire、realtime |
| limitcenter | 周期限额、预占、执行凭证、消耗、释放及退款恢复 | payment 查证 |
| realtime | 订单/付款/退款投影与来源版本 | 接收可靠事件 |
| reconciliation | 封存账单、交易快照、批次、差错及复核记录 | payment、框架 file/transfer |
| channel | 渠道能力与支付宝/微信适配 | 外部渠道协议 |

realtime 和 reconciliation 不裁定支付结果，不直接修改交易资金事实。channel 是 payment 使用的插件聚合，不是独立业务数据库所有者。

## 分层与入口

七个服务均按 `client/domain/app/infrastructure/adapter/bootstrap` 拆分。client 保存 Facade、DTO、错误码及传输契约；domain 保存领域模型；app 编排用例并声明端口；infrastructure 实现持久化和远端调用；adapter 接入 HTTP/Dubbo；bootstrap 装配应用。

渠道模块按 `channel-api/channel-core/channel-alipay/channel-wechat` 拆分。各服务的配置以 `*-bootstrap/src/main/resources/application.yml` 及其附属配置为准，数据库迁移位于各 infrastructure 的 `src/main/resources/db/migration`。

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

## 应用间 HTTP 迁移

目标与阶段见 [Roadmap](ROADMAP.md)，逐操作状态见 [HTTP 迁移清单](HTTP_MIGRATION.md)。
customer、product、limitcenter、payment 提供端及对应消费端已改为标准 HTTP Service Clients + RestClient（31/44 操作）。
customer、limitcenter 删除各自 IDL/mapper；product 已删除 service，但暂留 payment 的共享 Wire 消息/mapper。
customer/product/limitcenter 不再装配 Dubbo binder 或 Nacos；payment 暂留 acquire/realtime 的出站调用。
其他链路保持当前 Triple 实现，逐批同步切换两端，不保留自动协议回退。以下 Triple 规范只适用于尚未迁移的链路。

customer 内部路径为 POST `/api/internal/v1/customer/eligibility/authorize`，复用既有 Command/DTO 及业务错误信封；
按 `loadup.http.clients.customer.base-url` 配置提供方地址（默认 `http://customer:8080`），支持本地覆盖。
声明式客户端共享现有 HTTP 连接池、超时、TLS、DNS 策略和 Boot Observation，没有额外注册中心依赖。

每次调用显式传递当前符合 customer audience 的 bearer；customer 的 Resource Server 重新校验 JWT 签名、issuer、audience、
有效期和 token_use，租户/主体由验证后凭证建立。HTTP 不采用下述 Triple exp-only 内部信任模型。
成功/业务失败均按 HTTP 200 + result/data 协议，客户端同时核对结果码与状态，远端未知码及传输失败按本地依赖失败处理。
资格事实检查、事务外联网和禁止自动重试均保留。

## 剩余应用间 RPC（Dubbo Triple + Protobuf IDL）

剩余内部调用采用 Triple 纯 IDL 模式，迁移完成的 customer、product、limitcenter、payment 的 HTTP 提供端不再适用此节。
商户/运营 API、收银台、渠道通知和外部系统接口沿用各自协议。

## 契约所有权

尚未迁移的服务在自己的 `*-client` 模块同时维护纯 Java Facade、Command/Query/DTO、结果码，以及 `.proto`、生成的
protobuf 消息/存根和 Wire↔DTO MapStruct mapper。IDL 代码固定在 `rpc` 子包（`io.github.loadup.pay.<svc>.rpc`），
与 `client` 业务契约隔离；domain 不依赖 client，app 不直接引用 rpc 类型，适配器负责 Wire↔DTO 转换。

| 提供方         | 契约模块              | Proto service / 方法                                                                                                |
|----------------|-----------------------|---------------------------------------------------------------------------------------------------------------------|
| customer       | customer-client       | 已迁移 `CustomerHttpService.authorize`                                                                                             |
| product        | product-client        | 已迁移 `ProductHttpService.authorize/detail`                                                                                       |
| limitcenter    | limitcenter-client    | 已迁移 `LimitCenterHttpService` 九个操作                          |
| payment        | payment-client        | 已迁移 Payment/PaymentCapture/PaymentStatement/PaymentVerificationHttpService，共 19 个操作 |
| acquire        | acquire-client        | `AcquireRpc.PaymentOutcome/FeeRefund/RefundNotification`                                                            |
| realtime       | realtime-client       | `RealtimeRpc.Order/Payment/Refund/RefundSummary`                                                                    |
| reconciliation | reconciliation-client | `ReconciliationRpc.Create/Attach/Start/Retry/Task/Detail`                                                           |

`payment-client` 依赖 `product-client`（AdmittedTerms 转换）；`acquire-client` 依赖 `payment-client`；`realtime-client` 依赖
`acquire-client` + `payment-client`。跨服务只复用源服务的 Wire 类型，不复制消息定义。

## 代码生成

`*-client` 的 POM 使用：

```xml
<plugin>
  <groupId>org.apache.dubbo</groupId>
  <artifactId>dubbo-maven-plugin</artifactId>
  <version>${dubbo.version}</version>
  <executions><execution><goals><goal>compile</goal></goals></execution></executions>
</plugin>
```

`src/main/proto` 生成 protobuf 消息、`<Service>Rpc` 接口和 `Dubbo<Service>RpcTriple` 存根/ImplBase。
`package` 决定 Triple 服务名（如 `loadup.pay.payment.v1.PaymentRpc`），`java_package` 决定 Java 包。
消息类型会命中 Dubbo 的 IDL 分支（不生成 Wrapper 信封），因此不需要 `serialization` 配置。

## 服务端

`*-adapter` 实现生成的 `ImplBase`，只重写业务方法，并标注 `@DubboService`：

实现模式参考现有 `acquire-adapter` 的 `AcquireRpcService`；customer、product、limitcenter、payment 已使用 MVC Controller。


业务 Facade 与事务仍在 `*-app`；adapter 不做业务判断。一个 proto service 对应一个 `@DubboService` 类；
原 payment 的 4 个 proto service 已切换为 HTTP，IDL 仅暂留共享事件消息。

`dubbo.scan.base-packages` 必须覆盖 adapter 实现类所在包，否则 `@DubboService` 不会被注册。

## 客户端

`*-infrastructure` 的适配器在字段上标注 `@DubboReference`，字段类型是生成的 `<Service>Rpc`：

参见现有 `PaymentAcquireRpcClient`：引用 AcquireRpc，构造器注入 mapper，调用时以 RpcTokenContext 传递当前服务凭证。

Dubbo 不支持构造器参数注入。消费适配器保留租户路由、事务边界、挂载令牌读取和本地错误白名单映射。

## 发现、端口与超时

- 未迁移服务 Triple 端口 `50051`；customer、product、limitcenter、payment 提供端仅保留 HTTP `8080`，其他服务 HTTP/Actuator 也是 `8080`。
- 当前默认通过 `dubbo.reference.<接口全名>.url` 配置 `tri://<service>-headless:50051` 直连，`dubbo.registry.address: N/A`。
- `nacos` profile 仍作为可选发现配置；后续 K8s DNS + HTTP Service Clients 的完整迁移以 [Roadmap](ROADMAP.md) 为准。
- 全局 `dubbo.consumer.check: false`、`timeout: 3000`、`retries: 0`。写调用不自动重试；账单下载已迁移为独立的 HTTP payment-statements 组，保留 60 秒读取与 32 MiB 响应上限。
- RPC 端口只对同 Namespace 开放；跨命名空间或外部访问必须经过入口网关。

## 身份与安全上下文

- 消费方用 `RpcTokenContext.call(token, ...)` 把访问令牌写入 Dubbo client attachment `loadup-authorization`，
  调用结束立即移除，不缓存凭证。
- 提供方 `RpcIdentityProviderFilter` 从 attachment 取回令牌，解析 claims 重建 `Jwt` 与 `SecurityContext`，
  调用结束后恢复原上下文。业务 actor provider 继续从 `SecurityContext` 读取凭证和权限。
- 签名、issuer、audience、token_use 由入口资源服务器验证一次；内部跳转不重复验签，只校验 `exp`。
  SERVICE 令牌逐次从挂载文件读取，支持外部轮换；商户委托令牌保持原受众约束。
- 令牌缺失或过期返回 UNAUTHENTICATED；参数校验失败返回 VALIDATION_EXCEPTION。两者都不是可持久化的业务拒绝。
- 网络调用不跨本地事务；租户来自已验证身份，不接受命令中的自报租户。

## 错误映射

业务拒绝由 provider filter 转换为携带结果码的 Dubbo 异常：

```text
RpcException(BIZ_EXCEPTION, "loadup-result-code=<CODE>")
```

消费方用 `RpcExceptionCodes.resultCode(failure)` 读取该值，并只映射自己的白名单；未知、缺失或非业务错误一律按
DEPENDENCY_UNAVAILABLE 处理。不得输出远端描述、异常栈、令牌或载荷。

## Wire 映射

MapStruct mapper 只做 Wire↔client DTO 转换：

- `optional` 标量用 `hasXxx()` 表达 presence；缺失必填字段按 INVALID_ARGUMENT 拒绝。
- 枚举以显式字符串传输，未知符号拒绝，不依赖 proto enum 的 0 默认值。
- `Timestamp` ↔ `OffsetDateTime`/`Instant` 由 `RpcScalarMapper` 处理，保留绝对时刻；`BigDecimal` 以字符串传输。
- 集合使用 builder adder；`oneof` 分支在 mapper 的 `default` 方法中显式展开。
- 删除字段必须 `reserved`；字段号不可复用。

## 协议切换与回退

每批同步升级对应调用方与提供方，不保留双栈或自动协议回退；旧、新协议端点无法互通。
本批需统一匹配 acquire、product、customer、limitcenter、payment 镜像与配置；未验收前不能将部分实例混配部署。
失败时恢复该批次匹配的镜像、地址配置与旧协议 Service/端口；未执行实际发布。

## 消费方 → 提供方

| 消费方         | 依赖的提供方                   |
|----------------|--------------------------------|
| acquire        | payment、product、realtime     |
| product        | customer、limitcenter          |
| limitcenter    | payment                        |
| payment        | realtime、limitcenter、acquire |
| reconciliation | payment                        |

## 构建与依赖

- `loadup-components-rpc-binder-dubbo` 封装 `RpcTokenContext`、`RpcExceptionCodes` 和 provider filter。
- `loadup-components-rpc-proto` 提供 Wire mapper 共享的标量转换。
- 尚未迁移的 `*-client` 引入 protobuf、MapStruct 与 `loadup-components-rpc-proto`；Dubbo 核心标记为 `optional`，避免 app
  模块传递获得传输依赖。`*-adapter` 与消费方 `*-infrastructure` 通过 `loadup-components-rpc-binder-dubbo` 获得 Dubbo。
  跨服务依赖沿用既有 client 依赖图，不新增模块边。
- Dubbo 版本由 `loadup-dependencies` BOM 固定为 3.3.6；`dubbo-maven-plugin` 版本在 pay 父 POM 中统一。

## 验收边界

源码编译、单测和真实 HTTP/Triple 验收分别报告。Boot 保持 4.1.x。
本批未运行构建或测试；已有历史编译记录不能证明当前迁移、JWT 传播、编解码或滚动发布已经验收。


## PostgreSQL 开发基线与验收

数据库组件当前只装配配置的默认 schema，尚未实现运行时租户路由。下述独立数据库/schema 是服务所有权边界，不代表动态多租户隔离已经启用；详见[框架数据库组件](../loadup-framework/loadup-components/data/loadup-components-database/README.md)。

状态：迁移源码已整理；真实 PostgreSQL 初始化、并发和故障恢复验收尚待执行。不要把 SQL 语法检查、编译或已有测试源码视为运行通过。

## 配置与所有权

acquire、payment、customer、product、limitcenter、realtime、reconciliation 各自使用独立数据库或隔离
schema；一个服务不得依赖另一个服务的表或事务。各服务使用对应的 `<SERVICE>_JDBC_URL`、
`<SERVICE>_JDBC_USERNAME` 和 `<SERVICE>_JDBC_PASSWORD`，URL 应为 PostgreSQL JDBC URL。启动时 `classpath:db/migration`
同时加载该服务 infrastructure 和其实际依赖 JAR 的共享组件迁移。只在新建的可丢弃开发库上执行初次验证，不对已有库自动执行
Flyway clean、repair 或数据搬迁。

每个支付服务的 infrastructure 只有一份初始 Flyway 文件。sequence、Outbox、DelayedTask、GlobalUnique，以及该服务实际引入的
merchant、contract、File 和 Transfer 迁移由各自模块拥有。安装 JAR 时应先 clean：旧构建输出可能仍含已删除的 MySQL
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
   RPC 时间往返。
5. 验证对账读取 RR、双侧输入封存、结果原子提交；使比较任务耗尽并显示 FAILED，修复故障后重试原 run/task，检查过时
   task、跨租户、运行中、已完成和并发重试均受控，最终只提交一次结果。
6. 记录常见查询计划，尤其运营分页和任务扫描；只有数据量与计划证明需要时再优化索引或分页。

测试使用 Testcontainers 隔离环境；支付持久化 IT 使用各 infrastructure 模块的 `postgresql-it` profile。构建和测试由开发者手动执行，统一使用带
`clean` 的窄模块命令，不能以单个测试结果推断七服务全部通过。

## Payment HTTP 边界

payment 的主操作与对账证据均使用 `/api/internal/v1/payment/`，原 Command/Query/DTO 与 app 事务逻辑复用。
capture 明细仍为平面 PageResponse；消费端在读取视图中先校验 result，再校验分页和连续序号。
PaymentCaptureContracts / PaymentEvidenceContracts 与 DTO 构造器保留旧 Wire 的状态、scope、微秒时间、金额及字节哈希检查。

原 payment.proto 的共享事件消息仍被 acquire/realtime 引用，暂不能清除整个消息生成链；这不代表 payment 继续提供 Triple。
内部 MVC JSON 入站大小限制仍需补齐；当前 HTTP 客户端的 32 MiB 限制约束出站/响应，不能替代入站流量限制。
