# 付款恢复意图的本地快照格式

更新：2026-10-27，对应 review R6。当前完成源码与测试输入，没有执行编译、数据库或运行验收。

## 格式归属

acquire 和 payment 不再把公开 PaymentExecuteCommand JSON 直接保存/反序列化。两边各自拥有基础设施内部 PaymentIntentSnapshot：

| 存储方 | 原列 | 唯一支持的 format |
|---|---|---|
| acquire / StoredPaymentAllocation | payment allocation 的 intent_payload/payload_hash | pay.acquire.payment-intent.v1 |
| payment / StoredPaymentAcceptance | payment_order.intent_payload/payload_hash | pay.payment.intent.v1 |

两个格式没有共享 Java 实现或共用数据库字节；通过原公开 Command/gRPC 边界转换后，各服务冻结自己的表示。不能把 acquire 的 payload 直接复制给 payment。
不新建 pay-common 或通用版本调度器，也不加载旧格式/裸 Command/其他 owner 格式作为兜底。
Codec port 仍以 Command 接收当前调用参数、返回恢复后的运行参数，存储行为封装在本地 codec/MapStruct 内，公开 client/RPC 契约不变。

## 冻结内容

顶层显式保存 format、payment/order/allocation/admission/payee ID、CNY 分额/币种、原 requestDigest、扁平 channel/method、可空 preparedInputRef、admittedTerms、subject 和 payableUntil。
choice 不使用公开 PaymentChoiceDTO。admittedTerms 及其 admission/order/payment/fee/refund/notification/limit/capability 层级均使用本地不可变 record，不以任何公开 DTO 作为存储字段类型。
保留公共业务枚举的语义名称及 JDK BigDecimal/RoundingMode；金额为精确 long 分额，没有中间 double。不是新建 Money 类型，运行领域继续复用 LoadUp Money。

payableUntil 是标准 OffsetDateTime.toString 的带偏移字符串，恢复显式解析，不依赖 API 时间序列化模块，也不将原绝对期限替换为“当前时间加合约窗口”。
Command 构造继续按现有 BusinessTime 的系统本地时区规范偏移表示；不强制 UTC，不调用当前合约/路由/额度接口重新填充快照。

capabilities 列表不可变，methods 以不可变 EnumSet 保存确定的枚举顺序。原条款版本、generation、snapshotHash、compiledTermsHash、限额规则引用、通知目标/签名引用及其他冻结策略均原值保存，不重算或升级。
preparedInputRef 可以显式 null，以保留 acquire 安全输入尚未附加的阶段；是否允许受理/提交仍由原安全输入与 Y2 流程决定。
快照不包含原始付款码、JSAPI 用户标识、密钥、凭证、渠道响应或新的执行许可。

## 校验与转换

PaymentIntentSnapshotConverter 使用标准 Spring MapStruct，在 Java 属性层映射公开 DTO 与本地嵌套 record；公开 DTO 的 JSON 命名/别名不参与存储映射。
当前 Command Bean Validation 只在 encode 接收当前参数时执行；decode 不拿当前接口校验注解重新裁定历史快照。快照构造器独立维护版本内必需字段、金额/身份/条款关系与各项策略约束。
恢复为当前 DTO 时仍经过 DTO 自身构造器；若未来语义约束/Java 结构变化，需要显式调整 mapper 或格式，不允许静默升级冻结业务条款。MapStruct 的严格目标字段检查继续约束新增 DTO 字段的处理。

两个 codec 使用本地固定标准 Jackson 配置，不继承 HTTP/API Mapper 或序列化注解：lowerCamelCase、标准枚举名称、原生整数/BigDecimal、显式 null。
由于期限已经是字符串、条款已经是本地 record，此 mapper 不需要框架 API 的自定义时间/Long/DTO serializer；不是新的 JSON 平台或可切换组件。
诊断输出另行复用 ToStringAsJson/DiagnosticHidden，输入引用、完整条款及通知签名引用隐藏；诊断策略不改变实际落库字段。

读取顺序为：UTF-8 payload 上限 262144 字节 → 原 SHA-256 校验 → 解析唯一 owner 格式及其构造约束 → 核对对应付款/占用索引 → 映射运行参数。
checksum 是完整性校验，不代表租户授权或资金证据；可信租户上下文、RPC 身份、门禁和 Y2 提交权均保持原职责。

严格 JSON 规则拒绝未知字段（含嵌套）、缺失构造字段、null primitive、浮点转整数、数值 ordinal 枚举和尾随额外文档；可空字段必须以明确 null 出现。
这些规则基于 Jackson 的 [反序列化开关](https://github.com/FasterXML/jackson-databind/blob/3.x/src/main/java/tools/jackson/databind/DeserializationFeature.java) 与
[注解隔离开关](https://github.com/FasterXML/jackson-databind/blob/3.x/src/main/java/tools/jackson/databind/MapperFeature.java)，本轮没有运行验证。
解析、格式、关系或索引失败均映射本模块 SNAPSHOT_INVALID，不记录原始 payload/异常消息，也不以失败自动释放占用或重建金融请求。

## 开发期数据处置

这是开发期直接格式替换。既有裸 Command JSON 在新源码下明确不可读，即使其旧 checksum 正确；没有兼容重载、双读、自动重写或按当前合约补字段。
本轮不执行开发库重建、数据回填、Flyway repair、DDL 或资金/任务写入；现有开发数据需要按新版格式重新准备。
列结构仍是原 LONGTEXT/哈希，没有新增表或 schema 迁移。初始化数据与真实 MySQL IT 通过原 encode 接口生成新格式，未来保留在途数据时必须另行制定明确的数据迁移并提供恢复证据。
本切片只处理上述两个付款意图；订单创建、合约发布、退款规划/事件等其他持久格式仍由各自原契约维护，不能宣称所有公开 DTO 持久化耦合都已消除。

## 测试源码与未完成证明

PaymentAcceptanceCodecImplTest / PaymentInitiationPersistenceTest 已同步新 mapper 装配，并补：

- API 命名策略与公开 Command/Admission JSON 字段改名后，存储字段及完整恢复值保持自己的表示；嵌套条款不跟随 API JSON。
- decode 不调用当前公开 Command Bean Validator；每个字段仍由独立快照构造规则和落库绑定约束。
- 裸旧格式、未知格式、错误 owner、缺失字段/null primitive、数值枚举、精度截断、嵌套未知字段、尾随文档拒绝，即使重新计算 checksum。
- 超过 JavaScript 精确整数范围的 long、完整合约/原期限、可空/已附加输入引用及嵌套集合不可变；诊断隐藏输入引用。

上述测试、MapStruct 注解处理、Spring 装配和原 MySQL IT 均未运行，没有新通过证据。
R6 完整验收仍需当前编译/测试、真实落库/重启恢复与调用链证明；不以本切片替代 R1/R2/R4/R5 或其他业务验收。
