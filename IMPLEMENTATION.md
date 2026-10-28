# 第一阶段：应用初始化与契约层

## 2026-10-23：公共能力收敛与默认时区

pay-common/pay-rpc 已从支付工程移除：JDK-only 的 DomainViolation 进入框架
commons-error，BusinessTime/EventEnvelope/ContractReference/共享枚举与显式 CNY 守卫进入 commons-dto，通用调用与
JWT/校验支持进入现有 gRPC binder。各服务继续维护自己的 RPC 契约。Wire↔DTO 普通字段改为 MapStruct 生成，protobuf
presence/oneof/Timestamp/集合语义保留。

系统时区取默认值，移除 JVM/TZ/JDBC/K8s/Headlamp 强制时区和合约 limit.timezone；额度运行规则记录实际
zone，建表脚本取消固定地区约束。本节覆盖下文历史批次中 pay-common/pay-rpc 与固定上海时区的状态。通过 IDEA MCP
完成受影响框架模块及六业务应用依赖的 clean test-compile，未运行测试、数据库变更或部署。详见[当前 RPC 规范](design/RPC.md)。

## 2026-10-23：内部传输统一 gRPC

本节覆盖下文各历史批次中的内部 HTTP/Ping/无协议模块状态。新增6个提供方自维护的 `*-rpc` 模块：分别维护 Protobuf
service、消息映射与方法定义，共25个 unary 方法；通用调用、JWT与错误处理支持归框架现有 gRPC binder。9个内部 HTTP 客户端迁移为
gRPC，移除对应内部 servlet 入口与业务 profile 的全部 `loadup.http`/`*_BASE_URL` 配置。商户、运营、收银台和外部渠道 HTTP 不变。

channel 由 Spring 配置类读取，固定同 Namespace Headless DNS、50051、round_robin、3秒 deadline；SERVICE token 逐次重读，商户委托令牌保持原
audience 约束。服务器复用资源服务器的签名/issuer/audience/token_use 校验与权限转换，在每个 gRPC 回调中隔离并恢复
SecurityContext。业务异常仅通过固定错误码 trailer 返回，调用方继续显式白名单映射；未知异常不输出载荷或凭证。

构建 profile 仍区分默认骨架与业务装配；业务镜像、Secret、TLS 和部署参数须匹配。协议切换不保留 HTTP
兼容；跨服务循环依赖须先启动整套提供方，不能用新旧协议镜像混跑。回归测试源码已同步为 gRPC，并新增 Protobuf
presence/oneof/Timestamp、认证隔离与原生 unary 调用测试。本批通过 IDEA MCP 终端执行六个业务 bootstrap 及其依赖的
`mvnd clean test-compile -am`（启用六个 business profile），结果 BUILD SUCCESS，协议生成及业务/测试源码编译通过；未执行
JUnit、DDL或集群发布。IDE 工程需重新加载 Maven 识别新模块。详见 [RPC 规范](design/RPC.md)。

日期：2026-10-21 ｜ 基线：[详细设计](DETAILED-DESIGN.md) ｜ 状态：P0/P1源码及六应用构建、镜像、Kubernetes启动验收完成；单测与业务执行待验收

## 1. 优先级与完成范围

| 优先级        | 本阶段结果                                                                                                                  | 后续工作                                                                     |
|---------------|-----------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| P0 应用初始化 | 六服务client/domain/bootstrap、根依赖管理、独立启动入口、Clock与Asia/Shanghai；六应用构建/镜像/Kubernetes启动及就绪验收通过 | 业务运行验收随P2实施；Headlamp本轮未发布                                     |
| P1 client契约 | Facade、Command/Query/DTO、错误码、强类型条款及跨字段校验、授权撤销、完整投影事件和消费接口                                 | 权限/幂等/事务应用服务及传输适配                                             |
| P1 domain契约 | 聚合生命周期、可信结果收敛、退款容量、原桶恢复、Gateway、纯计算规则和定向单测源码                                           | 持久化、可靠执行、数据库并发证明                                             |
| P2 执行闭环   | 已定义相应接口                                                                                                              | DB/Flyway、app/infrastructure、Outbox/Inbox、DelayedTask、业务gRPC、真实渠道 |

本阶段的Facade是Java接口，未注册伪实现或支付成功Controller。Actuator健康仅表示应用骨架自身健康，不代表下单、收款、退款、合约准入或额度数据库事务已经可用。

## 2. 工程与依赖

```text
loadup-pay/
  pay-common/                    # 渠道词汇、时间、合约引用、事件头与收单金额约束
  customer|product|limitcenter|payment|acquire|realtime/
    <service>-client/            # 业务接口与传输无关的公开数据契约
    <service>-domain/            # 纯Java模型、策略、Gateway；无Spring/ORM/client依赖
    <service>-bootstrap/         # Spring Boot装配与技术入口
```

全部子模块继承根`loadup-pay`，内部版本在根dependencyManagement集中管理；框架版本仍由LoadUp BOM管理。服务目录的POM只聚合子模块，启动JAR位于
`<service>/<service>-bootstrap/target/`；Dockerfile已同步。

源码入口：[订单Facade](acquire/acquire-client/src/main/java/io/github/loadup/pay/acquire/client/facade/AcquireOrderFacade.java)、[订单聚合](acquire/acquire-domain/src/main/java/io/github/loadup/pay/acquire/domain/model/AcquireOrder.java)、[付款Facade](payment/payment-client/src/main/java/io/github/loadup/pay/payment/client/facade/PaymentExecutionFacade.java)、[商户额度Facade](limitcenter/limitcenter-client/src/main/java/io/github/loadup/pay/limitcenter/client/facade/MerchantLimitFacade.java)。

client依赖方向：product→框架contract-client，customer→框架merchant-client，payment→product-client，acquire→payment-client，realtime→acquire/payment-client。limitcenter-client独立声明自身规则激活与额度接口。domain依赖pay-common及实际使用的LoadUp
commons-dto/json基础能力，不依赖Spring/ORM注解、业务client或其他服务领域实现。当前不保留空协议模块或测试RPC运行依赖。

domain保留独立的生命周期枚举；app阶段通过MapStruct映射client枚举，不让传输对象变成领域模型。pay-common仅包含跨域的基础值与信封，没有共享商户/订单/付款聚合。

## 3. LoadUp复用

- `loadup-commons-dto`
  ：Money、IdQuery、PageDTO和PageQuery.normalized；删除原pay-common.Money。框架Money支持有符号金额和多币种，DomainChecks.amount只保留收单CNY/非负校验，直接返回/校验框架值；计算读取getCent/getCurrencyValue，不改变整数分或checked
  arithmetic。公开Command/DTO仍保持amountMinor/currency报文。
- `loadup-commons-error`：六服务ResultCodeEnum和后续CommonException映射。纯domain抛DomainViolation，应用层负责映射稳定业务码。
- `loadup-commons-json`：DTO、领域record及事件信封统一使用ToStringAsJson.reflectionToString；付款码、授权票据、token
  URL、执行许可、preparedInputRef、credentialReference及信封data使用DiagnosticHidden或框架敏感字段规则。注解在嵌套反射诊断中也生效，不改变实际HTTP字段。
- `loadup-components-webmvc`及原生Actuator/Validation：统一Web基础装配；`/actuator/**`不属于`/api`报文封装范围。
- merchant-client/contract-client：复用框架公共商户事实及合约管理契约，新增的授权关系/准入由本项目建模，不复制框架商户主档或产品目录模型。
- `loadup-components-rpc-binder-grpc`：计划在P2业务RPC适配时使用，目前未引入启动模块。

