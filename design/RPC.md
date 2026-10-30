# 应用间 RPC 规范（Dubbo Triple + Protobuf IDL）

应用间只使用 Dubbo Triple 的 Protobuf 纯 IDL 模式；不保留内部 HTTP 回退，也不使用 Java 接口 + Wrapper 序列化。
商户/运营 API、收银台、渠道通知和外部系统接口沿用各自协议。

## 契约所有权

每个服务在自己的 `*-client` 模块同时维护纯 Java Facade、Command/Query/DTO、结果码，以及 `.proto`、生成的
protobuf 消息/存根和 Wire↔DTO MapStruct mapper。IDL 代码固定在 `rpc` 子包（`io.github.loadup.pay.<svc>.rpc`），
与 `client` 业务契约隔离；domain 不依赖 client，app 不直接引用 rpc 类型，适配器负责 Wire↔DTO 转换。

| 提供方         | 契约模块            | Proto service / 方法                                                                                                       |
|----------------|---------------------|----------------------------------------------------------------------------------------------------------------------------|
| customer       | customer-client     | `CustomerRpc.Authorize`                                                                                                    |
| product        | product-client      | `ProductRpc.Authorize/Detail`                                                                                              |
| limitcenter    | limitcenter-client  | `LimitCenterRpc.Activate/Reserve/Detail/Begin/Verify/Expire/Confirm/Release/RestoreRefund`                                 |
| payment        | payment-client      | `PaymentRpc.*`（13 个方法）、`PaymentCaptureRpc.*`、`PaymentStatementRpc.Download`、`PaymentVerificationRpc.Verify`         |
| acquire        | acquire-client      | `AcquireRpc.PaymentOutcome/FeeRefund/RefundNotification`                                                                   |
| realtime       | realtime-client     | `RealtimeRpc.Order/Payment/Refund/RefundSummary`                                                                           |
| reconciliation | reconciliation-client | `ReconciliationRpc.Create/Attach/Start/Retry/Task/Detail`                                                                  |

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

```java
@DubboService(interfaceClass = CustomerRpc.class)
public class CustomerRpcService extends DubboCustomerRpcTriple.CustomerRpcImplBase {
    @Override
    public WireMerchantEligibilityDTO authorize(WireMerchantActionAuthorizeCommand request) {
        return CustomerRpcMapper.INSTANCE.toWireMerchantEligibilityDTO(
                facade.authorize(CustomerRpcMapper.INSTANCE.fromWireMerchantActionAuthorizeCommand(request)));
    }
}
```

业务 Facade 与事务仍在 `*-app`；adapter 不做业务判断。一个 proto service 对应一个 `@DubboService` 类；
`payment` 的 4 个 proto service 分别暴露为 4 个类。

`dubbo.scan.base-packages` 必须覆盖 adapter 实现类所在包，否则 `@DubboService` 不会被注册。

## 客户端

`*-infrastructure` 的适配器在字段上标注 `@DubboReference`，字段类型是生成的 `<Service>Rpc`：

```java
@DubboReference(url = "tri://customer-headless:50051")
private CustomerRpc customerRpc;

public MerchantEligibilityDTO authorizeNewOrder(String tenant, String payee) {
    return RpcTokenContext.call(actor.customerAccessToken(), () ->
            CustomerRpcMapper.INSTANCE.fromWireMerchantEligibilityDTO(customerRpc.authorize(
                    CustomerRpcMapper.INSTANCE.toWireMerchantActionAuthorizeCommand(command))));
}
```

Dubbo 不支持构造器参数注入。消费适配器保留租户路由、事务边界、挂载令牌读取和本地错误白名单映射。

## 发现、端口与超时

- Triple 端口统一 `50051`；HTTP/Actuator 端口保持 `8080`。
- 每个服务由 `<service>-headless` Headless Service 暴露；`dubbo.registry.address: N/A`，不使用 Nacos/ZK。
- 调用方固定 `tri://<service>-headless:50051`，依赖 K8s DNS 直接解析。
- 全局 `dubbo.consumer.check: false`、`timeout: 3000`、`retries: 0`。写调用不自动重试；账单下载在
  `@DubboReference` 上单独设置 60 秒超时。
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

## 不保留兼容

本次迁移是有意的破坏性变更：不保留 gRPC 端点、双栈或新旧镜像混跑。发布时必须同时升级调用方和提供方；
滚动发布期间旧、新协议无法互通。

## 消费方 → 提供方

| 消费方         | 依赖的提供方                                  |
|----------------|-----------------------------------------------|
| acquire        | payment、product、realtime                    |
| product        | customer、limitcenter                         |
| limitcenter    | payment                                       |
| payment        | realtime、limitcenter、acquire                |
| reconciliation | payment                                       |

## 构建与依赖

- `loadup-components-rpc-binder-dubbo` 封装 `RpcTokenContext`、`RpcExceptionCodes` 和 provider filter。
- `loadup-components-rpc-proto` 提供 Wire mapper 共享的标量转换。
- `*-client` 引入 protobuf、MapStruct 与 `loadup-components-rpc-proto`；Dubbo 核心标记为 `optional`，避免 app
  模块传递获得传输依赖。`*-adapter` 与消费方 `*-infrastructure` 通过 `loadup-components-rpc-binder-dubbo` 获得 Dubbo。
  跨服务依赖沿用既有 client 依赖图，不新增模块边。
- Dubbo 版本由 `loadup-dependencies` BOM 固定为 3.3.6；`dubbo-maven-plugin` 版本在 pay 父 POM 中统一。

## 验收边界

源码编译、单测和真实 RPC 验收分别报告。Dubbo 3.3.6 面向 Spring Boot 2.7/3.x 发布，本仓库运行在
Spring Boot 4.1.1：编译期已通过，服务启动、JWT 透传、protobuf 编解码和滚动发布仍需在真实环境验收，
不能把依赖接入视为运行兼容性通过。
