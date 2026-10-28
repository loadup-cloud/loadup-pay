# Client API 详细设计

2026-10-23传输更新：应用间统一为 Protobuf gRPC，当前6个 service/25个方法分别由各服务`*-rpc/src/main/proto/<service>.proto`
维护，运行配置与迁移边界见 [RPC 规范](RPC.md)。下文“业务 gRPC 待接入/无协议模块”属于历史状态，其他未实现业务仍按原批次说明。

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
- 时间：Java OffsetDateTime，HTTP RFC3339显式`+08:00`，DB转系统默认时区的LocalDateTime；gRPC
  Timestamp只作绝对时间编码，业务归桶仍系统默认时区。
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
        OffsetDateTime requestedDueAt) {
}

public record CheckoutCreateCommand(
        String payeeMerchantId, String merchantOrderNo, String productScopeKey,
        long amountMinor, String currency, String subject,
        OffsetDateTime requestedDueAt, String returnTargetCode) {
}

public record PaymentChoiceDTO(Channel channel, PaymentMethod method) {
}

public record PaymentStartCommand(
        String orderId, String actionKey, long amountMinor,
        PaymentChoiceDTO choice, PaymentInteractionDTO interaction) {
}

public record CashierPaymentStartCommand(
        String orderId, String actionKey, long amountMinor,
        PaymentChoiceDTO choice, PaymentInteractionDTO interaction) {
}

public record DirectPayCommand(
        String payeeMerchantId, String merchantOrderNo, String productScopeKey,
        long amountMinor, String currency, String subject,
        OffsetDateTime requestedDueAt, String actionKey, long firstPaymentAmountMinor,
        PaymentChoiceDTO choice, PaymentInteractionDTO interaction) {
}

public record AcquireOrderCloseCommand(String id, String requestKey, String reasonCode) {
}

public record AcquireOrderExtendCommand(
        String id, String requestKey, OffsetDateTime requestedDueAt, long expectedVersion) {
}

public record CheckoutSessionCreateCommand(
        String orderId, String requestKey, String returnTargetCode) {
}
```

`PaymentInteractionDTO`是有封闭类型集合的交互参数，不是任意Map：NONE、PAYMENT_CODE（短期付款码）、CHANNEL_IDENTITY（当前渠道要求的授权交换票据）。HTTP用
`type`判别且拒绝多种输入并存；Java用sealed
interface及对应record。敏感record的toString仅返回类型/脱敏标志，校验异常不含原值。不能让浏览器自报一个跨渠道、跨应用可复用的payerId；服务端完成作用域验证与交换，不建设消费者档案。

当前支付方式枚举：PAYMENT_CODE、JSAPI、H5。Native 二维码与原生 APP
支付不在当前范围。渠道与方式是否可用以冻结合约、当前实际商户开通能力及输入前提共同判断；有枚举值不代表该渠道已完成适配。浏览器不提交bindingId、服务商号、子商户号、credentialRef或接入模式；服务端选路决定实际DIRECT/SERVICE_PROVIDER。

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
        OffsetDateTime orderDueAt, long version) {
}

public record CheckoutDTO(
        AcquireOrderDTO order, String sessionId, String checkoutUrl,
        OffsetDateTime sessionExpiresAt) {
}

public record PaymentStartDTO(
        String orderId, String paymentId, String allocationId,
        PaymentState state, PaymentActionDTO action, long version) {
}

public record DirectPayDTO(AcquireOrderDTO order, PaymentRecoveryDTO payment) {
}

public record PaymentActionDTO(
        PaymentActionType type, OffsetDateTime expiresAt,
        PaymentActionDataDTO data) {
}
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

同session重放生成完全相同token/期限；会话过期或撤销后不能因重新签出token而恢复权限。旧Secret版本保留至对应会话最长有效期/重试期结束；单链接撤销使用独立
revoked_at 永久标记，保留业务终态；新签发的 session 使用当前密钥版本。

## 3. product与customer：准入和授权

```java
public interface OrderAdmissionFacade {
    OrderAdmissionDTO authorize(OrderAdmissionAuthorizeCommand command);

    OrderAdmissionDTO detail(IdQuery query);
}

public record OrderAdmissionAuthorizeCommand(
        String orderId, String payeeMerchantId, String productScopeKey,
        String requestDigest, long amountMinor, String currency,
        OffsetDateTime requestedDueAt) {
}

public record OrderAdmissionDTO(
        String admissionId, String orderId, String payeeMerchantId,
        AdmissionDecision decision, String reasonCode, String requestDigest,
        String contractId, int revision, long generation, String snapshotHash, String compiledTermsHash,
        OffsetDateTime admittedAt, OffsetDateTime materializeBefore,
        AcquiringTermsDTO terms) {
}

public interface MerchantEligibilityFacade {
    MerchantEligibilityDTO authorize(MerchantActionAuthorizeCommand command);
}

public record MerchantActionAuthorizeCommand(
        String payeeMerchantId, MerchantAction action) {
}
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
        AdmittedOrderTermsDTO admittedTerms) {
}

public record PaymentCloseCommand(
        String paymentId, String orderId, String payeeMerchantId,
        String requestKey, long fenceEpoch, String reasonCode) {
}

public record ParentPaymentFenceCommand(
        String orderId, String payeeMerchantId, long closeEpoch, String requestKey) {
}

public record ParentFenceStatusQuery(String orderId, long closeEpoch) {
}

public record ParentFenceDTO(
        String orderId, long closeEpoch, boolean fenced,
        boolean allChildrenConverged, int unknownCount) {
}
```

AdmittedOrderTermsDTO含contractId/revision/原snapshotHash/compiledTermsHash、固定order总额、原退款/通知/限额配置等强类型必要子DTO，payload有schemaVersion；由可信acquire传递并校验绑定摘要，不能从公网上原样映射为已信任事实。PaymentExecutionDTO含payment/order/allocation、金额、state、activeAttempt、action、version和终结证据引用；不返回敏感input/credential。

| 内部操作          | 关键语义                                                                                              |
|-------------------|-------------------------------------------------------------------------------------------------------|
| accept            | paymentId唯一；同ID异digest拒绝；先门禁再持久化，返回已接受不保证已向渠道提交                         |
| close             | 没有PaymentOrder也先持久化单笔tombstone；返回CLOSING/最终结果，不能将“没查到本地付款”直接当成安全释放 |
| fenceParent       | 持久化父gate，拒绝迟到新paymentId；closeEpoch单调，低代次幂等不覆盖高代次                             |
| parentFenceStatus | fenced不等于allChildrenConverged；只有所有已有付款已最终收敛才允许acquire正常关闭                     |

敏感输入准备使用payment内部
`PaymentInputPrepareCommand(paymentId,orderId,allocationId,payeeMerchantId,amountMinor,currency,inputKey,choice,interaction)`
及DTO (inputRef,inputDigest,expiresAt)
，只对可信acquire开放。业务重试复用inputKey；同key不同敏感值冲突。该接口不触发渠道提交，不得把inputRef当成任意付款均可使用的bearer凭证。

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
        String policyId, int policyVersion, String policyHash, String requestDigest) {
}

public record LimitBeginExecutionCommand(
        String operationId, String attemptId, String requestKey,
        String previousAttemptEvidenceId) {
}

public record LimitConfirmCommand(
        String operationId, String paymentId, long paymentVersion,
        String businessEventId, String successEvidenceId,
        long amountMinor, String currency, OffsetDateTime succeededAt) {
}

public record LimitReleaseCommand(
        String operationId, String paymentId, long paymentVersion,
        String businessEventId, String noCollectionEvidenceId, String reasonCode) {
}

public record LimitRefundRestoreCommand(
        String operationId, String paymentId, String refundId,
        long refundVersion, String businessEventId, long refundAmountMinor,
        long cumulativePaymentRefundedMinor, String currency, String refundEvidenceId) {
}

public record LimitOperationQuery(String operationId) {
}
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
        List<RefundExplicitAllocationDTO> explicitAllocations) {
}

public record RefundExplicitAllocationDTO(String paymentId, long amountMinor) {
}

public record RefundRequestDTO(
        String refundRequestId, String orderId, String merchantRefundNo,
        String currency, long amountMinor, RefundRequestState state,
        long succeededMinor, long failedMinor, int unknownCount,
        int planVersion, List<RefundAllocationDTO> allocations, long version) {
}

public record RefundAllocationDTO(
        int sequenceNo, String paymentId, String refundId,
        long amountMinor, RefundState state) {
}
```

金额/币种和原商户校验由payment完成，币种继承原付款/订单，不能通过退款换汇。默认FIFO；explicitAllocations只有原合约选择且实际实现EXPLICIT时可传，金额求和必须恰等于申请额，paymentId归同一order/payee/currency。FIFO下传显式分配是参数错误；首期未实现LIFO/EXPLICIT时在发布配置时拒绝，不运行时静默降为FIFO。

首次创建申请返回INIT及固定分配计划；原退款号重试返回当前已知进度。INIT仅表示本地申请和容量预占已提交，不表示渠道已执行。原订单/成功付款一直保持原状态；部分已收订单能否退及窗口按原退款条款，不把“父订单PAID后禁止订单操作”解释成禁止独立退款。

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
        AcquireOrderState state, int page, int size) {
}
```

所有查询先取授权商户范围。请求payee只是进一步缩小，不能扩大。分页使用PageQuery.normalized，订单列表稳定按orderCreatedAt、orderId排序；payments/refunds有orderId/申请筛选与独立状态，detail只读组合四类投影。

TradeDetailDTO分别返回originalOrder、payments、refunds、refundSummary及snapshotAt/sourceVersions；净收款可派生，不回写订单paid。列表不声称是实时资金裁定；客户端需要立即确认付款时调用权威paymentProgress，不因投影滞后自动重收。

## 8. 事件接收与通知协议

### 8.1 内部可靠事件

```java
public record EventEnvelope<T>(
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
  "eventId": "E1001",
  "eventType": "PaymentSucceeded",
  "schemaVersion": 1,
  "producer": "acquire",
  "aggregateType": "PaymentOrder",
  "aggregateId": "P1001",
  "aggregateVersion": 3,
  "occurredAt": "2026-10-20T12:00:00+08:00",
  "data": {
    "orderId": "O1001",
    "paymentId": "P1001",
    "payeeMerchantId": "M1001",
    "amountMinor": 3000,
    "currency": "CNY",
    "orderPaidAmountMinor": 3000,
    "orderAmountMinor": 10000,
    "orderState": "PENDING"
  }
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
message PaymentIdQuery {string id = 1;}
message BusinessResult {string code = 1; string message = 2;}
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
    ProjectionAckDTO applyOrder(EventEnvelope<OrderSnapshotEventDTO> event);
    ProjectionAckDTO applyPayment(EventEnvelope<PaymentSnapshotEventDTO> event);
    ProjectionAckDTO applyRefund(EventEnvelope<RefundSnapshotEventDTO> event);
    ProjectionAckDTO applyRefundSummary(EventEnvelope<OrderRefundSummaryEventDTO> event);
}
```

| 载荷                       | 完整快照中的主要事实                                                                                           |
|----------------------------|----------------------------------------------------------------------------------------------------------------|
| OrderSnapshotEventDTO      | order/payee/merchantOrderNo、原总额币种、gross paid/held、state、orderCreatedAt/sourceUpdatedAt、sourceVersion |
| PaymentSnapshotEventDTO    | payment/order/payee、原付款金额币种、state、channel/accessMode、initiatedAt/succeededAt、sourceVersion         |
| RefundSnapshotEventDTO     | refund/request/payment/order/payee、原退款金额币种、state、refundCreatedAt/succeededAt、sourceVersion          |
| OrderRefundSummaryEventDTO | order/payee/currency、refunded/held、独立summaryVersion；固定大小金额快照，不携带历史申请列表                  |

不再用商户查询DTO替代这些投影事件。RefundOutcomeEventDTO专供额度恢复事实，不能充当缺少原创建/成功时间的退款完整快照。OrderRefundSummaryEventDTO只包含独立版本的累计已退/占用金额，由payment同库订单退款聚合产生，不由realtime逐笔累加。已移除无限增长的requests列表及其专用进度DTO；逐笔退款通过独立快照投影和分页查询，申请详情仍使用RefundFacade。

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

AcquireOrderCreationFacade 已实现 create/detail，完整 AcquireOrderFacade 继承该切片。POST `/api/acquire/orders/create`、
`/detail` 分别要求 pay:acquire:create/read，并限定签名原调用主体范围；detail 的 IdQuery 为 orderId。原 JWT 需具备
acquire/product/customer 的 audience 与权限，product 的 customer 授权仍以原主体执行。

create 以 tenant+payee+merchantOrderNo 固定 ORDER 意图，固定字段 UTF-8 长度分帧摘要协议 v1
包含原主体范围、总额/币种、标题、产品scope和原请求截止时间。相同命令复用 A0 分配的 orderId；改参冲突，换主体拒绝。网络调用位于事务外，依赖失败保留
INIT；原准入 DENIED/首次落单超 materializeBefore 分别提交
REJECTED/EXPIRED，之后同号重试固定拒绝。A1订单/快照/INIT整单费用/COMPLETED在同一数据库事务完成，完成后回放不再请求当前合约。

当前不注册全量 AcquireOrderFacade 实现，不暴露 directPay/startPayment/close/extend 占位接口；Checkout
已按第19节实现。自动关单、事件、付款结果和真正收款验收仍待实施。运行和证据边界见[acquire说明](../acquire/README.md)。

## 18. 原单 Checkout 会话与 Cashier 适配

POST `/api/acquire/session/create` 实现 CheckoutSessionFacade.create，要求原商户主体和 pay:acquire:session；POST
`/api/cashier/order/summary` 实现 CashierSummaryFacade.summary，依赖 Authorization 会话能力及 X-Checkout-Tenant
定位值，IdQuery 必须属于已验证 scope。GET `/checkout` 已有 H5 手动付款/原动作恢复与进度页面源码；CashierFacade 注册
startPayment/paymentProgress，使用相同会话能力，接口见本文末尾的收银台付款接线与页面补充。

独立续发使用 create→session.create 两步；CheckoutCreateCommand 一步创单/A1同事务签发见第19节。签发按原订单/冻结条款进行，普通合约停单不重新判定；同
requestKey 返回同 session/token/expiry，不因过期、取消或订单终态而重开会话；新 requestKey 只允许仍可续付订单。returnTargetCode
为原合约默认或同一审核 code，不能指定任意 URL。

scope 版本1现在冻结 ORDER_SUMMARY/PAYMENT_START/PAYMENT_PROGRESS，权限仅限原订单和本会话付款。opaque token 沿用2.5
HMAC协议，nonce/密钥引用和版本持久化，固定期限按秒对齐。恒定时间匹配原摘要并检查
ACTIVE/COMPLETED、严格未过期、Origin 后，才建立订单scope；查询后再次检查期限。只放行摘要、付款发起与本会话进度三类 POST，其他
cashier API 拒绝；商户API仍用框架JWT。PAID/CLOSED
可只读，摘要不含条款/凭证，choices 是原合约与显式启用托管能力的交集。详见[acquire运行说明](../acquire/README.md)。

## 19. Checkout 一步创单实际适配

`CheckoutOrderFacade.checkout(CheckoutCreateCommand)` 已实现 POST `/api/acquire/checkout/create`，权限
`pay:acquire:checkout`；完整 AcquireOrderFacade 继承此切片但仍未注册其余方法。请求字段见2.1，金额为正整数人民币分，currency
默认 CNY，requestedDueAt 统一上海偏移且最多微秒。回跳仅接受冻结合约 code；返回 `SuccessResponse<CheckoutDTO>`，业务失败为固定
FailureResponse，凭证响应设置 no-store。

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

商户订单幂等身份与普通 create 共用，入口类型不同即冲突；Checkout 的原始 returnTargetCode（省略值也固定）参与摘要及载荷比较。原生
ORDER 的摘要版本及持久 JSON 不变。A0 固定 orderId/initialSessionId，A1 同事务写订单/条款/费用/首次 ACTIVE
会话和完成标记；网络准入不占本地事务。首次会话不直接发起渠道支付，scope 授予原订单摘要/本会话付款发起/进度；仍需用户显式付款操作。

同号同参重试返回当前订单视图和原 sessionId/checkoutUrl/sessionExpiresAt，不是重建订单或刷新会话；订单视图可随后续交易推进。过期/取消/终态不续期，密钥不可用返回
SESSION_UNAVAILABLE，原首次会话缺失返回 SNAPSHOT_INVALID。独立 `/api/acquire/session/create` 的新 requestKey
是续发入口，不改变创单请求或迁移付款动作。

持久拒绝结果含 CONTRACT_NOT_ADMITTING、ADMISSION_EXPIRED、ACTION_FORBIDDEN（回跳 code
不匹配）、ORDER_NOT_PAYABLE（签发时已无可用窗口）；同号之后不重新准入。签名/存储等系统失败保留 INIT，可同参重试。上线需遵循
DATABASE 第12节，源码与新增测试尚未运行环境验收。

## 20. Payment 受理与查询实际适配

PaymentAcceptanceFacade.accept/detail 已实现 POST `/api/payment/executions/accept`、`/detail`；完整 PaymentExecutionFacade
继承此切片；关闭与父门禁见第21节，startAttempt 尚未注册。accept 接 PaymentExecuteCommand，detail 接 IdQuery (paymentId)，返回
SuccessResponse<PaymentExecutionDTO>，业务失败沿用固定错误码 FailureResponse。

仅允许已验证 SERVICE JWT 的指定 acquire-service-id；sub 必须等于 service_id，租户从 tenant_id 获取，分别要求 pay:payment:
accept/read。不能将商户 JWT 或自填请求头当作服务身份。AdmittedOrderTermsDTO 的来源字段、原条款、compiledTermsHash
均必填，generation 从1开始；requestDigest 为64位小写十六进制。绑定关系及本笔金额上界由 payment 校验，跨笔占用仍由 acquire
负责。

首次受理返回 INIT/version0、空 activeAttemptId/action/terminalEvidenceId，不代表额度已预占或渠道已提交。重放比较原服务、摘要及完整解码命令，返回原
initiatedAt/operation 对应的付款视图；allocation 不能改绑新 payment。detail 为内部权威查询，不是商户查询或 cashier
能力入口。不存在本地记录时仅返回 NOT_FOUND，不构造“渠道未扣款”证明。

完整命令由本地 intent_payload/payload_hash 持久，服务信任来自资源服务器身份授权，副本校验和不替代身份与跨服务占用验证。preparedInputRef
只冻结引用，后续执行必须核对其绑定和有效性。既有门禁封禁阻止首次受理，已受理记录的查询/幂等重放不重新开门禁或触发扣款；封禁/关闭写入口见第21节，渠道工作流仍待实现。运行及编译证据见[payment说明](../payment/README.md)。

## 21. Payment 封禁、收敛与关闭实际适配

PaymentControlFacade 已实现：POST `/api/payment/executions/close`、`/fence-parent`、`/parent-fence-status`，分别要求 pay:
payment:close、pay:payment:fence、pay:payment:fence:read。仅接受同受理入口指定的 acquire SERVICE 身份，租户来自已验证 JWT。

```java
public record PaymentCloseCommand(
        String paymentId, String orderId, String payeeMerchantId,
        String requestKey, long fenceEpoch, String reasonCode) {
}

