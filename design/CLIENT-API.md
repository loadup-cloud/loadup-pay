# Client API 详细设计

版本：v1.0 ｜ 日期：2026-10-20 ｜ 对应[详细设计](../DETAILED-DESIGN.md)与[数据库设计](DATABASE.md)

2026-10-21：P1 Java
Facade、Command/Query/DTO、错误码、条款校验、授权撤销和完整结果/投影事件已落源码，补充差异见第11节及[实施说明](../IMPLEMENTATION.md)
。本文保留目标协议；HTTP/业务gRPC适配及Facade实现尚未完成，真实渠道原生Action参数后续扩展。

本文为待实现的业务契约。`client`
指COLA公开Java业务接口；HTTP与gRPC是其适配。示例省略package/import及重复的Schema/toString，不表示已有可编译SDK。框架已有接口保持原名，下面收单平台新增接口不伪称为框架现成功能。

## 1. 全局契约

- 包：`io.github.loadup.pay.<service>.client.{facade,command,query,dto,error}`
  。DTO/Command/Query为record；Facade不依赖Spring、生成stub、Mapper或HTTP envelope。
- 认证层先建立可信tenant、principal、服务身份、授权动作及商户作用域；这些不是商户能在JSON里任填的字段。后台job和RPC服务以最小服务权限执行。所有payeeMerchantId仍需归属/授权校验。
- 公共入参只表达业务意图。持久结果中的orderId/paymentId/refundId/operationId由服务生成并保持稳定；跨服务内部Command可以接收权威上游分配的稳定ID。
- 金额：`amountMinor: long`，CNY为分；currency创建时可省略，规范化为CNY后计算幂等摘要，其余内部接口显式传币种。JSON金额用整数，浏览器使用安全整数校验/大整数方案，禁止以小数元直接提交。
- 时间：Java OffsetDateTime，HTTP RFC3339显式`+08:00`，DB转Asia/Shanghai的LocalDateTime；gRPC
  Timestamp只作绝对时间编码，业务归桶仍Asia/Shanghai。
- 幂等先检查已提交结果，再校验是否允许一个新的业务动作。相同key不同规范化摘要冲突；同key重试不能扩大金额/换收款人/增加子退款。
- 详情复用框架`IdQuery`；有附加筛选的Query独立命名。分页使用`PageDTO<T>`，Controller映射`PageResponse.of(page)`，顶层`data`及
  `pageInfo(totalCount,pageIndex,pageSize)`；不新建业务分页容器。
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

`PaymentInteractionDTO`是有封闭类型集合的交互参数，不是任意Map：NONE、PAYMENT_CODE（短期付款码）、CHANNEL_IDENTITY（当前渠道要求的授权交换票据）。HTTP用
`type`判别且拒绝多种输入并存；Java用sealed
interface及对应record。敏感record的toString仅返回类型/脱敏标志，校验异常不含原值。不能让浏览器自报一个跨渠道、跨应用可复用的payerId；服务端完成作用域验证与交换，不建设消费者档案。

当前支付方式枚举：PAYMENT_CODE、JSAPI、H5。Native 二维码与原生 APP 支付不在当前范围。渠道与方式是否可用以冻结合约、当前实际商户开通能力及输入前提共同判断；有枚举值不代表该渠道已完成适配。浏览器不提交bindingId、服务商号、子商户号、credentialRef或接入模式；服务端选路决定实际DIRECT/SERVICE_PROVIDER。

`requestedDueAt`只是商户申请值，服务端按原条款范围验证。回跳使用经审核的returnTargetCode，不接受任意URL。不接受商户直接指定contractRevision、feeAmount或quota
threshold。

### 2.2 幂等范围和完成语义

| 方法                      | 幂等身份                              | 摘要与语义                                                                                                        |
|---------------------------|---------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| create/checkout/directPay | tenant+payee+merchantOrderNo          | 入口类型、固定总额/币种/标题/产品scope/请求等待值，DirectPay还含首笔动作；不能同号把Checkout改成DirectPay再次首付 |
| startPayment              | tenant+orderId+actorScope+actionKey   | 手动金额、channel/method、输入的服务端keyed摘要；不允许同key改金额                                                |
| close/extend              | tenant+orderId+commandType+requestKey | 参数固定；close返回CLOSING表示已接受关闭，不表示渠道已确定关闭                                                    |
| session.create            | tenant+orderId+requestKey             | 返回相同session；重放过期session不自动续期，新的续发意图用新requestKey                                            |

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

| action.type  | data字段                             | 行为                                       |
|--------------|--------------------------------------|--------------------------------------------|
| NONE         | 无                                   | 无需交互，不代表成功                       |
| WAIT         | recommendedPollAfterMillis           | 查原paymentId，不能新建付款                |
| DISPLAY_QR   | qrContent                            | 展示固定本笔金额的二维码，不能改用于下一笔 |
| REDIRECT     | redirectUrl                          | 仅服务端渠道适配产出的受信地址             |
| INVOKE_JSAPI | channel、appId及各渠道类型化签名字段 | 只提供调用该渠道所需的公开短期参数         |
| INVOKE_APP   | channel及类型化原生参数              | 不包含商户长期私钥                         |

不提供一个要求SDK自行拼装渠道协议的通用payload字符串。JSAPI等具体字段跟随真实渠道产品，由adapter对应版本DTO定义；不虚构统一银联字段。

CashierOrderDTO是受限视图：orderId、商户显示名、标题、金额汇总、状态/付款进度、可用choice集合和会话到期；不暴露合约、费率、服务商凭证、内部gate和其他付款人标识。PaymentProgressDTO返回该笔权威结果及所属订单汇总，前端success回跳不参与成功认定。

### 2.4 HTTP接入示例

JSON业务Controller统一POST：

| URL                                                                 | Facade         | 身份                 |
|---------------------------------------------------------------------|----------------|----------------------|
| `/api/acquire/order/create`                                         | create         | 商户后端             |
| `/api/acquire/checkout/create`                                      | checkout       | 商户后端             |
| `/api/acquire/direct-pay`                                           | directPay      | 商户后端/POS可信后端 |
| `/api/acquire/payment/start`                                        | startPayment   | 商户后端             |
| `/api/acquire/order/detail`、`/close`、`/extend`                    | 对应方法       | 商户后端             |
| `/api/acquire/session/create`                                       | session.create | 商户后端             |
| `/api/cashier/order/summary`、`/payment/start`、`/payment/progress` | CashierFacade  | 订单scope token      |
| `/api/payment/refund/create`、`/detail`                             | RefundFacade   | 退款授权商户后端     |

托管页面`GET /checkout`是静态页面入口；URL
fragment携带受限token，页面取出后通过Authorization头传API，并清除敏感fragment，不把token放进渠道returnUrl或服务端access
log。若代理/渠道必须跳转，服务端根据已认证会话生成受限回跳关联，不复制长期商户凭证。

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

数据库只保存token_hash，不能依赖读取明文token实现Checkout重试。实现基线使用服务端版本化Secret密钥对固定sessionId/orderId/nonce/expiry生成HMAC-SHA256
token；nonce非秘密、与密钥引用/版本一起保存，token可按相同输入重建，数据库仍只有token摘要。该短期会话能力使用JCA与受管Secret即可，不能假定框架已经提供cashier
token类。