测试用Ping客户端、服务端、descriptor/proto与整个协议模块均已删除，原pay-contract也不恢复。同步清理了两应用的gRPC依赖及配置、payment的9090端口和acquire的连接目标/专用健康检查环境变量。verify.sh仅检查六个应用就绪及Service
ready
EndpointSlice；Actuator基础健康检查保留。当前没有可用RPC接口；业务proto/stub、框架binder、身份/TLS和业务调用验收按P2实际用例实施，不预生成空模块。重新加载根Maven项目即可更新IDE模块。

## 4. 主要接口与领域行为

| 服务        | client入口                                                                                             | domain落点                                                                                                                         |
|-------------|--------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| acquire     | AcquireOrderFacade、CheckoutSessionFacade、CashierFacade、PaymentOutcomeFacade、FeeRefundOutcomeFacade | AcquireOrder、OrderPaymentAllocation、OrderAdmissionProof、OrderFee、CheckoutSession；关单证据                                     |
| payment     | PaymentExecutionFacade、PaymentInputFacade、RefundFacade；付款/退款/退款汇总事件                       | PaymentOrder/Attempt、父子ExecutionGate、ChannelBindingRevision、RefundRequest/Order/Capacity、FifoRefundPolicy、渠道及风控Gateway |
| customer    | MerchantEligibilityFacade、MerchantAuthorizationFacade；框架MerchantFacade/QueryFacade                 | MerchantRelationship、MerchantActionGrant及必要MerchantFacts；关系不替代明确动作授权                                               |
| product     | OrderAdmissionFacade；框架合约公开client                                                               | MerchantContractFacts、OrderAdmission、准入/商户事实Gateway                                                                        |
| limitcenter | MerchantLimitFacade、LimitPolicyFacade                                                                 | LimitBucketSet、Reservation/Item、OperationGate、自然窗口策略、释放证据与持久化Gateway                                             |
| realtime    | MerchantTradeQueryFacade、TradeProjectionFacade                                                        | 各类完整快照、独立SourceVersion和投影Gateway                                                                                       |

订单聚合与单笔allocation作为同一acquire本地事务的变更返回；Gateway明确saveChange，尚未实现数据库原子性。相同actionKey的查询/冲突与唯一键由未来app/infrastructure落地，不把纯领域计算宣称为数据库并发验证。

已有纯领域规则包括：固定总额与paid+reserved上界；未知结果保留占用；累计恰好付清；CLOSE与KEEP_PENDING截止处理；需要父封禁和子项收敛的关闭；整单计费及退款先到时的退费依据；FIFO固定金额分配；退款状态独立；日/周/月/终生窗口；全部桶预占的不可变提案；执行后不按预占TTL失效；不同生产方/聚合版本不可互比。

会话签名、渠道验签、证据可信性、支付提交、退款调度、额度原子落库和订单创建意图恢复均属后续应用/适配实现。domain的Evidence值由可信适配层构造，不是对公网请求的认证能力。

JSAPI/APP方法枚举作为目标能力保留；本阶段PaymentActionData仅提供NONE/WAIT/QR/REDIRECT类型，真实渠道原生参数DTO在对应适配阶段加入。未实现方式不得因存在枚举就启用；没有伪造银联机构字段、渠道凭证或返回URL。

## 5. P1补齐结果

| 领域             | 本次补齐的行为与边界                                                                                                                                                                                    |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| acquire          | finishClose必须匹配父门禁回执的orderId/closeEpoch，零占用本身不能证明可关单；截止检查区分CLOSE/KEEP_PENDING；延期从原始dueAt计算累计上限；会话激活/过期/完成/取消；action重放核对原摘要                 |
| payment          | 门禁INIT→OPEN且FENCED不可重新开启；渠道结果匹配付款、尝试、账户范围、请求号和金额；UNKNOWN不允许换尝试，NO_COLLECTION还须执行已封禁；单尝试失败不自动终结整笔付款；CLOSING中的既有成功可收敛为SUCCEEDED |
| refund           | 固定计划顺序执行，UNKNOWN阻塞后项，确定失败继续固定下一项；成功将原容量reserved转refunded，失败释放reserved，重复终态不重复变更；返回子退款/容量变更供同一事务保存                                      |
| limitcenter      | 安全换尝试须匹配原operation/payment/attempt/epoch，递增执行代次，不重置原预占或受理时间；已执行不随TTL释放；退款按原item累计高水位恢复，乱序不多恢复，金额恢复到原桶，笔数仅全退后恢复一次              |
| customer/product | 关系/授权的INIT、激活和撤销；撤销不可重新激活；代操作校验可信principal所属父商户；新单准入同时检查商户事实、当前合约与额度规则激活；依赖失败不转为允许                                                  |
| realtime         | 专用订单/付款/退款完整快照，补齐商户、原创建时间、渠道及成功时间；完整退款申请进度与累计一致；查询返回独立读模型，不伪造权威订单可付能力                                                                |

新增三个`*SnapshotEventDTO`，替换realtime消费原查询DTO的接口；退款汇总事件增加完整`requests`
列表。各事件可核对信封的producer/type/schema/aggregate/version与payload绑定；这只是结构校验，不能代替服务身份、租户认证、验签或Inbox事务。customer输出生命周期字段使用
`GrantState`，新增撤销Command带requestKey/expectedVersion；尚无已发布业务接口需要兼容。

条款DTO构造时校验付款金额上下界、初始等待/累计延期、费率上下界及通知凭证/重试范围。未实现的渠道原生动作、LIFO/EXPLICIT策略仍不可由配置发布为可用；配置编译/发布门禁在P2实现。

## 6. 验证边界

累计15个JUnit测试类、37个测试方法源码，覆盖多笔付款、关单回执、累计延期、条款范围、渠道身份/金额、安全换尝试、固定退款顺序/容量幂等、乱序退款恢复、商户授权撤销、完整快照及信封绑定等。本次补充框架Money的币种/负数边界与分单位费用、领域对象及嵌套信封脱敏的4个用例。product-client/payment-client的JUnit仍为test
scope。

本次按用户明确要求执行Java 25/mvnd clean package、六镜像构建和OrbStack部署，生产源码编译及应用启动验收通过。构建使用
`-Dmaven.test.skip=true`，未编译或执行上述37个单测，未执行DDL或业务交易验收。此前IDE检查未完成，不能当作检查通过；本次以实际打包和启动结果验证装配。

静态检查覆盖Maven模块/依赖图、纯domain依赖方向、Java包路径和公开类型引用、配置绑定、YAML/RBAC引用、脚本语法、Docker目标及文档链接。静态证据不能代替Spring启动、JUnit执行或真实MySQL并发测试。

## 7. 构建脚本与部署记录

六应用各有`build.sh`和`deploy.sh`，公共库另有`pay-common/build.sh`；统一逻辑见[scripts说明](scripts/README.md)。全量应用构建使用一次
`mvnd clean package -pl <六bootstrap> -am`，随后构建六个镜像；独立部署只选择该应用的Deployment/Service及namespace，不改其他应用。构建自动采用mise固定的Java
25，清理了pay-common重复JSON依赖声明。

2026-10-21实测：

