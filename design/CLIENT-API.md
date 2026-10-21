# Client API 详细设计

版本：v1.0 ｜ 日期：2026-10-20 ｜ 对应[详细设计](../DETAILED-DESIGN.md)与[数据库设计](DATABASE.md)

2026-10-21：主要Java Facade、Command/Query/DTO、错误码与结果事件已在各服务client模块落源码，见[实施说明](../IMPLEMENTATION.md)。本文保留目标协议；HTTP/业务gRPC适配及Facade实现尚未完成，真实渠道原生Action参数后续扩展。

本文为待实现的业务契约。`client`指COLA公开Java业务接口；HTTP与gRPC是其适配。示例省略package/import及重复的Schema/toString，不表示已有可编译SDK。框架已有接口保持原名，下面收单平台新增接口不伪称为框架现成功能。

## 1. 全局契约

- 包：`io.github.loadup.pay.<service>.client.{facade,command,query,dto,error}`。DTO/Command/Query为record；Facade不依赖Spring、生成stub、Mapper或HTTP envelope。
- 认证层先建立可信tenant、principal、服务身份、授权动作及商户作用域；这些不是商户能在JSON里任填的字段。后台job和RPC服务以最小服务权限执行。所有payeeMerchantId仍需归属/授权校验。
- 公共入参只表达业务意图。持久结果中的orderId/paymentId/refundId/operationId由服务生成并保持稳定；跨服务内部Command可以接收权威上游分配的稳定ID。
- 金额：`amountMinor: long`，CNY为分；currency创建时可省略，规范化为CNY后计算幂等摘要，其余内部接口显式传币种。JSON金额用整数，浏览器使用安全整数校验/大整数方案，禁止以小数元直接提交。
- 时间：Java OffsetDateTime，HTTP RFC3339显式`+08:00`，DB转Asia/Shanghai的LocalDateTime；gRPC Timestamp只作绝对时间编码，业务归桶仍Asia/Shanghai。
- 幂等先检查已提交结果，再校验是否允许一个新的业务动作。相同key不同规范化摘要冲突；同key重试不能扩大金额/换收款人/增加子退款。
- 详情复用框架`IdQuery`；有附加筛选的Query独立命名。分页使用`PageDTO<T>`，Controller映射`PageResponse.of(page)`，顶层`data`及`pageInfo(totalCount,pageIndex,pageSize)`；不新建业务分页容器。
- 使用模块ResultCodeEnum实现框架ResultCode，抛CommonException；错误文案不充当稳定错误码。transport失败和业务受理结果分别表达。

## 2. acquire：商户订单与付款入口

```java
public interface AcquireOrderFacade {
    AcquireOrderDTO create(AcquireOrderCreateCommand command);
    CheckoutDTO checkout(CheckoutCreateCommand command);
    DirectPayDTO directPay(DirectPayCommand command);
    PaymentStartDTO startPayment(PaymentStartCommand command);
    AcquireOrderDTO close(AcquireOrderCloseCommand command);
    AcquireOrderDTO extend(AcquireOrderExtendCommand command);
    AcquireOrderDTO detail(IdQuery query);
}

public interface CheckoutSessionFacade {
    CheckoutDTO create(CheckoutSessionCreateCommand command);
}

public interface CashierFacade {
    CashierOrderDTO summary(IdQuery query);
    PaymentStartDTO startPayment(CashierPaymentStartCommand command);
    PaymentProgressDTO paymentProgress(IdQuery query);
}
```

CashierFacade在经过会话认证的上下文执行；IdQuery只能引用该会话订单或其付款。商户Facade与cashier共用领域规则，cashier不取得商户列表/退款/改合约权限。

### 2.1 关键Command

```java
public record AcquireOrderCreateCommand(
        String payeeMerchantId, String merchantOrderNo, String productScopeKey,
        long amountMinor, String currency, String subject,
        OffsetDateTime requestedDueAt) {}

public record CheckoutCreateCommand(
        String payeeMerchantId, String merchantOrderNo, String productScopeKey,
        long amountMinor, String currency, String subject,
        OffsetDateTime requestedDueAt, String returnTargetCode) {}

public record PaymentChoiceDTO(Channel channel, PaymentMethod method) {}

public record PaymentStartCommand(
        String orderId, String actionKey, long amountMinor,
        PaymentChoiceDTO choice, PaymentInteractionDTO interaction) {}

public record CashierPaymentStartCommand(
        String orderId, String actionKey, long amountMinor,
        PaymentChoiceDTO choice, PaymentInteractionDTO interaction) {}

public record DirectPayCommand(
        String payeeMerchantId, String merchantOrderNo, String productScopeKey,
        long amountMinor, String currency, String subject,
        OffsetDateTime requestedDueAt, String actionKey, long firstPaymentAmountMinor,
        PaymentChoiceDTO choice, PaymentInteractionDTO interaction) {}

public record AcquireOrderCloseCommand(String id, String requestKey, String reasonCode) {}
public record AcquireOrderExtendCommand(
        String id, String requestKey, OffsetDateTime requestedDueAt, long expectedVersion) {}
public record CheckoutSessionCreateCommand(
        String orderId, String requestKey, String returnTargetCode) {}
```

