# 应用间 gRPC 规范

应用间只使用 Protobuf gRPC；不保留内部 HTTP 回退。商户/运营 API、收银台、渠道通知和外部系统接口沿用各自协议。

## 服务所有权

各服务在自己的 `*-rpc` 模块维护 `.proto`、MapStruct mapper 和类型化方法定义，各业务方法由提供方维护。消费方引用提供方契约，client/domain 不引入 gRPC。源事件类型归生产方；消费方的信封与 RPC 方法通过 proto import 引用源类型。

| 提供方 | 协议入口 |
| --- | --- |
| customer | [customer.proto](../customer/customer-rpc/src/main/proto/customer.proto) |
| product | [product.proto](../product/product-rpc/src/main/proto/product.proto) |
| limitcenter | [limitcenter.proto](../limitcenter/limitcenter-rpc/src/main/proto/limitcenter.proto) |
| payment | [payment.proto](../payment/payment-rpc/src/main/proto/payment.proto) |
| acquire | [acquire.proto](../acquire/acquire-rpc/src/main/proto/acquire.proto) |
| realtime | [realtime.proto](../realtime/realtime-rpc/src/main/proto/realtime.proto) |

| 调用方 | 提供方 | 用途 |
| --- | --- | --- |
| acquire | product | 订单准入 |
| product | customer / limitcenter | 商户资格 / 策略激活 |
| acquire | payment | 安全输入、受理、查询、控制、结算事实和退款金额汇总权威查询 |
| payment | limitcenter | 预占、执行许可、确认、释放和退款恢复 |
| limitcenter | payment | 权威付款结算与成功退款事实 |
| payment | acquire | 付款结果、退款费用基数和退款商户通知登记事件 |
| acquire / payment | realtime | 完整交易快照 |

## 公共能力归属

支付工程的 pay-common、pay-rpc 已删除，不新增替代公共模块：

| 能力 | 框架归属 |
| --- | --- |
| BusinessTime、EventEnvelope、ContractReference、共享支付枚举、显式 CNY 金额守卫 | loadup-commons-dto |
| DomainViolation | loadup-commons-error |
| channel 调用、方法绑定、JWT 回调隔离、校验、错误 trailer、Protobuf 标量转换 | loadup-components-rpc-binder-grpc |

commons 不依赖 components。CNY 约束通过 `DomainChecks.cnyAmount` 明确表达，不改变框架 Money 的多币种与有符号金额能力。通用 EventEnvelope 不持有支付聚合或服务端可信身份。

## Wire 与 DTO 映射

每个 `XxxRpcMapper` 使用框架 `LoadUpMapStructConfig` 和 MapStruct 注解处理器生成字段转换，消费端/服务端使用其不可变 `INSTANCE`。普通字段不手写逐字段 setter；protobuf 元数据不参与业务映射。Wire → DTO 保持严格目标字段检查；DTO → protobuf 显式列出业务字段，避开 unknownFields 等生成器基础设施属性。

- `optional` 依赖生成的 `hasX()` 判断存在性，区分缺失与零值/空字符串。
- 业务枚举使用明确的符号字符串，未知值拒绝，不默认降级。
- sealed 类型的 `oneof` 分支保留显式转换，缺失或未知 variant 拒绝。
- repeated 字段在 `@AfterMapping` 中调用 protobuf builder adders；不能修改 getter 返回的不可变集合。读取集合由 MapStruct 完成。
- `GrpcScalarMapper` 处理标准 Timestamp 与精确十进制。Timestamp 保持绝对时刻，Java 边界恢复系统默认时区；费率字符串不转 double。

消息字段号永久稳定，删除必须 reserved，不重排或复用。更改消息时同步本服务的映射和回归测试；生成代码仅存在 target，不提交。

## 配置与认证

同一环境内调用双方位于同一 Namespace。服务 `<service>` 的 gRPC Headless Service 固定为 `<service>-headless`、端口50051，使用 `clusterIP: None`。HTTP ClusterIP Service 仅承载外部 API/Actuator。

```yaml
loadup:
  rpc:
    clients:
      payment:
        channel: payment
        deadline: 3s
spring:
  grpc:
    client:
      channel:
        payment:
          target: dns:///payment-headless:50051
          default:
            load-balancing-policy: round_robin
```

`RpcClientProperties` 由 Spring 绑定 channel/deadline，目标与 TLS 沿用 `spring.grpc.*`。复用 starter 管理的 channel，不在 Java 拼接目标地址。DNS 返回就绪 Pod IP，round_robin 分配新 RPC；既有流不迁移，DNS/端点变化有重解析延迟。

业务 adapter 显式导入框架 `GrpcSupportConfiguration`，并在服务注册时绑定 `RpcJwtInterceptor`。这组校验支持按需使用：消费应用需已有 resource-server 和 Validator，不改变只引入原生 starter 的用户。

入站复用 JWT 签名、issuer/audience/token_use 校验及权限转换，每个回调独立绑定和恢复 SecurityContext。租户由已验证身份与应用层确定；metadata 自报 tenant/producer 不作为认证。SERVICE token 逐次读取，商户委托令牌保留既有受众约束。

