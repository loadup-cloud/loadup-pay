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
package io.github.loadup.pay.acquire.rpc;

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
        uses = {GrpcScalarMapper.class, io.github.loadup.pay.payment.rpc.PaymentRpcMapper.class})
public interface AcquireRpcMapper {
    AcquireRpcMapper INSTANCE = Mappers.getMapper(AcquireRpcMapper.class);

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
    io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfPaymentOutcomeEventDTO toWirePayEventEnvelopeOfPaymentOutcomeEventDTO(io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO> value);

    io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO> fromWirePayEventEnvelopeOfPaymentOutcomeEventDTO(io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfPaymentOutcomeEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deliveryId", source = "deliveryId")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "paymentVersion", source = "paymentVersion")
    @Mapping(target = "evidenceId", source = "evidenceId")
    @Mapping(target = "orderVersion", source = "orderVersion")
    @Mapping(target = "accepted", source = "accepted")
    io.github.loadup.pay.acquire.rpc.proto.WirePaymentOutcomeAckDTO toWirePaymentOutcomeAckDTO(io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO value);

    io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO fromWirePaymentOutcomeAckDTO(io.github.loadup.pay.acquire.rpc.proto.WirePaymentOutcomeAckDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "merchantOrderNo", source = "merchantOrderNo")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "paidAmountMinor", source = "paidAmountMinor")
    @Mapping(target = "paymentReservedMinor", source = "paymentReservedMinor")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "orderCreatedAt", source = "orderCreatedAt")
    @Mapping(target = "sourceUpdatedAt", source = "sourceUpdatedAt")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    io.github.loadup.pay.acquire.rpc.proto.WireOrderSnapshotEventDTO toWireOrderSnapshotEventDTO(io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO value);

    io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO fromWireOrderSnapshotEventDTO(io.github.loadup.pay.acquire.rpc.proto.WireOrderSnapshotEventDTO value);

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
    io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO> value);

    io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO> fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "deliveryId", source = "deliveryId")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    @Mapping(target = "appliedVersion", source = "appliedVersion")
    @Mapping(target = "accepted", source = "accepted")
    io.github.loadup.pay.acquire.rpc.proto.WireFeeRefundAckDTO toWireFeeRefundAckDTO(io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO value);

    io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO fromWireFeeRefundAckDTO(io.github.loadup.pay.acquire.rpc.proto.WireFeeRefundAckDTO value);

}