`PaymentInteractionDTO`是有封闭类型集合的交互参数，不是任意Map：NONE、PAYMENT_CODE（短期付款码）、CHANNEL_IDENTITY（当前渠道要求的授权交换票据）。HTTP用`type`判别且拒绝多种输入并存；Java用sealed interface及对应record。敏感record的toString仅返回类型/脱敏标志，校验异常不含原值。不能让浏览器自报一个跨渠道、跨应用可复用的payerId；服务端完成作用域验证与交换，不建设消费者档案。

支付方式枚举：PAYMENT_CODE、QR、JSAPI、H5、APP。渠道与方式是否可用以冻结合约、当前实际商户开通能力及输入前提共同判断；有枚举值不代表该渠道已完成适配。浏览器不提交bindingId、服务商号、子商户号、credentialRef或接入模式；服务端选路决定实际DIRECT/SERVICE_PROVIDER。

`requestedDueAt`只是商户申请值，服务端按原条款范围验证。回跳使用经审核的returnTargetCode，不接受任意URL。不接受商户直接指定contractRevision、feeAmount或quota threshold。

### 2.2 幂等范围和完成语义

| 方法 | 幂等身份 | 摘要与语义 |
|---|---|---|
| create/checkout/directPay | tenant+payee+merchantOrderNo | 入口类型、固定总额/币种/标题/产品scope/请求等待值，DirectPay还含首笔动作；不能同号把Checkout改成DirectPay再次首付 |
| startPayment | tenant+orderId+actorScope+actionKey | 手动金额、channel/method、输入的服务端keyed摘要；不允许同key改金额 |
| close/extend | tenant+orderId+commandType+requestKey | 参数固定；close返回CLOSING表示已接受关闭，不表示渠道已确定关闭 |
| session.create | tenant+orderId+requestKey | 返回相同session；重放过期session不自动续期，新的续发意图用新requestKey |

不同session的actionKey可以相同，但同一明确付款动作应保留原sessionId/actionKey与paymentId供重试；会话轮换不自动迁移未完成动作到新key。已知paymentId应查询原笔，不能根据页面刷新再收同一笔钱。

API在受理完成前RPC断开，调用方以原key重试或查询原ID。action结果尚在准备时返回WAIT。DirectPay的业务总额和首笔金额分别校验，首笔允许小于总额；扫码枪常见一次付清只是取firstPaymentAmountMinor=amountMinor。

### 2.3 返回DTO

```java
public record AcquireOrderDTO(
        String orderId, String merchantOrderNo, String payeeMerchantId,
        String currency, long amountMinor, long paidAmountMinor,
        long paymentReservedMinor, long outstandingAmountMinor,
        long availableToPayMinor, AcquireOrderState state,
        PaymentProgress progress, boolean canAcceptNewPayment,
        OffsetDateTime orderDueAt, long version) {}

public record CheckoutDTO(
        AcquireOrderDTO order, String sessionId, String checkoutUrl,
        OffsetDateTime sessionExpiresAt) {}

public record PaymentStartDTO(
        String orderId, String paymentId, String allocationId,
        PaymentState state, PaymentActionDTO action, long version) {}

public record DirectPayDTO(AcquireOrderDTO order, PaymentStartDTO payment) {}

public record PaymentActionDTO(
        PaymentActionType type, OffsetDateTime expiresAt,
        PaymentActionDataDTO data) {}
```

PaymentActionDataDTO也是封闭类型：

| action.type | data字段 | 行为 |
|---|---|---|
| NONE | 无 | 无需交互，不代表成功 |
| WAIT | recommendedPollAfterMillis | 查原paymentId，不能新建付款 |
| DISPLAY_QR | qrContent | 展示固定本笔金额的二维码，不能改用于下一笔 |
| REDIRECT | redirectUrl | 仅服务端渠道适配产出的受信地址 |
| INVOKE_JSAPI | channel、appId及各渠道类型化签名字段 | 只提供调用该渠道所需的公开短期参数 |
| INVOKE_APP | channel及类型化原生参数 | 不包含商户长期私钥 |

不提供一个要求SDK自行拼装渠道协议的通用payload字符串。JSAPI等具体字段跟随真实渠道产品，由adapter对应版本DTO定义；不虚构统一银联字段。

