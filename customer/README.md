# Customer 业务实现

> 当前数据库基线为 PostgreSQL；运行验收状态不能由历史 MySQL 记录推断。

应用间传输使用 Dubbo Triple；HTTP 默认 8080，Triple 默认 50051。见[RPC 约束](../ARCHITECTURE.md)。

商户逐项授权的 COLA 业务链路为：HTTP adapter → app Facade → domain → Spring JDBC Gateway。完整商户主档复用框架
merchant 模块，不建立消费者、钱包账户或独立商户主档副本。product 通过 Dubbo Triple 使用商户授权契约。


实现边界与内部流程见 [ARCHITECTURE](ARCHITECTURE.md)。

## 能力与入口

所有入口使用 POST JSON；Controller 与 Java Facade 复用相同 Command。HTTP 成功使用 `SuccessResponse`，业务失败使用框架
`FailureResponse`，业务错误返回 HTTP 200。

| 入口 `/api/customer`     | Facade 方法          | 当前行为                                                          |
|--------------------------|----------------------|-------------------------------------------------------------------|
| `/relationships/create`  | `createRelationship` | 校验同租户商户、拒绝自关联和重复未撤销关系；INIT 后在同一事务激活 |
| `/relationships/revoke`  | `revokeRelationship` | expectedVersion 校验，撤销不允许重新激活                          |
| `/grants/create`         | `grant`              | 对一个主体、一个收款商户、一个动作显式授权，校验可选关系          |
| `/grants/revoke`         | `revokeGrant`        | 版本校验和独立撤销，支持商户停用后的权限回收                      |
| `/eligibility/authorize` | `authorize`          | 检查商户当前状态、可信主体、显式动作和有效时间，以及关系的有效性  |

前四项需要认证权限 `pay:customer:manage`。持有普通角色或父子商户关系不会获得管理权限，也不会自动获得 CREATE_ORDER、REFUND
等权限。未授权结果不返回商户档案或授权事实；已授权结果只返回框架 `MerchantProfileDTO` 安全摘要。

本批没有新增商户主档维护 HTTP 入口。主档由复用的框架 `MerchantFacade` 管理；业务启用前需通过受控管理集成创建同租户主档。不要直接修改交易表来代替正式业务接口。

## 可信身份

复用 `loadup-components-resource-server` 校验 JWT 签名、issuer、audience、有效期和 `token_use=access`。customer 从已认证的
Authentication 的 Jwt 凭证读取以下签名 claim；请求 JSON 和 X-Tenant-Id 不提供调用方身份。

| Claim            | 含义                                                                              |
|------------------|-----------------------------------------------------------------------------------|
| `sub`            | 稳定 principalId；授权以此标识匹配                                                |
| `tenant_id`      | 必填租户，最长64字符，不允许首尾空白                                              |
| `principal_type` | MERCHANT_APP / MERCHANT / OPERATOR                                                |
| `merchant_id`    | MERCHANT_APP 与 MERCHANT 必填，来自认证方维护的商户归属；MERCHANT 必须与 sub 相同 |
| `permissions`    | 由框架转换为权限，管理动作需包含 pay:customer:manage                              |

OPERATOR 在直接授权时可无 merchant_id；绑定父子关系的代操作仍需有效的父商户归属。认证方负责维护应用与商户的可信绑定，本接口不接受调用方自行声明归属。资格调用覆盖临时租户上下文，方法完成后恢复外层上下文。

资格结果是本次检查的观察事实；尚未提供跨服务的持久 OrderAdmission 或强截止撤销承诺。后续 product
必须按设计实现新单准入和合约门禁，不能把本结果永久缓存为通行证。

## 构建与启动

默认 bootstrap 包含 customer 业务适配器，启动前需要配置数据库和 JWT。

```bash
# 在 loadup-pay 根目录执行，由开发者按需运行；本批未执行构建或测试。
mvnd clean verify -pl customer/customer-bootstrap -am

# 或使用既有脚本构建 JAR 和本地镜像（脚本默认跳过测试）。
./customer/build.sh

# 准备下表外部配置后，运行上面构建的业务 JAR。
java \
  -jar customer/customer-bootstrap/target/customer-bootstrap-0.0.1-SNAPSHOT.jar
```

| 环境变量                                        | 用途                             |
|-------------------------------------------------|----------------------------------|
| CUSTOMER_JDBC_URL                               | 专属 PostgreSQL 数据库 JDBC URL  |
| CUSTOMER_JDBC_USERNAME / CUSTOMER_JDBC_PASSWORD | 通过外部 Secret 注入的数据库凭证 |
| CUSTOMER_JWT_ISSUER_URI                         | 可信令牌签发方 issuer            |
| CUSTOMER_JWT_AUDIENCE                           | 本业务 API audience              |
| CUSTOMER_JWT_JWK_SET_URI                        | 签发方公钥 JWK Set 地址          |

业务配置开启框架 Flyway，扫描依赖内的 db/migration，包含框架主档、序列、幂等表及本次两个表；启动会执行尚未应用的迁移。使用专属业务库，不把其他服务的迁移目录混入
customer。数据库会话、JVM 与业务 Clock 使用一致的部署时区，JWT epoch 仍保持绝对时间。

部署前需准备 `customer-config` 和 `customer-secret`，注入上述数据库和签发方配置。

## 验证边界

本次只核对文档与当前配置/源码，未运行构建或测试。当前 PostgreSQL 基线的迁移、并发、回滚及真实服务调用需单独验收，历史数据库测试记录不代表当前版本已通过。
