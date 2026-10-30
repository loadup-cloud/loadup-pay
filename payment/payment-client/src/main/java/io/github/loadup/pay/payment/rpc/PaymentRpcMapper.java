package io.github.loadup.pay.payment.rpc;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.AlipayJsapiActionDTO;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.NoActionDTO;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentActionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentActionDataDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.dto.RedirectActionDTO;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.dto.WaitActionDTO;
import io.github.loadup.pay.payment.client.dto.WechatJsapiActionDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
import io.github.loadup.pay.payment.rpc.proto.*;
import io.github.loadup.pay.product.rpc.ProductRpcMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(config = LoadUpMapStructConfig.class, unmappedSourcePolicy = ReportingPolicy.IGNORE, collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, uses = {RpcScalarMapper.class, ProductRpcMapper.class})
public interface PaymentRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    public WireIdQuery toWireIdQuery(IdQuery value);

    public IdQuery fromWireIdQuery(WireIdQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "allocationId", source = "allocationId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "inputKey", source = "inputKey"), @Mapping(target = "choice", source = "choice"), @Mapping(target = "interaction", source = "interaction")})
    public WirePaymentInputPrepareCommand toWirePaymentInputPrepareCommand(PaymentInputPrepareCommand value);

    public PaymentInputPrepareCommand fromWirePaymentInputPrepareCommand(WirePaymentInputPrepareCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "channel", source = "channel"), @Mapping(target = "method", source = "method")})
    public WirePaymentChoiceDTO toWirePaymentChoiceDTO(PaymentChoiceDTO value);

    public PaymentChoiceDTO fromWirePaymentChoiceDTO(WirePaymentChoiceDTO value);

    default public WirePaymentInteractionDTO toWirePaymentInteractionDTO(PaymentInteractionDTO value) {
        if (value == null) {
            throw new IllegalArgumentException("RPC value is required");
        }
        WirePaymentInteractionDTO.Builder result = WirePaymentInteractionDTO.newBuilder();
        if (value instanceof NoInteractionDTO) {
            NoInteractionDTO variant = (NoInteractionDTO) value;
            result.setOption0(this.toWireNoInteractionDTO(variant));
        } else if (value instanceof PaymentCodeInteractionDTO) {
            PaymentCodeInteractionDTO variant = (PaymentCodeInteractionDTO) value;
            result.setOption1(this.toWirePaymentCodeInteractionDTO(variant));
        } else if (value instanceof ChannelIdentityInteractionDTO) {
            ChannelIdentityInteractionDTO variant = (ChannelIdentityInteractionDTO) value;
            result.setOption2(this.toWireChannelIdentityInteractionDTO(variant));
        } else if (value instanceof H5SceneInteractionDTO) {
            H5SceneInteractionDTO variant = (H5SceneInteractionDTO) value;
            result.setOption3(this.toWireH5SceneInteractionDTO(variant));
        } else {
            throw new IllegalArgumentException("Unknown RPC variant");
        }
        return result.build();
    }

    default public PaymentInteractionDTO fromWirePaymentInteractionDTO(WirePaymentInteractionDTO value) {
        return switch (value.getVariantCase()) {
            case WirePaymentInteractionDTO.VariantCase.OPTION0 -> this.fromWireNoInteractionDTO(value.getOption0());
            case WirePaymentInteractionDTO.VariantCase.OPTION1 ->
                    this.fromWirePaymentCodeInteractionDTO(value.getOption1());
            case WirePaymentInteractionDTO.VariantCase.OPTION2 ->
                    this.fromWireChannelIdentityInteractionDTO(value.getOption2());
            case WirePaymentInteractionDTO.VariantCase.OPTION3 ->
                    this.fromWireH5SceneInteractionDTO(value.getOption3());
            default -> throw new IllegalArgumentException("RPC variant is required");
        };
    }

    @BeanMapping(ignoreByDefault = true)
    public WireNoInteractionDTO toWireNoInteractionDTO(NoInteractionDTO value);

    public NoInteractionDTO fromWireNoInteractionDTO(WireNoInteractionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentCode", source = "paymentCode")
    public WirePaymentCodeInteractionDTO toWirePaymentCodeInteractionDTO(PaymentCodeInteractionDTO value);

    public PaymentCodeInteractionDTO fromWirePaymentCodeInteractionDTO(WirePaymentCodeInteractionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "channel", source = "channel"), @Mapping(target = "identifier", source = "identifier"), @Mapping(target = "appScope", source = "appScope"), @Mapping(target = "identityType", source = "identityType")})
    public WireChannelIdentityInteractionDTO toWireChannelIdentityInteractionDTO(ChannelIdentityInteractionDTO value);

    public ChannelIdentityInteractionDTO fromWireChannelIdentityInteractionDTO(WireChannelIdentityInteractionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "payerClientIp", source = "payerClientIp"), @Mapping(target = "sceneType", expression = "java(value.sceneType().name())")})
    public WireH5SceneInteractionDTO toWireH5SceneInteractionDTO(H5SceneInteractionDTO value);

    @BeforeMapping
    default public void requireH5SceneFields(WireH5SceneInteractionDTO value) {
        if (value == null || !value.hasPayerClientIp() || !value.hasSceneType()) {
            throw new IllegalArgumentException("RPC H5 scene fields are required");
        }
    }

    @Mapping(target = "sceneType", expression = "java(io.github.loadup.pay.channel.api.model.H5SceneType.valueOf(value.getSceneType()))")
    public H5SceneInteractionDTO fromWireH5SceneInteractionDTO(WireH5SceneInteractionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "inputRef", source = "inputRef"), @Mapping(target = "inputDigest", source = "inputDigest"), @Mapping(target = "expiresAt", source = "expiresAt")})
    public WirePaymentInputDTO toWirePaymentInputDTO(PaymentInputDTO value);

    public PaymentInputDTO fromWirePaymentInputDTO(WirePaymentInputDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "allocationId", source = "allocationId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "inputKey", source = "inputKey"), @Mapping(target = "choice", source = "choice")})
    public WirePaymentInputLookupQuery toWirePaymentInputLookupQuery(PaymentInputLookupQuery value);

    public PaymentInputLookupQuery fromWirePaymentInputLookupQuery(WirePaymentInputLookupQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "inputRef", source = "inputRef"), @Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "allocationId", source = "allocationId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "inputKey", source = "inputKey"), @Mapping(target = "choice", source = "choice")})
    public WirePaymentInputExpireCommand toWirePaymentInputExpireCommand(PaymentInputExpireCommand value);

    public PaymentInputExpireCommand fromWirePaymentInputExpireCommand(WirePaymentInputExpireCommand value);

    @BeforeMapping
    default public void requireInputExpiryFields(WirePaymentInputExpireCommand value) {
        if (!(value != null && value.hasInputRef() && value.hasPaymentId() && value.hasOrderId() && value.hasAllocationId() && value.hasPayeeMerchantId() && value.hasAmountMinor() && value.hasCurrency() && value.hasInputKey() && value.hasChoice())) {
            throw new IllegalArgumentException("RPC input expiry fields are required");
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "disposition", source = "disposition"), @Mapping(target = "closed", source = "closed")})
    public WirePaymentInputExpiryDTO toWirePaymentInputExpiryDTO(PaymentInputExpiryDTO value);

    @Mappings(value = {@Mapping(target = "disposition", expression = "java(io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition.valueOf(value.getDisposition()))"), @Mapping(target = "closed", expression = "java(value.hasClosed() ? fromWirePaymentCloseDTO(value.getClosed()) : null)")})
    public PaymentInputExpiryDTO fromWirePaymentInputExpiryDTO(WirePaymentInputExpiryDTO value);

    @BeforeMapping
    default public void requireInputExpiryResult(WirePaymentInputExpiryDTO value) {
        WirePaymentCloseDTO proof;
        if (!(value != null && value.hasPaymentId() && value.hasOrderId() && value.hasDisposition() && "EXPIRED_CLOSED".equals(value.getDisposition()) == value.hasClosed())) {
            throw new IllegalArgumentException("RPC input expiry result is invalid");
        }
        if (!(!value.hasClosed() || (proof = value.getClosed()).hasPaymentId() && proof.hasOrderId() && proof.hasFenceEpoch() && proof.hasFenced() && proof.getFenced() && !proof.hasPayment() && proof.hasNoCollectionEvidenceId() && proof.getFenceEpoch() >= 1L && !proof.getNoCollectionEvidenceId().isBlank() && proof.getPaymentId().equals(value.getPaymentId()) && proof.getOrderId().equals(value.getOrderId()))) {
            throw new IllegalArgumentException("RPC input expiry proof is invalid");
        }
    }

    @BeforeMapping
    default public void requireInputLookupFields(WirePaymentInputLookupQuery value) {
        if (!(value != null && value.hasPaymentId() && value.hasOrderId() && value.hasAllocationId() && value.hasPayeeMerchantId() && value.hasAmountMinor() && value.hasCurrency() && value.hasInputKey() && value.hasChoice())) {
            throw new IllegalArgumentException("RPC input lookup fields are required");
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "found", source = "found"), @Mapping(target = "input", source = "input")})
    public WirePaymentInputLookupDTO toWirePaymentInputLookupDTO(PaymentInputLookupDTO value);

    @Mapping(target = "input", expression = "java(value.hasInput() ? fromWirePaymentInputDTO(value.getInput()) : null)")
    public PaymentInputLookupDTO fromWirePaymentInputLookupDTO(WirePaymentInputLookupDTO value);

    @BeforeMapping
    default public void requireInputLookupResult(WirePaymentInputLookupDTO value) {
        if (!(value != null && value.hasFound() && value.getFound() == value.hasInput() && (!value.hasInput() || value.getInput().hasInputRef() && value.getInput().hasInputDigest() && value.getInput().hasExpiresAt()))) {
            throw new IllegalArgumentException("RPC input lookup result is invalid");
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "allocationId", source = "allocationId"), @Mapping(target = "admissionId", source = "admissionId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "requestDigest", source = "requestDigest"), @Mapping(target = "choice", source = "choice"), @Mapping(target = "preparedInputRef", source = "preparedInputRef"), @Mapping(target = "admittedTerms", source = "admittedTerms"), @Mapping(target = "subject", source = "subject"), @Mapping(target = "payableUntil", source = "payableUntil")})
    public WirePaymentExecuteCommand toWirePaymentExecuteCommand(PaymentExecuteCommand value);

    public PaymentExecuteCommand fromWirePaymentExecuteCommand(WirePaymentExecuteCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "allocationId", source = "allocationId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "state", source = "state"), @Mapping(target = "activeAttemptId", source = "activeAttemptId"), @Mapping(target = "action", source = "action"), @Mapping(target = "terminalEvidenceId", source = "terminalEvidenceId"), @Mapping(target = "version", source = "version"), @Mapping(target = "initiatedAt", source = "initiatedAt")})
    public WirePaymentExecutionDTO toWirePaymentExecutionDTO(PaymentExecutionDTO value);

    public PaymentExecutionDTO fromWirePaymentExecutionDTO(WirePaymentExecutionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "type", source = "type"), @Mapping(target = "expiresAt", source = "expiresAt"), @Mapping(target = "data", source = "data")})
    public WirePaymentActionDTO toWirePaymentActionDTO(PaymentActionDTO value);

    public PaymentActionDTO fromWirePaymentActionDTO(WirePaymentActionDTO value);

    default public WirePaymentActionDataDTO toWirePaymentActionDataDTO(PaymentActionDataDTO value) {
        if (value == null) {
            throw new IllegalArgumentException("RPC value is required");
        }
        WirePaymentActionDataDTO.Builder result = WirePaymentActionDataDTO.newBuilder();
        if (value instanceof NoActionDTO) {
            NoActionDTO variant = (NoActionDTO) value;
            result.setOption0(this.toWireNoActionDTO(variant));
        } else if (value instanceof WaitActionDTO) {
            WaitActionDTO variant = (WaitActionDTO) value;
            result.setOption1(this.toWireWaitActionDTO(variant));
        } else if (value instanceof RedirectActionDTO) {
            RedirectActionDTO variant = (RedirectActionDTO) value;
            result.setOption2(this.toWireRedirectActionDTO(variant));
        } else if (value instanceof WechatJsapiActionDTO) {
            WechatJsapiActionDTO variant = (WechatJsapiActionDTO) value;
            result.setOption3(this.toWireWechatJsapiActionDTO(variant));
        } else if (value instanceof AlipayJsapiActionDTO) {
            AlipayJsapiActionDTO variant = (AlipayJsapiActionDTO) value;
            result.setOption4(this.toWireAlipayJsapiActionDTO(variant));
        } else {
            throw new IllegalArgumentException("Unknown RPC variant");
        }
        return result.build();
    }

    default public PaymentActionDataDTO fromWirePaymentActionDataDTO(WirePaymentActionDataDTO value) {
        return switch (value.getVariantCase()) {
            case WirePaymentActionDataDTO.VariantCase.OPTION0 -> this.fromWireNoActionDTO(value.getOption0());
            case WirePaymentActionDataDTO.VariantCase.OPTION1 -> this.fromWireWaitActionDTO(value.getOption1());
            case WirePaymentActionDataDTO.VariantCase.OPTION2 -> this.fromWireRedirectActionDTO(value.getOption2());
            case WirePaymentActionDataDTO.VariantCase.OPTION3 -> this.fromWireWechatJsapiActionDTO(value.getOption3());
            case WirePaymentActionDataDTO.VariantCase.OPTION4 -> this.fromWireAlipayJsapiActionDTO(value.getOption4());
            default -> throw new IllegalArgumentException("RPC variant is required");
        };
    }

    @BeanMapping(ignoreByDefault = true)
    public WireNoActionDTO toWireNoActionDTO(NoActionDTO value);

    public NoActionDTO fromWireNoActionDTO(WireNoActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "recommendedPollAfterMillis", source = "recommendedPollAfterMillis")
    public WireWaitActionDTO toWireWaitActionDTO(WaitActionDTO value);

    public WaitActionDTO fromWireWaitActionDTO(WireWaitActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "redirectUrl", source = "redirectUrl")
    public WireRedirectActionDTO toWireRedirectActionDTO(RedirectActionDTO value);

    public RedirectActionDTO fromWireRedirectActionDTO(WireRedirectActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "appId", source = "appId"), @Mapping(target = "timeStamp", source = "timeStamp"), @Mapping(target = "nonceStr", source = "nonceStr"), @Mapping(target = "packageValue", source = "packageValue"), @Mapping(target = "signType", source = "signType"), @Mapping(target = "paySign", source = "paySign")})
    public WireWechatJsapiActionDTO toWireWechatJsapiActionDTO(WechatJsapiActionDTO value);

    public WechatJsapiActionDTO fromWireWechatJsapiActionDTO(WireWechatJsapiActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "tradeNo", source = "tradeNo")
    public WireAlipayJsapiActionDTO toWireAlipayJsapiActionDTO(AlipayJsapiActionDTO value);

    public AlipayJsapiActionDTO fromWireAlipayJsapiActionDTO(WireAlipayJsapiActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "payment", source = "payment"), @Mapping(target = "executionFenced", source = "executionFenced")})
    public WirePaymentOutcomeDTO toWirePaymentOutcomeDTO(PaymentOutcomeDTO value);

    public PaymentOutcomeDTO fromWirePaymentOutcomeDTO(WirePaymentOutcomeDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "payment", source = "payment"), @Mapping(target = "limitOperationId", source = "limitOperationId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "executionFenced", source = "executionFenced")})
    public WirePaymentSettlementFactsDTO toWirePaymentSettlementFactsDTO(PaymentSettlementFactsDTO value);

    public PaymentSettlementFactsDTO fromWirePaymentSettlementFactsDTO(WirePaymentSettlementFactsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "requestKey", source = "requestKey"), @Mapping(target = "fenceEpoch", source = "fenceEpoch"), @Mapping(target = "reasonCode", source = "reasonCode")})
    public WirePaymentCloseCommand toWirePaymentCloseCommand(PaymentCloseCommand value);

    public PaymentCloseCommand fromWirePaymentCloseCommand(WirePaymentCloseCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "fenceEpoch", source = "fenceEpoch"), @Mapping(target = "fenced", source = "fenced"), @Mapping(target = "payment", source = "payment"), @Mapping(target = "noCollectionEvidenceId", source = "noCollectionEvidenceId")})
    public WirePaymentCloseDTO toWirePaymentCloseDTO(PaymentCloseDTO value);

    public PaymentCloseDTO fromWirePaymentCloseDTO(WirePaymentCloseDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "closeEpoch", source = "closeEpoch"), @Mapping(target = "requestKey", source = "requestKey")})
    public WireParentPaymentFenceCommand toWireParentPaymentFenceCommand(ParentPaymentFenceCommand value);

    public ParentPaymentFenceCommand fromWireParentPaymentFenceCommand(WireParentPaymentFenceCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "orderId", source = "orderId"), @Mapping(target = "closeEpoch", source = "closeEpoch"), @Mapping(target = "fenced", source = "fenced"), @Mapping(target = "allChildrenConverged", source = "allChildrenConverged"), @Mapping(target = "unknownCount", source = "unknownCount")})
    public WireParentFenceDTO toWireParentFenceDTO(ParentFenceDTO value);

    public ParentFenceDTO fromWireParentFenceDTO(WireParentFenceDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "orderId", source = "orderId"), @Mapping(target = "closeEpoch", source = "closeEpoch")})
    public WireParentFenceStatusQuery toWireParentFenceStatusQuery(ParentFenceStatusQuery value);

    public ParentFenceStatusQuery fromWireParentFenceStatusQuery(WireParentFenceStatusQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "requestKey", source = "requestKey"), @Mapping(target = "choice", source = "choice"), @Mapping(target = "preparedInputRef", source = "preparedInputRef"), @Mapping(target = "previousAttemptEvidenceId", source = "previousAttemptEvidenceId")})
    public WirePaymentAttemptStartCommand toWirePaymentAttemptStartCommand(PaymentAttemptStartCommand value);

    public PaymentAttemptStartCommand fromWirePaymentAttemptStartCommand(WirePaymentAttemptStartCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "allocationId", source = "allocationId"), @Mapping(target = "operationId", source = "operationId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "state", source = "state"), @Mapping(target = "paymentVersion", source = "paymentVersion"), @Mapping(target = "evidenceId", source = "evidenceId"), @Mapping(target = "executionFenced", source = "executionFenced")})
    public WirePaymentOutcomeEventDTO toWirePaymentOutcomeEventDTO(PaymentOutcomeEventDTO value);

    public PaymentOutcomeEventDTO fromWirePaymentOutcomeEventDTO(WirePaymentOutcomeEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "state", source = "state"), @Mapping(target = "channel", source = "channel"), @Mapping(target = "accessMode", source = "accessMode"), @Mapping(target = "initiatedAt", source = "initiatedAt"), @Mapping(target = "succeededAt", source = "succeededAt"), @Mapping(target = "sourceVersion", source = "sourceVersion")})
    public WirePaymentSnapshotEventDTO toWirePaymentSnapshotEventDTO(PaymentSnapshotEventDTO value);

    public PaymentSnapshotEventDTO fromWirePaymentSnapshotEventDTO(WirePaymentSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "refundId", source = "refundId"), @Mapping(target = "refundRequestId", source = "refundRequestId"), @Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "state", source = "state"), @Mapping(target = "refundCreatedAt", source = "refundCreatedAt"), @Mapping(target = "succeededAt", source = "succeededAt"), @Mapping(target = "sourceVersion", source = "sourceVersion")})
    public WireRefundSnapshotEventDTO toWireRefundSnapshotEventDTO(RefundSnapshotEventDTO value);

    public RefundSnapshotEventDTO fromWireRefundSnapshotEventDTO(WireRefundSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "refundedMinor", source = "refundedMinor"), @Mapping(target = "refundReservedMinor", source = "refundReservedMinor"), @Mapping(target = "refundSummaryVersion", source = "refundSummaryVersion")})
    public WireOrderRefundSummaryEventDTO toWireOrderRefundSummaryEventDTO(OrderRefundSummaryEventDTO value);

    public OrderRefundSummaryEventDTO fromWireOrderRefundSummaryEventDTO(WireOrderRefundSummaryEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "refund", source = "refund"), @Mapping(target = "paymentAmountMinor", source = "paymentAmountMinor"), @Mapping(target = "paymentVersion", source = "paymentVersion"), @Mapping(target = "paymentEvidenceId", source = "paymentEvidenceId"), @Mapping(target = "paymentAttemptId", source = "paymentAttemptId"), @Mapping(target = "paymentInitiatedAt", source = "paymentInitiatedAt"), @Mapping(target = "paymentRefundVersion", source = "paymentRefundVersion")})
    public WireRefundSettlementFactsDTO toWireRefundSettlementFactsDTO(RefundSettlementFactsDTO value);

    public RefundSettlementFactsDTO fromWireRefundSettlementFactsDTO(WireRefundSettlementFactsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "refundId", source = "refundId"), @Mapping(target = "refundRequestId", source = "refundRequestId"), @Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "operationId", source = "operationId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "state", source = "state"), @Mapping(target = "refundVersion", source = "refundVersion"), @Mapping(target = "cumulativePaymentRefundedMinor", source = "cumulativePaymentRefundedMinor"), @Mapping(target = "evidenceId", source = "evidenceId")})
    public WireRefundOutcomeEventDTO toWireRefundOutcomeEventDTO(RefundOutcomeEventDTO value);

    public RefundOutcomeEventDTO fromWireRefundOutcomeEventDTO(WireRefundOutcomeEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "aggregateType", source = "aggregateType"), @Mapping(target = "refundId", source = "refundId"), @Mapping(target = "refundRequestId", source = "refundRequestId"), @Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "merchantRefundNo", source = "merchantRefundNo"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "state", source = "state"), @Mapping(target = "succeededMinor", source = "succeededMinor"), @Mapping(target = "failedMinor", source = "failedMinor"), @Mapping(target = "sourceVersion", source = "sourceVersion"), @Mapping(target = "succeededAt", source = "succeededAt")})
    public WireRefundMerchantEventDTO toWireRefundMerchantEventDTO(RefundMerchantEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "aggregateType", source = "aggregateType"), @Mapping(target = "refundId", expression = "java(value.hasRefundId() ? value.getRefundId() : null)"), @Mapping(target = "refundRequestId", source = "refundRequestId"), @Mapping(target = "paymentId", expression = "java(value.hasPaymentId() ? value.getPaymentId() : null)"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "merchantRefundNo", source = "merchantRefundNo"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "state", source = "state"), @Mapping(target = "succeededMinor", source = "succeededMinor"), @Mapping(target = "failedMinor", source = "failedMinor"), @Mapping(target = "sourceVersion", source = "sourceVersion"), @Mapping(target = "succeededAt", source = "succeededAt")})
    public RefundMerchantEventDTO fromWireRefundMerchantEventDTO(WireRefundMerchantEventDTO value);

    @BeforeMapping
    default public void requireRefundMerchantEventFields(WireRefundMerchantEventDTO value) {
        if (!(value != null && value.hasAggregateType() && value.hasRefundRequestId() && value.hasOrderId() && value.hasPayeeMerchantId() && value.hasMerchantRefundNo() && value.hasAmountMinor() && value.hasCurrency() && value.hasState() && value.hasSucceededMinor() && value.hasFailedMinor() && value.hasSourceVersion())) {
            throw new IllegalArgumentException("RPC refund merchant event fields are required");
        }
    }
}