CashierOrderDTO是受限视图：orderId、商户显示名、标题、金额汇总、状态/付款进度、可用choice集合和会话到期；不暴露合约、费率、服务商凭证、内部gate和其他付款人标识。PaymentProgressDTO返回该笔权威结果及所属订单汇总，前端success回跳不参与成功认定。

### 2.4 HTTP接入示例

JSON业务Controller统一POST：

| URL | Facade | 身份 |
|---|---|---|
| `/api/acquire/order/create` | create | 商户后端 |
| `/api/acquire/checkout/create` | checkout | 商户后端 |
| `/api/acquire/direct-pay` | directPay | 商户后端/POS可信后端 |
| `/api/acquire/payment/start` | startPayment | 商户后端 |
| `/api/acquire/order/detail`、`/close`、`/extend` | 对应方法 | 商户后端 |
| `/api/acquire/session/create` | session.create | 商户后端 |
| `/api/cashier/order/summary`、`/payment/start`、`/payment/progress` | CashierFacade | 订单scope token |
| `/api/payment/refund/create`、`/detail` | RefundFacade | 退款授权商户后端 |

托管页面`GET /checkout`是静态页面入口；URL fragment携带受限token，页面取出后通过Authorization头传API，并清除敏感fragment，不把token放进渠道returnUrl或服务端access log。若代理/渠道必须跳转，服务端根据已认证会话生成受限回跳关联，不复制长期商户凭证。

Checkout请求：

```json
{
  "payeeMerchantId": "M1001", "merchantOrderNo": "SHOP-20261020-001",
  "productScopeKey": "ACQUIRING", "amountMinor": 10000,
  "currency": "CNY", "subject": "门店收款", "returnTargetCode": "SHOP_RESULT"
}
```

首笔付款：

```json
{
  "orderId": "O1001", "actionKey": "d3aa12a6-1",
  "amountMinor": 3000,
  "choice": {"channel": "WECHAT", "method": "QR"},
  "interaction": {"type": "NONE"}
}
```

成功后订单paid=3000、state=PENDING，下一次明确付款使用新actionKey。最后累计10000才PAID；退款4000后paid仍10000、availableToPay仍0。

### 2.5 会话token的幂等返回

数据库只保存token_hash，不能依赖读取明文token实现Checkout重试。实现基线使用服务端版本化Secret密钥对固定sessionId/orderId/nonce/expiry生成HMAC-SHA256 token；nonce非秘密、与密钥引用/版本一起保存，token可按相同输入重建，数据库仍只有token摘要。该短期会话能力使用JCA与受管Secret即可，不能假定框架已经提供cashier token类。

规范输入是UTF-8，以LF分行并保留末尾LF：`pay-checkout-v1`、sessionId、orderId、expiryEpochSeconds、tokenNonce。输出`sessionId + "." + base64url_no_padding(HMAC)`；入站恒定时间比较摘要、检查数据库状态和expiresAt，再建立该订单scope。sessionId可由框架IdGenerator生成，tokenNonce使用安全随机源的32字节并以Hex保存；HMAC密钥不下发浏览器或SDK。expiry对齐到秒；数据库记录确切同一值，不能每次重试重新计算到期时间。

同session重放生成完全相同token/期限；会话过期或撤销后不能因重新签出token而恢复权限。旧Secret版本保留至对应会话最长有效期/重试期结束；紧急撤销可取消对应session，轮换新session使用新版本。

## 3. product与customer：准入和授权

```java
public interface OrderAdmissionFacade {
    OrderAdmissionDTO authorize(OrderAdmissionAuthorizeCommand command);
    OrderAdmissionDTO detail(IdQuery query);
}

public record OrderAdmissionAuthorizeCommand(
        String orderId, String payeeMerchantId, String productScopeKey,
        String requestDigest, long amountMinor, String currency,
        OffsetDateTime requestedDueAt) {}

public record OrderAdmissionDTO(
        String admissionId, String orderId, String payeeMerchantId,
        AdmissionDecision decision, String reasonCode, String requestDigest,
        String contractId, int revision, long generation, String snapshotHash, String compiledTermsHash,
        OffsetDateTime admittedAt, OffsetDateTime materializeBefore,
        AcquiringTermsDTO terms) {}

public interface MerchantEligibilityFacade {
    MerchantEligibilityDTO authorize(MerchantActionAuthorizeCommand command);
}

public record MerchantActionAuthorizeCommand(
        String payeeMerchantId, MerchantAction action) {}
```