- 目标：Docker/Kubernetes context均为`orbstack`，namespace为`loadup-pay`。
- 全量发布标签：`20261021144823-43940`，镜像为`loadup-pay/<service>:<tag>`；六应用Deployment均1/1就绪，Pod
  Running、零重启，Service均有ready端点。
- 六应用HTTP `/actuator/health`均返回UP；六个Service DNS从acquire容器解析成功；六容器均为CST +0800；payment
  Service已仅暴露8080，测试RPC未恢复。
- 用已有镜像单独执行payment/deploy.sh成功，其他五个应用的完整spec前后相同；重复构建已有标签在Maven前被拒绝，避免原标签被覆盖但Pod未更新。
- 本次修复部署前六应用因本地镜像缺失产生的ErrImageNeverPull。使用本地共享镜像，不向远程registry推送，不发布Headlamp或修改其他namespace工作负载。

原始deploy/pay.yml保留示例标签，部署脚本经kubectl本地dry-run和JSON资源选择绑定本次标签，不修改源码中的镜像版本。不要再直接apply示例标签覆盖已发布版本。每次build默认产生新标签，回用已有镜像需显式SKIP_BUILD=1与IMAGE_TAG。

## 8. 下一实施批次（P2）

1. customer业务源码首批已补齐，见下节；继续完成其窄范围构建与真实MySQL/JWT验收，以及product的OrderAdmission、limitcenter规则激活和全部桶事务。
2. acquire的创建意图、原合约快照、金额占用/幂等、费用和到期任务，payment的执行门禁/回调/查证及Outbox事实。
3. 接业务gRPC与单渠道Checkout，随后实现退款固定计划、通知、realtime投影和收银台；按原架构补齐三渠道。

风控厂商和reconciliation仍按既定优先级后置；容量、保留期、SLO/RTO/RPO不在本轮增加。

## 9. P2 首批：customer 商户授权

新增 customer-app、customer-infrastructure、customer-adapter，完成两个既有 Facade 的业务实现和五个 POST JSON 入口。复用框架
merchant 主档、数据库/ID、GlobalUnique、resource-server、MapStruct 和统一响应。业务装配通过 customer-business Maven/Spring
profile 显式启用；现有默认骨架与运行中的六应用不变。

本批增加两个实际 Flyway 表，修正撤销后重授的唯一槽约束，保存不可复活的历史记录；写入与幂等 claim
同事务，资格查询与权限变更通过收款商户主档行锁协调。管理入口要求独立权限，主体及租户取自已验证JWT，授权拒绝不披露档案。详细入口、配置、并发边界和启动方法见[customer说明](customer/README.md)。

新增4类22个单测源码，截至customer首批合计19类59个方法；尚未编译或执行。IDE批量检查超时；本轮仅完成源码、依赖和diff空白核对，没有构建、执行DDL或部署。本节新增源码不在前述已部署标签的运行验收范围内，不能用此前六应用健康结果替代业务验收。

## 10. P2 第二批：product 持久准入

新增 product-app、product-infrastructure、product-adapter，补齐 OrderAdmissionFacade
的两个HTTP入口、完整意图冻结/重放、合约行锁与运行条件、原条款快照及独立哈希。复用框架合约表、ContractCodec/RuntimeResolver、HTTP、JWT、数据库/ID和MapStruct；customer远程查询在本地事务外，凭证需满足双方audience，业务拒绝与依赖失败分开处理。

新增实际product两表迁移及product-business业务装配。额度发布/ACK生产未实现，本批只读取可信ACTIVE回执，无回执保持拒绝；产品管理和实际渠道发布验收仍待接入，未宣称完整收单链路完成。启动方法和数据/认证边界见[product说明](product/README.md)。

新增6类33个单测源码，覆盖原决定回放、完整参数冲突、事务/回滚边界、编译门禁、JWT委托、客户响应和高精度/完整性；未执行编译、单测、DDL或部署。本批源码不属于此前已部署镜像的运行验收范围。

本次IDE批量检查未在20秒等待窗口返回有效结果，未计通过。32个POM的XML解析和git diff --check通过，仅是元数据/空白检查，不替代注解处理、编译或运行验证。

## 11. P2 第三批：limitcenter 激活与原子预占

新增 limitcenter-app、limitcenter-infrastructure、limitcenter-adapter，完成规则激活、全窗口/指标原子预占和原调用服务查询的
HTTP/Facade 源码。复用框架数据库/ID、BaseDO、生成 Mapper、MapStruct、Money、JSON、资源服务器及响应；通过 limitcenter-business
Maven/Spring profile 装配。

新迁移包含7张实际业务表，策略头协调版本激活和预占，规则/桶按稳定身份加锁并以版本条件写回；旧 ACK
不回滚阈值，旧策略订单使用当前阈值但原时间归桶，不重置累计值。完整激活参数和可信租户进入规范化哈希，预占重试逐项核对原意图。当前限定收款商户自身规则，稳定计数形状不可直接变更；父级共享规则与迁移后续实现。

本批增加6类30个测试方法源码，未编译/执行；未执行DDL或部署。35个POM XML解析及git diff
--check完成；IDE部分应用/转换源码未报告错误，持久化类及测试装配因未生成Mapper/Converter仍有未解析错误，尚不能认定编译通过。运行入口、配置、精确语义与验收清单见[limitcenter说明](limitcenter/README.md)。

此处只完成额度预占切片：原MerchantLimitFacade的执行许可、确认、释放及退款恢复尚无实现；不发放假permit，不从公开evidenceId推断渠道事实，不启用无证据TTL回收。product的可靠发布/ACK仍未打通，不能把本批当作实际渠道交易链路验收。

## 12. P2 第四批：limitcenter 执行许可与显式过期

实现 LimitExecutionFacade 的 beginExecution/verifyExecution/expire 三个认证HTTP入口。operation_gate
锁内重读、policy_head协调，首次Begin将epoch/许可回执和EXECUTING状态同事务持久化；同attempt/requestKey按原keyId重放，不刷新期限。复用框架HMAC，密钥仅由外部Secret提供，DB仅存摘要；verify核对当前绑定与金额，不能代替未来payment
Y2本地执行CAS。

新增加法迁移V20261021190000创建limit_execution_grant，前一迁移不修改。显式expire只释放达到期限且仍RESERVED的原完整桶占额，EXPIRED幂等，执行后不受TTL影响；自动扫描和payment订单占用补偿后续接入。不同attempt即使携带evidenceId也拒绝，尚未接真实渠道查证。

新增4类25个测试方法源码，与前批合计10类55个新增方法；未运行构建、JUnit、DDL或部署。IDE本批应用编排/转换、HMAC配置及新测试源码未报告错误，但生成Mapper/Converter仍待clean构建验证。详细许可协议、密钥轮换、运行变量和验收范围见[limitcenter说明](limitcenter/README.md)
。确认、释放、退款恢复及product发布ACK仍未实现，不能认定收单主链路完成。

## 13. P2 第五批：product 初始额度可靠发布与 ACK

新增LimitPolicyPublicationFacade及publish/detail/replay管理HTTP入口，限定OPERATOR/SERVICE及独立权限。源contract/revision/hash冻结，框架合约行锁、初始商户唯一约束和同数据源Outbox事务保证重复发布只产生一个意图；网络投递不持本地事务，ACK绑定原policy/version/hash/event后CAS写ACTIVE。失败重放沿用原事件ID并由框架审计；旧schema0行保留且不作新准入可信回执。

