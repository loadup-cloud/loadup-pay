package io.github.loadup.pay.product.rpc;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
import io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO;
import io.github.loadup.pay.product.client.dto.FeeTermsDTO;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import io.github.loadup.pay.product.client.dto.PaymentTermsDTO;
import io.github.loadup.pay.product.client.dto.RefundTermsDTO;
import io.github.loadup.pay.product.rpc.proto.WireAcquiringTermsDTO;
import io.github.loadup.pay.product.rpc.proto.WireAdmittedOrderTermsDTO;
import io.github.loadup.pay.product.rpc.proto.WireChannelCapabilityDTO;
import io.github.loadup.pay.product.rpc.proto.WireFeeTermsDTO;
import io.github.loadup.pay.product.rpc.proto.WireIdQuery;
import io.github.loadup.pay.product.rpc.proto.WireLimitPolicyRefDTO;
import io.github.loadup.pay.product.rpc.proto.WireNotificationTermsDTO;
import io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO;
import io.github.loadup.pay.product.rpc.proto.WireOrderTermsDTO;
import io.github.loadup.pay.product.rpc.proto.WirePaymentTermsDTO;
import io.github.loadup.pay.product.rpc.proto.WireRefundTermsDTO;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(config = LoadUpMapStructConfig.class, unmappedSourcePolicy = ReportingPolicy.IGNORE, collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, uses = {RpcScalarMapper.class})
public interface ProductRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "productScopeKey", source = "productScopeKey"), @Mapping(target = "requestDigest", source = "requestDigest"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "requestedDueAt", source = "requestedDueAt")})
    public WireOrderAdmissionAuthorizeCommand toWireOrderAdmissionAuthorizeCommand(OrderAdmissionAuthorizeCommand value);

    public OrderAdmissionAuthorizeCommand fromWireOrderAdmissionAuthorizeCommand(WireOrderAdmissionAuthorizeCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "admissionId", source = "admissionId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "decision", source = "decision"), @Mapping(target = "reasonCode", source = "reasonCode"), @Mapping(target = "requestDigest", source = "requestDigest"), @Mapping(target = "contractId", source = "contractId"), @Mapping(target = "revision", source = "revision"), @Mapping(target = "generation", source = "generation"), @Mapping(target = "snapshotHash", source = "snapshotHash"), @Mapping(target = "compiledTermsHash", source = "compiledTermsHash"), @Mapping(target = "admittedAt", source = "admittedAt"), @Mapping(target = "materializeBefore", source = "materializeBefore"), @Mapping(target = "terms", source = "terms")})
    public WireOrderAdmissionDTO toWireOrderAdmissionDTO(OrderAdmissionDTO value);

    public OrderAdmissionDTO fromWireOrderAdmissionDTO(WireOrderAdmissionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "schemaVersion", source = "schemaVersion"), @Mapping(target = "order", source = "order"), @Mapping(target = "payment", source = "payment"), @Mapping(target = "fee", source = "fee"), @Mapping(target = "refund", source = "refund"), @Mapping(target = "notification", source = "notification"), @Mapping(target = "limits", source = "limits"), @Mapping(target = "routingProfileRef", source = "routingProfileRef"), @Mapping(target = "returnTargetCode", source = "returnTargetCode")})
    public WireAcquiringTermsDTO toWireAcquiringTermsDTO(AcquiringTermsDTO value);

    @Mapping(target = "capabilities", source = "capabilitiesList")
    public AcquiringTermsDTO fromWireAcquiringTermsDTO(WireAcquiringTermsDTO value);

    @AfterMapping
    default public void appendAcquiringTermsDTO(AcquiringTermsDTO value, @MappingTarget WireAcquiringTermsDTO.Builder builder) {
        if (value.capabilities() != null) {
            for (ChannelCapabilityDTO item : value.capabilities()) {
                builder.addCapabilities(this.toWireChannelCapabilityDTO(item));
            }
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "waitSeconds", source = "waitSeconds"), @Mapping(target = "maxWaitSeconds", source = "maxWaitSeconds"), @Mapping(target = "unpaidAction", source = "unpaidAction"), @Mapping(target = "extensionAllowed", source = "extensionAllowed"), @Mapping(target = "extensionMaxSeconds", source = "extensionMaxSeconds"), @Mapping(target = "sessionSeconds", source = "sessionSeconds")})
    public WireOrderTermsDTO toWireOrderTermsDTO(OrderTermsDTO value);

    public OrderTermsDTO fromWireOrderTermsDTO(WireOrderTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "minAmountMinor", source = "minAmountMinor"), @Mapping(target = "maxAmountMinor", source = "maxAmountMinor"), @Mapping(target = "maxSuccessfulPayments", source = "maxSuccessfulPayments"), @Mapping(target = "allowExactRemainderBelowMin", source = "allowExactRemainderBelowMin")})
    public WirePaymentTermsDTO toWirePaymentTermsDTO(PaymentTermsDTO value);

    public PaymentTermsDTO fromWirePaymentTermsDTO(WirePaymentTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "rate", source = "rate"), @Mapping(target = "fixedMinor", source = "fixedMinor"), @Mapping(target = "rounding", source = "rounding"), @Mapping(target = "minimumMinor", source = "minimumMinor"), @Mapping(target = "maximumMinor", source = "maximumMinor"), @Mapping(target = "refundPolicy", source = "refundPolicy")})
    public WireFeeTermsDTO toWireFeeTermsDTO(FeeTermsDTO value);

    public FeeTermsDTO fromWireFeeTermsDTO(WireFeeTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "enabled", source = "enabled"), @Mapping(target = "partialAllowed", source = "partialAllowed"), @Mapping(target = "windowSeconds", source = "windowSeconds"), @Mapping(target = "allocationPolicy", source = "allocationPolicy")})
    public WireRefundTermsDTO toWireRefundTermsDTO(RefundTermsDTO value);

    public RefundTermsDTO fromWireRefundTermsDTO(WireRefundTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "enabled", source = "enabled"), @Mapping(target = "targetRef", source = "targetRef"), @Mapping(target = "signingIdentityRef", source = "signingIdentityRef"), @Mapping(target = "timeoutMillis", source = "timeoutMillis"), @Mapping(target = "retryInitialSeconds", source = "retryInitialSeconds"), @Mapping(target = "retryMultiplier", source = "retryMultiplier"), @Mapping(target = "retryMaximumSeconds", source = "retryMaximumSeconds"), @Mapping(target = "retryMaxAttempts", source = "retryMaxAttempts"), @Mapping(target = "retryWindowSeconds", source = "retryWindowSeconds")})
    public WireNotificationTermsDTO toWireNotificationTermsDTO(NotificationTermsDTO value);

    public NotificationTermsDTO fromWireNotificationTermsDTO(WireNotificationTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "policyId", source = "policyId"), @Mapping(target = "policyVersion", source = "policyVersion"), @Mapping(target = "policyHash", source = "policyHash"), @Mapping(target = "parentAggregationEnabled", source = "parentAggregationEnabled"), @Mapping(target = "parentPolicyRef", source = "parentPolicyRef"), @Mapping(target = "amountRestore", source = "amountRestore"), @Mapping(target = "countRestore", source = "countRestore")})
    public WireLimitPolicyRefDTO toWireLimitPolicyRefDTO(LimitPolicyRefDTO value);

    public LimitPolicyRefDTO fromWireLimitPolicyRefDTO(WireLimitPolicyRefDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "channel", source = "channel"), @Mapping(target = "accessMode", source = "accessMode")})
    public WireChannelCapabilityDTO toWireChannelCapabilityDTO(ChannelCapabilityDTO value);

    @Mapping(target = "methods", source = "methodsList")
    public ChannelCapabilityDTO fromWireChannelCapabilityDTO(WireChannelCapabilityDTO value);

    @AfterMapping
    default public void appendChannelCapabilityDTO(ChannelCapabilityDTO value, @MappingTarget WireChannelCapabilityDTO.Builder builder) {
        if (value.methods() != null) {
            for (PaymentMethod item : value.methods()) {
                builder.addMethods(item.name());
            }
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    public WireIdQuery toWireIdQuery(IdQuery value);

    public IdQuery fromWireIdQuery(WireIdQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "admissionId", source = "admissionId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "orderAmountMinor", source = "orderAmountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "contractId", source = "contractId"), @Mapping(target = "revision", source = "revision"), @Mapping(target = "generation", source = "generation"), @Mapping(target = "snapshotHash", source = "snapshotHash"), @Mapping(target = "compiledTermsHash", source = "compiledTermsHash"), @Mapping(target = "terms", source = "terms")})
    public WireAdmittedOrderTermsDTO toWireAdmittedOrderTermsDTO(AdmittedOrderTermsDTO value);

    public AdmittedOrderTermsDTO fromWireAdmittedOrderTermsDTO(WireAdmittedOrderTermsDTO value);
}