MerchantEligibilityFacade从可信上下文取调用者，不从请求读取actorMerchantId冒充权限；跨product→customer校验“原商户调用者”时，使用经认证网关/服务端签发的委托上下文（audience/action/expiry），不能只转发一个可自填header。返回可信payee经营状态、商户资料版本、授权依据/关系版本；仅返回必要事实，不下发全部商户联系人。

`authorize`是持久业务命令，重复查询原准入不是重新做一次当前资格。新准入同时验证合约当前状态/区间、customer事实和limit policy激活，普通停单与新准入在product本地串行。DENIED结果以明确decision/reason返回，不能将依赖不可用伪装成业务允许；依赖故障保持意图待恢复。

已有订单续付是acquire的`ValidateAdmittedOrder`领域规则，输入本地原准入/条款/当前订单/新付款意图，不需要为每笔付款同步调用当前ContractResolveFacade。原合约停止不会撤销原INIT/PENDING；实际渠道授权失效/订单关闭/剩余额不足仍可拒绝。

框架MerchantFacade/QueryFacade、MerchantContractFacade、ContractResolveFacade用于已有资料/合约管理与配置解析。新增OrderAdmission不套在当前resolve外面假定获得原子停单；必须与合约状态修改共用product本地锁和事务。新接口先在收单平台product中实现，只有形成通用能力后再考虑回沉框架。

## 4. payment：执行与关闭的内部client

```java
public interface PaymentExecutionFacade {
    PaymentExecutionDTO accept(PaymentExecuteCommand command);
    PaymentExecutionDTO detail(IdQuery query);
    PaymentExecutionDTO close(PaymentCloseCommand command);
    ParentFenceDTO fenceParent(ParentPaymentFenceCommand command);
    ParentFenceDTO parentFenceStatus(ParentFenceStatusQuery query);
}

public record PaymentExecuteCommand(
        String paymentId, String orderId, String allocationId, String admissionId,
        String payeeMerchantId, long amountMinor, String currency,
        String requestDigest, PaymentChoiceDTO choice, String preparedInputRef,
        AdmittedOrderTermsDTO admittedTerms) {}

public record PaymentCloseCommand(
        String paymentId, String orderId, String requestKey,
        long fenceEpoch, String reasonCode) {}

public record ParentPaymentFenceCommand(
        String orderId, String payeeMerchantId, long closeEpoch, String requestKey) {}

public record ParentFenceStatusQuery(String orderId, long closeEpoch) {}

public record ParentFenceDTO(
        String orderId, long closeEpoch, boolean fenced,
        boolean allChildrenConverged, int unknownCount) {}
```

AdmittedOrderTermsDTO含contractId/revision/原snapshotHash/compiledTermsHash、固定order总额、原退款/通知/限额配置等强类型必要子DTO，payload有schemaVersion；由可信acquire传递并校验绑定摘要，不能从公网上原样映射为已信任事实。PaymentExecutionDTO含payment/order/allocation、金额、state、activeAttempt、action、version和终结证据引用；不返回敏感input/credential。

| 内部操作 | 关键语义 |
|---|---|
| accept | paymentId唯一；同ID异digest拒绝；先门禁再持久化，返回已接受不保证已向渠道提交 |
| close | 没有PaymentOrder也先持久化单笔tombstone；返回CLOSING/最终结果，不能将“没查到本地付款”直接当成安全释放 |
| fenceParent | 持久化父gate，拒绝迟到新paymentId；closeEpoch单调，低代次幂等不覆盖高代次 |
| parentFenceStatus | fenced不等于allChildrenConverged；只有所有已有付款已最终收敛才允许acquire正常关闭 |

敏感输入准备使用payment内部`PaymentInputPrepareCommand(paymentId,inputKey,inputType,sensitiveValue)`及DTO(inputRef,inputDigest,expiresAt)，只对可信acquire开放。业务重试复用inputKey；同key不同敏感值冲突。该接口不触发渠道提交，不得把inputRef当成任意付款均可使用的bearer凭证。

原paymentId更换渠道/方式或新的付款码是`PaymentAttemptStartCommand(paymentId,requestKey,choice,preparedInputRef,previousAttemptEvidenceId)`：首期仅在旧attempt确定安全终结且付款仍允许新尝试时开放，结果未知必须拒绝；先按requestKey返回原尝试，再锁payment创建新attempt。普通超时重试accept不会新建attempt。若产品首期不对外开放换方式，则保留该内部用例，商户需安全关闭原付款后用新action发起，不用一个通用retry按钮盲目扣款。

## 5. limitcenter client