规范输入是UTF-8，以LF分行并保留末尾LF：`pay-checkout-v1`、sessionId、orderId、expiryEpochSeconds、tokenNonce。输出
`sessionId + "." + base64url_no_padding(HMAC)`
；入站恒定时间比较摘要、检查数据库状态和expiresAt，再建立该订单scope。sessionId可由框架IdGenerator生成，tokenNonce使用安全随机源的32字节并以Hex保存；HMAC密钥不下发浏览器或SDK。expiry对齐到秒；数据库记录确切同一值，不能每次重试重新计算到期时间。

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

`authorize`是持久业务命令，重复查询原准入不是重新做一次当前资格。新准入同时验证合约当前状态/区间、customer事实和limit
policy激活，普通停单与新准入在product本地串行。DENIED结果以明确decision/reason返回，不能将依赖不可用伪装成业务允许；依赖故障保持意图待恢复。

已有订单续付是acquire的`ValidateAdmittedOrder`
领域规则，输入本地原准入/条款/当前订单/新付款意图，不需要为每笔付款同步调用当前ContractResolveFacade。原合约停止不会撤销原INIT/PENDING；实际渠道授权失效/订单关闭/剩余额不足仍可拒绝。

框架MerchantFacade/QueryFacade、MerchantContractFacade、ContractResolveFacade用于已有资料/合约管理与配置解析。新增OrderAdmission不套在当前resolve外面假定获得原子停单；必须与合约状态修改共用product本地锁和事务。新接口先在收单平台product中实现，只有形成通用能力后再考虑回沉框架。

## 4. payment：执行与关闭的内部client

```java
public interface PaymentExecutionFacade {
    PaymentExecutionDTO accept(PaymentExecuteCommand command);
    PaymentExecutionDTO detail(IdQuery query);
    PaymentCloseDTO close(PaymentCloseCommand command);
    ParentFenceDTO fenceParent(ParentPaymentFenceCommand command);
    ParentFenceDTO parentFenceStatus(ParentFenceStatusQuery query);
}

public record PaymentExecuteCommand(
        String paymentId, String orderId, String allocationId, String admissionId,
        String payeeMerchantId, long amountMinor, String currency,
        String requestDigest, PaymentChoiceDTO choice, String preparedInputRef,
        AdmittedOrderTermsDTO admittedTerms) {}

public record PaymentCloseCommand(
        String paymentId, String orderId, String payeeMerchantId,
        String requestKey, long fenceEpoch, String reasonCode) {}

public record ParentPaymentFenceCommand(
        String orderId, String payeeMerchantId, long closeEpoch, String requestKey) {}

public record ParentFenceStatusQuery(String orderId, long closeEpoch) {}

public record ParentFenceDTO(
        String orderId, long closeEpoch, boolean fenced,
        boolean allChildrenConverged, int unknownCount) {}
```

AdmittedOrderTermsDTO含contractId/revision/原snapshotHash/compiledTermsHash、固定order总额、原退款/通知/限额配置等强类型必要子DTO，payload有schemaVersion；由可信acquire传递并校验绑定摘要，不能从公网上原样映射为已信任事实。PaymentExecutionDTO含payment/order/allocation、金额、state、activeAttempt、action、version和终结证据引用；不返回敏感input/credential。

| 内部操作          | 关键语义                                                                                              |
|-------------------|-------------------------------------------------------------------------------------------------------|
| accept            | paymentId唯一；同ID异digest拒绝；先门禁再持久化，返回已接受不保证已向渠道提交                         |
| close             | 没有PaymentOrder也先持久化单笔tombstone；返回CLOSING/最终结果，不能将“没查到本地付款”直接当成安全释放 |
| fenceParent       | 持久化父gate，拒绝迟到新paymentId；closeEpoch单调，低代次幂等不覆盖高代次                             |
| parentFenceStatus | fenced不等于allChildrenConverged；只有所有已有付款已最终收敛才允许acquire正常关闭                     |

敏感输入准备使用payment内部`PaymentInputPrepareCommand(paymentId,orderId,allocationId,payeeMerchantId,amountMinor,currency,inputKey,choice,interaction)`及DTO
(inputRef,inputDigest,expiresAt)，只对可信acquire开放。业务重试复用inputKey；同key不同敏感值冲突。该接口不触发渠道提交，不得把inputRef当成任意付款均可使用的bearer凭证。

原paymentId更换渠道/方式或新的付款码是
`PaymentAttemptStartCommand(paymentId,requestKey,choice,preparedInputRef,previousAttemptEvidenceId)`
：首期仅在旧attempt确定安全终结且付款仍允许新尝试时开放，结果未知必须拒绝；先按requestKey返回原尝试，再锁payment创建新attempt。普通超时重试accept不会新建attempt。若产品首期不对外开放换方式，则保留该内部用例，商户需安全关闭原付款后用新action发起，不用一个通用retry按钮盲目扣款。

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
        String policyId, int policyVersion, String policyHash, String requestDigest) {}

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

Release早于Reserve时也创建operation
gate的FENCED记录，返回RELEASED（reservationId可为空）；后到Reserve/Begin返回EXECUTION_FENCED，不能把一次NOT_FOUND
ACK当成已防住迟到申请。与已确认成功矛盾的Release拒绝自动释放并进入查证。

成功退款恢复笔数需payment完整累计退款额达到原付款金额，且原item最多恢复1。规则按原reservation/item解释，退款发生月不能替代原付款月。

规则发布为另一个仅product可调的`LimitPolicyFacade.activate(LimitPolicyActivateCommand)`
：绑定policyId/version/hash与编译规则；同版本异hash冲突，持久化后返回activationReceipt。阈值更新用稳定ruleScope，不能以新policyVersion生成新累计范围。

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

| 独立投递type                             | 载荷关键字段                                                    | 消费效果                                        |
|------------------------------------------|-----------------------------------------------------------------|-------------------------------------------------|
| `pay.acquire.payment.execute.v1`         | PaymentExecuteCommand                                           | payment接受同paymentId意图                      |
| `pay.acquire.parent.fence.v1`            | orderId、payee、closeEpoch                                      | payment先存父门禁                               |
| `pay.payment.outcome.acquire.v1`         | payment/allocation/order、固定金额币种、完整state/version、证据 | acquire逐笔消耗/释放；中间UNKNOWN只更新观察信息 |
| `pay.payment.outcome.limit.v1`           | operationId、成功或最终无收款风险事实                           | Confirm或Release原桶                            |
| `pay.payment.snapshot.realtime.v1`       | 完整单笔付款快照                                                | 单payment版本CAS                                |
| `pay.acquire.snapshot.realtime.v1`       | 完整订单/金额快照                                               | 单order版本CAS                                  |
| `pay.payment.refund.realtime.v1`         | 子退款完整快照                                                  | 单refund版本CAS                                 |
| `pay.payment.refund-summary.realtime.v1` | orderId、完整退款累计、refundSummaryVersion                     | 独立退款汇总CAS                                 |
| `pay.payment.refund.limit.v1`            | 成功子退款及原operation、原付款累计退款额                       | 可配置恢复原桶                                  |
| `pay.payment.refund.fee.v1`              | 完整order退款累计、summaryVersion、原条款关联                   | acquire只调整独立费用，不写订单                 |
| `pay.payment.outcome.risk.v1`            | 咨询关联和真实资金结果                                          | 后置结果确认，不参与订单/额度事务               |

原始渠道回调不是内部可信事件。PaymentOutcome标记可释放必须同时有持久gate和确定无收款风险证据，不能把某个attempt失败简单翻译成整个PaymentOrder
FAILED。consumer按deliveryId Inbox去重，再按aggregateVersion/业务状态检查；消费失败回滚去重记录。