业务异常通过 FAILED_PRECONDITION 和固定 `loadup-result-code` trailer 返回；调用方只映射已声明白名单，未知错误作为依赖失败，不输出远端文案、载荷或凭证。认证失败为 UNAUTHENTICATED，非法请求为 INVALID_ARGUMENT。写调用保留幂等意图和 deadline，不自动重试或回退 HTTP，本地事务不跨网络。

## 时间与发布边界

BusinessTime 每次读取 `ZoneId.systemDefault()`，默认 Clock 使用 `Clock.systemDefaultZone()`；不设置全局 TimeZone、固定 JVM/TZ 环境变量、Headlamp 时区挂载或 JDBC `SET time_zone`。数据库会话与应用的本地时间语义须一致，由实际环境提供默认值。JWT epoch、Protobuf Timestamp 保持绝对时间语义。

额度自然日/周/月按系统默认时区计算；运行规则记录实际 zone 并校验一致性，避免环境变更后重解释旧桶。`limit.timezone` 已从产品合约配置移除；新建表约束接受非空 zone，不再限定 Shanghai。修改了开发阶段建表脚本，本轮没有对已有数据库执行迁移或改写存量数据。

业务 gRPC 随各应用的 `*-business` 构建/运行 profile 装配，默认骨架仅有 HTTP 健康入口。部署前准备数据库、JWT、挂载令牌和生产 TLS；Headless/端口声明不能证明实际监听或认证链路成功。当前本地脚本固定 loadup-pay Namespace，不等于生产流水线。

通过 IDEA MCP 终端完成框架受影响模块与六业务应用及其依赖的窄范围 `clean test-compile`，普通字段映射由 MapStruct 生成。系统默认时区、optional/oneof/Timestamp、集合和认证隔离的测试源码已编译，未执行测试、DDL或部署。真实环境仍需验收 JWT/TLS、幂等重放、多副本请求分布和滚动发布。详细清单与发布步骤见[框架 K8s 规范](../../loadup-framework/loadup-components/integration/loadup-components-rpc/loadup-components-rpc-binder-grpc/KUBERNETES.md)。


## 退款商户通知登记（2026-10-24）

`AcquireRpc/RefundNotification` 接收 `EventEnvelope<RefundMerchantEventDTO>`，返回 `RefundNotificationAckDTO`。源 DTO/Wire 消息属于 payment，acquire 的信封通过 proto import 引用。Wire 标量使用 optional/int64，保留 request 级事件缺少 refundId/paymentId/succeededAt 的存在性；拒绝缺必需字段、未知聚合或终态。使用已有 acquire channel/deadline/token-file，无内部 HTTP 回退。

只允许配置的 payment SERVICE 和 `pay:acquire:refund:notification`。现有挂载令牌须由发行方增加该权限；逐次读取文件继续支持令牌轮换。本轮没有签发令牌、部署或发出真实调用。语义 ACK 精确绑定 deliveryId/businessEventId/aggregateId/sourceVersion/merchantEventId，disposition=ENQUEUED/DISABLED；不表示商户已收到。


## 原安全输入引用查询（2026-10-24）

新增 `PaymentRpc/InputLookup(WirePaymentInputLookupQuery) -> WirePaymentInputLookupDTO`，消息仍归 payment-rpc。
请求保留原付款/订单/预占/商户/金额/CNY/inputKey/choice 完整范围；响应 found 为 optional bool，必须显式存在，
与 input 消息 presence 一致。缺请求字段、found 未设置、found/input 不匹配、缺引用/摘要/期限或未知渠道符号均拒绝。
金额仍为 int64，期限使用 Timestamp，DTO 恢复本地时区。新消息不复用或重排已有字段号；生成类不提交。

复用 payment channel/deadline、逐次读取 SERVICE token 和现有 GrpcTransport，独立权限为 `pay:payment:input:read`。
provider 不解密/续期，不接受自报 tenant/service；consumer 不把异常或远端 NOT_FOUND 当作 found=false。
只白名单映射原幂等/参数错误，未知失败继续 ACQUIRE_DEPENDENCY_UNAVAILABLE。无 HTTP 回退和跨网络本地事务。
已同步 Facade/port/operation/服务绑定/MapStruct/consumer 及测试源码；未运行编译、测试或真实 JWT/RPC 验收，挂载凭证权限尚需发行方配置。


## 原安全输入到期条件处理（2026-10-26）

新增 payment 所有权的 `InputExpire(WirePaymentInputExpireCommand) -> WirePaymentInputExpiryDTO`。
请求含原 inputRef 与完整不可变意图，所有标量保留 optional presence、金额 int64；结果 disposition 必需且只接受
INPUT_ACTIVE/PAYMENT_EXISTS/EXPIRED_CLOSED，closed 消息仅最后一种允许且必需。
closed 的原 order/payment、fenced、正 epoch、未受理 evidence 都须存在并匹配，不能夹带 payment 记录。
普通字段用 MapStruct，严格 hasX/未知枚举/互斥结果校验留在协议边界；旧消息字段号没有改动。

新增 SERVICE 权限 `pay:payment:input:expire`，复用 payment channel/deadline/token-file/JWT 隔离，
操作内部只有本地数据库事务，不调用网络；消费方在 acquire 事务外调用，再用自己的 A3 落库。
异常不回退 HTTP、不伪装成未受理或可释放；新权限尚未在真实挂载令牌/部署环境配置。
协议、服务绑定、consumer 与测试源码已补，未运行任何编译、测试或真实 RPC 验收。
