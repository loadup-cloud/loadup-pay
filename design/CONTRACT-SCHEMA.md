# 收单合约参数与快照设计

版本：v1.0 ｜ 日期：2026-10-20 ｜ 对应[详细设计](../DETAILED-DESIGN.md)

## 1. 复用方式

product复用LoadUp的产品/方案/商户合约及不可变revision。当前框架以扁平字段路径、ParameterDefinition/TypedValue实现STRING、INTEGER、DECIMAL、BOOLEAN，参数来源沿PRODUCT→BUNDLE→SALES_PLAN→MERCHANT解析；不是现成的任意JSON
Schema引擎。

收单平台定义一套收单参数目录，把框架ConfigurationDTO经编译器转成`AcquiringTermsDTO(schemaVersion=1)`
。编译器只接受支持的参数和枚举，不执行脚本。复杂集合首期通过固定路径展开或可信引用表达，不把任意JSON字符串塞进STRING绕过校验；框架将来支持对象/数组时再增加schemaVersion。

下表是参数定义目录：值由产品/方案/商户按字段授权配置，未给出真实费率/限额数值。平台硬语义不可被商户覆盖，渠道硬限制在payment执行时再次约束。BOOLEAN是配置值类型，数据库标志仍使用TINYINT。

## 2. 参数目录

### 2.1 订单与多笔付款

| 路径                                   | 类型    | 约束/解释                                                |
|----------------------------------------|---------|----------------------------------------------------------|
| `trade.defaultCurrency`                | STRING  | 固定CNY；显式其他币种需先实现能力，不自动换汇            |
| `trade.amountModel`                    | STRING  | 固定FIXED_TOTAL，商户不可覆盖                            |
| `trade.completionRule`                 | STRING  | 固定SUCCESS_SUM_EQUALS_TOTAL                             |
| `order.waitSeconds`                    | INTEGER | 正数；从admittedAt计算默认orderDueAt                     |
| `order.maxWaitSeconds`                 | INTEGER | ≥waitSeconds；约束商户requestedDueAt                     |
| `order.unpaidAction`                   | STRING  | CLOSE / KEEP_PENDING，签约最终必须有值                   |
| `order.extensionAllowed`               | BOOLEAN | 是否允许非终态在原条款内延期                             |
| `order.extensionMaxSeconds`            | INTEGER | 可延期的累计上限；不开启时为0                            |
| `payment.minAmountMinor`               | INTEGER | >0；还须满足渠道最低金额                                 |
| `payment.maxAmountMinor`               | INTEGER | ≥min；不替代订单剩余额上界                               |
| `payment.maxSuccessfulPaymentsEnabled` | BOOLEAN | 启用时同时预占在途笔数                                   |
| `payment.maxSuccessfulPayments`        | INTEGER | 启用时>0；attempt重试不另计                              |
| `payment.allowExactRemainderBelowMin`  | BOOLEAN | 仅本笔恰为未占用全部尾款时可绕过业务min，不能绕过渠道min |
| `checkout.sessionSeconds`              | INTEGER | >0，受平台短期凭证上限约束；不是订单寿命                 |
| `checkout.returnTargetCode`            | STRING  | 引用审核过的回跳目标版本，不能传任意URL                  |

KEEP_PENDING下waitSeconds仅定义策略检查时点。`order.maxWaitSeconds`约束初始等待申请，`extensionMaxSeconds`
约束显式延期；二者不能被解释成KEEP_PENDING到了时间自动禁止续付。终态禁止付款、改额、延期和重开是固定代码语义，不提供
`allowReopen`一类开关。

### 2.2 渠道、接入与身份

首期固定展开`channel.<name>.<mode>.<method>.enabled`布尔路径，例如`channel.WECHAT.SERVICE_PROVIDER.QR.enabled`
。编译后的DTO用类型化集合表示，存储仍是框架支持的扁平值。只允许平台注册、实际实现的组合；不能靠配置开启未知方法。