首批仅支持框架revision1和policyVersion1，每商户单个初始发布绑定；完整八个指标/窗口持续计量，stableScope不含合约/策略版本。配置独立出站租户和可轮换挂载服务JWT文件，不保存调用者令牌。增量迁移V20261021200000只增加可空来源字段、版本及约束，不修改既有迁移或删除旧数据。

新增7类31个测试方法源码，未编译/执行。IDE部分源码检查完成，测试字符串语法错误已修正；生成Mapper/Converter待clean构建验证。35个POM
XML解析及git diff
--check完成；未运行构建、JUnit、DDL或部署。初始额度发布具备源码链路，不代表真实JWT/HTTP/MySQL/Outbox验收通过；实际渠道、引用发布验收、多revision共享策略、acquire及payment执行仍待完成。配置和验收范围见[product说明](product/README.md)。

## 14. MyBatis-Flex 处理器加载修复

customer、product、limitcenter 的 infrastructure 模块此前仅在 annotationProcessorPaths 声明 LoadUp 包装处理器。BOM 将其上游
mybatis-flex-processor 管理为 provided，该依赖不会传递到包装处理器的隔离加载路径，导致 javac 报 Could not instantiate an
instance of processor，Mapper/TableDef 未生成。

三个模块现显式将 com.mybatis-flex:mybatis-flex-processor 加入处理器路径，版本继续由 LoadUp BOM 管理；annotationProcessors
仍只执行 MapStruct 与 LoadUp 包装处理器，不重复启动上游处理器，也不新增业务侧 mybatis-flex.config。

使用 Java 25、全新临时输出目录与已缓存依赖执行最小 javac 探针：修复前三个模块配置均复现同一实例化错误，修复后三者均成功生成并编译样例
Mapper、TableDef 和 Tables。探针未编译业务模块，未运行 Maven/业务测试或部署。IDEA 需 Reload All Maven Projects
使处理器路径重新导入，再重新编译；该验证不替代后续业务模块的 clean 构建。

## 15. MapStruct 领域行为误识别修复

MapStruct 1.6.3 将单参数、返回自身类型的领域方法识别为 fluent setter，即使目标是不可变 record，也会将这些方法作为待映射属性。最小样例及实际
LimitStorageConverter、PolicyPublicationStorageConverter 均复现
advance、confirm/release/restore、transitionTo、release/expire、publishing 的未映射目标错误；禁用 Builder 无效。

在这两个持久化 Converter 的对应映射方法中显式忽略七个领域行为目标，保留 LoadUpMapStructConfig 的
unmappedTargetPolicy=ERROR，领域方法及调用语义不变。新增两类四个回归测试方法源码，通过 Spring 获取真实
Converter，核对策略头、额度桶、执行中预占及 INIT/PUBLISHING/ACTIVE 发布对象的持久化往返不改变字段、状态或版本。

使用 Java 25、MapStruct 1.6.3 和全新临时目录执行 javac -proc:only：customer、product、limitcenter 当前全部九个 Converter
成功生成，生成代码未调用上述领域行为；反向样例仍对真实缺失字段 requiredField 报错。四个修改 Java 文件的 IDEA 错误检查通过，git
diff --check 通过。该验证仅覆盖 MapStruct 注解生成，新增 JUnit 未执行，未运行 Maven 构建、数据库迁移或部署。

## 16. P2 第六批：acquire 普通创单与查询

新增 acquire-app、acquire-infrastructure、acquire-adapter，AcquireOrderCreationFacade 实现 POST create/detail；完整
AcquireOrderFacade 继承此独立切片，未暴露其他占位方法。A0 唯一意图先提交，原身份委托 product，在 A1 intent 行锁内重读并验证准入，原子写
INIT 订单、不可变条款、INIT 整单费用及 COMPLETED。依赖失败保留稳定 orderId；拒绝/精确截止到期先持久化终态再返回错误，已完成请求无需当前合约依赖即可回放。

新增加法迁移 V20261021210000 创建本服务4表；不创建后续付款、会话或退款调整表。原 compiled_terms_hash 保留可信 product
关联，本地完整准入 DTO 另以 payload_hash 校验，不声称可由省略字段来源的 DTO 重算原编译哈希。仓储使用生成 Mapper/Tables，复用框架
BaseDO、ID、Money/JSON、JWT、HTTP 和数据库事务。业务 Maven/Spring profile 为
acquire-business，运行配置与尚缺的付款/任务入口见[acquire说明](acquire/README.md)。

新增4类23个测试方法源码，未编译或执行。关键编排/Converter/适配/测试的 IDEA 错误检查完成；仓储仍待业务 target 生成类型。38个
POM XML解析、git diff --check 完成；在全新临时 Maven 目录用 Java 25 javac -proc:only 成功生成两个 Converter、四个 Mapper、四个
TableDef 和 Tables。此验证仅覆盖注解生成，不代替业务编译、JUnit、真实MySQL事务/JWT/HTTP验收。本批未运行 Maven 构建、DDL
或部署，不属于此前已部署镜像。

## 17. P2 第七批：原单会话与收银台只读摘要

实现 CheckoutSessionFacade.create、CashierSummaryFacade.summary，CashierFacade 继承摘要切片。商户 JWT 会话签发锁原订单、检查原主体与冻结回跳
code，新会话按原条款/订单等待策略计算期限；相同 requestKey 回放原
session/nonce/keyVersion/token/expiry，不因过期或订单终态续期。会话凭证复用框架 HMAC，scope 版本1只允许摘要，数据库只存
token 摘要；密钥缺失/漂移拒绝重建。

新增加法迁移 V20261021220000 创建 checkout_session，本服务实际合计5表。独立 cashier 安全链将 opaque capability 与商户 JWT
分开，tenant 仅作定位，摘要服务逐次认证 token/状态/期限/Origin 与订单 scope，不允许读其他订单。GET /checkout
提供无第三方资源的只读页面，fragment 立即移除，凭证只留页面内存；CSP/no-store/no-referrer、纯文本标题及精确整数分值展示已补。未暴露付款或
CheckoutCreateCommand 一步创单占位入口。

新增5类29个测试方法源码，未执行 JUnit。关键源码/测试 IDEA 检查、38个POM XML解析、git diff --check 完成；临时 Maven 样式目录的
Java25 javac -proc:only 成功生成两个新 Converter 与 session Mapper/TableDef/Tables，未向业务 target 写生成类。真实页面JS语法及无网络
Node VM 样例通过，验证大整数不舍入、缺失能力不请求API、标题纯文本与 fragment 清除；不是浏览器或服务端业务集成验收。未执行
Maven、DDL、部署。所需新增外部 Secret/公开URL配置及剩余付款范围见[acquire说明](acquire/README.md)。

## 18. P2 第八批：Checkout 一步创单

实现 CheckoutOrderFacade.checkout 及 POST /api/acquire/checkout/create，完整 AcquireOrderFacade 继承该切片。A0 保存
CHECKOUT 入口、初始 sessionId 和完整请求；ORDER 原 v1 不变，两入口共用商户订单唯一键且互相冲突。A1 复核准入后准备签名/可信
URL，原子写入订单、条款、整单费用、首次会话及完成标记。独立 session.create 复用同一会话准备/重建逻辑，保持原事务边界。

完成重放固定原会话及期限，不把原会话丢失或密钥失效当作续发机会。业务拒绝先提交意图结果，基础设施失败回滚 A1 并保留 A0 的稳定
ID。新增 V20261022010000 扩展 intent 字段/约束；旧迁移不改动，五张表不增减。按新增前期开发规则统一使用当前模型，不要求旧模型或混合版本兼容。