```java
public interface MerchantLimitFacade {
    LimitReservationDTO reserve(LimitReserveCommand command);
    LimitExecutionPermitDTO beginExecution(LimitBeginExecutionCommand command);
    LimitReservationDTO confirm(LimitConfirmCommand command);
    LimitReservationDTO release(LimitReleaseCommand command);
    LimitRestoreDTO restoreRefund(LimitRefundRestoreCommand command);
    LimitReservationDTO detail(LimitOperationQuery query);
}

public record LimitReserveCommand(
        String operationId, String paymentId, String orderId, String payeeMerchantId,
        long amountMinor, String currency, OffsetDateTime initiatedAt,
        String policyId, int policyVersion, String requestDigest) {}

public record LimitBeginExecutionCommand(
        String operationId, String attemptId, String requestKey,
        String previousAttemptEvidenceId) {}

public record LimitConfirmCommand(
        String operationId, String paymentId, long paymentVersion,
        String businessEventId, String successEvidenceId,
        long amountMinor, String currency, OffsetDateTime succeededAt) {}

public record LimitReleaseCommand(
        String operationId, String paymentId, long paymentVersion,
        String businessEventId, String noCollectionEvidenceId, String reasonCode) {}

public record LimitRefundRestoreCommand(
        String operationId, String paymentId, String refundId,
        long refundVersion, String businessEventId, long refundAmountMinor,
        long cumulativePaymentRefundedMinor, String currency, String refundEvidenceId) {}

public record LimitOperationQuery(String operationId) {}
```

LimitReservationDTO返回reservationId/operationId/state/原policy/initiatedAt/version；不把全部商户额度明细默认发给浏览器。LimitExecutionPermitDTO绑定operationId/paymentId/attemptId/executionEpoch及不可伪造的permit，payment只能用于对应的本地Y2；permit不是渠道扣款参数。

Reserve不接受请求自带threshold、父商户列表或任意ruleScope。limitcenter通过已激活policy内可信范围得到应判定的全部规则；parent关系变更只通过受控policy激活处理，不能由页面省略父级绕过限制。initiatedAt必须来自payment首次受理事实，不能由商户回填任意日期选空桶。

同operation重复Reserve不重加held；同attempt/requestKey重复Begin返回同许可，安全换attempt时递增epoch。超时先查原operation。RESERVED过期与Begin互斥，过期后拒绝该次执行；首期将原付款安全终止/释放订单占用，再用新的明确付款动作，不在旧operation上悄悄重置桶。EXECUTING没有仅凭TTL的Release接口。

Confirm/Release/Restore仅供可信payment事实投递，当前合约是否活跃不作为成功事实接收条件。Confirm相同成功幂等；不匹配金额/币种/付款绑定拒绝。Restore收到退款早于Confirm时返回DEPENDENCY_NOT_READY，使投递重试；NO_RESTORE返回明确NO_ACTION；不能提前ACK并丢掉将来的恢复。

Release早于Reserve时也创建operation gate的FENCED记录，返回RELEASED（reservationId可为空）；后到Reserve/Begin返回EXECUTION_FENCED，不能把一次NOT_FOUND ACK当成已防住迟到申请。与已确认成功矛盾的Release拒绝自动释放并进入查证。

成功退款恢复笔数需payment完整累计退款额达到原付款金额，且原item最多恢复1。规则按原reservation/item解释，退款发生月不能替代原付款月。

规则发布为另一个仅product可调的`LimitPolicyFacade.activate(LimitPolicyActivateCommand)`：绑定policyId/version/hash与编译规则；同版本异hash冲突，持久化后返回activationReceipt。阈值更新用稳定ruleScope，不能以新policyVersion生成新累计范围。

## 6. 独立退款client

```java
public interface RefundFacade {
    RefundRequestDTO create(RefundCreateCommand command);
    RefundRequestDTO detail(IdQuery query);
}

public record RefundCreateCommand(
        String orderId, String payeeMerchantId, String merchantRefundNo,
        long amountMinor, String reasonCode,
        List<RefundExplicitAllocationDTO> explicitAllocations) {}

public record RefundExplicitAllocationDTO(String paymentId, long amountMinor) {}

public record RefundRequestDTO(
        String refundRequestId, String orderId, String merchantRefundNo,
        String currency, long amountMinor, RefundRequestState state,
        long succeededMinor, long failedMinor, int unknownCount,
        int planVersion, List<RefundAllocationDTO> allocations, long version) {}

public record RefundAllocationDTO(
        int sequenceNo, String paymentId, String refundId,
        long amountMinor, RefundState state) {}
```

金额/币种和原商户校验由payment完成，币种继承原付款/订单，不能通过退款换汇。默认FIFO；explicitAllocations只有原合约选择且实际实现EXPLICIT时可传，金额求和必须恰等于申请额，paymentId归同一order/payee/currency。FIFO下传显式分配是参数错误；首期未实现LIFO/EXPLICIT时在发布配置时拒绝，不运行时静默降为FIFO。