聚合间无全序。退款恢复先到、付款确认未到时保持重试；订单投影和退款投影版本分别比较。需要支付事实重放时保持businessEventId和原ID，不生成新的资金操作。

### 8.2 商户业务通知

| eventType                      | 来源/聚合                                                    | 商户含义                             |
|--------------------------------|--------------------------------------------------------------|--------------------------------------|
| PaymentSucceeded               | acquire应用该payment成功后，聚合paymentId/来源paymentVersion | 本笔成功，不能据此认定整单付清       |
| OrderFullyPaid                 | acquire，orderId/orderVersion                                | 累计恰等于应收，可按商户业务履约     |
| OrderClosed                    | acquire，orderId/orderVersion                                | 正常关闭，可含部分已收；不是退款成功 |
| RefundSucceeded / RefundFailed | payment，refundId/refundVersion                              | 单子退款结果                         |
| RefundRequestCompleted         | payment，refundRequestId/requestVersion                      | 固定申请已收敛，可部分成功           |

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

商户必须同时返回HTTP 2xx和精确语义ACK `{"eventId":"E1001","result":"SUCCESS"}`
；任一不满足则未确认。eventId不匹配、body无法解析、空body或只是HTTP 200均重试。商户在自己的事务内先去重/更新业务再ACK；已经处理的同eventId返回同ACK。

### 8.3 签名与重试

复用LoadUp RequestSigner/Verifier的`loadup-request-v1`
，按框架规范签method、原始path/query、Content-Type、原始body摘要、app/audience/key-version、timestamp、nonce。每次投递使用同一body字节/eventId，重新生成timestamp/nonce/签名；不能重用nonce，也不能重新序列化body后发送。通知body的occurredAt不因重试改变。

keyId语义在传输层由受信app/audience到KMS密钥名称/版本映射表达；密钥名称和私钥不从payload任意选。使用框架规定的X-Key-Version等签名头，不另造与组件不兼容的X-Key-Id协议。商户接收方按签名头与事先绑定的验签公钥配置验证，先验签/防重放再处理业务。

target/签名引用/超时/退避/最大窗口来自原冻结通知策略。独立notification_delivery保存固定body和语义ACK状态，DelayedTask负责合约要求的通知退避；Outbox只可靠投递到此投递状态机/唤醒入口，不把框架默认有限重试次数当作完整商户重试窗口。通知任务bizId含deliveryId和attemptSequence，达到上限记EXHAUSTED并可审计重放原eventId。

通知目标按product审核版本冻结。目标更改只影响新单；历史补发至新目标需要显式授权迁移并留审计，不能直接用请求URL覆盖。冻结的签名密钥版本保留至对应重试窗口结束；紧急吊销后的迁移单独受控，不通过悄悄换收款身份或改eventId来恢复。

通用gotone webhook若只判断HTTP 2xx不能直接作为支付通知成功判据；可复用HTTP/签名基础能力，支付的语义ACK、业务幂等和窗口由收单平台投递状态机负责。

## 9. gRPC映射与错误

服务间计划采用LoadUp gRPC binder：generated BindableService Bean + GrpcChannelFactory + `spring.grpc.*`
，DNS目标与端口在业务适配时配置。client Java
record与proto分别维护，由MapStruct/适配器转换，不以proto取代domain模型。协议归提供方的业务适配，不向业务client添加gRPC依赖，也不预生成空协议模块。测试Ping及其协议模块已删除，当前没有RPC接口；以下业务proto示例、stub与适配仍待P2实现。

示例wire骨架（其余Command按上文同名字段类型化展开）：

```protobuf
syntax = "proto3";
package loadup.pay.payment.v1;
option java_multiple_files = true;
option java_package = "io.github.loadup.pay.payment.grpc.v1";
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

JSON terms是不可变、限长、schemaVersion+hash校验的配置快照，不是开放给调用者的任意事实Map。proto
string枚举值需显式校验；若落地使用enum，0必须UNSPECIFIED并拒绝，不能把缺省0当作INIT已受理。字段号不可复用，删除字段reserved；breaking
change换v2 package。

身份认证/授权失败映射UNAUTHENTICATED/PERMISSION_DENIED；不可解析请求INVALID_ARGUMENT；网络UNAVAILABLE/DEADLINE_EXCEEDED仍是传输错误。已处理的业务拒绝通过BusinessResult明确返回code，不让SDK把所有异常都重试扣款。HTTP成功使用框架SuccessResponse，失败FailureResponse；渠道原生回调走独立协议ACK，不能强套商户JSON
envelope。

| 业务码示例                               | 含义                                                      | 调用方动作                         |
|------------------------------------------|-----------------------------------------------------------|------------------------------------|
| ACQUIRE_IDEMPOTENCY_CONFLICT             | 同业务key参数变化                                         | 修正调用，不自动换key重收          |
| ACQUIRE_CONTRACT_NOT_ADMITTING           | 新单无当前准入                                            | 拒绝新单；原单续付不使用此校验     |
| ACQUIRE_ORDER_NOT_PAYABLE                | CLOSING/终态或原等待策略停止付款                          | 查询并展示，不能重开               |
| ACQUIRE_AMOUNT_EXCEEDED                  | 超出未占用剩额                                            | 重新查金额，用户明确新动作才发起   |
| PAYMENT_OUTCOME_UNKNOWN                  | 请求新尝试时旧尝试仍可能收款；普通查询UNKNOWN仍为正常data | 查原paymentId，保持占用            |
| PAYMENT_EXECUTION_FENCED                 | 父/子持久关闭门禁                                         | 不再提交                           |
| LIMIT_EXCEEDED / LIMIT_POLICY_NOT_ACTIVE | 额度不足/未激活                                           | 无渠道提交；原intent按可恢复性处理 |
| LIMIT_DEPENDENCY_NOT_READY               | 退款恢复等待原Confirm                                     | 相同事实可靠重投                   |
| PAYMENT_REFUND_AMOUNT_EXCEEDED           | 成功可退容量不足                                          | 不修改计划重试其他原付款           |
| COMMON_DEPENDENCY_UNAVAILABLE            | 上游暂不可用且未作允许决定                                | 查原ID/同key恢复，不把超时当未扣款 |

Acquire/payment业务受理后不使用短deadline取消作为释放依据。每跳设置自己的有界deadline，扣款POST禁用盲目自动重试；固定请求号查证和原key幂等重试由业务控制。服务端不持数据库锁等待RPC。

## 10. 风控与对账预留

domain只预留`RiskConsultGateway.consult(RiskConsultation)`与`RiskOutcomeGateway.confirm(RiskOutcome)`
。最小关联为operation/order/payment/attempt、payee、金额币种、选择渠道、consultationRef、真实结果及来源版本。未接入/超时/不可用记录DEFAULT_ALLOW和真实原因；外部明确DENY与默认放行分开。结果落库后通过独立Outbox确认，不能阻塞渠道ACK。外部厂商字段、协议和审核流程后置，本期不定义消费者风险资源。

reconciliation后置消费独立事件镜像/受控权威快照。预留`SubmitEvidenceForVerification`
用例时只允许提交来源和证据引用，由payment验证/主动查证后做自己的状态迁移；不提供UpdatePaymentStatus、直接退款或改额度接口。账单文件schema、导入和差错审批在该服务实施时另写详细设计。

本稿的API示例用于契约评审，未生成SDK/proto或运行接口测试。真实渠道JSAPI/银联细节在适配材料确定后按类型扩展，不影响以上订单、付款、退款和额度契约。

## 11. P1源码契约补充

以下为本阶段已落源码的接口；不表示已经注册业务实现、对外发布或具备数据库事务。

```java
public interface TradeProjectionFacade {
    void applyOrder(PayEventEnvelope<OrderSnapshotEventDTO> event);
    void applyPayment(PayEventEnvelope<PaymentSnapshotEventDTO> event);
    void applyRefund(PayEventEnvelope<RefundSnapshotEventDTO> event);
    void applyRefundSummary(PayEventEnvelope<OrderRefundSummaryEventDTO> event);
}
```

| 载荷                       | 完整快照中的主要事实                                                                                           |
|----------------------------|----------------------------------------------------------------------------------------------------------------|
| OrderSnapshotEventDTO      | order/payee/merchantOrderNo、原总额币种、gross paid/held、state、orderCreatedAt/sourceUpdatedAt、sourceVersion |
| PaymentSnapshotEventDTO    | payment/order/payee、原付款金额币种、state、channel/accessMode、initiatedAt/succeededAt、sourceVersion         |
| RefundSnapshotEventDTO     | refund/request/payment/order/payee、原退款金额币种、state、refundCreatedAt/succeededAt、sourceVersion          |
| OrderRefundSummaryEventDTO | order/payee/currency、refunded/held、独立summaryVersion、完整requests进度列表                                  |

不再用商户查询DTO替代这些投影事件。RefundOutcomeEventDTO专供额度恢复事实，不能充当缺少原创建/成功时间的退款完整快照。RefundRequestSummaryDTO包含申请/商户退款号、申请额、state、成功/失败额、unknownCount/version；各申请成功额之和等于refunded，各未收敛额之和等于refundReserved。列表是完整替换快照，不能发送增量列表或空列表却声明非零累计。

快照的`requireEnvelope`
核对载荷、生产方、固定投递type、schemaVersion=1、聚合ID和版本；结果事件也提供相应检查。入站适配必须另行核验经过认证的服务身份/租户，app在本地事务中处理Inbox与版本CAS。信封中的producer和业务Evidence本身不授予可信权限。

realtime的TradeOrderDTO改为独立扁平读模型，保留原订单/商户/金额/状态/创建时间、退款额、净收款和各来源版本；不从投影伪造
`canAcceptNewPayment`或权威`availableToPay`。TradeDetailDTO增加独立完整refundSummary。立即付款决策仍读取acquire/payment权威接口。

```java
public interface MerchantAuthorizationFacade {
    MerchantRelationshipDTO createRelationship(MerchantRelationshipCreateCommand command);
    MerchantActionGrantDTO grant(MerchantActionGrantCommand command);
    MerchantRelationshipDTO revokeRelationship(MerchantRelationshipRevokeCommand command);
    MerchantActionGrantDTO revokeGrant(MerchantActionGrantRevokeCommand command);
}