新增14个测试方法源码，覆盖原子提交调用顺序、原会话回放、跨入口冲突、回跳冲突、签名故障恢复、会话落库失败、准入拒绝/截止、权限和原
ORDER 摘要固定向量。IDE 检查关键源码与测试；未执行构建、注解处理、JUnit、DDL 或部署。mock 事务边界不是实际 MySQL
行回滚证明，当前收银台继续仅摘要展示；DirectPay、付款占用/结果、关单任务和通知仍待实现。

## 19. 创单意图映射与前期开发规则

显式声明 OrderCreationStorageConverter 的 entryMode/initialSessionId 双向映射，保留严格目标检查；移除 OrderCreateIntent
的旧模型兼容构造器，测试改用完整当前模型，新增2个持久化往返回归用例。排查发现本地安装的 acquire-domain JAR 仍缺字段，而
target 中模型已更新；构建需将 infrastructure 及依赖放入同一 reactor，命令见 acquire/README.md。

AGENTS.md 已加入前期无需兼容、允许直接重构/删除的规则，并同步去除本批 Checkout 文档中的混合版本发布要求。本轮仅静态检查，未运行构建、JUnit、DDL
或部署。

## 20. P2 第九批：payment 持久受理与查询

新增 payment-app、payment-infrastructure、payment-adapter，PaymentAcceptanceFacade.accept/detail 与 /api/payment/executions
下同名 POST 入口；完整 PaymentExecutionFacade 继承该切片。框架已验证 SERVICE JWT 绑定 tenant/sub/service_id，应用进一步限定
acquire-service-id 及独立权限。强制完整原准入条款、原订单/商户/金额/币种/能力绑定，持久完整命令及副本校验和。

READ_COMMITTED 事务按父门禁→单笔门禁→付款记录顺序建立首个 INIT，冻结 initiatedAt 与额度 operationId；既有非 OPEN
门禁禁止首次受理。同 paymentId 重放原事实而不再开门禁或执行渠道，同摘要改参仍冲突，同 allocation 不能绑定第二笔付款。三张表由
V20261022020000 创建，MapStruct/MyBatis-Flex 沿用框架配置与生成接口。

Maven/Spring payment-business 双 profile 及脚本入口已装配。当前只实现 durable acceptance，尚无后台执行器，action/attempt
为空；acquire 付款金额占用及调用、额度/渠道/输入准备、封禁写入口、可信结果和退款均未接通，不能视为可收款链路。

IDEA MCP 未在等待窗口内返回，按新协作规则使用 mvnd：clean compile -pl payment/payment-bootstrap -am -Ppayment-business
通过（9模块），clean test-compile -pl payment/payment-app,payment/payment-infrastructure,payment/payment-adapter -am
通过（8模块）。两次均跳过测试及无关检查；新增4类24个方法已编译，未执行
JUnit、数据库迁移或部署。范围与运行变量见[payment说明](payment/README.md)。

## 21. P2 第十批：payment 执行封禁与本地安全关闭

实现 PaymentControlFacade.close/fenceParent/parentFenceStatus 与内部同名 HTTP 适配，增加独立 close/fence/fence:read
权限，继续仅允许指定 acquire SERVICE 身份。父/单笔 gate 和付款按原顺序加锁，gate rowVersion/付款 version 执行
CAS；父封禁代次单调，status 要求代次覆盖及全部子付款具备终结证据，不能把 fenced 当作收敛。

新增不可变 PaymentControlReceipt 与 V20261022030000：控制请求服务端摘要固定参数，同 key 改参拒绝。未创建付款先保存
tombstone+未收款回执；已有 gate 而缺付款时必须有原证据，不用缺记录推断安全。仅 INIT/version0/无 activeAttempt 可本地 CLOSED
并记录证明；PROCESSING/UNKNOWN 只进 CLOSING，SUCCEEDED/FAILED/CLOSED 不重写终态。当前模型直接调整 PaymentCloseCommand 新增
payee 且 epoch 必须正数，close 返回 PaymentCloseDTO，避免伪造未知付款。

受理/查询 DTO 已携带原 terminalEvidenceId，关闭后 accept 重放仍只返回原 CLOSED。当前4表；渠道扫描、真实关闭/查询、acquire
占用释放、limitcenter 释放及 Outbox 结果仍未接通。

IDEA MCP 已恢复，关键 app/domain/client/adapter、Converter、测试源码检查无错误；仓储生成类型在 IDEA 索引中仍未解析，未声称其检查通过。必要的窄范围
mvnd clean test-compile -pl payment/payment-bootstrap -am -Ppayment-business 已通过9模块，MapStruct/Flex 真实生成通过；修正编译发现的关闭
DTO 嵌套映射后复验。新增2类22个测试方法已编译，累计6类46个新增业务方法，未运行 JUnit、DDL
或部署。详细协议见[payment说明](payment/README.md)。

## 22. P2 第十一批：acquire 商户付款发起与金额占用

新增 PaymentInitiationFacade.startPayment 和 `/api/acquire/payments/start`。原主体及独立权限、出站租户/原冻结能力检查后，A2
锁订单、校验金额/笔数，原子写 HELD allocation、订单占用及完整固定命令；事务外用挂载 SERVICE JWT 调 payment.accept。同
actionKey 沿原 paymentId/命令恢复，依赖失败不释放。首批仅 QR + NONE，不接敏感输入或渠道执行。

V20261022040000 创建 acquire 第6表。严格 MapStruct/生成 Mapper、Money/JSON/BaseDO/ID/HTTP 和本地 Clock
继续复用。新增3类23个测试方法源码。在用户暂停验证之前，10模块窄范围 acquire-business mvnd clean test-compile 已通过，JUnit
未执行；IDEA 仍有过期索引反馈，不把全部静态检查计为通过。DDL、业务镜像、部署和真实链路未执行。

## 23. P2 第十二批：权威付款结果刷新与 A3 金额落账

新增 PaymentOutcomeFacade/outcome 内部入口，指定 acquire SERVICE+read 权限，在父/单笔门禁锁内返回付款和持久
executionFenced；不存在付款不构造未收款事实。acquire 新增 PaymentProgressFacade.refresh 及 `/api/acquire/payments/refresh`
，原订单主体/独立 refresh 权限，不接收外部自报资金结果。

远端查询不持本地事务；A3 按订单→占用加锁重读冻结绑定，成功消耗占用并累计
gross，未收款终态且单笔已封禁才释放，UNKNOWN/处理中保留。订单/占用/不可变结果回执同事务 CAS 写入，固定原 sourceVersion
和终结证据；重复不累计，冲突/倒退不覆盖。足额才 PAID，退款不改 gross 或生命周期。

V20261022050000 新增 order_payment_outcome，acquire 合计7表。仅请求触发收敛，渠道尝试/结果生产、自动扫描/事件、费用确认、limitcenter
确认/释放、关单与 Outbox 通知仍待实现。start 不自动落账，需 refresh；cashier 页面和 scope 继续只读摘要。

按用户“先不验证，继续开发代码”指令，本节修改未执行 IDEA 检查、构建、测试、DDL 或部署。第22节编译记录不覆盖本节后续修改。

## 24. P2 第十三批：acquire 显式关单与未受理占用收敛

