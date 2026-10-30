package io.github.loadup.pay.acquire.adapter.rpc;

import io.github.loadup.pay.acquire.client.facade.FeeRefundOutcomeFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentOutcomeFacade;
import io.github.loadup.pay.acquire.client.facade.RefundNotificationFacade;
import io.github.loadup.pay.acquire.rpc.AcquireRpcMapper;
import io.github.loadup.pay.acquire.rpc.proto.AcquireRpc;
import io.github.loadup.pay.acquire.rpc.proto.DubboAcquireRpcTriple;
import io.github.loadup.pay.acquire.rpc.proto.WireFeeRefundAckDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfPaymentOutcomeEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfRefundMerchantEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.rpc.proto.WireRefundNotificationAckDTO;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Protobuf IDL entry point for the acquire event callbacks.
 */
@DubboService(interfaceClass = AcquireRpc.class)
public class AcquireRpcService extends DubboAcquireRpcTriple.AcquireRpcImplBase {
    private final PaymentOutcomeFacade paymentOutcome;
    private final FeeRefundOutcomeFacade feeRefundOutcome;
    private final RefundNotificationFacade refundNotification;

    private final AcquireRpcMapper acquireRpcMapper;

    public AcquireRpcService(
            AcquireRpcMapper acquireRpcMapper,
            PaymentOutcomeFacade paymentOutcome,
            FeeRefundOutcomeFacade feeRefundOutcome,
            RefundNotificationFacade refundNotification) {
        this.acquireRpcMapper = acquireRpcMapper;
        this.paymentOutcome = paymentOutcome;
        this.feeRefundOutcome = feeRefundOutcome;
        this.refundNotification = refundNotification;
    }

    @Override
    public WirePaymentOutcomeAckDTO paymentOutcome(WirePayEventEnvelopeOfPaymentOutcomeEventDTO request) {
        return acquireRpcMapper.toWirePaymentOutcomeAckDTO(paymentOutcome.accept(
                acquireRpcMapper.fromWirePayEventEnvelopeOfPaymentOutcomeEventDTO(request)));
    }

    @Override
    public WireFeeRefundAckDTO feeRefund(WirePayEventEnvelopeOfOrderRefundSummaryEventDTO request) {
        return acquireRpcMapper.toWireFeeRefundAckDTO(feeRefundOutcome.accept(
                acquireRpcMapper.fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(request)));
    }

    @Override
    public WireRefundNotificationAckDTO refundNotification(WirePayEventEnvelopeOfRefundMerchantEventDTO request) {
        return acquireRpcMapper.toWireRefundNotificationAckDTO(refundNotification.accept(
                acquireRpcMapper.fromWirePayEventEnvelopeOfRefundMerchantEventDTO(request)));
    }
}
