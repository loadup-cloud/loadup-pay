# 第一阶段：应用初始化与契约层

日期：2026-10-21 ｜ 基线：[详细设计](DETAILED-DESIGN.md) ｜ 状态：P0/P1源码及六应用构建、镜像、Kubernetes启动验收完成；单测与业务执行待验收

## 1. 优先级与完成范围

| 优先级 | 本阶段结果 | 后续工作 |
|---|---|---|
| P0 应用初始化 | 六服务client/domain/bootstrap、根依赖管理、独立启动入口、Clock与Asia/Shanghai；六应用构建/镜像/Kubernetes启动及就绪验收通过 | 业务运行验收随P2实施；Headlamp本轮未发布 |
| P1 client契约 | Facade、Command/Query/DTO、错误码、强类型条款及跨字段校验、授权撤销、完整投影事件和消费接口 | 权限/幂等/事务应用服务及传输适配 |
| P1 domain契约 | 聚合生命周期、可信结果收敛、退款容量、原桶恢复、Gateway、纯计算规则和定向单测源码 | 持久化、可靠执行、数据库并发证明 |
| P2 执行闭环 | 已定义相应接口 | DB/Flyway、app/infrastructure、Outbox/Inbox、DelayedTask、业务gRPC、真实渠道 |

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

全部子模块继承根`loadup-pay`，内部版本在根dependencyManagement集中管理；框架版本仍由LoadUp BOM管理。服务目录的POM只聚合子模块，启动JAR位于`<service>/<service>-bootstrap/target/`；Dockerfile已同步。

源码入口：[订单Facade](acquire/acquire-client/src/main/java/io/github/loadup/pay/acquire/client/facade/AcquireOrderFacade.java)、[订单聚合](acquire/acquire-domain/src/main/java/io/github/loadup/pay/acquire/domain/model/AcquireOrder.java)、[付款Facade](payment/payment-client/src/main/java/io/github/loadup/pay/payment/client/facade/PaymentExecutionFacade.java)、[商户额度Facade](limitcenter/limitcenter-client/src/main/java/io/github/loadup/pay/limitcenter/client/facade/MerchantLimitFacade.java)。

client依赖方向：product→框架contract-client，customer→框架merchant-client，payment→product-client，acquire→payment-client，realtime→acquire/payment-client。limitcenter-client独立声明自身规则激活与额度接口。domain依赖pay-common及实际使用的LoadUp commons-dto/json基础能力，不依赖Spring/ORM注解、业务client或其他服务领域实现。当前不保留空协议模块或测试RPC运行依赖。

domain保留独立的生命周期枚举；app阶段通过MapStruct映射client枚举，不让传输对象变成领域模型。pay-common仅包含跨域的基础值与信封，没有共享商户/订单/付款聚合。

## 3. LoadUp复用

- `loadup-commons-dto`：Money、IdQuery、PageDTO和PageQuery.normalized；删除原pay-common.Money。框架Money支持有符号金额和多币种，DomainChecks.amount只保留收单CNY/非负校验，直接返回/校验框架值；计算读取getCent/getCurrencyValue，不改变整数分或checked arithmetic。公开Command/DTO仍保持amountMinor/currency报文。
- `loadup-commons-error`：六服务ResultCodeEnum和后续CommonException映射。纯domain抛DomainViolation，应用层负责映射稳定业务码。
- `loadup-commons-json`：DTO、领域record及事件信封统一使用ToStringAsJson.reflectionToString；付款码、授权票据、token URL、执行许可、preparedInputRef、credentialReference及信封data使用DiagnosticHidden或框架敏感字段规则。注解在嵌套反射诊断中也生效，不改变实际HTTP字段。
- `loadup-components-webmvc`及原生Actuator/Validation：统一Web基础装配；`/actuator/**`不属于`/api`报文封装范围。
- merchant-client/contract-client：复用框架公共商户事实及合约管理契约，新增的授权关系/准入由本项目建模，不复制框架商户主档或产品目录模型。
- `loadup-components-rpc-binder-grpc`：计划在P2业务RPC适配时使用，目前未引入启动模块。