创建申请返回PROCESSING或即时已知进度。原订单/成功付款一直保持原状态；部分已收订单能否退及窗口按原退款条款，不把“父订单PAID后禁止订单操作”解释成禁止独立退款。

首期按plan.sequenceNo串行执行，前项确定成功/失败后继续固定计划下一项，UNKNOWN先等待查证。确定FAILED不重新打开；通信重放只查原请求号/原ID。退款查询由payment读取申请与固定子项，商户同refundNo重试返回原计划；子项UNKNOWN不释放可退容量，不把同一个计划失败项自动改退另一笔成功付款。成功后的RefundSucceeded通知与订单完成通知完全独立。

## 7. realtime client

```java
public interface MerchantTradeQueryFacade {
    TradeDetailDTO detail(TradeDetailQuery query);
    PageDTO<TradeOrderDTO> page(TradeOrderPageQuery query);
    PageDTO<TradePaymentDTO> payments(TradePaymentPageQuery query);
    PageDTO<TradeRefundDTO> refunds(TradeRefundPageQuery query);
}

public record TradeOrderPageQuery(
        String payeeMerchantId, String merchantOrderNo,
        OffsetDateTime createdFrom, OffsetDateTime createdTo,
        AcquireOrderState state, int page, int size) {}
```

所有查询先取授权商户范围。请求payee只是进一步缩小，不能扩大。分页使用PageQuery.normalized，订单列表稳定按orderCreatedAt、orderId排序；payments/refunds有orderId/申请筛选与独立状态，detail只读组合四类投影。

TradeDetailDTO分别返回originalOrder、payments、refunds、refundSummary及snapshotAt/sourceVersions；净收款可派生，不回写订单paid。列表不声称是实时资金裁定；客户端需要立即确认付款时调用权威paymentProgress，不因投影滞后自动重收。

## 8. 事件接收与通知协议

### 8.1 内部可靠事件

```java
public record PayEventEnvelope<T>(
        String deliveryId, String businessEventId, String producer,
        String eventType, int schemaVersion, String aggregateId,
        long aggregateVersion, OffsetDateTime occurredAt, T data) {}
```

deliveryId取框架OutboxEvent.id；businessEventId是一次领域事实的稳定ID；type是独立目标投递类型。tenant不由公开Command任填，内部wire中有tenant声明时必须与经过认证的服务授权一致。

| 独立投递type | 载荷关键字段 | 消费效果 |
|---|---|---|
| `pay.acquire.payment.execute.v1` | PaymentExecuteCommand | payment接受同paymentId意图 |
| `pay.acquire.parent.fence.v1` | orderId、payee、closeEpoch | payment先存父门禁 |
| `pay.payment.outcome.acquire.v1` | payment/allocation/order、固定金额币种、完整state/version、证据 | acquire逐笔消耗/释放；中间UNKNOWN只更新观察信息 |
| `pay.payment.outcome.limit.v1` | operationId、成功或最终无收款风险事实 | Confirm或Release原桶 |
| `pay.payment.snapshot.realtime.v1` | 完整单笔付款快照 | 单payment版本CAS |
| `pay.acquire.snapshot.realtime.v1` | 完整订单/金额快照 | 单order版本CAS |
| `pay.payment.refund.realtime.v1` | 子退款完整快照 | 单refund版本CAS |
| `pay.payment.refund-summary.realtime.v1` | orderId、完整退款累计、refundSummaryVersion | 独立退款汇总CAS |
| `pay.payment.refund.limit.v1` | 成功子退款及原operation、原付款累计退款额 | 可配置恢复原桶 |
| `pay.payment.refund.fee.v1` | 完整order退款累计、summaryVersion、原条款关联 | acquire只调整独立费用，不写订单 |
| `pay.payment.outcome.risk.v1` | 咨询关联和真实资金结果 | 后置结果确认，不参与订单/额度事务 |

原始渠道回调不是内部可信事件。PaymentOutcome标记可释放必须同时有持久gate和确定无收款风险证据，不能把某个attempt失败简单翻译成整个PaymentOrder FAILED。consumer按deliveryId Inbox去重，再按aggregateVersion/业务状态检查；消费失败回滚去重记录。

聚合间无全序。退款恢复先到、付款确认未到时保持重试；订单投影和退款投影版本分别比较。需要支付事实重放时保持businessEventId和原ID，不生成新的资金操作。

### 8.2 商户业务通知