public record PaymentCloseDTO(
        String paymentId, String orderId, long fenceEpoch, boolean fenced,
        PaymentExecutionDTO payment, String noCollectionEvidenceId) {
}
```

上述为当前关闭契约；payee 必填、fenceEpoch 必须大于0。父封禁/查询沿用 ParentPaymentFenceCommand/ParentFenceStatusQuery，实际响应
closeEpoch 为当前持久代次，低代次不覆盖高代次。控制摘要由 payment 计算，kind+target+requestKey 的唯一回执固定原请求，相同
key 改代次/原因/商户等均冲突。

close 返回 SuccessResponse<PaymentCloseDTO>。payment=null 表示没有可返回的付款记录，只有持久 tombstone
与未收款回执；不创建假付款。INIT/version0/无尝试可在门禁下本地 CLOSED，并同时返回 payment.terminalEvidenceId 和
noCollectionEvidenceId。处理中/未知只进入 CLOSING，后者为空；成功付款仍 SUCCEEDED，成功证据与未收款证据不混用。终态及原支付金额不会因关闭或独立退款改写。

先关闭后受理的新请求因单笔门禁拒绝；已受理再关闭的 accept 同号重放返回原 CLOSED/证据而不重启。既有 gate 缺付款且无原未收款证据，或有回执却缺
gate，返回 SNAPSHOT_INVALID。相同 key 重放不补 gate、不换证据，返回当前付款视图；不同 key 对未创建付款复用原证据。

父 status 只在 gate 的代次覆盖请求时返回 fenced=true；没有 gate 或请求高于当前代次均为false。allChildrenConverged
还要求全部已有付款终结且证据存在；unknownCount 保守包含有 activeAttempt 的处理中/未知/关闭中及终态证据缺失。父 gate
与回执同事务，status 持父行锁；它不替代逐笔未收款证明、acquire 金额占用释放或 limitcenter 额度释放。当前没有渠道关闭扫描器，CLOSING
保留待后续可信工作流推进。

## 22. 商户付款发起实际适配

PaymentInitiationFacade.startPayment 已实现 POST `/api/acquire/payments/start`，输入 PaymentStartCommand，返回
SuccessResponse<PaymentStartDTO>。原已验证商户主体还需 `pay:acquire:payment:start` 权限，只能操作自身原订单。当前支持 H5 +
H5_SCENE（微信要求真实终端 IP 与场景）、PAYMENT_CODE + 付款码、JSAPI + 同渠道身份票据；NONE 只表示空输入，能否执行由渠道能力决定。敏感输入在
A2 后由 payment 加密准备并附加固定引用，不接受商户自填
preparedInputRef。

A2 以 tenant/orderId/actorScope/actionKey 固定一笔付款：actorScope 当前为 merchant/原主体hash。锁订单后原子写占用及 HELD
allocation，分配重试稳定的 paymentId 并持久完整原准入/选择/金额命令；提交后才调用
payment.accept。remaining、min/max、尾款及成功笔数（含占用）统一由领域模型校验。成功受理仍是 INIT，不是渠道成功。

同键先回放原动作，改金额/选择冲突；过期后原动作可以恢复，新动作不能绕过原截止。超时、错误响应、远端未受理均保留原占用。商户必须保留
actionKey 并同参重试；不同 key 会产生另一笔占用。非 HELD 的原动作只读既有付款，不另建记录。

出站使用独立 SERVICE JWT 挂载文件和固定 payment HTTP 目标，拒绝未配置的租户；商户凭证不转发。权限 pay:payment:accept/read 由
payment 校验。cashier 的会话能力与商户 JWT 接口保持独立，付款共用原 A2 编排。

## 23. 权威结果刷新与金额收敛实际适配

```java
public record PaymentProgressRefreshCommand(String orderId, String paymentId) {
}

public interface PaymentProgressFacade {
    PaymentProgressDTO refresh(PaymentProgressRefreshCommand query);
}

public record PaymentOutcomeDTO(PaymentExecutionDTO payment, boolean executionFenced) {
}

