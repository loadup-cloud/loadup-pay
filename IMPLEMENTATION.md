# 第一阶段：应用初始化与契约层

日期：2026-10-21 ｜ 基线：[详细设计](DETAILED-DESIGN.md) ｜ 状态：源码已落地，待本地构建验收

## 1. 优先级与完成范围

| 优先级 | 本阶段结果 | 后续工作 |
|---|---|---|
| P0 应用初始化 | 六服务client/domain/bootstrap、根依赖管理、独立启动入口、Clock与Asia/Shanghai；六应用Deployment/Service和Headlamp范围 | 本地构建、启动与部署验收 |
| P1 client契约 | Facade、Command/Query/DTO、错误码、强类型条款、付款交互/动作、结果事件和消费接口 | 权限/幂等/事务应用服务及传输适配 |
| P1 domain契约 | 聚合/值对象、金额约束、状态枚举、Gateway、纯计算规则和定向单测源码 | 持久化、可靠执行、数据库并发证明 |
| P2 执行闭环 | 已定义相应接口 | DB/Flyway、app/infrastructure、Outbox/Inbox、DelayedTask、业务gRPC、真实渠道 |

本阶段的Facade是Java接口，未注册伪实现或支付成功Controller。Actuator健康仅表示应用骨架自身健康，不代表下单、收款、退款、合约准入或额度数据库事务已经可用。

## 2. 工程与依赖

```text
loadup-pay/
  pay-common/                    # JDK类型：Money、渠道词汇、时间、合约引用、事件头
  pay-contract/                  # 保留已有Ping wire contract
  customer|product|limitcenter|payment|acquire|realtime/
    <service>-client/            # 业务接口与传输无关的公开数据契约
    <service>-domain/            # 纯Java模型、策略、Gateway；无Spring/ORM/client依赖
    <service>-bootstrap/         # Spring Boot装配与技术入口
```

全部子模块继承根`loadup-pay`，内部版本在根dependencyManagement集中管理；框架版本仍由LoadUp BOM管理。服务目录的POM只聚合子模块，启动JAR位于`<service>/<service>-bootstrap/target/`；Dockerfile已同步。

源码入口：[订单Facade](acquire/acquire-client/src/main/java/io/github/loadup/pay/acquire/client/facade/AcquireOrderFacade.java)、[订单聚合](acquire/acquire-domain/src/main/java/io/github/loadup/pay/acquire/domain/model/AcquireOrder.java)、[付款Facade](payment/payment-client/src/main/java/io/github/loadup/pay/payment/client/facade/PaymentExecutionFacade.java)、[商户额度Facade](limitcenter/limitcenter-client/src/main/java/io/github/loadup/pay/limitcenter/client/facade/MerchantLimitFacade.java)。

client依赖方向：product→框架contract-client，customer→框架merchant-client，payment→product-client，acquire→payment-client，realtime→acquire/payment-client。limitcenter-client独立声明自身规则激活与额度接口。跨服务只依赖公开client，domain均只依赖pay-common，不依赖任何其他服务领域实现。

domain保留独立的生命周期枚举；app阶段通过MapStruct映射client枚举，不让传输对象变成领域模型。pay-common仅包含跨域的基础值与信封，没有共享商户/订单/付款聚合。

## 3. LoadUp复用

- `loadup-commons-dto`：IdQuery、PageDTO和PageQuery.normalized；没有重新定义HTTP响应或分页容器。
- `loadup-commons-error`：六服务ResultCodeEnum和后续CommonException映射。纯domain抛DomainViolation，应用层负责映射稳定业务码。
- `loadup-commons-json`：DTO诊断JSON与DiagnosticHidden；付款码、授权票据、token URL和执行许可不进入toString，实际传输字段不被诊断脱敏改变。
- `loadup-components-webmvc`及原生Actuator/Validation：统一Web基础装配；`/actuator/**`不属于`/api`报文封装范围。
- merchant-client/contract-client：复用框架公共商户事实及合约管理契约，新增的授权关系/准入由本项目建模，不复制框架商户主档或产品目录模型。
- `loadup-components-rpc-binder-grpc`：acquire使用GrpcChannelFactory；payment提供BindableService Bean；框架管理channel/server生命周期。保持`loadup.pay.v1.PaymentProbe/Ping`、3秒deadline和`payment:acquire`响应。

gRPC仍是本地开发明文配置；生产服务身份/TLS在传输适配阶段完成。本次按[Spring Boot gRPC说明](https://docs.spring.io/spring-boot/reference/io/grpc.html)和[配置属性](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)核对了`spring.grpc.*`、server开关和channel SSL配置。业务proto/stub生成与Java Facade绑定尚未实现，未在本阶段增加未使用的代码生成插件。

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

## 5. 验证边界

新增JUnit源码覆盖多笔付款付清/重复结果、未知占用与关闭、等待策略、退款先到的费用调整、FIFO/可退容量、退款未知状态、自然月与周边界、桶提案全失败不变、执行不随TTL释放、逐项授权、原准入与当前合约分开、跨生产方版本不可比较。

按项目约定未运行构建、测试、镜像构建、DDL或部署。IDE批量检查已尝试，但新增模块未导入其内容根，返回outside project content roots，不能当作检查通过。需要重新加载根Maven项目后由本地构建完成编译/装配验收；已有两个IDE运行配置已指向bootstrap模块。

静态检查覆盖Maven模块/依赖图、纯domain依赖方向、Java包路径和公开类型引用、配置绑定、YAML/RBAC引用、脚本语法、Docker目标及文档链接。静态证据不能代替Spring启动、JUnit执行或真实MySQL并发测试。

## 6. 下一实施批次

1. product/customer权威事实与OrderAdmission、limitcenter规则激活和全部桶事务；配套Flyway与数据库Gateway。
2. acquire的创建意图、原合约快照、金额占用/幂等、费用和到期任务，payment的执行门禁/回调/查证及Outbox事实。
3. 接业务gRPC与单渠道Checkout，随后实现退款固定计划、通知、realtime投影和收银台；按原架构补齐三渠道。

风控厂商和reconciliation仍按既定优先级后置；容量、保留期、SLO/RTO/RPO不在本轮增加。
