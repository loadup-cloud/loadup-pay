# Acquire 收单订单

> 当前数据库基线为 PostgreSQL；运行验收状态不能由历史 MySQL 记录推断。

内部提供端和 product/payment/realtime 消费端均使用标准 HTTP，默认 8080。契约见[总体架构](../ARCHITECTURE.md)。

已实现普通创单/原调用主体查询、原单会话签发/能力认证/收银台只读摘要，并补齐商户付款发起、金额占用及权威结果刷新落账的 COLA
源码。复用框架 Money、JSON、BaseDO、ID、数据库/事务、生成 Mapper、Spring MapStruct、HTTP 和 JWT 资源服务器。业务默认币种为
CNY，订单、数据库会话与业务 Clock 使用 系统默认时区。


实现边界与内部流程见 [ARCHITECTURE](ARCHITECTURE.md)。

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

## 构建与运行

默认 acquire-bootstrap 包含业务装配。以下供开发者按需执行：

```bash
# 在 loadup-pay 根目录，按需执行窄范围构建及相关测试。
mvnd clean verify -pl acquire/acquire-bootstrap -am

# 既有脚本构建 JAR 和镜像，默认跳过测试。
python3 scripts/build.py acquire

java \
  -jar acquire/acquire-bootstrap/target/acquire-bootstrap-0.0.1-SNAPSHOT.jar
```

| 环境变量                                                                | 用途                                                            |
|-------------------------------------------------------------------------|-----------------------------------------------------------------|
| ACQUIRE_JDBC_URL / ACQUIRE_JDBC_USERNAME / ACQUIRE_JDBC_PASSWORD        | 独立 PostgreSQL 业务库及外部 Secret 凭证                             |
| ACQUIRE_JWT_ISSUER_URI / ACQUIRE_JWT_AUDIENCE / ACQUIRE_JWT_JWK_SET_URI | 本服务已验证资源服务器                                          |
| PRODUCT_JWT_AUDIENCE                                 | product 委托 audience；目标由 PRODUCT_BASE_URL 配置                           |
| PAYMENT_JWT_AUDIENCE                                 | payment 服务凭证 audience；目标由 PAYMENT_BASE_URL 配置                       |
| ACQUIRE_PAYMENT_TENANT_ID                                               | 当前出站服务凭证所属租户；其他租户在占用前拒绝                  |
| ACQUIRE_SERVICE_ID                                                      | 出站 SERVICE 身份，默认 acquire，须匹配 payment 的受信配置      |
| ACQUIRE_PAYMENT_TOKEN_FILE                                              | 外部 Secret 挂载的服务 JWT 文件；每次请求重新读取以支持轮换     |
| ACQUIRE_CHECKOUT_PAGE_URL                                               | 同源托管 `/checkout` 的公开 HTTPS 地址；本地 loopback 可用 HTTP |
| ACQUIRE_CHECKOUT_SECRET                                                 | v1会话 HMAC 密钥，至少32个随机 UTF-8 字节，由外部 Secret 注入   |
| ACQUIRE_CHECKOUT_ACTIVE_KEY_VERSION                                     | 默认v1；切换版本须同时配置 checkout.keys 对应条目               |

业务 profile 启用 Flyway，首次启动会应用未运行的迁移；数据库会话 +08:00。还需按框架约定为部署配置稳定 ID sequence site。现有
deploy/pay.yml 未包含业务 DB/JWT 配置，业务上线需覆盖层显式注入 Secret/ConfigMap 和 SPRING_PROFILES_ACTIVE。

## 验证边界

本次只核对文档与当前配置/源码，未运行构建或测试。当前 PostgreSQL 基线的迁移、并发、回滚及真实服务调用需单独验收，历史数据库测试记录不代表当前版本已通过。

## 应用间 HTTP

内部事件路径 /api/internal/v1/acquire/events/ 接收 payment 的付款结果、费用退款汇总与商户退款通知。
PRODUCT_BASE_URL、PAYMENT_BASE_URL、REALTIME_BASE_URL 默认使用普通 Service 的 HTTP 8080，本地 profile 使用 localhost 8085/8081/8086。
每次传递目标受众的原凭证，原事务、幂等、来源/ACK 和错误语义保留。已清除 proto/Wire/Dubbo/Nacos 默认装配，运行验收见 [迁移清单](../HTTP_MIGRATION.md)。
