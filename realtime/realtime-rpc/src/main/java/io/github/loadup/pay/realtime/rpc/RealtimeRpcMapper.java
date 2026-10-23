/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.realtime.rpc;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.rpc.grpc.GrpcScalarMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(config = LoadUpMapStructConfig.class, componentModel = "default",
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        uses = {GrpcScalarMapper.class, io.github.loadup.pay.acquire.rpc.AcquireRpcMapper.class, io.github.loadup.pay.payment.rpc.PaymentRpcMapper.class})
public interface RealtimeRpcMapper {
    RealtimeRpcMapper INSTANCE = Mappers.getMapper(RealtimeRpcMapper.class);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deliveryId", source = "deliveryId")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "producer", source = "producer")
    @Mapping(target = "eventType", source = "eventType")
    @Mapping(target = "schemaVersion", source = "schemaVersion")
    @Mapping(target = "aggregateId", source = "aggregateId")
    @Mapping(target = "aggregateVersion", source = "aggregateVersion")
    @Mapping(target = "occurredAt", source = "occurredAt")
    @Mapping(target = "data", source = "data")
    io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderSnapshotEventDTO toWirePayEventEnvelopeOfOrderSnapshotEventDTO(io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO> value);

    io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO> fromWirePayEventEnvelopeOfOrderSnapshotEventDTO(io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deliveryId", source = "deliveryId")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "producer", source = "producer")
    @Mapping(target = "aggregateId", source = "aggregateId")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    @Mapping(target = "projectedVersion", source = "projectedVersion")
    @Mapping(target = "disposition", source = "disposition")
    @Mapping(target = "accepted", source = "accepted")
    io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO toWireProjectionAckDTO(io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO value);

    io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO fromWireProjectionAckDTO(io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deliveryId", source = "deliveryId")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "producer", source = "producer")
    @Mapping(target = "eventType", source = "eventType")
    @Mapping(target = "schemaVersion", source = "schemaVersion")
    @Mapping(target = "aggregateId", source = "aggregateId")
    @Mapping(target = "aggregateVersion", source = "aggregateVersion")
    @Mapping(target = "occurredAt", source = "occurredAt")
    @Mapping(target = "data", source = "data")
    io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfPaymentSnapshotEventDTO toWirePayEventEnvelopeOfPaymentSnapshotEventDTO(io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO> value);

    io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO> fromWirePayEventEnvelopeOfPaymentSnapshotEventDTO(io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfPaymentSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deliveryId", source = "deliveryId")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "producer", source = "producer")
    @Mapping(target = "eventType", source = "eventType")
    @Mapping(target = "schemaVersion", source = "schemaVersion")
    @Mapping(target = "aggregateId", source = "aggregateId")
    @Mapping(target = "aggregateVersion", source = "aggregateVersion")
    @Mapping(target = "occurredAt", source = "occurredAt")
    @Mapping(target = "data", source = "data")
    io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfRefundSnapshotEventDTO toWirePayEventEnvelopeOfRefundSnapshotEventDTO(io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO> value);

    io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO> fromWirePayEventEnvelopeOfRefundSnapshotEventDTO(io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfRefundSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deliveryId", source = "deliveryId")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "producer", source = "producer")
    @Mapping(target = "eventType", source = "eventType")
    @Mapping(target = "schemaVersion", source = "schemaVersion")
    @Mapping(target = "aggregateId", source = "aggregateId")
    @Mapping(target = "aggregateVersion", source = "aggregateVersion")
    @Mapping(target = "occurredAt", source = "occurredAt")
    @Mapping(target = "data", source = "data")
    io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO> value);

    io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO> fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO value);

}