| eventType | 来源/聚合 | 商户含义 |
|---|---|---|
| PaymentSucceeded | acquire应用该payment成功后，聚合paymentId/来源paymentVersion | 本笔成功，不能据此认定整单付清 |
| OrderFullyPaid | acquire，orderId/orderVersion | 累计恰等于应收，可按商户业务履约 |
| OrderClosed | acquire，orderId/orderVersion | 正常关闭，可含部分已收；不是退款成功 |
| RefundSucceeded / RefundFailed | payment，refundId/refundVersion | 单子退款结果 |
| RefundRequestCompleted | payment，refundRequestId/requestVersion | 固定申请已收敛，可部分成功 |

同成功事务既可能产生PaymentSucceeded又产生OrderFullyPaid；两个不同eventId/type，不保证哪一个先送达。通知data明确event涉及对象并含orderId/paymentId/refundId、金额币种、订单已付汇总（若有）和状态。版本只在同producer/aggregateType/aggregateId内比较。

固定body示例（省略仅为可读性的非必需字段）：

```json
{
  "eventId": "E1001", "eventType": "PaymentSucceeded", "schemaVersion": 1,
  "producer": "acquire", "aggregateType": "PaymentOrder", "aggregateId": "P1001",
  "aggregateVersion": 3, "occurredAt": "2026-10-20T12:00:00+08:00",
  "data": {"orderId": "O1001", "paymentId": "P1001", "payeeMerchantId": "M1001",
    "amountMinor": 3000, "currency": "CNY", "orderPaidAmountMinor": 3000,
    "orderAmountMinor": 10000, "orderState": "PENDING"}
}
```

商户必须同时返回HTTP 2xx和精确语义ACK `{"eventId":"E1001","result":"SUCCESS"}`；任一不满足则未确认。eventId不匹配、body无法解析、空body或只是HTTP 200均重试。商户在自己的事务内先去重/更新业务再ACK；已经处理的同eventId返回同ACK。

### 8.3 签名与重试

复用LoadUp RequestSigner/Verifier的`loadup-request-v1`，按框架规范签method、原始path/query、Content-Type、原始body摘要、app/audience/key-version、timestamp、nonce。每次投递使用同一body字节/eventId，重新生成timestamp/nonce/签名；不能重用nonce，也不能重新序列化body后发送。通知body的occurredAt不因重试改变。

keyId语义在传输层由受信app/audience到KMS密钥名称/版本映射表达；密钥名称和私钥不从payload任意选。使用框架规定的X-Key-Version等签名头，不另造与组件不兼容的X-Key-Id协议。商户接收方按签名头与事先绑定的验签公钥配置验证，先验签/防重放再处理业务。

target/签名引用/超时/退避/最大窗口来自原冻结通知策略。独立notification_delivery保存固定body和语义ACK状态，DelayedTask负责合约要求的通知退避；Outbox只可靠投递到此投递状态机/唤醒入口，不把框架默认有限重试次数当作完整商户重试窗口。通知任务bizId含deliveryId和attemptSequence，达到上限记EXHAUSTED并可审计重放原eventId。

通知目标按product审核版本冻结。目标更改只影响新单；历史补发至新目标需要显式授权迁移并留审计，不能直接用请求URL覆盖。冻结的签名密钥版本保留至对应重试窗口结束；紧急吊销后的迁移单独受控，不通过悄悄换收款身份或改eventId来恢复。

通用gotone webhook若只判断HTTP 2xx不能直接作为支付通知成功判据；可复用HTTP/签名基础能力，支付的语义ACK、业务幂等和窗口由收单平台投递状态机负责。

## 9. gRPC映射与错误

服务间采用LoadUp gRPC binder：generated BindableService Bean + GrpcChannelFactory + `spring.grpc.*`，DNS目标如`static://payment.loadup-pay.svc.cluster.local:9090`。client Java record与proto分别维护，由MapStruct/适配器转换，不以proto取代domain模型。pay-contract按服务package分proto，不建包含所有服务Java Facade的共享领域大包。

示例wire骨架（其余Command按上文同名字段类型化展开）：

