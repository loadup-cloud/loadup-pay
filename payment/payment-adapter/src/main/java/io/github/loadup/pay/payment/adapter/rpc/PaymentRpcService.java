package io.github.loadup.pay.payment.adapter.rpc;

import io.github.loadup.pay.payment.client.facade.PaymentAcceptanceFacade;
import io.github.loadup.pay.payment.client.facade.PaymentAttemptFacade;
import io.github.loadup.pay.payment.client.facade.PaymentControlFacade;
import io.github.loadup.pay.payment.client.facade.PaymentInputFacade;
import io.github.loadup.pay.payment.client.facade.PaymentOutcomeFacade;
import io.github.loadup.pay.payment.client.facade.PaymentSettlementFactsFacade;
import io.github.loadup.pay.payment.client.facade.RefundSettlementFactsFacade;
import io.github.loadup.pay.payment.client.facade.RefundSummaryFactsFacade;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.DubboPaymentRpcTriple;
import io.github.loadup.pay.payment.rpc.proto.PaymentRpc;
import io.github.loadup.pay.payment.rpc.proto.WireIdQuery;
import io.github.loadup.pay.payment.rpc.proto.WireOrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.rpc.proto.WireParentFenceDTO;
import io.github.loadup.pay.payment.rpc.proto.WireParentFenceStatusQuery;
import io.github.loadup.pay.payment.rpc.proto.WireParentPaymentFenceCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentAttemptStartCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentExecuteCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputExpireCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputExpiryDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputLookupDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputLookupQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputPrepareCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentOutcomeDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.rpc.proto.WireRefundSettlementFactsDTO;
import org.apache.dubbo.config.annotation.DubboService;

/** Protobuf IDL entry point for the payment execution facade. */
@DubboService(interfaceClass = PaymentRpc.class)
public class PaymentRpcService extends DubboPaymentRpcTriple.PaymentRpcImplBase {
    private final PaymentInputFacade paymentInput;
    private final PaymentAcceptanceFacade paymentAcceptance;
    private final PaymentOutcomeFacade paymentOutcome;
    private final PaymentSettlementFactsFacade paymentSettlementFacts;
    private final PaymentControlFacade paymentControl;
    private final PaymentAttemptFacade paymentAttempt;
    private final RefundSummaryFactsFacade refundSummaryFacts;
    private final RefundSettlementFactsFacade refundSettlementFacts;

    public PaymentRpcService(
            PaymentInputFacade paymentInput,
            PaymentAcceptanceFacade paymentAcceptance,
            PaymentOutcomeFacade paymentOutcome,
            PaymentSettlementFactsFacade paymentSettlementFacts,
            PaymentControlFacade paymentControl,
            PaymentAttemptFacade paymentAttempt,
            RefundSummaryFactsFacade refundSummaryFacts,
            RefundSettlementFactsFacade refundSettlementFacts) {
        this.paymentInput = paymentInput;
        this.paymentAcceptance = paymentAcceptance;
        this.paymentOutcome = paymentOutcome;
        this.paymentSettlementFacts = paymentSettlementFacts;
        this.paymentControl = paymentControl;
        this.paymentAttempt = paymentAttempt;
        this.refundSummaryFacts = refundSummaryFacts;
        this.refundSettlementFacts = refundSettlementFacts;
    }

    @Override
    public WirePaymentInputDTO inputPrepare(WirePaymentInputPrepareCommand request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentInputDTO(paymentInput.prepare(
                PaymentRpcMapper.INSTANCE.fromWirePaymentInputPrepareCommand(request)));
    }

    @Override
    public WirePaymentInputLookupDTO inputLookup(WirePaymentInputLookupQuery request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentInputLookupDTO(paymentInput.lookup(
                PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupQuery(request)));
    }

    @Override
    public WirePaymentInputExpiryDTO inputExpire(WirePaymentInputExpireCommand request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentInputExpiryDTO(paymentControl.expireInput(
                PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpireCommand(request)));
    }

    @Override
    public WirePaymentExecutionDTO accept(WirePaymentExecuteCommand request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentExecutionDTO(paymentAcceptance.accept(
                PaymentRpcMapper.INSTANCE.fromWirePaymentExecuteCommand(request)));
    }

    @Override
    public WirePaymentExecutionDTO detail(WireIdQuery request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentExecutionDTO(
                paymentAcceptance.detail(PaymentRpcMapper.INSTANCE.fromWireIdQuery(request)));
    }

    @Override
    public WirePaymentOutcomeDTO outcome(WireIdQuery request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentOutcomeDTO(
                paymentOutcome.outcome(PaymentRpcMapper.INSTANCE.fromWireIdQuery(request)));
    }

    @Override
    public WirePaymentSettlementFactsDTO facts(WireIdQuery request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentSettlementFactsDTO(
                paymentSettlementFacts.paymentFacts(PaymentRpcMapper.INSTANCE.fromWireIdQuery(request)));
    }

    @Override
    public WirePaymentCloseDTO close(WirePaymentCloseCommand request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentCloseDTO(paymentControl.close(
                PaymentRpcMapper.INSTANCE.fromWirePaymentCloseCommand(request)));
    }

    @Override
    public WireParentFenceDTO fenceParent(WireParentPaymentFenceCommand request) {
        return PaymentRpcMapper.INSTANCE.toWireParentFenceDTO(paymentControl.fenceParent(
                PaymentRpcMapper.INSTANCE.fromWireParentPaymentFenceCommand(request)));
    }

    @Override
    public WireParentFenceDTO parentFenceStatus(WireParentFenceStatusQuery request) {
        return PaymentRpcMapper.INSTANCE.toWireParentFenceDTO(paymentControl.parentFenceStatus(
                PaymentRpcMapper.INSTANCE.fromWireParentFenceStatusQuery(request)));
    }

    @Override
    public WirePaymentExecutionDTO startAttempt(WirePaymentAttemptStartCommand request) {
        return PaymentRpcMapper.INSTANCE.toWirePaymentExecutionDTO(paymentAttempt.startAttempt(
                PaymentRpcMapper.INSTANCE.fromWirePaymentAttemptStartCommand(request)));
    }

    @Override
    public WireOrderRefundSummaryEventDTO refundSummary(WireIdQuery request) {
        return PaymentRpcMapper.INSTANCE.toWireOrderRefundSummaryEventDTO(
                refundSummaryFacts.refundSummary(PaymentRpcMapper.INSTANCE.fromWireIdQuery(request)));
    }

    @Override
    public WireRefundSettlementFactsDTO refundFacts(WireIdQuery request) {
        return PaymentRpcMapper.INSTANCE.toWireRefundSettlementFactsDTO(
                refundSettlementFacts.refundFacts(PaymentRpcMapper.INSTANCE.fromWireIdQuery(request)));
    }
}
