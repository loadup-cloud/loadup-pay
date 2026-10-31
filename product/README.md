# Product 订单准入与额度发布

> 当前数据库基线为 PostgreSQL；运行验收状态不能由历史 MySQL 记录推断。

应用间传输使用 Dubbo Triple；HTTP 默认 8080，Triple 默认 50051。见[RPC 约束](../ARCHITECTURE.md)。

本批补齐 COLA 的 product-app、product-infrastructure、product-adapter，落地 OrderAdmissionFacade 的持久化准入/原决定查询，以及
LimitPolicyPublicationFacade 的初始额度可靠发布、ACK与失败重放。复用框架合约主档、不可变
revision、ContractCodec、ContractRuntimeResolver、Spring JDBC、ID、HTTP、JWT 与统一响应；不复制 merchant_contract 主档。


实现边界与内部流程见 [ARCHITECTURE](ARCHITECTURE.md)。

## 能力与边界

| 能力       | 本批源码行为                                                                                                  |
|------------|---------------------------------------------------------------------------------------------------------------|
| 原决定回放 | 先查原 orderId；同主体、同完整意图回原 ADMITTED/DENIED，不刷新条款、截止或经营状态                            |
| 意图比对   | 固定 payee、产品范围、requestDigest、金额/币种、requestedDueAt 和调用主体范围；不能保持 digest 却更改其他参数 |
| 新准入     | customer 远程查询在本地事务外；锁本地合约后重查原决定，校验状态、有效期、框架使用条件和额度激活回执           |
| 停单门禁   | 准入持有框架 merchant_contract 行锁；框架 updateStatus 更新同一行并校验 generation，使状态变更与新准入串行    |
| 独立快照   | 冻结原合约 hash、单独 compiledTermsHash、强类型条款及字段来源；回放不依赖当前合约                             |
| 竞争处理   | 唯一键冲突逃出事务，回滚后再读胜出记录；网络超时/认证失败不持久化为允许或业务拒绝                             |
| 权限       | JWT 的主体/租户绑定，admit/read 独立权限，只允许原主体查询该准入；不接受请求体或普通 header 冒充主体          |

默认 bootstrap 包含 product 业务装配，启动前需提供业务配置。本批尚未执行编译、JUnit、迁移、启动或部署，不能据此宣称业务已经运行。

limitcenter
激活消费者与本服务初始额度发布/ACK工作流已有源码。只有冻结v1载荷、绑定Outbox且经过匹配ACK的ACTIVE回执用于新准入；缺回执仍持久拒绝，原DENIED不自动改判。没有手工设置ACTIVE入口。真实渠道适配与受控引用的可执行性验收仍待完成，额度ACK只代表规则就绪，编译后的渠道组合只表达合约允许范围，不能据此认定主交易链路已运行。

## HTTP 与身份

| POST JSON 路径                      | Command/Query                  | 权限              |
|-------------------------------------|--------------------------------|-------------------|
| `/api/product/admissions/authorize` | OrderAdmissionAuthorizeCommand | pay:product:admit |
| `/api/product/admissions/detail`    | IdQuery，id为admissionId       | pay:product:read  |

Controller 返回 SuccessResponse，业务失败由框架 FailureResponse 处理，HTTP业务报文使用200。DENIED 是成功获取到的持久业务决定，其
terms、contract/revision/generation、时间窗口均为空；依赖异常使用固定 PRODUCT_DEPENDENCY_UNAVAILABLE 错误，未完成的意图可恢复。

requestDigest 为64位小写 SHA-256。amountMinor 使用 CNY 分；requestedDueAt 必须带偏移且不超过微秒精度，归一为上海时间，首次准入时须晚于当前时间且不超过合约
maxWaitSeconds。NULL表示由后续 acquire 按原等待条款生成默认检查点。

认证方签发的 access JWT 需要 tenant_id、principal_type、sub、merchant_id 等可信绑定，语义同[customer](../customer/README.md)
。MERCHANT_APP 必须绑定商户，MERCHANT 的 sub 必须与 merchant_id 一致。权限由框架的 permissions claim 转换，普通角色不自动授予准入权限。

