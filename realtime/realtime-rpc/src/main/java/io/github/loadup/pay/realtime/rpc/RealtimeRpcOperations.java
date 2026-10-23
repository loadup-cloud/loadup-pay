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

import io.github.loadup.components.rpc.grpc.RpcOperation;

public final class RealtimeRpcOperations {
    private RealtimeRpcOperations() {}

    public static final RpcOperation<io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO>, io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO, io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderSnapshotEventDTO, io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO> REALTIME_ORDER =
            new RpcOperation<>("realtime", io.github.loadup.pay.realtime.rpc.proto.RealtimeRpcGrpc.getOrderMethod(),
                    RealtimeRpcMapper.INSTANCE::toWirePayEventEnvelopeOfOrderSnapshotEventDTO, RealtimeRpcMapper.INSTANCE::fromWireProjectionAckDTO);

    public static final RpcOperation<io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO>, io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO, io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfPaymentSnapshotEventDTO, io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO> REALTIME_PAYMENT =
            new RpcOperation<>("realtime", io.github.loadup.pay.realtime.rpc.proto.RealtimeRpcGrpc.getPaymentMethod(),
                    RealtimeRpcMapper.INSTANCE::toWirePayEventEnvelopeOfPaymentSnapshotEventDTO, RealtimeRpcMapper.INSTANCE::fromWireProjectionAckDTO);

    public static final RpcOperation<io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO>, io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO, io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfRefundSnapshotEventDTO, io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO> REALTIME_REFUND =
            new RpcOperation<>("realtime", io.github.loadup.pay.realtime.rpc.proto.RealtimeRpcGrpc.getRefundMethod(),
                    RealtimeRpcMapper.INSTANCE::toWirePayEventEnvelopeOfRefundSnapshotEventDTO, RealtimeRpcMapper.INSTANCE::fromWireProjectionAckDTO);

    public static final RpcOperation<io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO>, io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO, io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO, io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO> REALTIME_REFUND_SUMMARY =
            new RpcOperation<>("realtime", io.github.loadup.pay.realtime.rpc.proto.RealtimeRpcGrpc.getRefundSummaryMethod(),
                    RealtimeRpcMapper.INSTANCE::toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO, RealtimeRpcMapper.INSTANCE::fromWireProjectionAckDTO);
}
