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

/** Protobuf IDL entry point for the realtime projection. */
@DubboService(interfaceClass = RealtimeRpc.class)
public class RealtimeRpcService extends DubboRealtimeRpcTriple.RealtimeRpcImplBase {
    private final TradeProjectionFacade tradeProjection;

    public RealtimeRpcService(TradeProjectionFacade tradeProjection) {
        this.tradeProjection = tradeProjection;
    }

    @Override
    public WireProjectionAckDTO order(WirePayEventEnvelopeOfOrderSnapshotEventDTO request) {
        return RealtimeRpcMapper.INSTANCE.toWireProjectionAckDTO(tradeProjection.applyOrder(
                RealtimeRpcMapper.INSTANCE.fromWirePayEventEnvelopeOfOrderSnapshotEventDTO(request)));
    }

    @Override
    public WireProjectionAckDTO payment(WirePayEventEnvelopeOfPaymentSnapshotEventDTO request) {
        return RealtimeRpcMapper.INSTANCE.toWireProjectionAckDTO(tradeProjection.applyPayment(
                RealtimeRpcMapper.INSTANCE.fromWirePayEventEnvelopeOfPaymentSnapshotEventDTO(request)));
    }

    @Override
    public WireProjectionAckDTO refund(WirePayEventEnvelopeOfRefundSnapshotEventDTO request) {
        return RealtimeRpcMapper.INSTANCE.toWireProjectionAckDTO(tradeProjection.applyRefund(
                RealtimeRpcMapper.INSTANCE.fromWirePayEventEnvelopeOfRefundSnapshotEventDTO(request)));
    }

    @Override
    public WireProjectionAckDTO refundSummary(WirePayEventEnvelopeOfOrderRefundSummaryEventDTO request) {
        return RealtimeRpcMapper.INSTANCE.toWireProjectionAckDTO(tradeProjection.applyRefundSummary(
                RealtimeRpcMapper.INSTANCE.fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(request)));
    }
}