// Both revoke commands contain id, requestKey and expectedVersion.
```

关系/授权输出`GrantState(INIT, ACTIVE, REVOKED)`
，替代丢失生命周期的active布尔值。代操作校验还要求可信principal的所属商户匹配关系parentMerchantId；不能在公开Command中自填该所属商户。撤销后不可激活原记录；如需重新授权，另建明确的新授权。

条款DTO补充构造时的跨字段约束：wait≤maxWait，关闭延期时累计上限为0，开启时上限为正，付款min≤max，费用minimum≤maximum，通知启用时须配置目标/签名引用且重试倍率≥1。输入/结果事件也验证金额、币种、版本和终结证据。DomainViolation仍由后续app/adapter映射至对应模块稳定ResultCode，不能把原始领域异常直接作为HTTP响应。

安全换attempt时，limitcenter领域入口接收可信的LimitAttemptEvidence，匹配operation/payment/旧attempt/旧epoch后才能递增epoch。该证据由payment事实适配获得，不在公网提供可自填的“已封禁”开关；原LimitBeginExecutionCommand只传previousAttemptEvidenceId。

## 12. Customer 首批业务适配

既有 MerchantAuthorizationFacade 与 MerchantEligibilityFacade 已增加 app/数据库/HTTP 实现，入口、权限、JWT
claim、幂等和运行配置见[customer说明](../customer/README.md)。HTTP 路径统一在 `/api/customer` 下，Controller 复用本章
Command。管理动作要求 `pay:customer:manage`；资格调用只判断可信当前主体，不接受请求体指定调用主体。Java client
仍与HTTP/gRPC传输解耦。

本批尚未执行编译、单测或真实数据库/JWT业务验收；实际Flyway仅覆盖customer新增两表，其他领域DDL仍为设计草案。

## 13. Product 首批持久准入适配

OrderAdmissionFacade 已新增应用编排、数据库Gateway与 POST `/api/product/admissions/authorize`、`/detail` 适配。授权/查询分别要求
pay:product:admit、pay:product:
read，detail的IdQuery指admissionId，均限定原调用主体范围。完整命令字段单独冻结，不只相信requestDigest；原决定先回放，DENIED不会因后来开通合约悄悄变成ADMITTED。

requestDigest收紧为64位小写SHA-256；AcquiringTermsDTO增加routingProfileRef和returnTargetCode，LimitPolicyRefDTO增加parentPolicyRef。DTO与源码尚未发布为外部业务协议，实际字段以client源码为准。条款编译、凭证委托、业务装配和未执行的验收见[product说明](../product/README.md)。

## 14. LimitCenter 激活/预占源码适配

新增 LimitReservationFacade（reserve/detail），MerchantLimitFacade 继承该接口并保留未来执行生命周期契约；本批只注册前者的实现。实际三个
POST 入口、独立权限和 SERVICE JWT 契约见[limitcenter说明](../limitcenter/README.md)。不是商户可直接调用的内部接口。

LimitPolicyActivateCommand 增加 payeeMerchantId、refundAmountRestore、refundCountRestore，rules最多64条；policyHash 收紧64位小写
SHA-256，通过 client.LimitPolicyHash 的规范化算法计算并校验可信租户。LimitReserveCommand 增加冻结的
policyHash，requestDigest 同样收紧；重放逐项校验完整意图，不只比较摘要。client源码尚未发布为外部业务协议。

当前稳定规则形状只允许修订阈值/门禁及退款恢复解释，不允许直接增删/改名或改历法；父级共享规则、可信付款事实与结果处理继续后续批次，执行许可的新增适配见第15节。原始受理时间由
payment 的可信服务身份提供。新增接口与测试源码尚未编译或运行。

## 15. LimitCenter 执行许可与显式过期适配

新增
LimitExecutionFacade（beginExecution、verifyExecution、expire）；MerchantLimitFacade继承预占与执行两个接口，当前分别有Spring实现，完整MerchantLimitFacade仍不注册。POST
/api/limitcenter/executions/begin、/executions/verify、/reservations/expire，分别要求pay:limit:execute、pay:limit:
verify、pay:limit:expire以及原SERVICE归属。

LimitExecutionVerifyCommand绑定operationId/paymentId/attemptId/executionEpoch/amountMinor/currency/permit，permit为隐藏诊断的64位十六进制串；输出LimitExecutionVerificationDTO
(valid,reasonCode)
。LimitExpireCommand只含operationId，由服务端时间和持久状态决定是否释放，不接受客户端指定“已过期/未扣款”或桶列表。expire返回当前ReservationDTO，只有EXPIRED代表本次未执行预占已归还；NOT_FOUND不作无扣款证明。

同attempt/requestKey的Begin重放返回相同epoch/permit，不检查已经过去的预占TTL；不同attempt没有可信payment事实时返回OUTCOME_UNKNOWN。verify只表示当前观察，payment仍须本地Y2
CAS，不构造跨库原子承诺。许可与密钥轮换见[limitcenter说明](../limitcenter/README.md)。本批新增源码与单测尚未编译或执行。

## 16. Product 初始额度发布适配

新增LimitPolicyPublicationFacade.publish/detail/replay及/api/product/limit-publications下同名POST入口，分别要求pay:
product:publish、pay:product:publication:read、pay:product:publication:
replay。仅接受可信OPERATOR/SERVICE，主体最长64字符、SERVICE的sub必须等于service_id，发布租户必须匹配出站凭证租户。

LimitPolicyPublishCommand固定payeeMerchantId、productScopeKey、expectedContractId、expectedRevision=1、expectedSnapshotHash，不能提交任意阈值或ACTIVE开关。detail使用IdQuery
(publicationId)
，replay接受publicationId与最长512字符审核理由，只重放FAILED事件。DTO分别返回本地state与deliveryStatus/attempts，PUBLISHING+FAILED不代表已激活。

冻结激活命令与来源同事务写入并发框架Outbox；ACK只从服务身份HTTP成功响应获取，核对policy/version/hash后短事务落ACTIVE，重试不换payload/event。背景凭证使用挂载文件轮换，不保存操作者JWT。初始单revision/单商户绑定、迁移兼容与未验收范围见[product说明](../product/README.md)。


## 17. Acquire 首批普通创单适配

AcquireOrderCreationFacade 已实现 create/detail，完整 AcquireOrderFacade 继承该切片。POST `/api/acquire/orders/create`、`/detail` 分别要求 pay:acquire:create/read，并限定签名原调用主体范围；detail 的 IdQuery 为 orderId。原 JWT 需具备 acquire/product/customer 的 audience 与权限，product 的 customer 授权仍以原主体执行。

create 以 tenant+payee+merchantOrderNo 固定 ORDER 意图，固定字段 UTF-8 长度分帧摘要协议 v1 包含原主体范围、总额/币种、标题、产品scope和原请求截止时间。相同命令复用 A0 分配的 orderId；改参冲突，换主体拒绝。网络调用位于事务外，依赖失败保留 INIT；原准入 DENIED/首次落单超 materializeBefore 分别提交 REJECTED/EXPIRED，之后同号重试固定拒绝。A1订单/快照/INIT整单费用/COMPLETED在同一数据库事务完成，完成后回放不再请求当前合约。

当前不注册全量 AcquireOrderFacade 实现，不暴露 directPay/startPayment/close/extend 占位接口；Checkout 已按第19节实现。自动关单、事件、付款结果和真正收款验收仍待实施。运行和证据边界见[acquire说明](../acquire/README.md)。


## 18. 原单 Checkout 会话与只读 Cashier 首批适配

POST `/api/acquire/session/create` 实现 CheckoutSessionFacade.create，要求原商户主体和 pay:acquire:session；POST `/api/cashier/order/summary` 实现 CashierSummaryFacade.summary，依赖 Authorization 会话能力及 X-Checkout-Tenant 定位值，IdQuery 必须属于已验证 scope。GET `/checkout` 与两项本地静态资源提供同源只读页面。完整 CashierFacade 继承摘要切片，尚未注册 startPayment/paymentProgress 实现。

独立续发使用 create→session.create 两步；CheckoutCreateCommand 一步创单/A1同事务签发见第19节。签发按原订单/冻结条款进行，普通合约停单不重新判定；同 requestKey 返回同 session/token/expiry，不因过期、取消或订单终态而重开会话；新 requestKey 只允许仍可续付订单。returnTargetCode 为原合约默认或同一审核 code，不能指定任意 URL。

scope 版本1仅 ORDER_SUMMARY。opaque token 沿用2.5 HMAC协议，nonce/密钥引用和版本持久化，固定期限按秒对齐。恒定时间匹配原摘要并检查 ACTIVE/COMPLETED、严格未过期、Origin 后，才建立订单scope；查询后再次检查期限。其他 cashier API 拒绝，商户API仍用框架JWT。PAID/CLOSED 可只读，摘要不含条款/凭证，choices 暂为空。详见[acquire运行说明](../acquire/README.md)。


## 19. Checkout 一步创单实际适配

`CheckoutOrderFacade.checkout(CheckoutCreateCommand)` 已实现 POST `/api/acquire/checkout/create`，权限 `pay:acquire:checkout`；完整 AcquireOrderFacade 继承此切片但仍未注册其余方法。请求字段见2.1，金额为正整数人民币分，currency 默认 CNY，requestedDueAt 统一上海偏移且最多微秒。回跳仅接受冻结合约 code；返回 `SuccessResponse<CheckoutDTO>`，业务失败为固定 FailureResponse，凭证响应设置 no-store。

```json
{
  "payeeMerchantId": "merchant-demo",
  "merchantOrderNo": "CHECKOUT-20261022-001",
  "productScopeKey": "acquiring",
  "amountMinor": 10000,
  "currency": "CNY",
  "subject": "示例订单"
}
```

商户订单幂等身份与普通 create 共用，入口类型不同即冲突；Checkout 的原始 returnTargetCode（省略值也固定）参与摘要及载荷比较。原生 ORDER 的摘要版本及持久 JSON 不变。A0 固定 orderId/initialSessionId，A1 同事务写订单/条款/费用/首次 ACTIVE 会话和完成标记；网络准入不占本地事务。首次会话不发起渠道支付，scope 仍仅 ORDER_SUMMARY。

同号同参重试返回当前订单视图和原 sessionId/checkoutUrl/sessionExpiresAt，不是重建订单或刷新会话；订单视图可随后续交易推进。过期/取消/终态不续期，密钥不可用返回 SESSION_UNAVAILABLE，原首次会话缺失返回 SNAPSHOT_INVALID。独立 `/api/acquire/session/create` 的新 requestKey 是续发入口，不改变创单请求或迁移付款动作。

持久拒绝结果含 CONTRACT_NOT_ADMITTING、ADMISSION_EXPIRED、ACTION_FORBIDDEN（回跳 code 不匹配）、ORDER_NOT_PAYABLE（签发时已无可用窗口）；同号之后不重新准入。签名/存储等系统失败保留 INIT，可同参重试。上线需遵循 DATABASE 第12节，源码与新增测试尚未运行环境验收。


## 20. Payment 受理与查询实际适配

PaymentAcceptanceFacade.accept/detail 已实现 POST `/api/payment/executions/accept`、`/detail`；完整 PaymentExecutionFacade 继承此切片；关闭与父门禁见第21节，startAttempt 尚未注册。accept 接 PaymentExecuteCommand，detail 接 IdQuery(paymentId)，返回 SuccessResponse<PaymentExecutionDTO>，业务失败沿用固定错误码 FailureResponse。

仅允许已验证 SERVICE JWT 的指定 acquire-service-id；sub 必须等于 service_id，租户从 tenant_id 获取，分别要求 pay:payment:accept/read。不能将商户 JWT 或自填请求头当作服务身份。AdmittedOrderTermsDTO 的来源字段、原条款、compiledTermsHash 均必填，generation 从1开始；requestDigest 为64位小写十六进制。绑定关系及本笔金额上界由 payment 校验，跨笔占用仍由 acquire 负责。

首次受理返回 INIT/version0、空 activeAttemptId/action/terminalEvidenceId，不代表额度已预占或渠道已提交。重放比较原服务、摘要及完整解码命令，返回原 initiatedAt/operation 对应的付款视图；allocation 不能改绑新 payment。detail 为内部权威查询，不是商户查询或 cashier 能力入口。不存在本地记录时仅返回 NOT_FOUND，不构造“渠道未扣款”证明。

完整命令由本地 intent_payload/payload_hash 持久，服务信任来自资源服务器身份授权，副本校验和不替代身份与跨服务占用验证。preparedInputRef 只冻结引用，后续执行必须核对其绑定和有效性。既有门禁封禁阻止首次受理，已受理记录的查询/幂等重放不重新开门禁或触发扣款；封禁/关闭写入口见第21节，渠道工作流仍待实现。运行及编译证据见[payment说明](../payment/README.md)。


## 21. Payment 封禁、收敛与关闭实际适配

PaymentControlFacade 已实现：POST `/api/payment/executions/close`、`/fence-parent`、`/parent-fence-status`，分别要求 pay:payment:close、pay:payment:fence、pay:payment:fence:read。仅接受同受理入口指定的 acquire SERVICE 身份，租户来自已验证 JWT。

```java
public record PaymentCloseCommand(
        String paymentId, String orderId, String payeeMerchantId,
        String requestKey, long fenceEpoch, String reasonCode) {}