新增 AcquireOrderClosureFacade、原订单关闭 HTTP 入口、关闭意图领域/存储及有界编排。C0 锁订单原子提交关闭意图与
CLOSING，禁止新增付款；事务外使用同一服务凭证先封禁父订单，逐笔关闭并复用 A3
结果收敛，再查询父收敛证明。每请求最多16笔，以持久游标继续扫描，未知结果不释放占用，也不阻塞后面的待关闭付款。

增加 NOT_ACCEPTED 可信事实：payment.close 返回从未创建付款的 tombstone/证据时，使用0付款版本释放原占用，不伪造付款或把
epoch 当作版本。原结果回执和领域规则随当前模型直接调整。PaymentOutcomeOperations 供刷新/关单共用；刷新公开入参调整为写操作
Command，旧 Query 直接删除。

订单只有在远端父门禁/全部子付款收敛、本地所有占用清零且回执齐全时才 CLOSED。在途成功先付清仍保持
PAID，退款独立。原关闭请求固定身份/key/原因，重试继续原代次/远端 key；终态不重开。新增 V20261022060000，acquire
合计8表；修订此前新增、未在本工作流执行的结果回执 DDL，execution_fenced 统一 TINYINT。

沿用用户“先不验证，继续开发代码”要求，本批未运行 IDEA 检查、编译、测试、迁移或部署。后台定时关单/未知扫描、渠道关闭生产、费用确认、额度确认/释放和
Outbox 通知仍待实现。

## 25. P2 第十四批：原订单延期与幂等回执

新增 AcquireOrderExtensionFacade/extend HTTP 入口、应用编排、不可变延期回执领域/存储和 V20261022070000，acquire
合计9表。原主体/独立权限/原冻结条款控制延期；持久 ORDER/CHECKOUT 意图恢复原始截止，累计上限固定在原始截止上，避免滚动突破。

锁订单后先回放同键回执，再校验新动作 expectedVersion 和当前窗口。新截止及 deadlineVersion/rowVersion 与回执同事务 CAS
提交，其他字段使用白名单保留。原成功请求在终态也只读回放，首次延期不允许 CLOSING/PAID/CLOSED；CLOSE 精确截止拒绝，KEEP_PENDING
仍受原累计上限。

延期不延长原会话或渠道执行时间，不重查当前合约准入，不修改金额或原意图。自动到期任务后续使用截止代次接入。本批依用户要求未运行
IDEA 检查、编译、测试、DDL 或部署。

## 26. 支付方式及纯 API 范围修订

付款方式统一为 PAYMENT_CODE/JSAPI/H5，移除 Native 二维码及原生 APP。付款码交互契约已恢复，并标注敏感写入字段；商户扫码枪采集后调用纯
API，不依赖托管收银台。DirectPay 便利入口和 create→startPayment 两种 API 流程继续保留。JSAPI/H5 纯 API
返回调起参数/跳转地址，付款人仍需完成交互。

原 QR 受理样例及调用方改为 H5；展示二维码和 APP 调起契约移除，增加微信/支付宝的强类型 JSAPI 动作数据，H5 URL
长度调整为有界8192字符。当前受理仍只实现 H5+NONE；付款码/JSAPI 安全输入、真实渠道及 DirectPay 编排仍在剩余业务范围，未将契约调整计为运行实现。

本次未运行 IDEA 检查、编译、测试、DDL 或部署。

## 27. 全业务持续实施：付清费用与额度终态

新增 BUSINESS-IMPLEMENTATION.md 按完整目标跟踪各领域，保留未完成范围。付款方式以 PAYMENT_CODE/JSAPI/H5、纯 API 与可选收银台为准。

OrderFeeGateway/Converter/实现已接入 PaymentOutcomeOperations 的付清 A3，同事务确认固定总额费用；部分付款不拆分计费。新增
payment 专供 limitcenter 的受控权威付款事实接口、SERVICE 凭证 HTTP 查询及配置。

LimitSettlementFacade/confirm/release、原桶确认、明细 CAS、释放前置 tombstone 和不可变终态回执已补。release 响应改为可空
reservation 的 LimitReleaseDTO；未收款须同时有可信终态/版本/证据和执行封禁，原始桶按原政策/受理时间复核，未知不释放，过期不重复减占用。V20261022080000
新增第9张 LimitCenter 表。

按用户暂停验证要求，本批未运行 IDEA 检查、构建、测试、迁移或部署。退款恢复及大量执行/渠道/退款/任务/通知/查询/对账工作仍待完成，完整目标保持进行中。

## 28. 付款码/JSAPI 输入与纯 API 发起

补齐 payment.inputs.prepare 的 COLA app/domain/infra/adapter、payment_input 实际迁移、KMS envelope/HMAC版本指纹/短期到期、单
attempt 绑定及内部受限解密源码。accept 首次受理校验引用完整归属；重放保留原付款事实。acquire start 扩为三种方式，A2
后在事务外准备输入，再在原订单锁下 CAS 附加引用；原始码/票据不进入 acquire 意图。

DirectPaymentFacade/Service/Controller 已组合纯 API
create→start→detail，不创建收银台会话，返回实际当前订单及付款受理状态。固定原单/动作键重试；跨服务失败保留已有事实，不冒充事务回滚或渠道成功。输入改参及过期返回稳定业务码。

本批新增9个输入边界测试方法源码并同步原受理构造器；未运行 IDEA
检查、构建、JUnit、DDL、镜像或部署。真实执行/Y2/额度/risk/渠道、后台恢复/过期密文清理及其他剩余业务继续按
BUSINESS-IMPLEMENTATION.md 推进。

## 29. 渠道身份版本与路由基础

已补支付宝/微信 DIRECT/SERVICE_PROVIDER 的类型化非秘密身份、不可变 ChannelBindingRevision、可变 routing head 与操作回执。新增受信运维
SERVICE 的 publish/suspend/revoke/detail，版本 CAS、全历史幂等、旧版本保留与永久撤销门禁；不接受商户选择 binding
或原始凭证。账户范围不随凭证/授权版本轮换重置，身份负载严格规范校验。

新增三表实际迁移与 Spring MapStruct/MyBatis-Flex 持久层、内部 PaymentRouteSelector。按原冻结合约的渠道/方式/接入模式选
ACTIVE 头，优先级/id 稳定排序；无匹配报稳定业务码。选择器尚未接入 attempt 创建/额度/risk/Y2，未写假渠道成功或把配置状态当实际开通证明。

17个新增测试方法源码覆盖事务边界、发布/暂停/撤销、旧操作只读重放、版本/参数冲突、轮换保留、规范 JSON
与选路边界；两处领域夹具同步当前完整模型。未运行检查、编译、JUnit、DDL、镜像或部署。后续继续 B07 的 durable
attempt、额度与提交边界，B02/B08 的受控引用和实际开通/协议校验仍保留待办。

## 30. 持久支付尝试与 Y2 准备边界

首笔 PaymentAttemptFacade/app/infra/adapter 已补，冻结原绑定、金额和稳定32位请求号；原动作重放不换路由/请求号，盲替换阻止。新
payment_attempt 迁移保存原调用/摘要、风险和许可哈希等事实。PaymentExecutionLocks 统一新执行写者的父门禁→单笔门禁→付款顺序。