public interface PaymentOutcomeFacade {
    PaymentOutcomeDTO outcome(IdQuery query);
}
```

商户 POST `/api/acquire/payments/refresh` 要求原订单主体与 `pay:acquire:payment:refresh`，响应
SuccessResponse<PaymentProgressDTO> 且 no-store。调用方只给原订单/付款 ID；acquire 必须先证明原占用归属，不能以新 paymentId
查询其他付款。refresh 会推进已存在的原订单资金事实，不等价于普通只读 detail，也不发起新交易。

acquire 在事务外调用内部 POST `/api/payment/executions/outcome`。payment 只接受指定 SERVICE+pay:payment:
read，在两级门禁锁内重读原付款并返回一致的执行封禁标志。NOT_FOUND 不证明未扣款。

acquire 只对成功或明确未收款的终态落账：绑定 order/payment/allocation/金额/币种及正版本，终态必须有可信证据；成功还需成功
attempt，FAILED/CLOSED 还需单笔持久封禁。其他状态不改变金额。成功将原占用转为 gross 已付；未收款释放原占用，不减少 gross
已付；累计恰好等于固定总额才 PAID。

订单/占用/终态回执在同一 A3 事务 CAS 更新，原回执固定 sourceVersion/evidenceId。同一事实重放不二次累计；旧版本、不同终结证据或终态倒退不能覆盖原账。同一次成功后的
gate 状态变化不改变成功资金事实。原在途结果落账不重新检查当前合约或订单付款窗口。

当前 start 本身只持久受理，需显式 refresh 才应用结果；后台扫描/事件、费用确认及额度确认/释放后续实现。此批无真实渠道结果生产和
cashier 付款 UI；按用户要求暂停验证，不能将接口源码视为运行验收。

## 24. 商户显式关闭实际适配

AcquireOrderClosureFacade.close 已实现 POST `/api/acquire/orders/close`，复用 AcquireOrderCloseCommand：id 为原订单
ID，requestKey 和 reasonCode 在首个关闭请求中固定。要求原调用主体与 pay:acquire:close，成功返回
SuccessResponse<AcquireOrderDTO>、no-store。响应 CLOSING 表示关闭仍在收敛，不代表关闭失败或可换 key 重建；响应 PAID
表示原在途成功先付清，成功事实优先保留。

C0 订单锁内写关闭意图及 CLOSING/closeEpoch，先禁止本地新付款；提交后向 payment 先 fenceParent，再对每笔本地 HELD 调
close，最后查 parentFenceStatus。内部使用挂载的 SERVICE JWT，需原 accept/read 以及 close/fence/fence:read 权限。所有请求沿原
closeIntentId 派生的固定服务端 key 和代次，网络不跨本地事务。

每请求最多推进16笔，持久 paymentId 游标保证未知付款不会长期挡住后面付款。已受理结果与显式 refresh 复用同一 A3
逻辑。未创建付款必须携带远端持久 tombstone+未收款证据，才记录 NOT_ACCEPTED 并释放，不从 NOT_FOUND 推断。该事实
sourceVersion=0，不创建 PaymentExecutionDTO；此后 refresh 的 payment=null，start 返回 ORDER_NOT_PAYABLE。

CLOSED 的后置条件为父封禁/全部子付款终结、原订单全部占用清零、本地每笔 allocation 都有唯一终态回执。原请求因超时或中断可重试；同订单换
key、改原因冲突。第一次请求已为 PAID/CLOSED 的订单不能另起关闭操作；已存在原关闭请求的终态重放只返回当前事实。部分收款后关闭保留
gross，不隐式退款或重开订单。

资金刷新是写操作，其入参现统一使用 command 包下 PaymentProgressRefreshCommand (orderId,paymentId)，原 Query
直接移除。收银台只授予原订单摘要/本会话付款发起/进度，不授予商户 API、关闭或退款权限。本批未做验证。

## 25. 原订单延期实际适配

AcquireOrderExtensionFacade.extend 已实现 POST `/api/acquire/orders/extend`，完整 AcquireOrderFacade 继承该切片。要求原调用主体与
`pay:acquire:extend`，响应 SuccessResponse<AcquireOrderDTO>、no-store。

```json
{
  "id": "original-order-id",
  "requestKey": "extend-001",
  "requestedDueAt": "2026-10-22T18:30:00.123456+08:00",
  "expectedVersion": 3
}
```

expectedVersion 对应订单 version，参与幂等摘要；不要用新的订单 version
替换重试参数。同键先回放原延期回执，再考虑当前版本/状态，因此响应丢失后重试不会因已经延期而二次修改或误报版本冲突。返回当前订单视图，可包含后续付款/关闭事实。更改截止/expectedVersion
必须换新 key。

新延期只接受仍可续付的 INIT/PENDING，并遵守原冻结 extensionAllowed 和累计 extensionMaxSeconds。原始截止由持久
ORDER/CHECKOUT/DIRECT_PAY 意图及原 admittedAt 恢复，多次延期固定使用原始截止+累计上限，不滚动重置。CLOSE 到期即拒绝，KEEP_PENDING
可在旧截止后续期但仍受累计上限及未来时间约束。终态和 CLOSING 不新执行延期，原成功请求的终态重放仅查询。

requestedDueAt
支持显式偏移且最多微秒精度，等价偏移映射同一摘要；不允许缩短或维持原截止。订单锁内原子写新截止/两个版本和不可变回执，不访问外部准入服务、不延长已有会话、不改变金额或渠道执行窗口。主要固定错误码：ACTION_FORBIDDEN（主体/权限/条款禁止）、VERSION_CONFLICT、ORDER_NOT_PAYABLE、IDEMPOTENCY_CONFLICT、INVALID_ARGUMENT、SNAPSHOT_INVALID。

创建在 A1 同事务登记第 1 版到期任务，延期在原订单锁下同事务登记新 deadlineVersion 的到期任务；旧版到期任务被版本淘汰。
CLOSE 到期时持久化 C0 与关单任务，KEEP_PENDING 只按领域规则推进到 PENDING，仍允许付款。
原 C0 的关闭批处理由 native 任务继续，不需要商户持续重放；商户重放仍必须使用原请求键和原参数。源码未验证或部署。

## 26. 付款码与纯 API 接入范围修订

当前付款方式为 PAYMENT_CODE、JSAPI、H5。付款码输入使用 PaymentCodeInteractionDTO (type=PAYMENT_CODE,paymentCode)
，由商户扫码枪采集并经商户认证 API 提交；原始码为短期敏感凭证，诊断隐藏、HTTP 响应不回传。安全输入准备、绑定 paymentId
的短期加密存储和执行引用必须在渠道提交前完成，不把原始码写入订单意图或 Outbox。

DirectPayCommand/DirectPayDTO 保留为纯 API 创单并支付契约；普通 create→startPayment 同样不依赖
CheckoutSession。托管收银台只是一种可选入口。JSAPI/H5 的纯 API 返回调起参数或地址，商户前端负责付款人交互，不能承诺无用户授权的后台扣款。

Native 二维码与原生 APP 方式已从 PaymentMethod 移除；展示二维码和原生 APP 调起动作已移除。JSAPI 调起数据按微信/支付宝强类型分别建模，H5
使用受信跳转地址。收银台链接本身的展示方式不等于 Native 支付产品。

当前受理已补付款码/JSAPI 安全准备及纯 API start/DirectPay 编排源码；真实渠道执行仍待实现，新增源码未运行检查、构建或测试。

## 27. 额度确认/释放与权威付款核验

LimitSettlementFacade.confirm/release 实现 `/api/limit/settlement/confirm|release`，只接受配置的 payment
SERVICE+独立写权限。先在事务外查询 `/api/payment/facts/payment`（配置的 limitcenter SERVICE+pay:payment:
limit-facts），再在额度库内应用结果。该事实包含原 operation、商户和付款，不能用商户自报证据 ID 代替。

confirm 保持 LimitConfirmCommand→LimitReservationDTO；release 调整为 LimitReleaseCommand→LimitReleaseDTO，返回
operation/payment/fenced、可空 reservation、原付款版本与未收款证明。release 早于 Reserve 时返回 reservation=null
且持久封禁，绝不构造金额为0的预占。

同 operation 只有一个不可变最终语义回执，重复投递不重复消费/释放，冲突终态拒绝。保留原归桶和累计计数，EXPIRED
不重复回收，未知不释放。该接口源码与整单 A3 费用确认本批均未验证；退款额度恢复后续接入。

## 28. 敏感输入准备及纯 API 受理实际入口

内部 POST `/api/payment/inputs/prepare` 只供 acquire SERVICE+pay:payment:input:prepare，返回
SuccessResponse<PaymentInputDTO>。命令字段以第4节当前完整绑定版本为准；inputRef 为随机不透明标识，inputDigest 为服务端有版本密钥的
HMAC，expiresAt 保持原始有效期。每个 tenant/paymentId 只能准备一次；相同完整命令重放，改 key/金额/作用域/敏感值冲突，不触发扣款。

acquire POST `/api/acquire/payments/start` 为纯 API；敏感方式先持久占用和固定标识，再
prepare、CAS附加引用、accept，跨服务不持有本地事务。商户只提交原始交互，不传内部引用。PAYMENT_INPUT_EXPIRED 映射为
ACQUIRE_PAYMENT_INPUT_EXPIRED；输入幂等/绑定冲突映射为 ACQUIRE_IDEMPOTENCY_CONFLICT，其他未知远端码/消息不透传。

POST `/api/acquire/payments/direct-pay` 由 DirectPaymentFacade 实现，复用 create/start/detail，要求 create/read/payment:
start，首笔分值不得超过原总额；不创建收银台会话。创单用 merchantOrderNo、付款用 actionKey
幂等，任何失败都保留已有提交事实并允许原请求恢复。只是受理便利入口，当前不返回虚构渠道成功。渠道执行、后续 action
和后台恢复仍待接通；该批源码未验证。

## 29. 渠道绑定管理与新尝试选路

新增内部 ChannelBindingFacade：

| POST 路径（/api/payment/channel-bindings） | Command/Query                | SERVICE 权限                | 返回                    |
|--------------------------------------------|------------------------------|-----------------------------|-------------------------|
| /publish                                   | ChannelBindingPublishCommand | pay:payment:binding:publish | ChannelBindingChangeDTO |
| /suspend                                   | ChannelBindingSuspendCommand | pay:payment:binding:suspend | ChannelBindingChangeDTO |
| /revoke                                    | ChannelBindingRevokeCommand  | pay:payment:binding:revoke  | ChannelBindingChangeDTO |
| /detail                                    | ChannelBindingDetailQuery    | pay:payment:binding:read    | ChannelBindingDTO       |

仅 PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID（默认 pay-ops）可调用，传输复用受信 SERVICE JWT。发布命令固定
payee/bindingCode/channel/accessMode、ALIPAY_V1 或 WECHAT_V1 类型化身份及版本化凭证引用、能力、优先级和
expectedVersion/requestKey；不接原始私钥或 app_auth_token。DIRECT/服务商的身份组合由领域校验。银联当前拒绝发布，待明确机构/产品与
identity。

首次版本0发布；后续 CAS 当前 headVersion。相同 requestKey 跨 publish/suspend/revoke 不能复用；重放返回原操作已应用快照，detail
返回当前头。SUSPENDED 可发布新 revision 恢复，REVOKED 不能恢复原 code。头可变但历史 revision 不变，原付款/退款使用冻结版本，不能按当前头改收款身份。

PaymentRouteSelector 为内部 app 能力，不开放商户 bindingId 参数。它已接入 attempt 创建，读取原冻结 routingProfileRef
和允许的渠道/方式/接入模式，锁当前 ACTIVE 头、核对原 revision，再按方案 rules 顺序选择。无候选返回
PAYMENT_CHANNEL_BINDING_UNAVAILABLE；方案缺失/归属错误返回 PAYMENT_ROUTING_PROFILE_UNAVAILABLE。额度/风控/Y2
准备源码见下一节；真实渠道执行仍待完成。源码未运行验证。

## 30. 首笔支付尝试与额度准备实际契约

PaymentAttemptFacade.startAttempt 已注册 POST `/api/payment/executions/start-attempt`，指定 acquire SERVICE+pay:payment:
attempt:start，返回 SuccessResponse<PaymentExecutionDTO>。首笔 previousAttemptEvidenceId 为空，choice/inputRef
等于原受理命令；接口只建立固定 INIT attempt 与 PROCESSING 付款，不声明渠道提交/成功。原 requestKey 重放读取当前状态；新的
key 不能绕过活跃 attempt 进行盲重扣。安全换 attempt 尚待权威旧尝试查证和输入生命周期，不接受客户端 evidenceId 作为证明。

PaymentExecutionFacade 继续聚合各独立用例接口，Spring Controller 注入对应 Facade。Y1 已调用
PaymentRouteSelector，在原单服务权限和两级门禁锁内冻结实际 bindingRevision/requestNo。原 routingProfileRef
的不可变资源发布、归属校验与按序选路已补，见第31节；product 发布前跨服务资源验收仍待完成。

内部 PaymentSubmissionPreparation（无 HTTP）对固定 attempt 执行 risk → Reserve → BeginExecution → Verify → 本地 Y2。Reserve
参数来自原付款/冻结策略；Begin 的 key 固定 attempt/attemptId。新 PaymentLimitClient 使用专属 SERVICE JWT 和 tenant
配置，仅调用原额度公开 Facade 对应 HTTP 入口。对 operation/payment/attempt、epoch、金额/币种/策略/initiatedAt 逐项复核后，再锁原
payment/attempt、绑定输入并 CAS INIT→SUBMITTING。原始 permit 不存储、不返回商户，仅保存哈希；内存提交票据只给本地 CAS
赢家，不可持久重建或重放。

Y2 已提交视为可能发渠道；超时/崩溃/票据丢失不能换号或重发。默认风控明确 DEFAULT_ALLOW/NOT_CONNECTED；配置外部 provider
的错误不自动放行。DENY/超限/输入或额度过期仅在锁内确认从未跨 Y2 后生成本地未收款事实。显式关单新增相同边界，不能把已有
SUBMITTING 当本地失败。

该批只是首笔准备与提交权边界，真实渠道执行、结果动作、自动调度/查证/额度终态通知仍待完成；14个新测试方法及源码均未验证。

## 31. 不可变选路方案实际契约

RoutingProfileFacade：

| POST 路径                             | 请求                         | 指定管理 SERVICE 权限       | 返回                               |
|---------------------------------------|------------------------------|-----------------------------|------------------------------------|
| /api/payment/routing-profiles/publish | RoutingProfilePublishCommand | pay:payment:routing:publish | SuccessResponse<RoutingProfileDTO> |
| /api/payment/routing-profiles/detail  | RoutingProfileDetailQuery    | pay:payment:routing:read    | SuccessResponse<RoutingProfileDTO> |

指定 SERVICE 继续复用 PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID；商户/acquire 不可发布、查询管理资源或自行选择 bindingId。发布命令为
spec (profileRef/payeeMerchantId/rules) + requestKey。rules 是1—100条有序
bindingCode/channel/accessMode/method，优先级由数组顺序决定，不使用任意表达式或隐式兜底。引用和商户都是定义哈希的一部分，完全相同规则不可重复。

profileRef 在租户内唯一且不可覆盖；tenant/caller/requestKey 唯一。原键原定义重放返回首次
id/hash/时间，不重验当前绑定或改变其状态；原键改参数或新键覆盖旧 ref 返回 PAYMENT_IDEMPOTENCY_CONFLICT。首次发布只接受当前
ACTIVE 且归属匹配、具有声明能力的绑定。新定义使用新 profileRef。detail 要求给定 payee 与原定义一致。

原 Y1 选路不再忽略冻结 routingProfileRef：缺失/归属错误返回
PAYMENT_ROUTING_PROFILE_UNAVAILABLE；规则不能扩大原合约允许模式/渠道/方式。按显式候选顺序选择当前活跃 binding
revision，全部不可用返回 PAYMENT_CHANNEL_BINDING_UNAVAILABLE。已建立的 attempt 重放不重新选路。该版本资源运行时验证不等于
product 发布前跨服务验收或真实渠道可用性证明；后两项仍待完成。源码及新增测试未运行验证。

## 32. 内部渠道结果应用与事件生产

PaymentResultService.record 为应用内部用例，不新增公共 HTTP 成功确认、人工 evidenceId 记账或原始回调接收端点。PaymentChannelGateway
的支付 submit/query/close 已返回
PaymentObservation（真实适配器尚待实现），包含完整固定身份、金额/CNY、结果、可空交易号/渠道成功时间、source/receiptKey/sourceDigest。退款方法仍使用独立
ChannelRefundOutcome 契约。原 ChannelPaymentOutcome 是纯领域迁移输入，由本地用例绑定持久 evidenceId。

未来渠道回调只能先完成验签/解密/账户校验再调用该内部入口，并在事务提交之后判断返回
PaymentResultApplication：APPLIED/IGNORED 表示已保存回执，CONFLICT 表示差错事实，不能伪装普通成功
ACK。身份/金额不一致和交易号归属冲突不会更新正常付款金额。NOT_FOUND、网络错误、未认证响应均不能证明
NO_COLLECTION；资金已退回需独立补偿。

终态事件复用 PaymentOutcomeEventDTO 和 EventEnvelope。类型为
pay.payment.outcome.acquire.v1、pay.payment.outcome.limit.v1、pay.payment.outcome.realtime.v1、pay.payment.outcome.risk.v1；每目标独立
deliveryId，共用首个终态 evidenceId 作为 businessEventId。完整原 payment/order/allocation/limitOperation/payee/金额币种、正源版本和
executionFenced 冻结在事务消息中。事件生产者字符串只作结构校验，后续消费仍需受信传输身份及各自权威事实核验。投递
handler、接收方 Inbox、商户通知签名/ACK 尚未完成，不能将入队当作送达或宣称四服务已联通。新增源码及测试未执行验证。

## 33. 额度/风控终态投递实际契约

PaymentLimitClient 新增 confirm (tenant, LimitConfirmCommand)、release (tenant, LimitReleaseCommand)，经
limitcenter/confirm|release 命名 HTTP 操作调用原 /api/limit/settlement/confirm|release。复用 payment 专用 SERVICE JWT，需
pay:limit:confirm|release，出站 tenant 显式固定；接收方反查付款权威事实。没有新增商户上报成功的接口。

本地 limit/risk Outbox handler 核对已登记 PROCESSING 队列、目标/schema、原终态/版本/证据，再在本地锁释放后调用外部服务。释放
reason 固定 PAYMENT_FAILED/CLOSED，原 businessEvent/付款版本/证据在重试中不变。确认 ACK 核对 CONFIRMED、原
operation/payment/policy/initiatedAt；释放 ACK 核对版本/证据/fenced，reservation 可空或原 RELEASED/EXPIRED。错误回执或调用失败不完成任务。

RiskOutcome 新增 tenantId/businessEventId，consultation/decision 来自原持久尝试，paymentState 仅
SUCCEEDED/FAILED/CLOSED、sourceVersion>0、evidenceId 必填。RiskOutcomeGateway.confirm 是至少一次调用，外部实现必须按
tenant/businessEvent 去重并校验同源版本/事实。未咨询的本地关闭不调用；DEFAULT_ALLOW/NOT_CONNECTED
仅来自实际默认咨询，不能吞掉已配置外部异常。外部风控协议实现仍后置。acquire/realtime 消费与商户通知签名/ACK、重放管理尚未完成；新测试源码未执行。

## 34. acquire 付款事件语义 ACK 与回查权限

| 接口                                     | 信任要求                                                                    | 结果                                                                     |
|------------------------------------------|-----------------------------------------------------------------------------|--------------------------------------------------------------------------|
| POST /api/acquire/events/payment-outcome | 指定 payment SERVICE + pay:acquire:payment:outcome                          | SuccessResponse<PaymentOutcomeAckDTO>，本地原子提交后返回                |
| POST /api/payment/facts/payment          | 指定 acquire SERVICE + pay:payment:acquire-facts，限原 callerServiceId 付款 | 原 PaymentSettlementFactsDTO，包含原 operation/payee/状态/版本/证据/封禁 |
| 同上事实接口的既有角色                   | 指定 limitcenter SERVICE + pay:payment:limit-facts                          | 保留原额度查证权限                                                       |

PaymentOutcomeFacade.accept 现返回语义 ACK，字段为
deliveryId、businessEventId、paymentId、paymentVersion、evidenceId、orderVersion、accepted。orderVersion 为首次接收时观察的订单版本；同
delivery 原参数重放返回原 ACK。不同 delivery 的同一资金事实仍由原 allocation outcome receipt 去重，不能重复加 paid/减
reserved/确认费用。事件请求不包含可自行授予的 merchant owner scope。

回查发生于本地锁外，接收端不凭事件标签/证据字符串直接记账。原渠道实际事实由 payment Y3 和签名适配器负责，acquire
再核对原冻结命令、operation/payee、金额/币种、终态/版本/证据。ACK 必须源标识完全相符才完成发送目标，网络超时保留原消息重试。令牌权限、单出站租户与路径配置见
payment/acquire README。新增源码未运行验证，realtime 和商户通知等尚未完成。

## 35. Realtime 完整快照与商户读模型实际接口

投影接口前缀 /api/realtime/projections：/order、/payment、/refund、/refund-summary，分别接既有
OrderSnapshotEventDTO、PaymentSnapshotEventDTO、RefundSnapshotEventDTO、OrderRefundSummaryEventDTO 的
EventEnvelope。TradeProjectionFacade 四方法现返回 ProjectionAckDTO，包含原
delivery/businessEvent/producer/aggregate/sourceVersion、接收时 projectedVersion、APPLIED/DUPLICATE/STALE 和 accepted。仅指定
acquire/payment SERVICE + 对应 pay:realtime:projection:* 权限可写。

付款终态第四目标实际使用 pay.payment.snapshot.realtime.v1，取代旧不完整的
outcome.realtime；来源事务已经冻结原尝试渠道、模式、原受理/成功时间和正源版本。realtime
不进行同步来源查询。其他三类接收已补、自动生产仍待接入，不能把预留端点当作所有来源已就绪。

查询前缀 /api/realtime/trades：/detail、/orders、/payments、/refunds，复用原 Query 类型和规范
SuccessResponse/PageResponse，只接受可信 merchant_id 与 pay:realtime:trade:read。请求的 payee 必须等于可信商户，SERVICE/无范围
OPERATOR 不获得任意读取权限。订单未到不阻止分页查询付款，detail 不伪造父订单。

TradeDetailDTO 改为订单头、独立退款汇总与源快照时间；移除无上限 payments/refunds 内嵌数组，用独立分页接口查询。TradeOrderDTO
的退款额/净额/退款源版本可空并新增 amountsConsistent，缺退款汇总不是0；来源暂时不一致时不裁零。TradePaymentDTO/TradeRefundDTO
分别补原 initiatedAt/refundCreatedAt，分页按这些业务时间和稳定ID倒序，默认1/20、上限100。所有变更为前期直接契约重构，无旧协议适配。新增源码未运行验证。

## 2026-10-23 acquire 到 realtime 的订单版本投递

生产端 OrderSnapshotEvents 在订单版本事务内生成 pay.acquire.snapshot.realtime.v1
完整信封，producer=acquire、aggregate=orderId、aggregateVersion=精确 rowVersion；occurredAt/sourceUpdatedAt
使用落库更新时间，orderCreatedAt 使用原始审计创建时间。不同版本具有独立
deliveryId/businessEventId，框架重试沿用原负载和身份；普通请求回放不生成新事件。

发送端使用 acquire SERVICE JWT（pay:realtime:projection:order）调用现有 POST /api/realtime/projections/order，不能转发商户
JWT。原版本已被较新快照覆盖时接受匹配身份的 STALE ACK，projectedVersion 必须不小于 sourceVersion；异常 ACK 不完成 Outbox
目标。重试不读取当前主订单，不强求实时查询已先看到所有历史版本。延期只推进当前事件已有字段和源版本，未向事件加入订单截止字段。新源码尚未运行验证。

## 2026-10-23 RefundFacade 的首期实现边界

POST /api/payment/refunds/create 与 /detail 使用可信 MERCHANT/MERCHANT_APP JWT：pay:payment:refund:create/read、签名
merchant_id 与当前 tenant。payeeMerchantId 不能由 body 提升到其他商户，SERVICE/OPERATOR 不获得绕过入口。创建返回 INIT 固定
FIFO 计划，子项 INIT、版本0，表示本地受理与容量已预占。相同 tenant/payee/merchantRefundNo 与相同 order/amount/reason
返回原计划/当前进度，窗口后来到期也不重新规划；改参数返回 PAYMENT_IDEMPOTENCY_CONFLICT。

窗口按每笔原成功时间计算，精确截止不可新增分配；过期部分不参与 eligible FIFO。partialAllowed=false 要求当前窗口内全部可退且无占用，true
可手动输入部分金额。合约停单/原 parent_gate FENCED 不撤销原成功事实的独立退款资格；不回写 acquire 状态或 gross 已付金额。非
FIFO/显式分配未实现时明确拒绝，不静默改为 FIFO。串行退款渠道执行、查询恢复和结果事件尚未接通，新入口不是渠道退款成功证明。新增实现与测试源码未运行验证。

## 2026-10-23 退款内部执行与观察契约

无新增商户记账端点。RefundFacade 创建仍返回 INIT 原计划；detail 可读取后续 PROCESSING/UNKNOWN
子项与申请累计。RefundExecutionService.progress 是内部单步编排，后续由可信持久任务驱动；真实渠道 binder
暂缺，缺能力时明确拒绝且不设置已提交标记。INIT→PROCESSING 的已提交本地边界只给一次首次网络票据，重复调用只 query
原请求号，UNKNOWN 不释放金额或自动改退其他付款。

PaymentChannelGateway 的 submitRefund/queryRefund 改为完整 RefundObservation，需验签/解密及实际原账户核验后生成，包含
refundId、原 ChannelPaymentIdentity、退款请求号、原支付交易号、金额、kind、成功退款引用/时间、观察来源/稳定键/源摘要。PreparedChannelRefund
补原成功付款金额和实际交易号。UNKNOWN/ACCEPTED 不携带成功流水/时间；查询未找到、超时或未认证响应不能定义为
FAILED_WITH_NO_REFUND。

RefundResultService 核对原持久身份/金额及提交标记后处理，观察键重放沿用原事实；同键改金融事实、终态矛盾或渠道退款引用冲突持久记录
CONFLICT，不重复退款/释放容量。渠道正/负终态都不可被商户提供的 evidenceId 直接生成。结果与提交的新增源码及测试未运行，实际渠道适配、自动恢复与下游退款事件仍待接入。

## 2026-10-23 退款投影生产与固定大小汇总契约

RefundSnapshotEventDTO 在 INIT、PROCESSING、UNKNOWN
与确定终态的新子项版本后，由原事务冻结原审计创建时间、真实渠道成功时间及状态/金额；同版本请求重放/忽略观察不再生产。OrderRefundSummaryEventDTO
现为 orderId/payeeMerchantId/currency/refundedMinor/refundReservedMinor/refundSummaryVersion 六字段，完整替换金额快照，不含历史申请列表；原
RefundRequestSummaryDTO 与 realtime 申请进度模型已删除。逐笔退款继续独立分页投影，申请详情使用 RefundFacade。

金额汇总由 payment 同库 parent 锁协议下的独立聚合产生，规划增加占用、成功确认已退、确定失败释放占用；UNKNOWN
不推进金额版本。两类完整信封分别写本地 Outbox，事务外调用 /api/realtime/projections/refund、/refund-summary。payment SERVICE
凭证需 pay:realtime:projection:refund/refund-summary 两个具体权限，不能用商户凭证替代。ACK 精确匹配原身份/入站版本并允许高版本已覆盖时的
STALE，失败保留独立目标重试。

渠道成功时刻与本地审计创建时刻各自保留原值，不能通过裁时间或拒绝精度差来伪造同一时钟。生产/投递源码已接入原服务，真实渠道、自动任务、数据库/消息端到端和最新源码尚未运行验证。费用/额度恢复和商户退款通知事件另行接入。

## 2026-10-23 费用退款内部 gRPC 契约

当前实际传输遵守 [RPC 规范](RPC.md)，本节替代前稿中费用退款内部 HTTP 占位描述。

| 方法                     | 入参                                            | 返回                       | 可信身份与权限                                       |
|--------------------------|-------------------------------------------------|----------------------------|------------------------------------------------------|
| PaymentRpc/RefundSummary | IdQuery.id=orderId                              | OrderRefundSummaryEventDTO | 原 acquire SERVICE + pay:payment:refund-summary:read |
| AcquireRpc/FeeRefund     | EventEnvelope&lt;OrderRefundSummaryEventDTO&gt; | FeeRefundAckDTO            | 配置的 payment SERVICE + pay:acquire:fee:refund      |

金额消息是 orderId/payeeMerchantId/currency/refundedMinor/refundReservedMinor/refundSummaryVersion 六字段完整快照；只用
refundedMinor 作为退费基数，所有金额/版本 wire 字段为 int64。事件固定
producer=payment、eventType=pay.payment.refund.fee.v1、schemaVersion=1，aggregateId=orderId，aggregateVersion=refundSummaryVersion>
0。

ACK 六字段为 deliveryId/businessEventId/orderId/sourceVersion/appliedVersion/accepted。sourceVersion
精确对应原事件，appliedVersion 是接收方验证并接受的源金额版本，可大于 sourceVersion；费用存储也可能已更新至更高版本。投递方不要求旧消息阻塞等待精确旧版本查询。

新 delivery
接收先事务外查询权威事实。相同源版本必须完整匹配消息；较新权威版本的累计成功退款不得低于原消息且不超过原固定总额，再按最新累计执行原冻结费用规则。查询与接收都校验原订单/收款商户/币种和服务所有权。接收本地事务内持原订单锁，费用基数/正差额与接收回执原子提交。退款已到、付款结果未到时仅留基数，原付款费用确认时补算。

tenant/deliveryId 是接收身份，同身份完整参数变化返回 IDEMPOTENCY_CONFLICT；原请求重放直接返回首次
ACK。查询不可用、新消息权威事实不符或本地落库失败均不返回 accepted ACK，交由框架 Outbox 重试。realtime 目标与费用目标的
delivery/businessEvent 独立，不相互确认。商户不能自行提交金额依据或退费成功事实。

## 2026-10-23 成功退款额度恢复实际契约

| 方法                         | 入参                      | 返回                     | 权限                                                  |
|------------------------------|---------------------------|--------------------------|-------------------------------------------------------|
| PaymentRpc/RefundFacts       | IdQuery.id=refundId       | RefundSettlementFactsDTO | 配置的 limitcenter SERVICE + pay:payment:refund-facts |
| LimitCenterRpc/RestoreRefund | LimitRefundRestoreCommand | LimitRestoreDTO          | 原 payment SERVICE + pay:limit:refund:restore         |

`RefundSettlementFactsDTO` 七字段为
refund（RefundOutcomeEventDTO）、paymentAmountMinor/paymentVersion/paymentEvidenceId/paymentAttemptId/paymentInitiatedAt/paymentRefundVersion。refund
包含原子项/申请/付款/订单/operation/商户、金额/币种/状态/源版本、该付款当前累计已退和终结证据。只为已验证 SUCCEEDED
退款提供该事实；缺记录不伪造零。与 acquire 整单费用基数不同，额度按原单笔 payment 及其 reservation 恢复。

`LimitRestoreDTO`
当前十二字段：operationId/paymentId/refundId/refundVersion/businessEventId/refundEvidenceId/cumulativePaymentRefundedMinor/verifiedPaymentRefundedMinor/verifiedPaymentRefundVersion/result/restoredAmountMinor/restoredPaymentCount。前七项绑定原请求，第八/九项是权威回查累计和版本。result
为 RESTORED 或 NO_ACTION；金额/笔数为首次接收该成功退款时实际产生的逻辑恢复差额，不是跨窗口求和或当前总余额。

成功子退款的 LIMIT 事件只生产一次，重试保留原 command 身份和累计。接收先事务外查证；较新权威累计可高于消息累计，再按原冻结规则推进所有原
item 的恢复高水位。tenant/refundId 已有回执时核对完整命令摘要和可信来源，原参数回放首次 ACK，改参冲突。bucket/item/回执同事务，Confirm
未到仍重试，不能提前 NO_ACTION。合约停止或新规则变更不阻断旧成功事实处理。

前稿四字段 `LimitRestoreDTO` 占位已直接替换，无兼容构造器。当前方案未执行编译、测试、迁移或真实 gRPC 验收。

## 2026-10-23 退款自动恢复调用边界

商户仍使用原 RefundFacade.create/detail，不新增“任务完成即退款成功”的 API。create 新计划必须与恢复游标和首轮 DelayedTask
同事务登记；原商户请求重放既有计划，不额外排队。detail 展示实际退款事实，轮询任务或异常重试不改变状态含义。

内部 RefundRecoveryService/RefundRecoveryTaskHandler 只由持久任务在可信租户执行；不依赖调度线程遗留的商户/JWT
身份。每轮使用冻结原计划和渠道退款号，渠道网络调用在本地事务外，成功/失败事实继续由 RefundResultService
验证并落账。恢复游标代次不作为付款/退款成功证据或渠道幂等键。

处理中自动登记后继，正常等待不耗尽异常次数；异常预算耗尽保留 FAILED 任务及原资金占用，不给商户返回假成功或释放额度。组件内部受控
retryFailed 可恢复同代，专门的运维权限/查询/重放 API 尚未提供。未实现退款取消；通用任务 cancel
也不是业务取消协议。真实渠道和端到端运行仍待后续实现及验收。

## 进程内渠道统一 API（2026-10-23）

channel-api 独立提供
ChannelPaymentFacade：validate/submit/query/recover/close/canRefund/submitRefund/queryRefund/recoverRefund。策略实现
ChannelProvider，声明唯一 Channel，由 channel-core 注册路由。调用上下文由 payment 基础设施桥接解析可信租户与原 binding
revision，API 不允许任意上游网关地址或未解析凭证。该接口是应用内部插件契约，不是新增商户 HTTP/RPC
入口。详细输入、校验和未完成范围见 [渠道模块](../channel/README.md)。

支付宝普通退款的 channelRefundTransactionNo 为验签后原 trade_no/out_request_no 自然键的稳定规范引用（alipay:
sha256），不声称渠道颁发独立流水；原元组仍完整保存在退款/claim。只有查询核验 REFUND_SUCCESS、身份、单次金额和成功时间后允许写入；退款提交
10000 仅为 ACCEPTED。

进程内支付 query/close 已改为 ChannelPaymentTarget (identity, amount)，不保留旧重载；统一 Facade
校验所有支付操作返回的原身份与金额。PaymentObservation.Source 增加 CLOSE_RESPONSE，支付宝该来源代表待付款查询与关闭成功双响应组合证据；单独
TRADE_CLOSED 不构成 NO_COLLECTION。该变更不新增商户 HTTP/RPC 入口。

## 收款提交契约更新（2026-10-23）

PaymentExecuteCommand 必须携带冻结的原订单 subject、payableUntil，gRPC WirePaymentExecuteCommand 同步为 subject (12)
/payable_until (13)。原订单截止由 acquire 当前持久 orderDueAt 冻结，重放按原意图，不因受理或后台重试延长。

JSAPI 输入 ChannelIdentityInteractionDTO 定义为
channel、identifier（隐藏诊断，最长128）、appScope、identityType（USER_ID/OPEN_ID）。它表示已完成渠道授权后解析出的标识，不接受
OAuth 授权 code 作为买家 ID；商户自行完成所属应用授权。Wire 字段2改为 identifier，新增 identity_type (4)
，不提供兼容分支。平台仍无消费者用户资源。

进程内 submit (context, ChannelPaymentRequest) 返回
ChannelPaymentSubmission（原身份/金额＋可空观察＋可空动作，至少一项）。AlipayJsapiChannelAction 映射 INVOKE_JSAPI +
AlipayJsapiActionDTO，不构成成功；有动作的观察只能 ACCEPTED。付款码确认成功统一使用查询路径取得渠道成功时间。动作持久化及
payment 详情/outcome/受理重放的授权返回已接源码；主链自动驱动已有源码；托管 cashier 页面仍待实现。

### 动作快照读取约束

不增加外部写动作或上报成功 API。内部 recordSubmission 只接收原 PreparedChannelPayment 与渠道 Facade 返回的
ChannelPaymentSubmission；有动作时必须有 ACCEPTED 观察，支付宝 JSAPI tradeNo 必须等于观察中的
channelTransactionNo。观察/动作同事务接收，快照冲突则整体失败。

PaymentExecutionDTO.action 由原 SERVICE 身份授权的详情/outcome 与幂等重放返回。读取在原父门禁、单付款门禁下进行，只暴露当前
active attempt/执行 epoch，支付 PROCESSING 且门禁 OPEN、动作未到期且不超过原 payableUntil；其余状态返回基础
DTO，不回传历史动作。重复受理保留首次动作，不借重放创建新交易或延长支付窗口。

ChannelActionCodec 属于 channel-api 的进程内契约，当前严格实现位于 channel-core。payment 的客户端 DTO
映射只投影动作表示，不负责渠道路由、签名或厂商协议。此接线仍待获准后的编译/事务/运行验收，自动发送已有源码，仍未执行运行验证。

### 自动执行与本地预检

accept 返回已持久受理后，payment 自身恢复任务自动创建原首笔并驱动提交/查询，无需 acquire 额外调用 startAttempt
才能启动。startAttempt 保留为既有 SERVICE 入口；与自动任务共享同一首笔创建逻辑，原 active attempt 已存在时不得创建第二笔。任务不使用外部请求伪装
SERVICE 身份，不暴露执行/回执写入 HTTP API。

内部 ChannelPaymentValidation 不带 epoch，仅用于同一 Provider 的本地参数/能力校验；通过不代表已受理或成功。真实 submit 仍需原
Y2 ticket；重试只能原号查询或在关闭门禁下收敛。正常轮询代次及恢复游标不是支付 API 的版本或渠道重试凭证。所有新增执行、任务与
API 接线尚未编译/测试或部署。

### 原交易动作恢复的进程内 API

`ChannelPaymentFacade.recover(context, ChannelPaymentRecovery)` 返回 ChannelPaymentSubmission，必须含 QUERY_RESPONSE
观察。请求只包含原 identity、Money、method、executionEpoch 与 payableUntil，不接收明文付款输入、任意渠道 URL、preparedInputRef
或 ticket。它通过 Provider SPI 查询同一交易并重建支持的有效动作，不是提交/重试扣款接口。

`PaymentResultService.recordRecovery` 仅供内部自动恢复编排调用，拒绝非 QUERY_RESPONSE；原观察与首份动作复用 Y3
同事务记录。客户端继续用现有 PaymentExecutionDTO.action 读取，无新增商户 HTTP/gRPC 端点。recover 与 PreparedChannelPayment
共享只读元数据契约不等于可以生成 Y2 ticket；原 UNKNOWN/终态和门禁可见性规则保持不变。本批源码和测试源码尚未执行验证。

### 渠道原生回调 Facade

PaymentChannelCallbackFacade.receive (endpointRef, ChannelNotificationInput) 是外部渠道协议入口，HTTP 为 POST
/api/payment/channel-callbacks/{endpointRef}；它不提供内部 gRPC 的成功写入方法。当前只收有界 form body，不读取 query/body
tenant 参数或租户请求头，返回 ChannelNotificationAcknowledgement 的原生 contentType/body。空包、超限或未登记 endpoint 不产生
success；已识别协议处理失败返回策略固定 failure。

ChannelNotificationFacade.identify 返回不可信 ChannelNotificationHint；verify 使用原
ChannelExecutionContext/ChannelPaymentTarget，返回已验签且核对账户、请求号、金额的触发元数据。只有随后查询产生的资金事实能够进入
Y3，回调状态与通知金额本身不是商户可用的记账 API。服务端按可信 tenant/bindingRevision 配置 JSAPI notify_url，商户支付命令不可指定。

CALLBACK 回执复合键绑定通知 ID 和权威查询事实；成功 ACK 必须在非冲突的 Y3 返回之后，不因解析或验签通过即
ACK。新接口/配置、边界与测试源码尚未执行验证，其他通知协议仍待实现。

### 微信 JSAPI 动作实现（2026-10-24）

channel-api 新增 WechatJsapiChannelAction，字段 appId/timeStamp/nonceStr/packageValue/signType/paySign/expiresAt，core 以
WECHAT_JSAPI 白名单快照保存。payment MapStruct 映射为已有 INVOKE_JSAPI/WechatJsapiActionDTO（外部字段 package）；复用现有动作查询授权和
Protobuf 分支，无新增内部
RPC。该动作只表示预支付会话可唤起，支付成功仍以原渠道查询的验签事实确定。具体能力、必需配置和未实现范围见 [微信插件](../channel/channel-wechat/README.md)。

### 微信普通退款执行（2026-10-24）

统一 submitRefund/queryRefund 已接微信策略，PreparedChannelRefund 继续使用原身份、原交易号、原金额、本次退款金额与固定
channelRefundNo，不增加商户可注入的退款成功入口。普通退款受理不等于成功；只有查询 SUCCESS/完整原号与金额/渠道成功时间产生成功金融事实，CLOSED
的失败证明沿原结果接收流程处理。外部回调、异常退款处置及未达渠道时的原号安全重提待后续。细节见 [微信退款协议](../channel/channel-wechat/README.md)。

### 加密通知协议修订（2026-10-24）

ChannelNotificationInput (contentType,headers,body) 保留有界多值签名头与原始字节；identify 改为接收可信原
ChannelExecutionContext，payment Gateway 传原 channel/bindingRevisionId 并按可信租户解析。ChannelNotificationAcknowledgement
(httpStatus,contentType,body) 返回渠道原生状态，微信支付通知失败必须为非 2xx；支付宝保持自身 ACK
规则。删除旧构造器/参数，调用方与测试同步。该契约只供应用内渠道插件及外部回调控制器使用，不新增内部 RPC
或任意金融写入口。微信通知只触发原号查证，金融事实仍沿 Y3 接收。

### 原生退款通知入口（2026-10-24）

RefundChannelCallbackFacade.receive (endpointRef,input) 承接 /api/payment/channel-callbacks/{endpointRef}/refund 原生
JSON。统一通知 Facade 的 identifyRefund (context,input) 返回 ChannelRefundNotificationHint，verifyRefund
(context,PreparedChannelRefund,input) 返回已认证的原退款触发元数据；这些都不能直接写金融终态。payment 通过原退款查询与既有
RefundResultService 接收 CALLBACK 事实，ACK 仅在非冲突事务提交后成功。微信退款 URL 使用独立可信 refund-notify-urls
目录，不从商户请求取 URL。不新增内部 HTTP/RPC 或商户成功写入口。

### 原号退款恢复 API（2026-10-24）

ChannelPaymentOperations/PaymentChannelGateway 新增 recoverRefund，仍接同一
PreparedChannelRefund，不提供商户选择新退款号或重写金融参数的入口。queryRefund 保持只读，回调沿用纯查询；恢复任务明确使用
recoverRefund，渠道策略仅在各自原号幂等保证和精确验签条件下重复原请求。返回为 QUERY_RESPONSE 或非终态
SUBMIT_RESPONSE（ACCEPTED/UNKNOWN），Facade 与 app 双重拒绝 CALLBACK/终态提交来源，不把受理响应替代退款查证。无兼容重载、额外
RPC、业务表或 DDL。

## 微信 H5 场景与统一跳转（2026-10-24）

纯 API 的 PaymentStartCommand 与 DirectPayCommand 选择 WECHAT/H5 时，传类型化 H5_SCENE；无需 CheckoutSession。示例为已有订单的付款发起：

```json
{
  "orderId": "order-001",
  "actionKey": "pay-001",
  "amountMinor": 1000,
  "choice": { "channel": "WECHAT", "method": "H5" },
  "interaction": {
    "type": "H5_SCENE",
    "payerClientIp": "203.0.113.10",
    "sceneType": "WAP"
  }
}
```

payerClientIp 必须由已认证商户服务端提供真实付款终端的 IPv4/IPv6 字面量，不能填商户服务器地址、转发头链或主机名。sceneType
为 WAP、IOS、ANDROID，表示付款环境，不是新增原生 APP 支付产品。公网示例地址仅为文档保留地址，实际请求必须提供真实终端
IP。拒绝回环/未指定/组播、端口、方括号和 zone id；校验无 DNS 访问。

acquire 在原 A2 占用后调用仅限受信 SERVICE 的 PaymentInputFacade.prepare，敏感场景经 KMS 加密与 keyed 指纹固定；原意图只保存不透明引用。改变
IP、场景、金额或原付款作用域不能覆盖原输入。同 actionKey 重放仍沿用原 allocation/payment；H5 无场景的 NONE 不满足微信渠道要求。

gRPC 的 WirePaymentInteractionDTO 新增 option3=4，WireH5SceneInteractionDTO 的 payer_client_ip=1、scene_type=2
均要求存在，场景符号严格限定。输入与输出仍走既有 prepare/accept/detail API，不增加厂商专用业务入口。

异步执行后，PaymentExecutionDTO.action 可返回 REDIRECT、expiresAt 与 RedirectActionDTO.redirectUrl（JSON 数据类型为既有
REDIRECT）。地址只来自微信验签响应，限定当前官方 HTTPS
跳转主机/路径，原编码与参数原样保留；商户不能自填跳转地址、附加任意参数或把页面回跳当成收款成功。期限为原订单截止与调用开始后五分钟的较早值；原
action 快照和重放不延长地址寿命，过期不展示。支付结果查询/回调沿用原渠道与原绑定，不在查询恢复中重新下单获取地址。

该路径已有源码和相关测试源码；尚未运行编译、测试、DDL、H5 域名/Referer 与真实浏览器/渠道验收。托管收银台、支付宝 H5
和安全刷新/丢失响应恢复仍为后续工作。

## 商户通知 acquire 实际接线（2026-10-24）

PaymentSucceeded 的 sourceVersion 为原 paymentVersion，aggregateType=PaymentOrder；OrderFullyPaid/OrderClosed 使用
AcquireOrder/orderVersion。data 固定包含 orderId、可空
paymentId、payeeMerchantId、amountMinor/CNY、orderPaidAmountMinor、orderAmountMinor、orderState。单笔成功金额为本笔金额，订单事件金额为订单总额；金额汇总是该事件时刻的快照，不保证跨聚合通知全序。

eventId/body/occurredAt/原策略不可变，新增投递表与首任务同源事务，不对外暴露任意事件入队或发送 URL
接口。任务和签名细节见 [acquire 通知说明](../acquire/README.md)。语义 ACK 严格为2xx及两字段 {"eventId":"原事件","result":
"SUCCESS"}，每次使用新的签名 nonce/timestamp，其他参数保持原冻结值。投递未 ACK 不能回滚已提交的收款事实，也不修改付款状态。

目录以 tenant+不可变版本引用定位且绑定商户；实际请求只使用审核的固定 HTTPS POST operation，无 query。原应用/受众/显式 KMS
key 版本、URI 和配置指纹绑定，不能由 payload 任意选密钥、目标或修改旧事件的路由。原生延迟任务的后继在发送前持久化，崩溃重放保持原事件；达到次数/窗口后
EXHAUSTED，运维审计重放接口、退款商户通知及目录发布验证后续补齐。新增源码和测试源码未执行验证或实际投递。

## 退款商户通知实际契约（2026-10-24）

| 内部方法                      | 入参                                        | 返回                     | 身份与权限                                               |
|-------------------------------|---------------------------------------------|--------------------------|----------------------------------------------------------|
| AcquireRpc/RefundNotification | EventEnvelope&lt;RefundMerchantEventDTO&gt; | RefundNotificationAckDTO | 配置的 payment SERVICE + pay:acquire:refund:notification |

payment 在原退款结果事务冻结独立 `pay.payment.refund.merchant.v1` Outbox，与费用、额度、realtime 分别投递和确认；非终态不生产商户退款事件。源数据包含
aggregateType、refundId、refundRequestId、paymentId、orderId、payeeMerchantId、merchantRefundNo、amountMinor、currency、state、succeededMinor、failedMinor、sourceVersion、succeededAt。信封
producer=payment/schemaVersion=1，aggregateId/version 必须匹配源数据。金额 CNY/int64，终态满足
succeededMinor+failedMinor=amountMinor。

| 商户 eventType         | aggregateType / aggregateId     | 语义                                                                                           |
|------------------------|---------------------------------|------------------------------------------------------------------------------------------------|
| RefundSucceeded        | RefundOrder / refundId          | 此固定子退款成功，全额为 succeededMinor，保留原渠道成功时刻                                    |
| RefundFailed           | RefundOrder / refundId          | 此固定子退款已确定无退款，金额全计入 failedMinor；不把未知状态当失败                           |
| RefundRequestCompleted | RefundRequest / refundRequestId | 固定分配全部完成，SUCCEEDED/FAILED/PARTIAL_SUCCEEDED；子退款与付款 ID 为空，无单一 succeededAt |

商户外部信封延续 eventId/eventType/schemaVersion/producer/aggregateType/aggregateId/aggregateVersion/occurredAt/data，退款
producer=payment；由 acquire 按原订单冻结的目标、签名身份和重试条款发送。data 不包含 sourceVersion（使用外层
aggregateVersion），其余为上述最小退款业务字段。amountMinor 是此子退款额或申请固定总额，不是减去退款后的原支付金额。订单和付款成功金额保持
gross，不改生命周期。

eventId 为原租户、商户事件类型、源聚合 ID 与版本的确定性 SHA-256；与内部 deliveryId/businessEventId 及 native task ID
分开。不同子退款不能比较版本后丢弃；请求完成和子退款事件也无全局到达顺序，商户以 eventId 去重、按各自 aggregateId/version
收敛。

内部 ACK 六字段为 deliveryId/businessEventId/aggregateId/sourceVersion/merchantEventId/disposition。ENQUEUED 表示 acquire
的回执、固定通知和首轮任务已同事务登记；DISABLED 表示原订单合约禁用通知。二者都不等于已送达，实际商户仍需返回严格的 2xx 与
`{"eventId":"原事件ID","result":"SUCCESS"}`。重试不重建正文、不切换原目标/密钥版本、不延长原结果事件窗口；每次签名使用新
nonce/timestamp。

接收侧只接受已认证源事实投影，核对原订单/商户/CNY、原 gross 支付额与子退款的 CONSUMED
allocation；付款投影未到则重试。此用例不执行金融写入，也不引入同步反查的跨服务可用性依赖；退款真实性仍由 payment
原金融结果/证据事务负责。原渠道成功时间和源审计时间各自保留，不能因两个系统时钟不同伪造先后关系。退款通知没有新增商户可调用的成功写入或登记
HTTP 入口。

生产、投递、接收、类型化正文校验与测试源码已补；未运行编译、测试、DDL、真实 RPC 或通知发送。

## 商户通知查询与运营唤醒（2026-10-24）

| 外部 HTTP 方法                         | Facade                                     | 入参                              | 返回                                               |
|----------------------------------------|--------------------------------------------|-----------------------------------|----------------------------------------------------|
| POST /api/acquire/notifications/detail | MerchantNotificationQueryFacade.detail     | IdQuery.id=deliveryId             | SuccessResponse&lt;MerchantNotificationDTO&gt;     |
| POST /api/acquire/notifications/page   | MerchantNotificationQueryFacade.page       | MerchantNotificationPageQuery     | PageResponse&lt;MerchantNotificationDTO&gt;        |
| POST /api/acquire/notifications/wake   | MerchantNotificationOperationsFacade.wake  | MerchantNotificationWakeCommand   | SuccessResponse&lt;MerchantNotificationWakeDTO&gt; |
| POST /api/acquire/notifications/wakes  | MerchantNotificationOperationsFacade.wakes | MerchantNotificationWakePageQuery | PageResponse&lt;MerchantNotificationWakeDTO&gt;    |

这些是商户/运营 HTTP 入口，不新增内部业务 HTTP 或 gRPC 协议。Facade 分页复用 PageDTO，HTTP 分页保持顶层 data 数组和
pageInfo；响应均 no-store。商户 MERCHANT/MERCHANT_APP 凭证需 `pay:acquire:notification:read`，merchant_id 来自已验证
JWT。详情只返回同商户资源，其他商户为 NOT_FOUND；分页必须显式 payeeMerchantId 且与 JWT 匹配，不能用请求扩展商户范围。父商户跨子商户查询不隐式放开。

运营 OPERATOR 或配置的 SERVICE（默认 pay-ops，主体须等于 service_id）需要 `pay:acquire:notification:manage`
。只有这两类可信身份可唤醒和读取原因/操作者审计；普通商户即使有同名字权限也不能调用。运营查询仍限认证租户，分页按显式单商户过滤。缺失权限、非配置的
SERVICE、混合商户/运营声明均拒绝。ACQUIRE_NOTIFICATION_OPERATIONS_SERVICE_ID 可配置服务标识，具体令牌由现有发行方提供，本轮不签发。

MerchantNotificationPageQuery 支持 payeeMerchantId、可选 orderId/eventId/state、occurredFrom（含）/occurredTo（不含）和
page/size；page 从1开始、size最多100。时间按系统本地语义查询，要求1970—9998年、最多微秒精度、from<to。按 occurredAt DESC/id
DESC 排序，相同时间有稳定次序；这是查询时的普通分页，不承诺并发写入下的全库快照。

MerchantNotificationDTO 只返回
id/eventId/orderId/payeeMerchantId/eventType/aggregateId/sourceVersion/state/attemptCount/maxAttempts/occurredAt/expiresAt/nextAttemptAt/acknowledgedAt/rowVersion。INIT/PENDING/EXHAUSTED
都不表示送达；只有严格商户 ACK 才进入 ACKED。终态保留历史 nextAttemptAt。接口不返回原通知正文、目标/签名引用、路由指纹或
native 错误。

唤醒示例：

```json
{
  "id": "notification-delivery-id",
  "requestKey": "ops-ticket-001",
  "expectedVersion": 2,
  "reason": "依赖恢复后继续当前通知任务"
}
```

expectedVersion 是详情的 rowVersion，不是 sourceVersion；必须显式提供。请求没有
URL、密钥、正文、重试次数或新截止时间。授权、完整参数绑定与首次回执重放先于当前版本检查，换令牌不改变操作身份；同运营主体内
requestKey 不得用于不同投递、版本或原因。新键版本不匹配返回 VERSION_CONFLICT；重放只返回首次操作决定，不重做调度。

| result      | 已审计决定                                                     |
|-------------|----------------------------------------------------------------|
| REGISTERED  | 当前代次缺 native 任务，在原 nextAttemptAt 登记                |
| RETRIED     | 仅重试当前 FAILED native 任务，原 executeAt 保持不变           |
| RESCHEDULED | 恢复当前 CANCELLED native 任务，executeAt 仍为原 nextAttemptAt |
| PENDING     | 已有等待任务，不加速或重置                                     |
| RUNNING     | 不抢占运行/租约；过期租约交框架回收                            |
| WAITING     | 尚未到业务退避时间，不跳过退避                                 |
| ACKED       | 已收到商户 ACK，不再发送                                       |
| EXHAUSTED   | 终止或业务次数已用尽，不增加许可                               |
| EXPIRED     | 原事件投递窗口结束，不延长窗口                                 |

当前代次的 native 任务若已 SUCCEEDED，但业务代次仍未推进或终止，返回 SNAPSHOT_INVALID，由后续数据修复流程处理，不盲目再发。任何可发送的唤醒仍经原
DelayedTaskHandler、原始商户正文/路由指纹/签名/有限业务次数校验；API 本身不发
HTTP、不重开交易、不改金额。任务重试、登记和不可变审计回执同事务，事务失败不返回成功决定。

MerchantNotificationWakeDTO 返回审计 id、deliveryId/eventId/requestKey、可信 principalType/principalId、原因、result、观察到的
attemptCount/deliveryVersion 和 acceptedAt；不是商户送达确认。/wakes 按 deliveryId/page/size 查询运营记录，acceptedAt
DESC/id DESC，原因在诊断 toString 隐藏，只有运营响应可读取。已超期或耗尽后另建人工补发窗口/预算仍需单独明确合约授权，目前明确拒绝越过原条款。

本批仅源码与测试源码，未运行编译、测试、DDL、认证链路或真实调度/发送。

## 收银台会话付款后端（2026-10-24）

| 外部 HTTP 方法                      | Facade                        | 入参                       | 返回                                     |
|-------------------------------------|-------------------------------|----------------------------|------------------------------------------|
| POST /api/cashier/order/summary     | CashierFacade.summary         | IdQuery.id=orderId         | SuccessResponse&lt;CashierOrderDTO&gt;   |
| POST /api/cashier/payments/start    | CashierFacade.startPayment    | CashierPaymentStartCommand | SuccessResponse&lt;CashierPaymentDTO&gt; |
| POST /api/cashier/payments/progress | CashierFacade.paymentProgress | IdQuery.id=paymentId       | SuccessResponse&lt;CashierPaymentDTO&gt; |

这三类入口由每次调用的 opaque capability 校验授权，不进入商户 JWT 解码器。Authorization: Bearer 原 session
token；X-Checkout-Tenant 仅为受限查找提示，不能绕过 token 摘要验证；Origin 存在时必须匹配原允许域。响应
no-store/no-referrer。只开放上述 POST，其他 cashier 方法和路径拒绝。

```json
{
  "orderId": "original-order",
  "actionKey": "one-fixed-user-action",
  "amountMinor": 3000,
  "choice": {
    "channel": "WECHAT",
    "method": "H5"
  }
}
```

CashierPaymentStartCommand 不接受浏览器传 payer IP、OpenID、渠道 URL、商户号、preparedInputRef 或任意
interaction。CashierInteractionProvider 由服务端提供可信场景；当前 HTTP 实现仅 H5/WAP，使用经过服务器/受信代理策略解析的
servlet remoteAddr，并复用 ChannelH5Input 字面量校验，不读取自报 X-Forwarded-For。代理部署必须正确配置可信地址还原；不能把网关地址作为实际付款终端地址。JSAPI
等待会话绑定的渠道 OAuth 身份流，不允许浏览器自填身份冒充授权。商户纯 API 的 JSAPI/H5/付款码入口保持独立，不受托管子集限制。

可选 acquire-cashier profile 使用 ACQUIRE_CASHIER_H5_CHANNELS 显式配置已部署、已开通的托管 H5 渠道，例如已接微信 H5 的环境填
WECHAT；默认目录为空，不伪造渠道可用性。公开 choices
取该目录与原订单冻结能力的交集，PAID/CLOSED/不可续付或可用金额为0时为空。该目录不是当前渠道注册表的远程可用性证明，发布与真实开通仍需渠道验收。

付款共用 PaymentInitiationOperations：原订单行锁、固定金额/次数预占、不可变执行意图、原合约/币种/收款商户、输入准备及 payment
受理均沿原链路。商户与收银台只改变授权策略和幂等作用域；收银台 actorScope=checkout/sessionId，callerScopeHash 仍是原订单持久归属。相同
session/actionKey 重放原 allocation/payment；变更金额/选择冲突，不因不确定响应换新 key。新 key
才代表另一笔手动付款，仍受金额、尾款最小金额例外、累计笔数和原单终态约束。

新预占按 order→session 顺序锁定并验证 ACTIVE/原订单/商户/原 owner/hash/权限/严格期限。进入 A2
后的原持久意图可继续履约，不因会话在网络调用途中到期而丢失占用；过期/取消的会话无法发起下一次 HTTP
操作。已付清或关闭仍不能新预占，已接受付款的权威结果仍可在无浏览器时由后台/事件收敛。

进度只允许原会话创建的付款，不能凭同订单 capability 查询其他会话或商户 API 付款；query.id 必须是 paymentId。应用先核对
session/order/callerScope/actorScope，再使用内部 OrderPaymentScope 执行原权威结果刷新，不构造商户身份。后台成功/关闭/费用/通知流程同样支持
checkout scope，不依赖 session 仍 ACTIVE。

CashierPaymentDTO 仅包含 orderId/paymentId/state/action/version/order（安全摘要），不返回
allocationId、terminalEvidenceId、商户凭证或冻结合约。state/version/action
为空只表示可信关单回执证明从未受理，不编造付款记录；有动作也不表示资金成功，结果只以原服务查询/事件为准。

后端、H5 场景/目录、页面表单/原链接/有界轮询与测试源码已补；JSAPI OAuth、进一步恢复及真实浏览器验收继续待办；会话撤销源码见下文。未运行
IDEA、编译、测试、DDL、部署或实际调用。

## 托管页面金额与响应契约补充（2026-10-24）

CashierOrderDTO 新增
minimumPaymentMinor、maximumPaymentMinor、allowExactRemainderBelowMin、remainingPaymentCount，表达原冻结单笔规则和扣除已成功/预占后的可用笔数；remainingPaymentCount=null
表示不限笔数。slots=0 的 choices 为空。它们仅辅助浏览器输入，不能覆盖服务端原 OrderPaymentPolicy 或并发预占。

CashierPaymentDTO 现在明确包含 accepted。start 的真实 PaymentOrder 投影固定 accepted=true；progress 只有原未受理可信回执且
payment 为空时 accepted=false，state/version/action 也为空。JSON accepted 即使为 false 也必须输出；客户端不得把缺失
accepted、丢包或任意错误当成未受理。付款身份必须匹配原操作；终态仍以原权威结果和 A3 收敛为准。

网页先持久化不含 token/支付地址的原动作 metadata，再 POST 固定参数；恢复不从 URL 选金额/渠道，也不重新生成动作键。已知
paymentId 后调用原 progress；start 若提前看到终态仍要 progress，确认原金额贡献已收敛后才允许下一笔。未知结果或操作信息损坏不自动清除记录。没有新增金融
API、平台用户或会计账户。

网页最多三十次、每3秒查询，隐藏时暂停，随后保留手动刷新。可用金额和单笔/尾款规则使用整数分，API int64 精确处理，不能用
parseFloat/Number 四舍五入。H5 支付动作只作为可点击的已验证 HTTPS 原链接；动作过期不换单，不使用浏览器返回信号记成功。页面
GET 资源与商户 JWT 解码链分开，业务三类 POST 仍由原 capability 校验授权。

本批页面、浏览器核心与测试源码已补，未运行验证。JSAPI OAuth 与页面交互、跨窗口过期/丢失响应的进一步恢复、真实浏览器/渠道验收仍未完成；会话撤销源码见下文。

## 收银台链接管理与永久撤销（2026-10-24）

`CheckoutSessionManagementFacade` 是商户管理契约，走商户 JWT，不接受买方 capability 作为管理身份。
所有接口均为 POST + JSON，响应 no-store。租户和原签发主体 hash 来自已验证 JWT，不能由请求指定；
同商户不同应用/操作主体也不能借 sessionId 管理别人的链接。

| 路径                               | 入参                                                      | 权限                         | data                                |
|------------------------------------|-----------------------------------------------------------|------------------------------|-------------------------------------|
| `/api/acquire/session/detail`      | `IdQuery(id=sessionId)`                                   | `pay:acquire:session:read`   | `CheckoutSessionStatusDTO`          |
| `/api/acquire/session/revoke`      | `CheckoutSessionRevokeCommand`                            | `pay:acquire:session:revoke` | `CheckoutSessionRevocationDTO`      |
| `/api/acquire/session/revocations` | `CheckoutSessionRevocationPageQuery(sessionId,page,size)` | `pay:acquire:session:read`   | 分页 `CheckoutSessionRevocationDTO` |

详情只返回 sessionId、原 orderId、收款商户、业务状态、原期限、明确 revoked 标记、首次 revokedAt 与 rowVersion；
不返回 token/hash/nonce/密钥引用、scope 或支付链接。先读详情取得当前版本，再提交：

```json
{
  "id": "session-1001",
  "requestKey": "revoke-1001",
  "expectedVersion": 0,
  "reason": "Merchant revoked the link"
}
```

首次成功写永久 revoked_at、rowVersion+1 和不可变操作回执；不改变 CheckoutSession.state、订单或付款状态。
ACTIVE/COMPLETED/EXPIRED/CANCELLED 等原业务状态都允许独立安全撤销，已 PAID/CLOSED 订单也可撤销旧链接。
`newlyRevoked=true` 表示本次首次写标记；已撤销时以新键及当前版本提交，返回 false，保留首次时间和版本。
没有恢复原链接的接口。原因只供原签发者审计读取，在日志诊断中隐藏。

requestKey 在 tenant+callerScopeHash 范围唯一，绑定 sessionId、expectedVersion、reason 和完整原请求摘要。
同键同参返回首次历史回执，即使提交的版本已过时；改参数返回 ACQUIRE_IDEMPOTENCY_CONFLICT。
新键旧版本返回 ACQUIRE_VERSION_CONFLICT。事务在订单→session 加锁后再次查询回执，处理等锁期间原请求已提交的重放竞态。

撤销与新 A2 预占使用相同订单→会话锁序：先提交的预占可继续物化与渠道履约；撤销先提交则后续新预占被拒绝。
后台结果/查单/关闭收敛不依赖买方能力仍有效；撤销本身不调用渠道关单或退款。
每次新认证及页面读入口都检查永久标记；已经通过鉴权的在途读可能完成，不声称全局零窗口撤销。
已撤销会话的原签发键重放返回 ACQUIRE_SESSION_UNAVAILABLE，不重建旧 token 或自动换链接；
需新链接时原商户使用新签发键，并重新满足订单仍可付款的条件。普通到期重放仍返回原到期链接，不延长期限。

本批域模型、持久化、API、鉴权/A2 门禁与测试源码已补，未运行 IDEA、构建、测试、DDL、部署或实际调用。

## 收银台原 actionKey 恢复（2026-10-24）

POST `/api/cashier/payments/recover` 复用原订单 capability 认证，入参为
`CashierPaymentRecoverCommand(orderId,actionKey,amountMinor,choice)`；它不接受买方身份/IP、密钥、绑定版本或任意渠道参数。
字段是第一次明确付款时保存的原值，金额为人民币整数分，choice 为原 channel/method。
响应 `SuccessResponse<CashierPaymentRecoveryDTO>`，包含原 orderId/actionKey、state 和可空 payment。
HTTP no-store/no-referrer；只开放此专用 POST，不能持 capability 调商户管理 API。

| state          | 含义                                                                         | 页面行为                                                           |
|----------------|------------------------------------------------------------------------------|--------------------------------------------------------------------|
| NOT_OBSERVED   | 在当前原订单/会话/actionKey 范围未读到 A2 意图                               | 保留原操作；只允许原键原金额重试，不能判定未收款或换键             |
| INPUT_REQUIRED | 已提交 A2，acquire 尚未绑定且远端未观察到原安全输入引用                      | 保留原操作；可以原参数重试 start，不接收替代输入或新身份           |
| RESOLVED       | 找到原意图，必要时用已绑定安全输入的固定命令恢复幂等受理，随后执行原结果收敛 | payment 必须存在，按其中明确 accepted/state/version 和订单摘要展示 |

RESOLVED 不表示已支付成功。payment 仍可为 INIT/PROCESSING/UNKNOWN 等非终态；
明确 accepted=false 只由原不可变 NOT_ACCEPTED 证据产生，不由 NOT_OBSERVED 或 HTTP/网络失败推导。
页面在原 paymentId 尚未知时先恢复；取得 id 后继续既有 progress 查询。恢复返回终态已完成 A3 收敛后才清除原操作、允许下一笔手动付款。

恢复先验证当前会话读权限，原订单→会话锁内按 tenant/order/checkout-session/actionKey 查原预占，核对原 issuer、
订单/商户、金额、choice 与原准入快照；不存在则不预占金额、不创建 input/payment。已提交意图继续履约的 D03 门禁保持不变。
安全输入已绑定时，在事务外仅用存储的原 PaymentExecuteCommand 进行幂等受理，不重算 interaction、请求摘要或当前路由，
不换 paymentId、allocationId、输入引用或渠道号。RELEASED/CONSUMED 等非 HELD 意图不再次受理，交既有证据/结果流程处理。
新付款预占、输入准备和动作重建的语义不并入恢复入口。

payment 已保存安全输入但其回复/本地引用绑定丢失时，恢复通过 InputLookup 查询精确原引用并在 acquire 重新加锁绑定，再按原命令受理。
原输入到期但从未受理的有证据关闭已接通 InputExpire；剩余边界为首次准备尚未提交时的网络地址变更竞态，以及已过期/撤销会话的买方恢复，仍需继续原身份恢复/商户核对设计。
这些情况不得更换付款身份、擅自释放占用或宣称失败。渠道预支付动作重建仍限原 Provider 的已实现能力，不自动重新创单。

Java 与浏览器核心测试源码已补，页面恢复/重试/刷新/有界轮询已接线；未运行任何验证或真实调用。

## 安全输入原引用查询与绑定恢复（2026-10-24）

`PaymentInputFacade.lookup(PaymentInputLookupQuery)` 仅通过 `PaymentRpc/InputLookup` 供配置的 acquire SERVICE 调用，
要求新增独立权限 `pay:payment:input:read`；prepare 权限不会自动授予 read。发行方需为 acquire→payment 挂载的 SERVICE token
明确配置此权限，本轮没有签发令牌、修改已部署凭证或发起真实调用。不增加内部 HTTP 入口或浏览器查询输入的 API。

Query 包含原 paymentId/orderId/allocationId/payeeMerchantId/amountMinor/CNY/inputKey/choice，不能只按一个公开 paymentId
取回输入引用。payment 绑定可信租户和原 callerServiceId 后核对每个不可变字段；改参返回 PAYMENT_IDEMPOTENCY_CONFLICT，
错误 SERVICE/权限返回 PAYMENT_ACTION_FORBIDDEN。返回 `PaymentInputLookupDTO(found,input)`，found 显式传输，
input 只含原 opaque inputRef、HMAC inputDigest 和 expiresAt。找不到时 found=false/input=null；不说明金融未发生，也不授权释放占用。

此查询不接收或返回付款码/OpenID/IP，不调用 KMS、解密、重新指纹计算、密钥选择或续期，不产生业务写事务。
INIT/BOUND、有效/过期输入均返回原引用和原期限；过期不伪装成缺记录。输入摘要与引用诊断隐藏，密文永不出库。

acquire 在 A2 已提交而本地 preparedInputRef 缺失时，以存储的原执行意图和 actionKey 构造查询，事务外调用；
只有明确 found 才沿原订单→会话锁序重新加载意图，保留原金融数据并一次性绑定引用。
并发线程已绑定同引用则重放，绑定不同引用则冲突；已 RELEASED/终结且没有引用的意图不能重新激活。
绑定后按原 PaymentExecuteCommand 幂等受理，继续原 A3 收敛。网络失败、非法 found/input 组合、错误参数、缺字段都不回退成
found=false，
不自动准备新输入或换 paymentId。先前保存的 IP/场景保持原样，浏览器网络变化不会改写已保存输入。

原期限与执行门禁继续有效：查询和补绑不授予新有效期。若从未受理且原输入已过期，恢复通过 InputExpire 原子建立单笔未受理门禁/证据，随后
A3 收敛；不能因查询到期直接释放占用。首次准备还在途、查询尚未观察到记录时的精确重试竞态继续保留。
所有源码、RPC 映射与测试源码已补，未运行验证。

## 原安全输入到期的条件关闭（2026-10-26）

`PaymentControlFacade.expireInput(PaymentInputExpireCommand)` 经内部 `PaymentRpc/InputExpire` 提供，
仅允许配置的 acquire SERVICE 和专门 `pay:payment:input:expire` 权限。命令包含原 inputRef 加 InputLookup 的完整原意图范围；
不由浏览器提供期限，不允许任意 requestKey、reason、fenceEpoch 或金融身份。输入引用诊断隐藏。

结果 `PaymentInputExpiryDTO(paymentId,orderId,disposition,closed)` 使用三种互斥决定：

| disposition    | 原子条件与结果                             | acquire 行为                                                         |
|----------------|--------------------------------------------|----------------------------------------------------------------------|
| INPUT_ACTIVE   | 尚未受理，原输入尚有效，无原到期关闭回执   | 使用原已绑定命令尝试幂等受理，不能延长输入                           |
| PAYMENT_EXISTS | 原付款已受理；原意图与输入范围精确匹配     | 不因输入到期关闭它，继续原权威进度/A3；不再次调用 accept             |
| EXPIRED_CLOSED | 原输入已到期且从未受理，或原关闭回执已提交 | closed 必须带精确单笔持久门禁与非空未受理证明；A3 幂等释放这一笔占用 |

此操作与 Y1/Y2 使用同样的 parent→child gate→payment→input 锁序。先核对已验证 SERVICE/可信租户、
完整输入范围及已受理记录；付款已存在时保持所有付款/attempt 状态、成功金额及门禁不变。
仅确实从未受理时，使用固定 `input-expiry/{allocationId}`、epoch=1、reason=INPUT_EXPIRED，
复用单笔 close 的 gate+不可变控制回执事务；原 parent 保持 OPEN（如果原本已由关单封禁则保持封禁），不禁止订单其他付款。
可能建立原 parent OPEN 协调门禁，但不会把有效输入或已受理付款关闭。

没有付款记录不等于未收款；必须成功持久化单笔 FENCED 门禁并取得未受理证据，阻止迟到 accept。
原 gate 有异常孤立状态但找不到证据时拒绝，不制造证明。BOUND 输入却缺原付款也视为损坏。
原回执支持回复丢失后的精确重放，不以当前墙钟回退而重新开放；同一单笔已有更高门禁版本不会回退。
输入密文、指纹、期限不改变，没有 KMS、解密或渠道调用。

收银台原 actionKey 恢复在安全输入引用已绑定后调用此条件操作：已受理则查原付款，仍有效则原命令受理，
EXPIRED_CLOSED 则先将原证明交 PaymentOutcomeOperations 的既有 A3。
只有原 tenant/order/payment、原 owner/准入/金额、fenced/fenceEpoch/evidence 都匹配，才释放对应 allocation；
paid gross、其他付款占用、成功笔数及订单业务状态不因此改变。NOT_ACCEPTED 不虚构 payment 行，不确认整单收费。
回执与释放后的 A3 都可以重放；网络错误、缺字段或不匹配决定保留未知和占用，不能输出 accepted=false。
页面收到已收敛的明确未受理结果后才清除原动作，允许下一笔明确金额。

这是收银台恢复路径的源码接线；首次准备仍未提交时的地址变化竞态、买方会话过期后的商户核对与全部运行验收继续。
SERVICE 令牌还需发行方配置 input:expire 权限，本轮没有签发令牌、执行构建/测试/DDL/部署或真实调用。

## 商户纯 API 原付款恢复（2026-10-26）

新增 `PaymentRecoveryFacade.recover(PaymentRecoverCommand)` 与商户 JWT 的 POST
`/api/acquire/payments/recover`，响应 `SuccessResponse<PaymentRecoveryDTO>`、no-store。
独立权限 `pay:acquire:payment:recover`，start 权限不会自动授予恢复权限。
命令只带第一次明确付款时的原 orderId/actionKey/amountMinor/choice，不携带付款码、OpenID、IP、输入引用或渠道绑定。

```json
{"orderId":"O1001","actionKey":"pay-0001","amountMinor":3000,"choice":{"channel":"WECHAT","method":"H5"}}
```

恢复始终绑定可信 JWT 的 tenant/callerScopeHash 和 `merchant/{callerScopeHash}`：
不能用该键读别的应用/主体或 `checkout/sessionId` 的意图，不能由参数指定商户、租户或 actorScope。
已存在意图需精确匹配金额/choice/原准入；改参冲突。此入口不创建新订单、输入、A2 预占或付款身份。
它与 cashier 共用 PaymentInitiationOperations 与 PaymentOutcomeOperations，原输入查回/补绑、条件到期门禁与 A3 不复制。

`PaymentRecoveryDTO` 返回原 orderId/actionKey、共享 `PaymentRecoveryState`、可空 paymentId、可空 accepted 和 progress：

| state          | paymentId / progress                                | accepted                                                    |
|----------------|-----------------------------------------------------|-------------------------------------------------------------|
| NOT_OBSERVED   | 均为空；本作用域暂未观察到原意图，不是未收款证明    | 明确 null                                                   |
| INPUT_REQUIRED | 均为空；已预占但原安全输入尚未准备或尚不可观察      | 明确 null                                                   |
| RESOLVED       | 固定原 paymentId，progress 包含已收敛订单和可空付款 | true 表示原付款已受理；false 只在原未受理证明经过 A3 后返回 |

RESOLVED/accepted=true 不等于支付成功；继续看 progress.payment.state。未观察到、依赖异常、超时或字段缺失不能变成 false。
原已受理付款只查原结果；到期但从未受理的原输入先获得 InputExpire 持久门禁和证明，再交 A3 释放原占用。
商户确认当前请求尚未落 A2 时仍只能以原动作键/原金额重试 start，不能将 NOT_OBSERVED 当作安全更换键的许可。

### 原无输入意图的恢复

H5 首次 API 合法使用 NoInteraction 时，本来没有安全输入，不应永远卡在 INPUT_REQUIRED。
原 v1 幂等摘要已绑定交互种类；恢复在原订单锁内使用框架 MapStruct 构造同元数据的 NONE 候选，
只有 choice.method=H5 且该摘要与原 allocation.requestDigest 完全相等，才确认原请求有意无输入。
这一路径使用原落库命令幂等受理，不调用 InputLookup/InputExpire 或重新准备输入；
不能因为 preparedInputRef 为空就跳过输入流程。原 H5_SCENE/PAYMENT_CODE/JSAPI 仍需原安全输入。
NONE 摘要的意图却出现 preparedInputRef 属于损坏，明确拒绝。没有回算或存储任何付款码/OpenID/IP。
是否支持这种 H5 输入仍由具体 Provider 预检，恢复不绕过渠道能力。

商户原调用者可在订单期限后恢复已提交意图，但新付款仍受订单/合约门禁；已到期或撤销的买方 capability 不获得商户权限。
本入口要求原 orderId 已知。DirectPay 整体回复丢失且未知 orderId 时，使用独立 direct-pay/recover 入口，按原商户订单号及完整非敏感元数据恢复
DIRECT_PAY 意图，见下节。现有 API/共用编排/DTO/权限/测试源码已补，全部运行验证仍暂停。

## DirectPay 独立意图与整体恢复（2026-10-26）

DirectPay 当前使用独立 `DIRECT_PAY` 创建意图，不再复用普通 ORDER。
A0 摘要和 intent_payload 固定原订单字段与首笔 actionKey/firstPaymentAmountMinor/choice/interactionKind。
唯一范围仍为 tenant+payeeMerchantId+merchantOrderNo，三种入口 ORDER/CHECKOUT/DIRECT_PAY 不能互换；
相同商户订单号下改变首笔键、金额、渠道/方式或输入类型会冲突，不能重新首付。
interactionKind 为 NONE/PAYMENT_CODE/CHANNEL_IDENTITY/H5_SCENE，并须与付款方式相符；
只保存这一种类，不保存付款码、OpenID、IP 等敏感输入值。这些值仍仅由首次明确付款的安全输入流程保管。

`DirectPayDTO` 现为 `{order, payment: PaymentRecoveryDTO}`；不保留早期 PaymentStartDTO 兼容表示。
DirectPay 权限在原 create/read/payment:start 外增加 `pay:acquire:payment:recover`。
先以元数据 createDirectPay 提交或重放 A0/A1，再恢复原首笔动作；已 RESOLVED 不重传原 raw input。
只有原动作尚未观察到或 INPUT_REQUIRED 时，才使用原 DirectPay 请求补做相同 start，随后经原恢复/A3 返回当前订单结果。
最终 accepted=true 不等于已收款，accepted=false 只可能来自经 A3 的原未受理证明；不会为证明未受理的意图新造 payment 行。

### 未知原 orderId 的恢复入口

POST `/api/acquire/payments/direct-pay/recover`，商户 JWT，no-store；入参 `DirectPayRecoverCommand` 是原创建与首笔付款的
非敏感元数据，去掉 raw interaction，显式使用原 interactionKind：

```json
{
  "payeeMerchantId":"merchant-1001", "merchantOrderNo":"M1001", "productScopeKey":"acquiring",
  "amountMinor":10000, "currency":"CNY", "subject":"Original subject",
  "actionKey":"first-action-1001", "firstPaymentAmountMinor":3000,
  "choice":{"channel":"WECHAT","method":"H5"}, "interactionKind":"H5_SCENE"
}
```

若首次请求指定 requestedDueAt，也必须传同一时刻，按系统本地时区归一化。
需同时具备 `pay:acquire:direct-pay:recover`、`pay:acquire:create`、`pay:acquire:read`、`pay:acquire:payment:recover`，
因为此命令可能续办已存在的创建意图并读取原付款；不要求 payment:start，也不会新建 A2。
原调用主体/可信租户及 DIRECT_PAY 的完整摘要和元数据均核对，不接受调用方指定 scope 或原系统 orderId。

| creationState | orderId / order / payment                                                                   |
|---------------|---------------------------------------------------------------------------------------------|
| NOT_OBSERVED  | 均空；当前尚未观察到 A0，不是原请求永不会提交的证明                                         |
| REJECTED      | 保留原预分配 orderId 与经过白名单核验的原 resultCode，order/payment 为空                    |
| EXPIRED       | 保留原预分配 orderId，resultCode=ACQUIRE_ADMISSION_EXPIRED，order/payment 为空              |
| COMPLETED     | 原固定 orderId、当前订单及原首笔 PaymentRecoveryDTO；首笔仍可能 NOT_OBSERVED/INPUT_REQUIRED |

已存在 INIT 只续办原 A0/A1，继续用原 orderId 重放 product 决定。`resumeDirectPay` 在 A0 事务中强制已存在：
若原行消失或尚未存在则拒绝，不调用 IdGenerator 分配另一个订单。已完成创建重放不重新准入。
拒绝/到期在首次续办中落库时，恢复返回其原决定；依赖失败、未知错误或损坏数据不转换为创建失败/未付款。
整个恢复入口不持有 raw input、不调用 start，不生成订单/付款/预占/输入身份；新建只能由商户显式重试原 DirectPay 命令。

开发期模型直接重构：旧 ORDER 行不自动推断成 DIRECT_PAY，旧模式与新 DirectPay 复用同号时明确冲突。
新增元数据编码/摘要、mode 守卫、两种创建入口（新建/仅续办）、HTTP/Facade/DTO/MapStruct 与测试源码已补。
已同步开发期 CHECK 迁移源码和表草案，未运行构建、测试、DDL、令牌签发、部署或实际 RPC/渠道调用。

## Acquire 原生任务的运维查询与失败重试（2026-10-26）

`AcquireRecoveryOperationsFacade.detail(AcquireRecoveryTaskQuery)` / `retry(AcquireRecoveryRetryCommand)` 对应
POST `/api/acquire/operations/recovery/tasks/detail` 与 `/retry`，返回标准 SuccessResponse，响应 no-store。
入口只接受 configured operations SERVICE、可信 tenant 与 recovery:read/retry 独立权限，不允许商户借通知或付款权限使用。

查询示例：

```json
{
  "kind": "PAYMENT_ALLOCATION",
  "subjectId": "original-payment-id",
  "generation": 0
}
```

重试示例（expected 字段必须取自实际查询结果）：

```json
{
  "kind": "PAYMENT_ALLOCATION",
  "subjectId": "original-payment-id",
  "generation": 0,
  "expectedTaskId": "observed-native-task-id",
  "expectedAttempts": 5,
  "expectedNextExecuteAt": "2026-10-26T12:00:00.123456+08:00",
  "requestKey": "ops-retry-001",
  "reason": "Dependency configuration corrected"
}
```

kind 另允许 ORDER_DEADLINE（generation 从 1 开始）、ORDER_CLOSURE（generation 从 0 开始），subjectId 使用原 orderId。
查询返回 nativeTaskId/nativeStatus/attempts/maxAttempts/executeAt/nextExecuteAt/businessCurrent/retryable；不返回 native
错误或敏感付款输入。
重试精确绑定 taskId、attempts、nextExecuteAt；新 deadline/executeAt、目标渠道或付款命令均不是可提交参数。
同一个 requestKey 的首次回执保持不变；改 reason、expected 条件或目标也视为改参。不同 key 可以表示新的显式运维决定。

回执 result=RETRIED 表示 native 原任务重新 PENDING；NO_LONGER_NEEDED 表示已过期代次或金融/关闭工作已结束。
两者都不是支付、退款或关闭的结果。非 FAILED 拒绝；当前业务工作还没到原 executeAt 时拒绝提前唤醒。
TASK 的预期条件变化返回 VERSION_CONFLICT，native 状态竞争返回 CONCURRENT_UPDATE，未知任务返回 NOT_FOUND；
缺原业务记录/代次或绑定不一致拒绝，不补建任务、不修改金融表。
源码与测试源码已补，未执行任何真实 HTTP/JWT、数据库、任务或资金验证。

## Payment 付款/退款原恢复任务运维 API（2026-10-26）

`PaymentRecoveryOperationsFacade.detail(PaymentRecoveryTaskQuery)` / `retry(PaymentRecoveryRetryCommand)` 对应
POST `/api/payment/operations/recovery/tasks/detail` 与 `/retry`，仅 configured pay-ops SERVICE 配独立 recovery:
read/retry 权限访问。
身份不会变成原 acquire 金融调用者，也不能由商户/退款令牌权限继承。返回标准 no-store SuccessResponse。

查询示例：

```json
{
  "kind": "REFUND_REQUEST",
  "subjectId": "original-refund-request-id",
  "generation": 0
}
```

kind 另支持 COLLECTION（subjectId=原 paymentId）。查询返回与 acquire 运维 API 同形的 nativeTaskId/nativeStatus、
attempts/maxAttempts/executeAt/nextExecuteAt/businessCurrent/retryable，不返回 native 错误或原金融报文。
重试必须把 kind/subjectId/generation 与查询中的 expectedTaskId、expectedAttempts、expectedNextExecuteAt 原样绑定，
另提供稳定 requestKey、必填 reason；不接收新截止/金融身份/路由/金额。

同键完整重放回放首次 RETRIED/NO_LONGER_NEEDED 回执，改任何原参数均冲突。原 native FAILED 已变化则拒绝，
任务身份/次数/时间快照变化为 PAYMENT_VERSION_CONFLICT，native 条件更新竞争为 PAYMENT_CONCURRENT_UPDATE。
FAILED 不等于付款或退款失败；原金融终态/过期代次只审计不唤醒。没有补建、改期、取消、运行任务或直接渠道操作接口。
源码及测试源码已补，未进行实际 HTTP/JWT/数据库/调度或资金验收。

## Review R2：恢复详情是观察快照（2026-10-26）

acquire/payment 运营 detail 的公开字段和鉴权保持原契约，读取改为短只读 REPEATABLE_READ 快照，使用非锁定 Gateway；
不锁订单、付款门禁、退款计划、allocation 或恢复游标。generation/nativeTaskId/attempts/nextExecuteAt 与 retryable 是同一次快照的观察。
响应送达前可能发生新提交；retry 重新持写锁核对当前业务与 native 原条件，过期前置条件仍返回
VERSION_CONFLICT/CONCURRENT_UPDATE。
不能把曾经返回 retryable=true 当成下一次重试的提交权。原业务 read helper 不被静默改为事务，远端调用边界保持不变。
源码与回归/真实数据库测试源码已补，未执行验证。

## Review R3：UNKNOWN 运营查证 API

PaymentUnknownRecoveryFacade.list (PaymentUnknownRecoveryQuery) 对应 POST
/api/payment/operations/recovery/unknown/list，响应 no-store。
仅配置的受信运营 SERVICE 与 pay:payment:recovery:read，租户来自已验证身份；不接受 tenantId 参数。
kind=COLLECTION/REFUND_REQUEST，minimumLevel=NORMAL/REVIEW/URGENT，limit=1..100；afterFirstUnknownAt/afterRecordId
必须成对，使用上页返回值。
返回原付款/子退款 recordId、父 task subjectId、订单/商户、Money、首次 UNKNOWN 时间、ageSeconds/attentionLevel、rowVersion、可空的
recoveryGeneration/recoveryCompleted。
page 带 hasMore/下一页游标、observedAt、实际关注阈值，不提供总数；NORMAL 对回拨后的未来时间仍可见，年龄下限 0。
该查询不是权限票据；后续仍按现有任务详情读取和 FAILED 审计重试协议操作，严禁据年龄重扣、释放或手动构造终态。
详见 [完整查证契约](RECOVERY-ESCALATION.md)，当前仅源码，未进行接口/JWT/数据库验收。

## 2026-10-27 对账契约增量

reconciliation-client 已定义 ReconciliationFacade 的 create/attach/start/detail/differences。调用只引用可信账户目录
code、账期与封存 artifact；不开放客户端上传“完整性/核对成功”声明、原资金状态修改或直接退款。当前已补应用实现、受信 SERVICE
身份适配与 HTTP/gRPC 入口源码；全部未构建或运行。
start 使用 ReconciliationBatchStartCommand (batchId)，detail 仍复用只读 IdQuery。
HTTP POST /api/reconciliation/batches/{create,attach,start,detail,differences}；gRPC
ReconciliationRpc/{Create,Attach,Start,Detail}。
权限 pay:reconciliation:batch:create/attach/start/read 与目录 service-ids 双重约束，租户取已验证 JWT，原 caller 冻结。
默认账户目录为空，商户令牌不可直接使用对账运营接口。分页复用 PageDTO/PageResponse，不提供上传规范化行或提交核对成功结果的
API。
字段与来源/完整性规则见 [对账设计](RECONCILIATION.md)。

## 2026-10-27 手工渠道账单导入 API

ReconciliationImportFacade：upload 技术流上传、submit (ReconciliationChannelImportCommand)、detail (IdQuery)、retry
(ReconciliationImportRetryCommand)。
运营 HTTP /api/reconciliation/imports/{upload,submit,detail,retry}；权限分别为 pay:reconciliation:import:
upload/submit/read/retry。
仅 SERVICE，租户与 owner 取已验证凭证；当前账户授权和原 caller 继续约束。无通用 Transfer/options HTTP 入口，无内部 HTTP 调用。

submit 字段为 batchId/requestKey/fileId/sourceSha256，sourceSha256 从 upload 返回，仅标识期望原字节；side 固定
CHANNEL、sourceKind 固定 MANUAL_UPLOAD，账户/profile 来自服务器。
upload 返回 fileId/size/sha256；导入 DTO 返回 importId/batchId/sourceKind/fileId/sourceSha256/原
taskId/taskStatus/state/artifactId/rejectionCode/version，不含 storageId 或原异常。
state 是逻辑 INIT/SEALED/REJECTED；taskStatus 独立，SEALED+FAILED 可用原 FAILED 任务重试完成 ACK，不重新解析/换源。
同键改批次、文件或 hash 冲突；坏文件不支持原请求换源。retry 只接受 importId/expectedTaskId，不自动重领
RUNNING，不把任务状态当付款或退款结果。
全部新增接口、映射与测试源码未运行，真实 JWT/上传/存储/任务及数据库验收仍待进行。

## P1 权威账务采集快照与 LEDGER 输入

提供方 PaymentCaptureFacade / PaymentCaptureRpc 新增 Create/Detail/Lines/Retry；范围由当前服务固定 profile 与原请求冻结。
Create 返回异步 INIT，SEALED 才能取得完整 PageDTO<PaymentLedgerLineDTO>；BLOCKED 的计数/金额/hash保持 null，不返回部分行。
Retry 使用 PaymentCaptureRetryCommand (captureId,expectedTaskId)，只重试原任务，终态结果不刷新。
线金额为整数分/CNY，退款的 channelRefundNo 表示原商户子退款请求号，sourceAt 是渠道受理日期；不改原付款金额。

ReconciliationLedgerFacade 与运营 HTTP `/api/reconciliation/ledger/{prepare,import,retry}`：
prepare (batchId,requestKey) 返回原 capture/native 状态；import (batchId,captureId) 完整校验并原子挂入 LEDGER，返回原批次
DTO；
retry (batchId,captureId,expectedTaskId) 是独立授权写命令。没有让运营调用方指定账户 hash、账期、页集合或“完整成功”的字段。
独立权限、原 caller/当前目录授权、EOF/hash/文件证据和时间语义见 [采集契约](PAYMENT-RECONCILIATION-CAPTURE.md)
。上述源码未编译/运行验收。

## P1 官方账单只读来源（2026-10-28）

PaymentStatementFacade.download (PaymentStatementQuery) / PaymentStatementRpc.Download 接受原
bindingRevisionId、channel、accountScopeHash、billDate、statementProfile、maxBytes。首期 WECHAT / wechat-trade-all-20260116；最多
16 MiB 原件、32 KiB 私有来源证据。DTO 包含原范围、format、requestSha256/evidenceSha256、官方 hash 类型和值、原件
contentSha256、接收/下载时间及隐藏的 evidence/content 字节。

仅配置的 reconciliation SERVICE 和 pay:payment:reconciliation:statement:read；无商户 HTTP 入口。原 revision 短 RR
读取后事务外下载。PAYMENT_STATEMENT_NOT_READY / PAYMENT_STATEMENT_NOT_FOUND
不转零行成功；未知渠道失败为依赖失败。该接口只读且不服务端封存，同参数重复读取可能有独立来源凭证；消费方必须先登记不可变意图，再首次有效提交封存原件/凭证。typed
operation payment-statement 需独立较长 deadline、20 MiB gRPC
接收上限与服务凭证；当前消费者/配置/持久化接线源码已补，尚未运行验证。完整语义见 [官方来源设计](WECHAT-STATEMENT-SOURCE.md)
，源码未验证。

## P1 官方来源请求与 Transfer 接线

ReconciliationStatementFacade.submit (batchId,requestKey)/detail (IdQuery)/retry (sourceId,expectedTaskId) 对应
/api/reconciliation/statements。只允许原批次 caller 与 pay:reconciliation:statement:submit/read/retry
权限；原绑定来自服务端目录，提交方不能传 URL/hash/来源类型。返回 sourceId、batchId、原
binding/scope、INIT/READY/SUPERSEDED、READY 时的 importId、原下载 taskId/status/attempts/maxAttempts，原件与凭证不返回浏览器。

INIT 先登记原请求与 DelayedTask；READY 表示两个永久文件引用、Transfer import、OFFICIAL_DOWNLOAD 已同库登记，后者仍 INIT，解析
SEALED/核对结果独立。SUPERSEDED 不覆盖批次现有输入；重复/失败原任务重放不替换 READY 文件。内部 official:<sourceId> import
key 为保留前缀，手工 submit 不可使用。Transfer 详情/重试继续使用 imports API 和各自
expectedTaskId。详见 [来源生命周期](WECHAT-STATEMENT-SOURCE.md)，未运行验证。

## P1 差错独立复核 API（2026-10-28）

ReconciliationReviewFacade 提供 uploadEvidence、propose、decide、detail、history。运营 HTTP 为
/api/reconciliation/reviews/evidence/propose/decide/detail/history，字段见 [复核契约](RECONCILIATION-REVIEW.md)。原
batch/difference 在服务器关联原 run/result/key hash；客户端不能指定新结果或资金状态。

propose 包含 requestKey/expectedVersion/conclusion/note/可选 evidenceFileId；decide 明确原
proposalId/expectedVersion/approve/note。仅当前账户授权的 SERVICE 与 review 独立权限，审批服务必须不同于提案服务；不接受自报自然人身份。返回原命令的
eventId/proposalId/state/version/可信 caller/时间，重放不换成当前 head。detail 返回当前提案/最后事件与原差错，history
默认1/20最大100且按版本连续。所有新源码和测试未运行，真实源端查证接口仍后置。

## P1 获批差错显式源端查询（2026-10-28）

ReconciliationVerificationFacade.submit/detail/retry 对应 /api/reconciliation/verifications。submit 只传原差错/提案/审批事件/版本和
requestKey；精确 APPROVED/NEEDS_SOURCE_VERIFICATION 与当前账户授权由服务核验，再同库登记不可变请求与原 Outbox。detail
返回独立观察和原 deliveryStatus/attempts；retry 必须带原 expectedDeliveryId/审计 reason，仅重放原 FAILED。权限 pay:
reconciliation:verification:submit/read/retry。

提供方 PaymentVerificationFacade.verify / PaymentVerificationRpc.Verify 只允许配置的 reconciliation SERVICE 与 pay:
payment:reconciliation:verify。提示为
sourceRequestId/channel/accountScopeHash/kind/merchantPaymentNo/账单原流水/退款时子退款号；来源重建原已提交对象，事务外仅
query/queryRefund，不写资金。回包完整关联 query，OBSERVED 可仍 UNKNOWN；NO_LOCAL_TARGET/NOT_SUBMITTED/AMBIGUOUS_TARGET
无金额/结果/版本等虚构字段。SourceDTO 保留实际流水、正整数 CNY Money、原 sourceVersion/state、已核验渠道摘要、开始/结束/成功/可选退款受理时间。

消费服务用自有领域/存储回执留档，RECORDED 不改原差错或金融状态；首次有效回包赢得记录，原 ACK
丢失后不重复查询或覆盖。完整契约见 [查证设计](RECONCILIATION-VERIFICATION.md)，新增源码/迁移/测试未运行。
