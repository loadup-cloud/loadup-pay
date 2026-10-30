package io.github.loadup.pay.realtime.rpc;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.rpc.AcquireRpcMapper;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO;
import io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderSnapshotEventDTO;
import io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfPaymentSnapshotEventDTO;
import io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfRefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(config = LoadUpMapStructConfig.class, unmappedSourcePolicy = ReportingPolicy.IGNORE, collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, uses = {RpcScalarMapper.class, AcquireRpcMapper.class, PaymentRpcMapper.class})
public interface RealtimeRpcMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "producer", source = "producer"), @Mapping(target = "eventType", source = "eventType"), @Mapping(target = "schemaVersion", source = "schemaVersion"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "aggregateVersion", source = "aggregateVersion"), @Mapping(target = "occurredAt", source = "occurredAt"), @Mapping(target = "data", source = "data")})
    public WirePayEventEnvelopeOfOrderSnapshotEventDTO toWirePayEventEnvelopeOfOrderSnapshotEventDTO(EventEnvelope<OrderSnapshotEventDTO> value);

    public EventEnvelope<OrderSnapshotEventDTO> fromWirePayEventEnvelopeOfOrderSnapshotEventDTO(WirePayEventEnvelopeOfOrderSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "producer", source = "producer"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "sourceVersion", source = "sourceVersion"), @Mapping(target = "projectedVersion", source = "projectedVersion"), @Mapping(target = "disposition", source = "disposition"), @Mapping(target = "accepted", source = "accepted")})
    public WireProjectionAckDTO toWireProjectionAckDTO(ProjectionAckDTO value);

    public ProjectionAckDTO fromWireProjectionAckDTO(WireProjectionAckDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "producer", source = "producer"), @Mapping(target = "eventType", source = "eventType"), @Mapping(target = "schemaVersion", source = "schemaVersion"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "aggregateVersion", source = "aggregateVersion"), @Mapping(target = "occurredAt", source = "occurredAt"), @Mapping(target = "data", source = "data")})
    public WirePayEventEnvelopeOfPaymentSnapshotEventDTO toWirePayEventEnvelopeOfPaymentSnapshotEventDTO(EventEnvelope<PaymentSnapshotEventDTO> value);

    public EventEnvelope<PaymentSnapshotEventDTO> fromWirePayEventEnvelopeOfPaymentSnapshotEventDTO(WirePayEventEnvelopeOfPaymentSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "producer", source = "producer"), @Mapping(target = "eventType", source = "eventType"), @Mapping(target = "schemaVersion", source = "schemaVersion"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "aggregateVersion", source = "aggregateVersion"), @Mapping(target = "occurredAt", source = "occurredAt"), @Mapping(target = "data", source = "data")})
    public WirePayEventEnvelopeOfRefundSnapshotEventDTO toWirePayEventEnvelopeOfRefundSnapshotEventDTO(EventEnvelope<RefundSnapshotEventDTO> value);

    public EventEnvelope<RefundSnapshotEventDTO> fromWirePayEventEnvelopeOfRefundSnapshotEventDTO(WirePayEventEnvelopeOfRefundSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mappings(value = {@Mapping(target = "deliveryId", source = "deliveryId"), @Mapping(target = "businessEventId", source = "businessEventId"), @Mapping(target = "producer", source = "producer"), @Mapping(target = "eventType", source = "eventType"), @Mapping(target = "schemaVersion", source = "schemaVersion"), @Mapping(target = "aggregateId", source = "aggregateId"), @Mapping(target = "aggregateVersion", source = "aggregateVersion"), @Mapping(target = "occurredAt", source = "occurredAt"), @Mapping(target = "data", source = "data")})
    public WirePayEventEnvelopeOfOrderRefundSummaryEventDTO toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(EventEnvelope<OrderRefundSummaryEventDTO> value);

    public EventEnvelope<OrderRefundSummaryEventDTO> fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(WirePayEventEnvelopeOfOrderRefundSummaryEventDTO value);
}