新准入调用 customer 时转发原已验证 JWT；令牌必须同时满足 product 和 customer 的 audience、issuer 与有效期要求。product 固定请求
CREATE_ORDER，customer 再验证该主体对目标商户的显式授权。未携带 customer audience 的凭证不能发起新查询；不将 product
自身的系统主体当作原商户主体。若部署采用独立签发方或单 audience，后续须接入可信 token
exchange；本批未实现交换，也不自行签发令牌。工作线程恢复原意图时，应由认证系统重新建立同一稳定主体范围，而不是持久保存旧 JWT
或伪造 header。

已存在的原决定无需再次远程查询；查询/回放仍需当前有效的 product 凭证和对应权限。回放不延长 materializeBefore：acquire 在 A1
必须检查原恢复期限，已落库的 INIT/PENDING 订单按原快照履约。这一行为不能当作当前合约的新单通行证。

## 业务装配与配置

在 Java 25 环境下，由开发者按需执行：

```bash
mise exec -- mvnd clean verify -pl product/product-bootstrap -am \
  -Dskip.spotless=true -Dskip.spotbugs=true

# 仅打包JAR与本地镜像；既有脚本默认跳过测试。
./product/build.sh

java \
  -jar product/product-bootstrap/target/product-bootstrap-0.0.1-SNAPSHOT.jar
```

| 环境变量                                                                | 用途                                                                    |
|-------------------------------------------------------------------------|-------------------------------------------------------------------------|
| PRODUCT_JDBC_URL / PRODUCT_JDBC_USERNAME / PRODUCT_JDBC_PASSWORD        | 独立 product PostgreSQL 库和外部 Secret 凭证                                     |
| PRODUCT_JWT_ISSUER_URI / PRODUCT_JWT_AUDIENCE / PRODUCT_JWT_JWK_SET_URI | product资源服务器信任配置                                               |
| CUSTOMER_JWT_AUDIENCE                                                   | 原凭证委托给customer所需audience                                        |
| PRODUCT_MATERIALIZE_SECONDS                                             | 可选，平台恢复窗口，默认300秒                                           |
| PRODUCT_PUBLICATION_TENANT_ID                                           | 本部署允许管理/投递额度发布的唯一可信租户；准入接口仍按原多租户身份运行 |
| PRODUCT_LIMITCENTER_TOKEN_FILE                                          | 绝对路径，挂载独立服务access JWT的文件，不是token原文                   |

配置复用 loadup-components-http
的固定命名客户/操作，连接、池等待和读取超时分别为2/1/3秒，customer响应限制64KiB、limitcenter响应16KiB，无HTTP自动重试或重定向（额度重试由Outbox统一负责）。显式允许可信集群私网地址，目标origin来自配置，不来自业务请求；响应的生产信任还需部署配置提供受控TLS/网络保障。没有配置出站credential-ref，避免覆盖原主体Bearer凭证。

为只装配需要的框架存储/codec，业务配置关闭
loadup.modules.contract.enabled，并显式导入相应bean与Mapper。产品目录、签约和状态管理继续通过受控的框架管理集成维护同一数据库；本批未开放通用产品/签约管理HTTP入口。

额度投递令牌由外部认证方/代理维护，需满足limitcenter的issuer/audience/token_use=access、principal_type=SERVICE、sub=service_id、tenant_id=PRODUCT_PUBLICATION_TENANT_ID及pay:
limit:activate权限。每次尝试重新打开挂载文件读取，最多16KiB，支持Kubernetes
Secret/代理轮换；令牌过期/缺失仅导致投递失败，绝不默认放行。当前不实现OAuth取token或持久缓存旧JWT，不自行签发SERVICE令牌。单部署只配置一个出站租户，其他租户的发布入口拒绝；多租户出站凭证路由后续实现。不要把商户凭证用作后台投递身份。

业务 profile 开启框架Outbox和Flyway，迁移框架合约/序列/Outbox及product业务表和本次加法迁移；启动可能执行尚未应用的迁移。JVM/Clock/数据库会话使用上海本地时间，readiness
包含自身数据库状态。现有 deploy/pay.yml 尚未注入这些数据库、JWT、customer、limitcenter及挂载服务凭证配置，业务发布需准备相应配置覆盖层。

## 验证边界

本次只核对文档与当前配置/源码，未运行构建或测试。当前 PostgreSQL 基线的迁移、并发、回滚及真实服务调用需单独验收，历史数据库测试记录不代表当前版本已通过。
