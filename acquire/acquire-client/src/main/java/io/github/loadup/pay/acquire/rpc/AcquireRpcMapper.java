package io.github.loadup.pay.acquire.rpc;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WireFeeRefundAckDTO;
import io.github.loadup.pay.acquire.rpc.proto.WireOrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfPaymentOutcomeEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfRefundMerchantEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.rpc.proto.WireRefundNotificationAckDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(config = LoadUpMapStructConfig.class, unmappedSourcePolicy = ReportingPolicy.IGNORE, collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, uses = {RpcScalarMapper.class, PaymentRpcMapper.class})
public interface AcquireRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "producer", source = "producer"), @Mapping(target = "eventType", source = "eventType"), @Mapping(target = "schemaVersion", source = "schemaVersion"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "aggregateVersion", source = "aggregateVersion"), @Mapping(target = "occurredAt", source = "occurredAt"), @Mapping(target = "data", source = "data")})
    public WirePayEventEnvelopeOfPaymentOutcomeEventDTO toWirePayEventEnvelopeOfPaymentOutcomeEventDTO(EventEnvelope<PaymentOutcomeEventDTO> value);

    public EventEnvelope<PaymentOutcomeEventDTO> fromWirePayEventEnvelopeOfPaymentOutcomeEventDTO(WirePayEventEnvelopeOfPaymentOutcomeEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "paymentId", source = "paymentId"), @Mapping(target = "paymentVersion", source = "paymentVersion"), @Mapping(target = "evidenceId", source = "evidenceId"), @Mapping(target = "orderVersion", source = "orderVersion"), @Mapping(target = "accepted", source = "accepted")})
    public WirePaymentOutcomeAckDTO toWirePaymentOutcomeAckDTO(PaymentOutcomeAckDTO value);

    public PaymentOutcomeAckDTO fromWirePaymentOutcomeAckDTO(WirePaymentOutcomeAckDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "orderId", source = "orderId"), @Mapping(target = "payeeMerchantId", source = "payeeMerchantId"), @Mapping(target = "merchantOrderNo", source = "merchantOrderNo"), @Mapping(target = "amountMinor", source = "amountMinor"), @Mapping(target = "currency", source = "currency"), @Mapping(target = "paidAmountMinor", source = "paidAmountMinor"), @Mapping(target = "paymentReservedMinor", source = "paymentReservedMinor"), @Mapping(target = "state", source = "state"), @Mapping(target = "orderCreatedAt", source = "orderCreatedAt"), @Mapping(target = "sourceUpdatedAt", source = "sourceUpdatedAt"), @Mapping(target = "sourceVersion", source = "sourceVersion")})
    public WireOrderSnapshotEventDTO toWireOrderSnapshotEventDTO(OrderSnapshotEventDTO value);

    public OrderSnapshotEventDTO fromWireOrderSnapshotEventDTO(WireOrderSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "producer", source = "producer"), @Mapping(target = "eventType", source = "eventType"), @Mapping(target = "schemaVersion", source = "schemaVersion"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "aggregateVersion", source = "aggregateVersion"), @Mapping(target = "occurredAt", source = "occurredAt"), @Mapping(target = "data", source = "data")})
    public WirePayEventEnvelopeOfOrderRefundSummaryEventDTO toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(EventEnvelope<OrderRefundSummaryEventDTO> value);

    public EventEnvelope<OrderRefundSummaryEventDTO> fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(WirePayEventEnvelopeOfOrderRefundSummaryEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "orderId", source = "orderId"), @Mapping(target = "sourceVersion", source = "sourceVersion"), @Mapping(target = "appliedVersion", source = "appliedVersion"), @Mapping(target = "accepted", source = "accepted")})
    public WireFeeRefundAckDTO toWireFeeRefundAckDTO(FeeRefundAckDTO value);

    public FeeRefundAckDTO fromWireFeeRefundAckDTO(WireFeeRefundAckDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "producer", source = "producer"), @Mapping(target = "eventType", source = "eventType"), @Mapping(target = "schemaVersion", source = "schemaVersion"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "aggregateVersion", source = "aggregateVersion"), @Mapping(target = "occurredAt", source = "occurredAt"), @Mapping(target = "data", source = "data")})
    public WirePayEventEnvelopeOfRefundMerchantEventDTO toWirePayEventEnvelopeOfRefundMerchantEventDTO(EventEnvelope<RefundMerchantEventDTO> value);

    @BeforeMapping
    default public void requireRefundMerchantEventEnvelope(WirePayEventEnvelopeOfRefundMerchantEventDTO value) {
        if (!(value != null && value.hasDeliveryId() && value.hasBusinessEventId() && value.hasProducer() && value.hasEventType() && value.hasSchemaVersion() && value.hasAggregateId() && value.hasAggregateVersion() && value.hasOccurredAt() && value.hasData())) {
            throw new IllegalArgumentException("RPC refund event envelope fields are required");
        }
    }

    public EventEnvelope<RefundMerchantEventDTO> fromWirePayEventEnvelopeOfRefundMerchantEventDTO(WirePayEventEnvelopeOfRefundMerchantEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "sourceVersion", source = "sourceVersion"), @Mapping(target = "merchantEventId", source = "merchantEventId"), @Mapping(target = "disposition", source = "disposition")})
    public WireRefundNotificationAckDTO toWireRefundNotificationAckDTO(RefundNotificationAckDTO value);

    public RefundNotificationAckDTO fromWireRefundNotificationAckDTO(WireRefundNotificationAckDTO value);
}