新增默认 DEFAULT_ALLOW/NOT_CONNECTED 风控与 PaymentLimitClient（挂载 SERVICE JWT + HttpTemplate），内部准备依次
risk/Reserve/Begin/Verify，并在独立本地 Y2 锁内绑定输入、CAS SUBMITTING。只给赢家一次内存提交票据；它尚未连接真实提交器，不是财务成功。关单新增对从未跨
Y2 的 INIT attempt 的本地证明，已提交或未知继续 CLOSING。拒绝/过期也只在相同锁内产生未提交证明，不放行旧 worker 或原始
evidenceId。

账户财务唯一范围改为真实收款账户，排除 tenant/app/模式/凭证别名，避免后续实际交易号被分范围重复认领。原完整身份仍由不可变
revision 校验。新增14个测试方法源码，旧构造器/账户范围夹具已同步；未运行检查、编译、JUnit、DDL、镜像或部署。

版本化 routingProfileRef 运行时资源校验已补；后续继续：实际渠道发送/结果动作/交易号 claim、查单/关闭、接受后的自动调度、quota
终态和风险结果 Outbox、退款及其余 BUSINESS-IMPLEMENTATION.md 全部范围。本批未将目标缩为“仅能准备”。

## 2026-10-22 不可变选路方案续批

已补 RoutingProfile 的全分层源码、受信管理发布/查询、规范编码/哈希、持久化及 V20261022120000__routing_profile.sql。Y1 强制读取冻结
routingProfileRef，在原租户/收款商户/合约能力与有序绑定白名单内选择 ACTIVE revision；缺失引用明确失败，不绕过合约或重选既有
attempt。22个新增测试方法仅有源码；按暂停验证要求未运行检查/构建/测试/DDL/镜像/部署。

接下来仍需真实渠道发送/动作/可信结果、交易号 claim、查单/关闭、自动调度与异步额度/风险结果通知；product 发布前引用/能力校验仍在
B02 后续范围。完整目标和状态见 BUSINESS-IMPLEMENTATION.md。

## 2026-10-22 Y3 与独立终态事务消息续批

已补内部结果应用、规范资金摘要、不可变回执/差错、全局渠道交易号认领、成功时间/退款容量、四目标事务
Outbox，以及本地未提交关单/风控拒绝的事件生产源码。支付成功状态/证据/渠道时间为同一行 CAS；跨租户同真实账户也不能重认领交易号，资金冲突只记录差错。新增
V20261022130000__payment_results.sql 四表与付款成功事实约束，复用框架 Outbox 迁移及同 DataSource 事务。

新增25个测试方法源码，并同步原编排构造器和本地终态事件断言；未执行检查/编译/测试/DDL/部署。当前没有真实渠道协议/验签/发送器，Y3
仅供后续受信适配器调用；四目标 Outbox 只有生产端，投递/接收幂等仍待闭环。其余完整范围保持 BUSINESS-IMPLEMENTATION.md
中的待办状态。

## 2026-10-23 额度/风控 Outbox 消费续批

新增两个独立 handler、严格事件解码、队列/租户/原终态/交易号与退款容量复核；短事务返回一致快照后才调用下游。额度复用原
confirm/release API、SERVICE JWT、ACK校验和持久幂等，释放允许空 reservation 或已过期。风控使用原咨询/决策及租户/业务事件幂等；未咨询不伪造，外部失败不默认成功。

新增39个测试方法源码；同步命名 HTTP 操作和权限说明，无新增业务表。按用户暂停要求未执行检查、编译、测试、DDL
或部署。acquire/realtime 消费和 BUSINESS-IMPLEMENTATION.md 的完整待办继续推进，不据此宣称所有业务或真实链路已完成。

## 2026-10-23 acquire 事件接收与第三目标投递续批

已补指定 SERVICE 的 acquire 接收/语义 ACK、原事实回查权限、原 merchant owner 从数据库恢复、order/allocation/fee 与业务
Inbox 原子提交；payment 新增 acquire 独立 Outbox handler、HTTP 客户端与挂载凭证配置，校验原事件/版本/证据 ACK 后才完成目标。重放/商户
refresh/不同 delivery 不重复落账；未新建测试 RPC 或伪造渠道成功。

新增一张接收回执表和37个测试方法源码，复用原 COLA 领域收敛、MapStruct、BaseDO、生成 Mapper/TableDef、HttpTemplate 和共享
SERVICE 凭证读取。按要求未运行检查/编译/测试/DDL/部署。下一步继续 realtime 独立查询投影、真实渠道发送/动作/查证、退款申请计划执行、通知及完整剩余业务。

## 2026-10-23 Realtime 独立查询续批

新增 realtime app/infrastructure/adapter、四类完整快照接收/语义回执、独立五表、源版本/哈希/终态与身份检查、商户授权查询/分页，以及
realtime-business 组装和配置。付款/退款可先于订单存储；缺来源不伪造金额，详情子列表改为分页，退款不改原 paid 或生成可支付金额。

payment 第四目标改为设计中的完整付款快照，Y3/本地终态同事务冻结原渠道和业务时间，独立 Outbox/HTTP/ACK 投递，不同步查询
realtime 或从 realtime 决策资金。终态自动生产源码已补，订单/退款与付款中间态生产、重建和其他完整待办仍未完成。

新增与同步领域/接收/查询/编码/鉴权/生产/发送测试源码，未运行检查、构建、测试、迁移、镜像或部署。新增业务 profile
不由旧骨架运行记录证明可用；继续 BUSINESS-IMPLEMENTATION.md 的全部原始范围。

## 2026-10-23 acquire 订单快照生产与投递

OrderSnapshotReader/MapStruct 从原订单可写事务读取落库完整头部及真实审计时间；OrderSnapshotEvents
在创单、占用、结果、关闭和延期的每个新订单版本后写同库 Outbox，检查精确版本，失败向外传播并回滚该事务。重复请求不生成新版本；输入附加/会话/关闭游标不另造快照。

OrderSnapshotDelivery/handler、严格 JSON codec、AcquireRealtimeHttpClient 和 acquire-business 配置已补，沿用完整原信封发送至
realtime/order；可信租户/PROCESSING 元数据校验和 ACK 身份/版本/处置校验，网络在本地事务外。新增 acquire 挂载服务凭证读取器并复用到原
payment 出站客户端，各目标配置仍独立。未新增业务表，复用 loadup_outbox_event。

同步既有测试构造器与幂等断言，新增事件生产事务失败、时间/金额冻结、独立投递与 ACK、严格解码、SERVICE
凭证轮换等测试源码。依用户要求未执行检查、构建、测试、DDL 或部署；所有新增代码状态为未验证。完整业务目标及未完事项保留于
BUSINESS-IMPLEMENTATION.md。

## 2026-10-23 独立退款申请与容量预占

已补 RefundPlanningService、RefundGatewayImpl、生成 Mapper/TableDef 所需三类 BaseDO、Spring MapStruct Converter、商户 JWT
actor 和 RefundController。首次以原成功付款/attempt/claim/冻结合约为事实，在 parent_gate→capacity (paymentId) 锁内生成固定
FIFO 子项，原请求号与原渠道身份不随重试改变；申请、计划、子退款和所有容量 CAS 同事务提交。退款号原参数重放不重新分配，查询仅可信商户，原收款
FENCED 不阻止独立退款。

V20261023030000__refund_planning.sql 新增 request/plan/child 三表，首次 INIT。按原成功时间应用合约窗口，所有金额继承 CNY
和原商户，容量预占不改 gross
已收或退款成功计数。新增业务/存储映射/鉴权测试源码，未运行。串行渠道执行/未知查证、终态应用、汇总、费用/限额恢复及投影通知保持未完，不能把计划受理作为渠道退款成功。遵守暂停验证：无检查/构建/测试/DDL/部署。