| 路径                                               | 类型    | 解释                                            |
|----------------------------------------------------|---------|-------------------------------------------------|
| `channel.ALIPAY.DIRECT.<method>.enabled`           | BOOLEAN | 支付宝直连该方式是否允许                        |
| `channel.ALIPAY.SERVICE_PROVIDER.<method>.enabled` | BOOLEAN | 支付宝服务商代商户调用该方式                    |
| `channel.WECHAT.DIRECT.<method>.enabled`           | BOOLEAN | 微信直连                                        |
| `channel.WECHAT.SERVICE_PROVIDER.<method>.enabled` | BOOLEAN | 微信服务商/子商户                               |
| `channel.UNIONPAY.<mode>.<method>.enabled`         | BOOLEAN | 按已确定机构/产品实现后注册的组合开放           |
| `channel.routingProfileRef`                        | STRING  | 受控选路方案版本，只在允许组合/本商户绑定内选路 |

合约不存真实私钥、付款码、长期access token或完整敏感渠道授权。实际收款号/应用范围/服务商号/子商户号属于payment binding
revision；凭证存Secret/KMS引用。一个order只收给一个实际merchantId，部分付款可使用不同允许渠道，不能把渠道服务商身份当作业务收款主体。

### 2.3 整单计费与退费

| 路径                      | 类型    | 约束/解释                                                               |
|---------------------------|---------|-------------------------------------------------------------------------|
| `fee.base`                | STRING  | 固定ORDER_AMOUNT                                                        |
| `fee.confirmationTrigger` | STRING  | 首期固定ORDER_FULLY_PAID                                                |
| `fee.rate`                | DECIMAL | 十进制比例，例如0.003表示千分之三；范围由产品约束                       |
| `fee.fixedMinor`          | INTEGER | 固定费用≥0，每订单一次                                                  |
| `fee.rounding`            | STRING  | 已实现的HALF_UP / HALF_EVEN / DOWN等，发布时校验                        |
| `fee.minimumMinor`        | INTEGER | ≥0；不需要保底时0                                                       |
| `fee.maximumEnabled`      | BOOLEAN | 是否封顶                                                                |
| `fee.maximumMinor`        | INTEGER | 启用时≥minimumMinor                                                     |
| `fee.refundPolicy`        | STRING  | NO_RETURN / PRO_RATA_CUMULATIVE / FULL_ON_FULL_REFUND；仅已实现策略可选 |

计算顺序固定：`raw = orderAmountMinor * rate + fixedMinor`，按配置rounding舍入到整数分，再应用minimum与启用的maximum。使用BigDecimal和checked
long转换；一个order只计算一次F，多笔付款不重复最低费或固定费。

退费按原固定F与独立成功退款累计计算：NO_RETURN为0；PRO_RATA_CUMULATIVE为
`round(F * cumulativeRefunded / originalOrderAmount)`
并限制0..F；FULL_ON_FULL_REFUND仅原成功支付总额全部退款后退F。一次调整=累计应退−累计已调整，避免多笔舍入超退。费用未确认时先保留退款累计依据，确认时再计算应退，不能因早到退款事件丢掉后续退费。

该设计不从消费者支付金额额外扣手续费，不建设商户余额或渠道清结算。若未来收费触发包括部分关闭，须新增明确策略及验收，不在现有ORDER_FULLY_PAID中悄悄启用。

### 2.4 独立退款

| 路径                          | 类型    | 解释                                                                  |
|-------------------------------|---------|-----------------------------------------------------------------------|
| `refund.enabled`              | BOOLEAN | 是否允许独立退款                                                      |
| `refund.partialAllowed`       | BOOLEAN | 是否允许只退一部分已收款；不等于限制原订单部分付款                    |
| `refund.windowSeconds`        | INTEGER | 从原付款succeededAt起的退款申请窗口，另受渠道硬期限约束               |
| `refund.allocationPolicy`     | STRING  | 首期FIFO；LIFO/EXPLICIT实现后才可启用                                 |
| `refund.executionMode`        | STRING  | 首期固定SEQUENTIAL，UNKNOWN等待，确定失败后继续原计划下一项，不重分配 |
| `refund.lateCollectionPolicy` | STRING  | MANUAL_REVIEW / VERIFIED_AUTO_REFUND；自动补偿实现前只允许人工核实    |

FIFO对本次可用成功付款按succeededAt、paymentId排序；在payment本地固定计划并占用可退容量。若partialAllowed=false，申请额必须等于规划时原订单全部成功付款的尚可退金额，且不存在冲突在途退款；原订单未收差额不纳入可退金额。跨渠道子项不保证同一时刻全部成功。

