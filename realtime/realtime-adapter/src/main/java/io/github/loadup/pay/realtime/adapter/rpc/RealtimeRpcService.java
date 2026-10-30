package io.github.loadup.pay.realtime.adapter.rpc;

import io.github.loadup.pay.realtime.client.facade.TradeProjectionFacade;
import io.github.loadup.pay.realtime.rpc.RealtimeRpcMapper;
import io.github.loadup.pay.realtime.rpc.proto.DubboRealtimeRpcTriple;
import io.github.loadup.pay.realtime.rpc.proto.RealtimeRpc;
import io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO;
import io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderSnapshotEventDTO;
import io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfPaymentSnapshotEventDTO;
import io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfRefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Protobuf IDL entry point for the realtime projection.
 */
@DubboService(interfaceClass = RealtimeRpc.class)
public class RealtimeRpcService extends DubboRealtimeRpcTriple.RealtimeRpcImplBase {
    private final TradeProjectionFacade tradeProjection;

    private final RealtimeRpcMapper realtimeRpcMapper;

    public RealtimeRpcService(RealtimeRpcMapper realtimeRpcMapper, TradeProjectionFacade tradeProjection) {
        this.realtimeRpcMapper = realtimeRpcMapper;
        this.tradeProjection = tradeProjection;
    }

    @Override
    public WireProjectionAckDTO order(WirePayEventEnvelopeOfOrderSnapshotEventDTO request) {
        return realtimeRpcMapper.toWireProjectionAckDTO(tradeProjection.applyOrder(
                realtimeRpcMapper.fromWirePayEventEnvelopeOfOrderSnapshotEventDTO(request)));
    }

    @Override
    public WireProjectionAckDTO payment(WirePayEventEnvelopeOfPaymentSnapshotEventDTO request) {
        return realtimeRpcMapper.toWireProjectionAckDTO(tradeProjection.applyPayment(
                realtimeRpcMapper.fromWirePayEventEnvelopeOfPaymentSnapshotEventDTO(request)));
    }

    @Override
    public WireProjectionAckDTO refund(WirePayEventEnvelopeOfRefundSnapshotEventDTO request) {
        return realtimeRpcMapper.toWireProjectionAckDTO(tradeProjection.applyRefund(
                realtimeRpcMapper.fromWirePayEventEnvelopeOfRefundSnapshotEventDTO(request)));
    }

    @Override
    public WireProjectionAckDTO refundSummary(WirePayEventEnvelopeOfOrderRefundSummaryEventDTO request) {
        return realtimeRpcMapper.toWireProjectionAckDTO(tradeProjection.applyRefundSummary(
                realtimeRpcMapper.fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(request)));
    }
}
