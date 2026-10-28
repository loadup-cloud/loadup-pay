# Customer 业务实现

> 当前开发基线正切换至 PostgreSQL；各服务业务迁移已合并为单文件，RPC 协议位于各服务 client。下方历史验收描述保留其当时的数据库和运行状态；新基线尚需编译与真实 PostgreSQL 验证。

应用间传输已于2026-10-23统一为 gRPC，见[RPC 规范](../design/RPC.md)。本文件旧批次中的内部 HTTP 描述与 `*_BASE_URL`
要求由该规范取代；商户/运营 HTTP 入口继续保留。实际业务 gRPC 随本应用 `*-business` 构建和运行 profile 装配，端口50051；本批通过
IDEA MCP 完成六业务应用及依赖的 `clean test-compile`；未执行测试或部署。

本批实现商户逐项授权这一条 COLA 业务链路：HTTP adapter → app Facade → domain → MyBatis-Flex Gateway。完整商户主档复用框架
merchant 模块，不建立消费者、钱包账户或独立商户主档副本。product 的订单准入和业务 gRPC 仍为后续批次。

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

## 数据与事务

- `merchant_profile` 及其迁移归框架 merchant；`global_unique` 归 GlobalUnique；ID 和序列分配归 database。
- customer 仅新增 merchant_relationship、merchant_action_grant。实际迁移：
  `customer-infrastructure/src/main/resources/db/migration/V20261021160000__create_customer_authorization.sql`
  ；设计草案同步更新，不能重复执行草案。
- 创建、授权、撤销、资格判断均使用主库 READ_COMMITTED 事务。以收款商户主档行锁串行化该商户的授权变更和资格判断；撤销另校验原资源版本。数据库唯一索引作为最终约束。
- 撤销保留原ID和版本历史。生成列 active_slot 在 INIT/ACTIVE 时为1，REVOKED
  时为NULL，只限制一个未撤销记录。撤销后可用新ID和新幂等键重新授予；到期记录仍需显式撤销。旧关系绑定的授权不会自动改绑到新关系。
- 有效窗口是左闭右开；授权与关系同时满足才可代操作。入参统一转为上海偏移，拒绝超过微秒精度的有效时间，避免 DATETIME (6)
  截断改变边界。
- requestKey 按租户、操作版本、主体类型和主体ID隔离。执行器保存参数 SHA-256 与首次响应快照；同键同参返回首次快照，同键异参报
  CUSTOMER_IDEMPOTENCY_CONFLICT。租户同时进入哈希键，回放再次核对租户精确一致，避免依赖表的大小写不敏感排序规则混淆租户。获取商户行锁后再次检查，处理并发重试。
- GlobalUnique claim 与业务写入使用同一数据源、同一事务；失败一起回滚。重复请求返回首次结果，即使资源后来被撤销，下一次资格判断仍读取最新状态。幂等记录不设置TTL，不通过软删除复用键。
- DO 继承 BaseDO；Mapper/Tables 由 LoadUpMyBatisFlexProcessor 生成；MapStruct 使用框架共享配置。没有手写空 Mapper、SQL
  Mapper 或内存生产实现。

## 构建与启动

现有默认 bootstrap 继续提供无数据库依赖的启动和健康入口。业务版本显式选择 Maven profile `customer-business`，运行时同时激活同名
Spring profile；只有默认骨架的镜像不包含业务适配器。

```bash
# 在 loadup-pay 根目录执行，由开发者按需运行；本批未执行构建或测试。
mvnd clean verify -pl customer/customer-bootstrap -am -Pcustomer-business

# 或使用既有脚本构建 JAR 和本地镜像（脚本默认跳过测试）。
./customer/build.sh -Pcustomer-business

# 准备下表外部配置后，运行上面构建的业务 JAR。
java \
  -jar customer/customer-bootstrap/target/customer-bootstrap-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=customer-business
```

| 环境变量                                        | 用途                                                              |
|-------------------------------------------------|-------------------------------------------------------------------|
| CUSTOMER_JDBC_URL                               | 专属 MySQL 数据库 JDBC URL，使用支持 CHECK 的 MySQL 8.0.16 及以上 |
| CUSTOMER_JDBC_USERNAME / CUSTOMER_JDBC_PASSWORD | 通过外部 Secret 注入的数据库凭证                                  |
| CUSTOMER_JWT_ISSUER_URI                         | 可信令牌签发方 issuer                                             |
| CUSTOMER_JWT_AUDIENCE                           | 本业务 API audience                                               |
| CUSTOMER_JWT_JWK_SET_URI                        | 签发方公钥 JWK Set 地址                                           |

业务配置开启框架 Flyway，扫描依赖内的 db/migration，包含框架主档、序列、幂等表及本次两个表；启动会执行尚未应用的迁移。使用专属业务库，不把其他服务的迁移目录混入
customer。MySQL 会话使用 +08:00；JVM 与业务 Clock 使用本地上海时间，JWT epoch 仍保持绝对时间。

现有 deploy/pay.yml 仍部署基础骨架，没有注入数据库和签发方配置。业务镜像用于 Kubernetes 前，应由配置覆盖层注入上述
Secret、ConfigMap 与 SPRING_PROFILES_ACTIVE=customer-business；本批未更改运行中的部署或数据库。

## 验证记录与待验收

本批新增4个JUnit类、22个方法源码，覆盖首次响应回放、参数冲突、锁等待后的重查、竞争失败不写入、授权拒绝与通过、撤销版本、可信租户覆盖和恢复、有效期、JWT主体绑定以及
Spring MapStruct 转换。

按仓库构建纪律未执行 Maven、JUnit、Flyway 或部署；IDE 批量检查超时，不能视为通过。源码和依赖核对、git diff --check
已完成。仍需开发者运行上面的窄范围构建，并在真实 MySQL 上验收：并发相同/冲突幂等键、写入失败同时回滚
claim、跨租户隔离、撤销后重授、资格判断与撤销并发，以及真实签名 JWT 的 HTTP 成功/失败响应。