lateCollectionPolicy只控制独立异常资金处置，不允许重开CLOSED。VERIFY/AUTO的真实渠道证据、幂等退款和审计尚未实现时，发布器拒绝开启自动模式。

### 2.5 商户额度

首期固定窗口DAY/WEEK/MONTH/LIFETIME、指标AMOUNT/PAYMENT_COUNT，以`limit.<window>.<metric>.*`
展开。父级汇总引用受控策略，不使用商户填入的任意父级ID数组。

| 路径                                | 类型    | 解释                                           |
|-------------------------------------|---------|------------------------------------------------|
| `limit.policyRef`                   | STRING  | 稳定policyId；版本在签约时解析并冻结           |
| `limit.countingBasis`               | STRING  | 首期INITIATED_AT，每PaymentOrder首次受理时点   |
| `limit.timezone`                    | STRING  | 固定Asia/Shanghai                              |
| `limit.weekStart`                   | INTEGER | 默认1（周一），1..7；已活跃桶不可直接改形状    |
| `limit.<window>.<metric>.enabled`   | BOOLEAN | 是否启用该窗口/指标                            |
| `limit.<window>.<metric>.threshold` | INTEGER | ≥0；启用0表示禁止新增，不解释成无限            |
| `limit.parentAggregationEnabled`    | BOOLEAN | 默认false；启用时真实父/子全部规则原子判定     |
| `limit.parentPolicyRef`             | STRING  | 启用时必需的受控父级累计策略                   |
| `limit.refundAmountRestore`         | STRING  | 默认NO_RESTORE；可选ORIGINAL_BUCKETS           |
| `limit.refundCountRestore`          | STRING  | 默认NO_RESTORE；可选FULL_PAYMENT_REFUNDED_ONCE |

每个规则稳定ruleScopeId由平台在策略创建时登记，普通修订禁止重新生成，终生累计不因升级重置。compiled
policy把可信merchant范围、ruleScope和各metric/window列明，再发limitcenter激活。阈值更新影响原稳定范围，不能通过切version取空计数；商业合约停止不删除其已激活规则。

对已登记并激活过的稳定规则，enabled仅控制阈值是否拦截，底层持续计量并保留原used/held/item；编译为enforcementEnabled和持续meteringEnabled。禁用期间仍按原范围预占/确认，但不比较商业threshold；重新启用沿用包含该期间的累计。尚未登记的规则不凭空生成桶；平台硬上限不可由商户关闭。规则的永久退役需要独立迁移设计，不能用enabled=false清除终生历史。

在同时有DAY/WEEK/MONTH/LIFETIME与父级规则时，Reserve全部原子成功/失败。退款恢复只动原item，不把跨月退款加到新月，也不增加原order.availableToPay。

### 2.6 通知

| 路径                         | 类型    | 解释                                      |
|------------------------------|---------|-------------------------------------------|
| `notify.enabled`             | BOOLEAN | 商户通知开关                              |
| `notify.targetRef`           | STRING  | 经审核的endpoint/audience版本，冻结到订单 |
| `notify.signingIdentityRef`  | STRING  | app/KMS密钥名称与版本的受信映射引用       |
| `notify.timeoutMillis`       | INTEGER | 单次HTTP时间预算，平台有上下限            |
| `notify.retryInitialSeconds` | INTEGER | 正数                                      |
| `notify.retryMultiplier`     | DECIMAL | ≥1，采用已实现的指数退避                  |
| `notify.retryMaximumSeconds` | INTEGER | ≥initial                                  |
| `notify.retryMaxAttempts`    | INTEGER | >0                                        |
| `notify.retryWindowSeconds`  | INTEGER | >0，截止从事件首次发生时算，不被重试刷新  |

策略最终需明确数值，不由本文臆定生产参数。下次执行为min (initial×multiplier^n,maximum)
并可加有界随机抖动；同时受maxAttempts与window限制。签名协议固定LoadUp request
v1，语义ACK固定eventId+SUCCESS，不给商户配置成“200即成功”。版本化目标/旧签名版本的可用期限覆盖冻结窗口，变更历史目标需受控迁移。

## 3. 编译与发布验证