public record PaymentCloseDTO(
        String paymentId, String orderId, long fenceEpoch, boolean fenced,
        PaymentExecutionDTO payment, String noCollectionEvidenceId) {}
```

上述为当前关闭契约；payee 必填、fenceEpoch 必须大于0。父封禁/查询沿用 ParentPaymentFenceCommand/ParentFenceStatusQuery，实际响应 closeEpoch 为当前持久代次，低代次不覆盖高代次。控制摘要由 payment 计算，kind+target+requestKey 的唯一回执固定原请求，相同 key 改代次/原因/商户等均冲突。

close 返回 SuccessResponse<PaymentCloseDTO>。payment=null 表示没有可返回的付款记录，只有持久 tombstone 与未收款回执；不创建假付款。INIT/version0/无尝试可在门禁下本地 CLOSED，并同时返回 payment.terminalEvidenceId 和 noCollectionEvidenceId。处理中/未知只进入 CLOSING，后者为空；成功付款仍 SUCCEEDED，成功证据与未收款证据不混用。终态及原支付金额不会因关闭或独立退款改写。

先关闭后受理的新请求因单笔门禁拒绝；已受理再关闭的 accept 同号重放返回原 CLOSED/证据而不重启。既有 gate 缺付款且无原未收款证据，或有回执却缺 gate，返回 SNAPSHOT_INVALID。相同 key 重放不补 gate、不换证据，返回当前付款视图；不同 key 对未创建付款复用原证据。

父 status 只在 gate 的代次覆盖请求时返回 fenced=true；没有 gate 或请求高于当前代次均为false。allChildrenConverged 还要求全部已有付款终结且证据存在；unknownCount 保守包含有 activeAttempt 的处理中/未知/关闭中及终态证据缺失。父 gate 与回执同事务，status 持父行锁；它不替代逐笔未收款证明、acquire 金额占用释放或 limitcenter 额度释放。当前没有渠道关闭扫描器，CLOSING 保留待后续可信工作流推进。

## 22. 商户付款发起实际适配

PaymentInitiationFacade.startPayment 已实现 POST `/api/acquire/payments/start`，输入 PaymentStartCommand，返回 SuccessResponse<PaymentStartDTO>。原已验证商户主体还需 `pay:acquire:payment:start` 权限，只能操作自身原订单。当前支持 H5 + NONE、PAYMENT_CODE + 付款码、JSAPI + 同渠道身份票据；敏感输入在 A2 后由 payment 加密准备并附加固定引用，不接受商户自填 preparedInputRef。

A2 以 tenant/orderId/actorScope/actionKey 固定一笔付款：actorScope 当前为 merchant/原主体hash。锁订单后原子写占用及 HELD allocation，分配重试稳定的 paymentId 并持久完整原准入/选择/金额命令；提交后才调用 payment.accept。remaining、min/max、尾款及成功笔数（含占用）统一由领域模型校验。成功受理仍是 INIT，不是渠道成功。

同键先回放原动作，改金额/选择冲突；过期后原动作可以恢复，新动作不能绕过原截止。超时、错误响应、远端未受理均保留原占用。商户必须保留 actionKey 并同参重试；不同 key 会产生另一笔占用。非 HELD 的原动作只读既有付款，不另建记录。

出站使用独立 SERVICE JWT 挂载文件和固定 payment HTTP 目标，拒绝未配置的租户；商户凭证不转发。权限 pay:payment:accept/read 由 payment 校验。只读 cashier 的 scope 与接口保持独立。

## 23. 权威结果刷新与金额收敛实际适配

```java
public record PaymentProgressRefreshCommand(String orderId, String paymentId) {}
public interface PaymentProgressFacade {
    PaymentProgressDTO refresh(PaymentProgressRefreshCommand query);
}
public record PaymentOutcomeDTO(PaymentExecutionDTO payment, boolean executionFenced) {}
public interface PaymentOutcomeFacade {
    PaymentOutcomeDTO outcome(IdQuery query);
}
```

商户 POST `/api/acquire/payments/refresh` 要求原订单主体与 `pay:acquire:payment:refresh`，响应 SuccessResponse<PaymentProgressDTO> 且 no-store。调用方只给原订单/付款 ID；acquire 必须先证明原占用归属，不能以新 paymentId 查询其他付款。refresh 会推进已存在的原订单资金事实，不等价于普通只读 detail，也不发起新交易。

acquire 在事务外调用内部 POST `/api/payment/executions/outcome`。payment 只接受指定 SERVICE+pay:payment:read，在两级门禁锁内重读原付款并返回一致的执行封禁标志。NOT_FOUND 不证明未扣款。

acquire 只对成功或明确未收款的终态落账：绑定 order/payment/allocation/金额/币种及正版本，终态必须有可信证据；成功还需成功 attempt，FAILED/CLOSED 还需单笔持久封禁。其他状态不改变金额。成功将原占用转为 gross 已付；未收款释放原占用，不减少 gross 已付；累计恰好等于固定总额才 PAID。

订单/占用/终态回执在同一 A3 事务 CAS 更新，原回执固定 sourceVersion/evidenceId。同一事实重放不二次累计；旧版本、不同终结证据或终态倒退不能覆盖原账。同一次成功后的 gate 状态变化不改变成功资金事实。原在途结果落账不重新检查当前合约或订单付款窗口。

当前 start 本身只持久受理，需显式 refresh 才应用结果；后台扫描/事件、费用确认及额度确认/释放后续实现。此批无真实渠道结果生产和 cashier 付款 UI；按用户要求暂停验证，不能将接口源码视为运行验收。

## 24. 商户显式关闭实际适配

AcquireOrderClosureFacade.close 已实现 POST `/api/acquire/orders/close`，复用 AcquireOrderCloseCommand：id 为原订单 ID，requestKey 和 reasonCode 在首个关闭请求中固定。要求原调用主体与 pay:acquire:close，成功返回 SuccessResponse<AcquireOrderDTO>、no-store。响应 CLOSING 表示关闭仍在收敛，不代表关闭失败或可换 key 重建；响应 PAID 表示原在途成功先付清，成功事实优先保留。

C0 订单锁内写关闭意图及 CLOSING/closeEpoch，先禁止本地新付款；提交后向 payment 先 fenceParent，再对每笔本地 HELD 调 close，最后查 parentFenceStatus。内部使用挂载的 SERVICE JWT，需原 accept/read 以及 close/fence/fence:read 权限。所有请求沿原 closeIntentId 派生的固定服务端 key 和代次，网络不跨本地事务。

每请求最多推进16笔，持久 paymentId 游标保证未知付款不会长期挡住后面付款。已受理结果与显式 refresh 复用同一 A3 逻辑。未创建付款必须携带远端持久 tombstone+未收款证据，才记录 NOT_ACCEPTED 并释放，不从 NOT_FOUND 推断。该事实 sourceVersion=0，不创建 PaymentExecutionDTO；此后 refresh 的 payment=null，start 返回 ORDER_NOT_PAYABLE。

CLOSED 的后置条件为父封禁/全部子付款终结、原订单全部占用清零、本地每笔 allocation 都有唯一终态回执。原请求因超时或中断可重试；同订单换 key、改原因冲突。第一次请求已为 PAID/CLOSED 的订单不能另起关闭操作；已存在原关闭请求的终态重放只返回当前事实。部分收款后关闭保留 gross，不隐式退款或重开订单。

资金刷新是写操作，其入参现统一使用 command 包下 PaymentProgressRefreshCommand(orderId,paymentId)，原 Query 直接移除。收银台能力继续只读，未授予商户关闭或付款权限。本批未做验证。

## 25. 原订单延期实际适配

AcquireOrderExtensionFacade.extend 已实现 POST `/api/acquire/orders/extend`，完整 AcquireOrderFacade 继承该切片。要求原调用主体与 `pay:acquire:extend`，响应 SuccessResponse<AcquireOrderDTO>、no-store。

```json
{
  "id": "original-order-id",
  "requestKey": "extend-001",
  "requestedDueAt": "2026-10-22T18:30:00.123456+08:00",
  "expectedVersion": 3
}
```

expectedVersion 对应订单 version，参与幂等摘要；不要用新的订单 version 替换重试参数。同键先回放原延期回执，再考虑当前版本/状态，因此响应丢失后重试不会因已经延期而二次修改或误报版本冲突。返回当前订单视图，可包含后续付款/关闭事实。更改截止/expectedVersion 必须换新 key。

新延期只接受仍可续付的 INIT/PENDING，并遵守原冻结 extensionAllowed 和累计 extensionMaxSeconds。原始截止由持久 ORDER/CHECKOUT 意图及原 admittedAt 恢复，多次延期固定使用原始截止+累计上限，不滚动重置。CLOSE 到期即拒绝，KEEP_PENDING 可在旧截止后续期但仍受累计上限及未来时间约束。终态和 CLOSING 不新执行延期，原成功请求的终态重放仅查询。

requestedDueAt 支持显式偏移且最多微秒精度，等价偏移映射同一摘要；不允许缩短或维持原截止。订单锁内原子写新截止/两个版本和不可变回执，不访问外部准入服务、不延长已有会话、不改变金额或渠道执行窗口。主要固定错误码：ACTION_FORBIDDEN（主体/权限/条款禁止）、VERSION_CONFLICT、ORDER_NOT_PAYABLE、IDEMPOTENCY_CONFLICT、INVALID_ARGUMENT、SNAPSHOT_INVALID。

自动到期任务及其版本淘汰后续接入；本批按要求未验证或部署。

## 26. 付款码与纯 API 接入范围修订

当前付款方式为 PAYMENT_CODE、JSAPI、H5。付款码输入使用 PaymentCodeInteractionDTO(type=PAYMENT_CODE,paymentCode)，由商户扫码枪采集并经商户认证 API 提交；原始码为短期敏感凭证，诊断隐藏、HTTP 响应不回传。安全输入准备、绑定 paymentId 的短期加密存储和执行引用必须在渠道提交前完成，不把原始码写入订单意图或 Outbox。

DirectPayCommand/DirectPayDTO 保留为纯 API 创单并支付契约；普通 create→startPayment 同样不依赖 CheckoutSession。托管收银台只是一种可选入口。JSAPI/H5 的纯 API 返回调起参数或地址，商户前端负责付款人交互，不能承诺无用户授权的后台扣款。

Native 二维码与原生 APP 方式已从 PaymentMethod 移除；展示二维码和原生 APP 调起动作已移除。JSAPI 调起数据按微信/支付宝强类型分别建模，H5 使用受信跳转地址。收银台链接本身的展示方式不等于 Native 支付产品。

当前受理已补付款码/JSAPI 安全准备及纯 API start/DirectPay 编排源码；真实渠道执行仍待实现，新增源码未运行检查、构建或测试。

## 27. 额度确认/释放与权威付款核验

LimitSettlementFacade.confirm/release 实现 `/api/limit/settlement/confirm|release`，只接受配置的 payment SERVICE+独立写权限。先在事务外查询 `/api/payment/facts/payment`（配置的 limitcenter SERVICE+pay:payment:limit-facts），再在额度库内应用结果。该事实包含原 operation、商户和付款，不能用商户自报证据 ID 代替。

confirm 保持 LimitConfirmCommand→LimitReservationDTO；release 调整为 LimitReleaseCommand→LimitReleaseDTO，返回 operation/payment/fenced、可空 reservation、原付款版本与未收款证明。release 早于 Reserve 时返回 reservation=null 且持久封禁，绝不构造金额为0的预占。

同 operation 只有一个不可变最终语义回执，重复投递不重复消费/释放，冲突终态拒绝。保留原归桶和累计计数，EXPIRED 不重复回收，未知不释放。该接口源码与整单 A3 费用确认本批均未验证；退款额度恢复后续接入。

## 28. 敏感输入准备及纯 API 受理实际入口

内部 POST `/api/payment/inputs/prepare` 只供 acquire SERVICE+pay:payment:input:prepare，返回 SuccessResponse<PaymentInputDTO>。命令字段以第4节当前完整绑定版本为准；inputRef 为随机不透明标识，inputDigest 为服务端有版本密钥的 HMAC，expiresAt 保持原始有效期。每个 tenant/paymentId 只能准备一次；相同完整命令重放，改 key/金额/作用域/敏感值冲突，不触发扣款。

acquire POST `/api/acquire/payments/start` 为纯 API；敏感方式先持久占用和固定标识，再 prepare、CAS附加引用、accept，跨服务不持有本地事务。商户只提交原始交互，不传内部引用。PAYMENT_INPUT_EXPIRED 映射为 ACQUIRE_PAYMENT_INPUT_EXPIRED；输入幂等/绑定冲突映射为 ACQUIRE_IDEMPOTENCY_CONFLICT，其他未知远端码/消息不透传。

POST `/api/acquire/payments/direct-pay` 由 DirectPaymentFacade 实现，复用 create/start/detail，要求 create/read/payment:start，首笔分值不得超过原总额；不创建收银台会话。创单用 merchantOrderNo、付款用 actionKey 幂等，任何失败都保留已有提交事实并允许原请求恢复。只是受理便利入口，当前不返回虚构渠道成功。渠道执行、后续 action 和后台恢复仍待接通；该批源码未验证。

## 29. 渠道绑定管理与新尝试选路

新增内部 ChannelBindingFacade：

| POST 路径（/api/payment/channel-bindings） | Command/Query | SERVICE 权限 | 返回 |
|---|---|---|---|
| /publish | ChannelBindingPublishCommand | pay:payment:binding:publish | ChannelBindingChangeDTO |
| /suspend | ChannelBindingSuspendCommand | pay:payment:binding:suspend | ChannelBindingChangeDTO |
| /revoke | ChannelBindingRevokeCommand | pay:payment:binding:revoke | ChannelBindingChangeDTO |
| /detail | ChannelBindingDetailQuery | pay:payment:binding:read | ChannelBindingDTO |

仅 PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID（默认 pay-ops）可调用，传输复用受信 SERVICE JWT。发布命令固定 payee/bindingCode/channel/accessMode、ALIPAY_V1 或 WECHAT_V1 类型化身份及版本化凭证引用、能力、优先级和 expectedVersion/requestKey；不接原始私钥或 app_auth_token。DIRECT/服务商的身份组合由领域校验。银联当前拒绝发布，待明确机构/产品与 identity。

首次版本0发布；后续 CAS 当前 headVersion。相同 requestKey 跨 publish/suspend/revoke 不能复用；重放返回原操作已应用快照，detail 返回当前头。SUSPENDED 可发布新 revision 恢复，REVOKED 不能恢复原 code。头可变但历史 revision 不变，原付款/退款使用冻结版本，不能按当前头改收款身份。

PaymentRouteSelector 为内部 app 能力，不开放商户 bindingId 参数。它已接入 attempt 创建，读取原冻结 routingProfileRef 和允许的渠道/方式/接入模式，锁当前 ACTIVE 头、核对原 revision，再按方案 rules 顺序选择。无候选返回 PAYMENT_CHANNEL_BINDING_UNAVAILABLE；方案缺失/归属错误返回 PAYMENT_ROUTING_PROFILE_UNAVAILABLE。额度/风控/Y2 准备源码见下一节；真实渠道执行仍待完成。源码未运行验证。

## 30. 首笔支付尝试与额度准备实际契约

PaymentAttemptFacade.startAttempt 已注册 POST `/api/payment/executions/start-attempt`，指定 acquire SERVICE+pay:payment:attempt:start，返回 SuccessResponse<PaymentExecutionDTO>。首笔 previousAttemptEvidenceId 为空，choice/inputRef 等于原受理命令；接口只建立固定 INIT attempt 与 PROCESSING 付款，不声明渠道提交/成功。原 requestKey 重放读取当前状态；新的 key 不能绕过活跃 attempt 进行盲重扣。安全换 attempt 尚待权威旧尝试查证和输入生命周期，不接受客户端 evidenceId 作为证明。

PaymentExecutionFacade 继续聚合各独立用例接口，Spring Controller 注入对应 Facade。Y1 已调用 PaymentRouteSelector，在原单服务权限和两级门禁锁内冻结实际 bindingRevision/requestNo。原 routingProfileRef 的不可变资源发布、归属校验与按序选路已补，见第31节；product 发布前跨服务资源验收仍待完成。

内部 PaymentSubmissionPreparation（无 HTTP）对固定 attempt 执行 risk → Reserve → BeginExecution → Verify → 本地 Y2。Reserve 参数来自原付款/冻结策略；Begin 的 key 固定 attempt/attemptId。新 PaymentLimitClient 使用专属 SERVICE JWT 和 tenant 配置，仅调用原额度公开 Facade 对应 HTTP 入口。对 operation/payment/attempt、epoch、金额/币种/策略/initiatedAt 逐项复核后，再锁原 payment/attempt、绑定输入并 CAS INIT→SUBMITTING。原始 permit 不存储、不返回商户，仅保存哈希；内存提交票据只给本地 CAS 赢家，不可持久重建或重放。

Y2 已提交视为可能发渠道；超时/崩溃/票据丢失不能换号或重发。默认风控明确 DEFAULT_ALLOW/NOT_CONNECTED；配置外部 provider 的错误不自动放行。DENY/超限/输入或额度过期仅在锁内确认从未跨 Y2 后生成本地未收款事实。显式关单新增相同边界，不能把已有 SUBMITTING 当本地失败。

该批只是首笔准备与提交权边界，真实渠道执行、结果动作、自动调度/查证/额度终态通知仍待完成；14个新测试方法及源码均未验证。

## 31. 不可变选路方案实际契约

RoutingProfileFacade：

| POST 路径 | 请求 | 指定管理 SERVICE 权限 | 返回 |
|---|---|---|---|
| /api/payment/routing-profiles/publish | RoutingProfilePublishCommand | pay:payment:routing:publish | SuccessResponse<RoutingProfileDTO> |
| /api/payment/routing-profiles/detail | RoutingProfileDetailQuery | pay:payment:routing:read | SuccessResponse<RoutingProfileDTO> |

指定 SERVICE 继续复用 PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID；商户/acquire 不可发布、查询管理资源或自行选择 bindingId。发布命令为 spec(profileRef/payeeMerchantId/rules) + requestKey。rules 是1—100条有序 bindingCode/channel/accessMode/method，优先级由数组顺序决定，不使用任意表达式或隐式兜底。引用和商户都是定义哈希的一部分，完全相同规则不可重复。

profileRef 在租户内唯一且不可覆盖；tenant/caller/requestKey 唯一。原键原定义重放返回首次 id/hash/时间，不重验当前绑定或改变其状态；原键改参数或新键覆盖旧 ref 返回 PAYMENT_IDEMPOTENCY_CONFLICT。首次发布只接受当前 ACTIVE 且归属匹配、具有声明能力的绑定。新定义使用新 profileRef。detail 要求给定 payee 与原定义一致。

原 Y1 选路不再忽略冻结 routingProfileRef：缺失/归属错误返回 PAYMENT_ROUTING_PROFILE_UNAVAILABLE；规则不能扩大原合约允许模式/渠道/方式。按显式候选顺序选择当前活跃 binding revision，全部不可用返回 PAYMENT_CHANNEL_BINDING_UNAVAILABLE。已建立的 attempt 重放不重新选路。该版本资源运行时验证不等于 product 发布前跨服务验收或真实渠道可用性证明；后两项仍待完成。源码及新增测试未运行验证。

## 32. 内部渠道结果应用与事件生产

PaymentResultService.record 为应用内部用例，不新增公共 HTTP 成功确认、人工 evidenceId 记账或原始回调接收端点。PaymentChannelGateway 的支付 submit/query/close 已返回 PaymentObservation（真实适配器尚待实现），包含完整固定身份、金额/CNY、结果、可空交易号/渠道成功时间、source/receiptKey/sourceDigest。退款方法仍使用独立 ChannelRefundOutcome 契约。原 ChannelPaymentOutcome 是纯领域迁移输入，由本地用例绑定持久 evidenceId。

未来渠道回调只能先完成验签/解密/账户校验再调用该内部入口，并在事务提交之后判断返回 PaymentResultApplication：APPLIED/IGNORED 表示已保存回执，CONFLICT 表示差错事实，不能伪装普通成功 ACK。身份/金额不一致和交易号归属冲突不会更新正常付款金额。NOT_FOUND、网络错误、未认证响应均不能证明 NO_COLLECTION；资金已退回需独立补偿。

终态事件复用 PaymentOutcomeEventDTO 和 PayEventEnvelope。类型为 pay.payment.outcome.acquire.v1、pay.payment.outcome.limit.v1、pay.payment.outcome.realtime.v1、pay.payment.outcome.risk.v1；每目标独立 deliveryId，共用首个终态 evidenceId 作为 businessEventId。完整原 payment/order/allocation/limitOperation/payee/金额币种、正源版本和 executionFenced 冻结在事务消息中。事件生产者字符串只作结构校验，后续消费仍需受信传输身份及各自权威事实核验。投递 handler、接收方 Inbox、商户通知签名/ACK 尚未完成，不能将入队当作送达或宣称四服务已联通。新增源码及测试未执行验证。