## 2026-10-23 退款提交与结果事务

已补 RefundExecutionLocks、RefundExecutionService、RefundResultService
及其纯领域变化约束。单步仅第一个未终结固定子项可推进；INIT→PROCESSING/submissionStartedAt 与申请版本同事务提交，只有赢家可首次
submit，重复执行 query 原号。网络不在本地事务内；本地超时只标 UNKNOWN 保留占用，缺真实渠道 binder 时不跨首次提交边界。

验证结果携带完整原身份、双方渠道请求/交易号、金额与原成功时间，按稳定观察键保存回执、差错和财务认领。成功子项/申请累计/容量变化/唯一退款流水认领/回执同事务落账，失败只释放容量，重复或乱序观察不重复落账或重开终态。新增三结果表及原规划迁移的未部署提交时间列，均为源码且未执行。无商户成功注入端点，也不改原订单/付款
gross 金额。

补领域边界、执行/结果服务、JSON 金额与事实摘要、存储转换测试源码；依用户要求未运行检查、构建或测试。真实渠道退款协议、自动任务驱动/未知查证、订单退款汇总/投影、退费/限额恢复和通知仍未完成；完整目标保持不变。

## 2026-10-23 退款金额汇总与 realtime 自动来源

新增 OrderRefundSummary 的领域/网关/BaseDO/MapStruct/生成 Mapper 接线及 V20261023050000
迁移。在退款规划/结果原事务内增加占用或确认/释放，独立金额版本不受 UNKNOWN 观察和原订单状态影响。RefundProjectionEvents
同事务读取落库原审计元数据，冻结逐笔退款全版本与金额汇总 Outbox；规划/首次提交/结果/本地 UNKNOWN 均已接入，重复观察不新增事件。

新增两类独立投递/严格类型解码/命名 HTTP 操作，复用既有 payment 到 realtime 的 SERVICE 认证与原消息重试，按语义 ACK
完成目标。订单退款汇总直接去除无限历史申请列表，当前六字段金额快照及 realtime
兼容性检查/测试/文档已同步重构，无旧协议兼容。逐笔查询仍独立分页，渠道实际时间与数据库审计时间不相互伪造。

本批新增及同步测试源码未运行；未执行检查、构建、测试、DDL、镜像或部署。业务完整目标仍未完成，后续为真实渠道与自动驱动、费用/额度恢复、商户通知、重建和对账。

## DirectPay 首笔绑定与整体恢复（2026-10-26）

DirectPay 改为独立 DIRECT_PAY A0，固定非敏感订单字段和首笔 actionKey/金额/choice/输入种类，禁止与 ORDER/CHECKOUT
同号混用或改变首笔。
raw input 不进入创建库。原 DirectPay 先恢复原首笔，确实尚未提交才用原请求补做 start；结果统一为当前订单和
PaymentRecoveryDTO，
不再保留早期 PaymentStartDTO 的嵌套兼容表示。调用权限增加 payment:recover。

新 POST /api/acquire/payments/direct-pay/recover 接收原非敏感元数据，新增 direct-pay:recover 并要求 create/read/payment:
recover。
未知 orderId 也能按原商户订单号恢复 A0：未观察到不分配 ID；INIT 通过仅续办入口继续原准入；已拒绝/到期返回白名单固定决定；
已完成后恢复原首笔。仅续办在 A0 事务内再次要求行存在，绝不因丢行重建新单；恢复不调用 start 或创建新 A2。

DTO、Facade、编码/摘要、COLA 编排、模式约束、MapStruct、开发期 CHECK 与表草案、测试源码已同步。
未执行 IDEA/构建/测试/DDL/部署或真实调用。首次输入准备并发/地址变动、JSAPI OAuth、其他渠道/通知/交易重建/风控/对账及全部原目标继续。

## Acquire A2 native 恢复接线（2026-10-26）

新增 AllocationRecoveryCursor/Key/Gateway、DO/MapStruct/GatewayImpl 和独立 Flyway 迁移；PaymentInitiationOperations
仅在新预占分支于同事务调用 AllocationRecoveryQueue.register，重放不登记新游标。首轮失败在 RPC 前回滚。
AllocationRecoveryService 直接实现 native Handler，复用原输入/原命令恢复与 A3 结果收敛；没有新增定时器、RPC 或厂商执行分支。
`application-acquire-business.yml` 提供轮询间隔环境变量。恢复权限来自已提交原意图，禁止创建操作，不检查原浏览器会话活性。
源码与测试源码已写入，未运行任何检查/构建/测试/DDL/部署/外部调用；FAILED 恢复与父订单到期任务仍在整体目标范围内。

## Native 订单生命周期任务接线（2026-10-26）

新增 OrderTaskKey、OrderDeadlineTasks/Service、OrderClosureTasks/RecoveryService；创建与延期构造器注入同库任务登记器。
商户 OrderClosureService 缩为鉴权 Facade，原协议编排移至 OrderClosureOperations，供原 C0 的后台续办复用。
OrderClosureGateway 增加 KEEP_PENDING 的精确状态推进实现；沿用生成 TableDef/Mapper 和现有 MapStruct 补丁。
A1 使用初始 deadlineVersion=1，延期使用原回执新版本；关闭任务代次使用原 closeIntent.rowVersion，初始为 0。
DirectPay 延期补齐原意图解码，不增加兼容重载、数据表或 RPC。轮询配置为 ACQUIRE_ORDER_CLOSE_POLL_DELAY_SECONDS。
源码/测试源码与文档已写入，未运行任何验证或部署，未变更数据库；native FAILED 恢复与外部协议验收仍待推进。

## Acquire task retry operations（2026-10-26）

新增 acquire client 的 RecoveryKind/查询/重试命令/DTO/Facade，app 使用独立 MapStruct 与原业务锁/证据检查，
运营 Controller 显式 no-store。身份复用 JwtAcquireServiceActorProvider，限制 configured pay-ops SERVICE 和独立 recovery
权限。
新增不可变审计领域/Gateway、BaseDO/生成 Mapper/MapStruct 存储适配与独立 Flyway，原子复用 DelayedTaskTemplate.retryFailed。
没有框架修改、通用任务代理、内部 HTTP/RPC 或资金执行入口。应用配置新增 ACQUIRE_RECOVERY_OPERATIONS_SERVICE_ID。
源码和测试源码已写入；没有运行任何验证或执行迁移/部署，其他服务的 FAILED 运维能力仍未声称完成。

## Payment original-task operational retries（2026-10-26）

新增 payment 自有 RecoveryKind/Query/RetryCommand/DTO/Facade、Spring MapStruct、应用编排与 no-store 运营 Controller。
身份复用 PaymentActorProvider，配置 PAYMENT_RECOVERY_OPERATIONS_SERVICE_ID；技术重试使用已有 DelayedTaskTemplate，不改框架。
独立 payment_recovery_retry_receipt 归本服务事务，未创建跨库 pay-common/pay-contract。原金融/退款锁边界维持并补强绑定。
测试源码覆盖原 callerService、两类命名空间、失败快照、跨周期重放、终态/旧代、未来期限、权限与回滚；新增错误码仍待执行规范门禁。
本轮未运行验证、DDL 或部署，未调用真实渠道或重试真实任务，整体目标保持进行中。