1. 验证字段类型、产品/方案/商户覆盖权限、必填项与数值范围，填充明确默认值，保留每字段sourceLayer/code/version。
2. 做跨字段校验：金额范围、等待/延期范围、fee最小最大、开通方式、父级policy、限额指标窗口、通知与退款策略支持情况。未知参数/策略拒绝，不静默忽略。
3.

生成不可变AcquiringTermsDTO和独立compiledTermsHash，原框架revision的snapshotHash原样保留，二者分别验证；含schemaVersion、contractId/revision，以及typed
OrderTerms/PaymentTerms/FeeTerms/RefundTerms/NotificationTerms/LimitPolicyRef。存储可为JSON，但client提供类型化子record。

4. 对quota编译结果发可靠激活命令，limitcenter返回policyId/version/hash
   receipt；未ACK的revision不可用于新OrderAdmission。ACK和合约本地激活之间使用状态机恢复，不做跨库事务；重试同版本同hash幂等。
5. 支持普通合约暂停/终止与准入共用锁；已发admission返回原快照。旧order按其持久副本履约，不重跑当前商业资格，旧limit解释继续存在。

框架现有generation代表当前合约状态代次，revision代表条款；额度policyVersion和收单平台schemaVersion是另外两种版本，不混为一个version字段。不能把生成的业务JSON直接塞回框架任意string字段绕过其覆盖校验。

## 4. 受控引用与当前状态

returnTarget、notification target、routing profile、limit policy、channel
binding分别由对应owner提供版本化引用并校验可信归属。原条款固定其商业解释；真实渠道授权撤销、凭证不可用有独立当前执行检查，不伪装为“合约停止后拒绝原单”。安全敏感引用不允许浏览器或SDK替换。

风控固定默认放行及结果异步反馈语义，本期不添加厂商规则参数、审核状态或外部ALLOW有效期要求。reconciliation参数在后置服务详细设计时再定义，不能作为新单/支付的必填前置。

本文定义支持的配置语义，没有发布任何真实产品/合约，也没有写入费率、限额或通知凭证。

## 5. Product 首批编译器源码落地

product-app 新增 AcquiringTermsCompiler，按前述类型/已知路径生成强类型条款；新增
routingProfileRef、returnTargetCode、parentPolicyRef，保持受控引用完整。compiledTermsHash使用无double转换的规范化结构，原始编译JSON以LONGTEXT存储；校验字段来源并保留原框架snapshotHash。

本批落地原准入回放、合约本地锁、框架运行条件、可信customer读取及额度ACTIVE回执门禁。限额发布/ACK生产、实际渠道支持性、引用归属的发布验收仍待完成；未确认回执不放行。此处没有发布真实参数或合约。运行配置与验收边界见[product说明](../product/README.md)。

## 6. LimitCenter 首批激活消费者

LimitPolicyFacade.activate
已有持久化消费者源码；它校验完整规则的规范化哈希、受控SERVICE权限、连续版本及稳定计数形状，并返回原始激活时刻的可重放ACK。退款恢复解释一并冻结，原订单使用原revision；runtime阈值独立推进且累计桶不清零。当前仅支持收款商户自身规则，父级共享发布与规则迁移暂拒绝。

product
初始额度可靠发布、ACK校验和本地ACTIVE落库已有源码，详见第7节；多revision演进和真实集成验收仍待完成，未收可信ACK仍不得新单准入。参数和实际装配见[limitcenter说明](../limitcenter/README.md)。

## 7. Product 初始额度发布源码

初始发布只消费框架不可变revision1的完整收单配置，生成policyVersion1的八个自然窗口/指标规则；关闭门禁仍持续计量。稳定scope仅包含可信tenant/payee和协议名，不含policy/contract版本；父级聚合及商户多个初始合约绑定暂拒绝。完整商业配置仍通过既有封闭词汇编译器校验，不依赖伪造ACTIVE来通过校验。

发布意图和Outbox同事务；远程激活后匹配ACK才写本地ACTIVE，无回执继续拒绝新单。ACK不恢复停止/到期合约，既有原DENIED不改判。当前只完成额度规则就绪协议，实际渠道开通、routing/return/notification引用的可执行性与归属发布验收继续后续实施；不能据额度ACK宣称渠道已开通或支付可成功。未执行真实合约发布或数据库迁移，见[product说明](../product/README.md)。