```protobuf
syntax = "proto3";
package loadup.pay.payment.v1;
option java_multiple_files = true;
option java_package = "io.github.loadup.pay.contract.payment.v1";
import "google/protobuf/timestamp.proto";

service PaymentExecution {
  rpc Accept(PaymentExecute) returns (PaymentReply);
  rpc Detail(PaymentIdQuery) returns (PaymentReply);
  rpc FenceParent(ParentFenceCommand) returns (ParentFenceReply);
}
message PaymentExecute {
  string payment_id = 1;
  string order_id = 2;
  string allocation_id = 3;
  string admission_id = 4;
  string payee_merchant_id = 5;
  int64 amount_minor = 6;
  string currency = 7;
  string request_digest = 8;
  string channel = 9;
  string payment_method = 10;
  optional string prepared_input_ref = 11;
  AdmittedTerms admitted_terms = 12;
}
message AdmittedTerms {
  string contract_id = 1;
  int32 revision = 2;
  string snapshot_hash = 3;
  int32 schema_version = 4;
  bytes compiled_terms_json = 5;
  string compiled_terms_hash = 6;
}
message PaymentIdQuery { string id = 1; }
message BusinessResult { string code = 1; string message = 2; }
message PaymentReply {
  BusinessResult result = 1;
  optional PaymentView data = 2;
}
message PaymentView {
  string payment_id = 1;
  string order_id = 2;
  string state = 3;
  int64 amount_minor = 4;
  string currency = 5;
  int64 version = 6;
  google.protobuf.Timestamp initiated_at = 7;
}
message ParentFenceCommand {
  string order_id = 1;
  string payee_merchant_id = 2;
  int64 close_epoch = 3;
  string request_key = 4;
}
message ParentFenceReply {
  BusinessResult result = 1;
  bool fenced = 2;
  bool all_children_converged = 3;
  int32 unknown_count = 4;
  int64 close_epoch = 5;
}
```

JSON terms是不可变、限长、schemaVersion+hash校验的配置快照，不是开放给调用者的任意事实Map。proto string枚举值需显式校验；若落地使用enum，0必须UNSPECIFIED并拒绝，不能把缺省0当作INIT已受理。字段号不可复用，删除字段reserved；breaking change换v2 package。

身份认证/授权失败映射UNAUTHENTICATED/PERMISSION_DENIED；不可解析请求INVALID_ARGUMENT；网络UNAVAILABLE/DEADLINE_EXCEEDED仍是传输错误。已处理的业务拒绝通过BusinessResult明确返回code，不让SDK把所有异常都重试扣款。HTTP成功使用框架SuccessResponse，失败FailureResponse；渠道原生回调走独立协议ACK，不能强套商户JSON envelope。

| 业务码示例 | 含义 | 调用方动作 |
|---|---|---|
| ACQUIRE_IDEMPOTENCY_CONFLICT | 同业务key参数变化 | 修正调用，不自动换key重收 |
| ACQUIRE_CONTRACT_NOT_ADMITTING | 新单无当前准入 | 拒绝新单；原单续付不使用此校验 |
| ACQUIRE_ORDER_NOT_PAYABLE | CLOSING/终态或原等待策略停止付款 | 查询并展示，不能重开 |
| ACQUIRE_AMOUNT_EXCEEDED | 超出未占用剩额 | 重新查金额，用户明确新动作才发起 |
| PAYMENT_OUTCOME_UNKNOWN | 请求新尝试时旧尝试仍可能收款；普通查询UNKNOWN仍为正常data | 查原paymentId，保持占用 |
| PAYMENT_EXECUTION_FENCED | 父/子持久关闭门禁 | 不再提交 |
| LIMIT_EXCEEDED / LIMIT_POLICY_NOT_ACTIVE | 额度不足/未激活 | 无渠道提交；原intent按可恢复性处理 |
| LIMIT_DEPENDENCY_NOT_READY | 退款恢复等待原Confirm | 相同事实可靠重投 |
| PAYMENT_REFUND_AMOUNT_EXCEEDED | 成功可退容量不足 | 不修改计划重试其他原付款 |
| COMMON_DEPENDENCY_UNAVAILABLE | 上游暂不可用且未作允许决定 | 查原ID/同key恢复，不把超时当未扣款 |

Acquire/payment业务受理后不使用短deadline取消作为释放依据。每跳设置自己的有界deadline，扣款POST禁用盲目自动重试；固定请求号查证和原key幂等重试由业务控制。服务端不持数据库锁等待RPC。

## 10. 风控与对账预留

domain只预留`RiskConsultGateway.consult(RiskConsultation)`与`RiskOutcomeGateway.confirm(RiskOutcome)`。最小关联为operation/order/payment/attempt、payee、金额币种、选择渠道、consultationRef、真实结果及来源版本。未接入/超时/不可用记录DEFAULT_ALLOW和真实原因；外部明确DENY与默认放行分开。结果落库后通过独立Outbox确认，不能阻塞渠道ACK。外部厂商字段、协议和审核流程后置，本期不定义消费者风险资源。

reconciliation后置消费独立事件镜像/受控权威快照。预留`SubmitEvidenceForVerification`用例时只允许提交来源和证据引用，由payment验证/主动查证后做自己的状态迁移；不提供UpdatePaymentStatus、直接退款或改额度接口。账单文件schema、导入和差错审批在该服务实施时另写详细设计。

本稿的API示例用于契约评审，未生成SDK/proto或运行接口测试。真实渠道JSAPI/银联细节在适配材料确定后按类型扩展，不影响以上订单、付款、退款和额度契约。