测试用Ping客户端、服务端、descriptor/proto与整个协议模块均已删除，原pay-contract也不恢复。同步清理了两应用的gRPC依赖及配置、payment的9090端口和acquire的连接目标/专用健康检查环境变量。verify.sh仅检查六个应用就绪及Service ready EndpointSlice；Actuator基础健康检查保留。当前没有可用RPC接口；业务proto/stub、框架binder、身份/TLS和业务调用验收按P2实际用例实施，不预生成空模块。重新加载根Maven项目即可更新IDE模块。

## 4. 主要接口与领域行为

| 服务 | client入口 | domain落点 |
|---|---|---|
| acquire | AcquireOrderFacade、CheckoutSessionFacade、CashierFacade、PaymentOutcomeFacade、FeeRefundOutcomeFacade | AcquireOrder、OrderPaymentAllocation、OrderAdmissionProof、OrderFee、CheckoutSession；关单证据 |
| payment | PaymentExecutionFacade、PaymentInputFacade、RefundFacade；付款/退款/退款汇总事件 | PaymentOrder/Attempt、父子ExecutionGate、ChannelBindingRevision、RefundRequest/Order/Capacity、FifoRefundPolicy、渠道及风控Gateway |
| customer | MerchantEligibilityFacade、MerchantAuthorizationFacade；框架MerchantFacade/QueryFacade | MerchantRelationship、MerchantActionGrant及必要MerchantFacts；关系不替代明确动作授权 |
| product | OrderAdmissionFacade；框架合约公开client | MerchantContractFacts、OrderAdmission、准入/商户事实Gateway |
| limitcenter | MerchantLimitFacade、LimitPolicyFacade | LimitBucketSet、Reservation/Item、OperationGate、自然窗口策略、释放证据与持久化Gateway |
| realtime | MerchantTradeQueryFacade、TradeProjectionFacade | 各类完整快照、独立SourceVersion和投影Gateway |

订单聚合与单笔allocation作为同一acquire本地事务的变更返回；Gateway明确saveChange，尚未实现数据库原子性。相同actionKey的查询/冲突与唯一键由未来app/infrastructure落地，不把纯领域计算宣称为数据库并发验证。

已有纯领域规则包括：固定总额与paid+reserved上界；未知结果保留占用；累计恰好付清；CLOSE与KEEP_PENDING截止处理；需要父封禁和子项收敛的关闭；整单计费及退款先到时的退费依据；FIFO固定金额分配；退款状态独立；日/周/月/终生窗口；全部桶预占的不可变提案；执行后不按预占TTL失效；不同生产方/聚合版本不可互比。

会话签名、渠道验签、证据可信性、支付提交、退款调度、额度原子落库和订单创建意图恢复均属后续应用/适配实现。domain的Evidence值由可信适配层构造，不是对公网请求的认证能力。

JSAPI/APP方法枚举作为目标能力保留；本阶段PaymentActionData仅提供NONE/WAIT/QR/REDIRECT类型，真实渠道原生参数DTO在对应适配阶段加入。未实现方式不得因存在枚举就启用；没有伪造银联机构字段、渠道凭证或返回URL。

## 5. P1补齐结果

| 领域 | 本次补齐的行为与边界 |
|---|---|
| acquire | finishClose必须匹配父门禁回执的orderId/closeEpoch，零占用本身不能证明可关单；截止检查区分CLOSE/KEEP_PENDING；延期从原始dueAt计算累计上限；会话激活/过期/完成/取消；action重放核对原摘要 |
| payment | 门禁INIT→OPEN且FENCED不可重新开启；渠道结果匹配付款、尝试、账户范围、请求号和金额；UNKNOWN不允许换尝试，NO_COLLECTION还须执行已封禁；单尝试失败不自动终结整笔付款；CLOSING中的既有成功可收敛为SUCCEEDED |
| refund | 固定计划顺序执行，UNKNOWN阻塞后项，确定失败继续固定下一项；成功将原容量reserved转refunded，失败释放reserved，重复终态不重复变更；返回子退款/容量变更供同一事务保存 |
| limitcenter | 安全换尝试须匹配原operation/payment/attempt/epoch，递增执行代次，不重置原预占或受理时间；已执行不随TTL释放；退款按原item累计高水位恢复，乱序不多恢复，金额恢复到原桶，笔数仅全退后恢复一次 |
| customer/product | 关系/授权的INIT、激活和撤销；撤销不可重新激活；代操作校验可信principal所属父商户；新单准入同时检查商户事实、当前合约与额度规则激活；依赖失败不转为允许 |
| realtime | 专用订单/付款/退款完整快照，补齐商户、原创建时间、渠道及成功时间；完整退款申请进度与累计一致；查询返回独立读模型，不伪造权威订单可付能力 |

新增三个`*SnapshotEventDTO`，替换realtime消费原查询DTO的接口；退款汇总事件增加完整`requests`列表。各事件可核对信封的producer/type/schema/aggregate/version与payload绑定；这只是结构校验，不能代替服务身份、租户认证、验签或Inbox事务。customer输出生命周期字段使用`GrantState`，新增撤销Command带requestKey/expectedVersion；尚无已发布业务接口需要兼容。

条款DTO构造时校验付款金额上下界、初始等待/累计延期、费率上下界及通知凭证/重试范围。未实现的渠道原生动作、LIFO/EXPLICIT策略仍不可由配置发布为可用；配置编译/发布门禁在P2实现。

## 6. 验证边界

累计15个JUnit测试类、37个测试方法源码，覆盖多笔付款、关单回执、累计延期、条款范围、渠道身份/金额、安全换尝试、固定退款顺序/容量幂等、乱序退款恢复、商户授权撤销、完整快照及信封绑定等。本次补充框架Money的币种/负数边界与分单位费用、领域对象及嵌套信封脱敏的4个用例。product-client/payment-client的JUnit仍为test scope。

本次按用户明确要求执行Java 25/mvnd clean package、六镜像构建和OrbStack部署，生产源码编译及应用启动验收通过。构建使用`-Dmaven.test.skip=true`，未编译或执行上述37个单测，未执行DDL或业务交易验收。此前IDE检查未完成，不能当作检查通过；本次以实际打包和启动结果验证装配。

静态检查覆盖Maven模块/依赖图、纯domain依赖方向、Java包路径和公开类型引用、配置绑定、YAML/RBAC引用、脚本语法、Docker目标及文档链接。静态证据不能代替Spring启动、JUnit执行或真实MySQL并发测试。

## 7. 构建脚本与部署记录

六应用各有`build.sh`和`deploy.sh`，公共库另有`pay-common/build.sh`；统一逻辑见[scripts说明](scripts/README.md)。全量应用构建使用一次`mvnd clean package -pl <六bootstrap> -am`，随后构建六个镜像；独立部署只选择该应用的Deployment/Service及namespace，不改其他应用。构建自动采用mise固定的Java 25，清理了pay-common重复JSON依赖声明。

2026-10-21实测：

- 目标：Docker/Kubernetes context均为`orbstack`，namespace为`loadup-pay`。
- 全量发布标签：`20261021144823-43940`，镜像为`loadup-pay/<service>:<tag>`；六应用Deployment均1/1就绪，Pod Running、零重启，Service均有ready端点。
- 六应用HTTP `/actuator/health`均返回UP；六个Service DNS从acquire容器解析成功；六容器均为CST +0800；payment Service已仅暴露8080，测试RPC未恢复。
- 用已有镜像单独执行payment/deploy.sh成功，其他五个应用的完整spec前后相同；重复构建已有标签在Maven前被拒绝，避免原标签被覆盖但Pod未更新。
- 本次修复部署前六应用因本地镜像缺失产生的ErrImageNeverPull。使用本地共享镜像，不向远程registry推送，不发布Headlamp或修改其他namespace工作负载。

原始deploy/pay.yml保留示例标签，部署脚本经kubectl本地dry-run和JSON资源选择绑定本次标签，不修改源码中的镜像版本。不要再直接apply示例标签覆盖已发布版本。每次build默认产生新标签，回用已有镜像需显式SKIP_BUILD=1与IMAGE_TAG。

## 8. 下一实施批次（P2）

1. product/customer权威事实与OrderAdmission、limitcenter规则激活和全部桶事务；配套Flyway与数据库Gateway。
2. acquire的创建意图、原合约快照、金额占用/幂等、费用和到期任务，payment的执行门禁/回调/查证及Outbox事实。
3. 接业务gRPC与单渠道Checkout，随后实现退款固定计划、通知、realtime投影和收银台；按原架构补齐三渠道。

风控厂商和reconciliation仍按既定优先级后置；容量、保留期、SLO/RTO/RPO不在本轮增加。
