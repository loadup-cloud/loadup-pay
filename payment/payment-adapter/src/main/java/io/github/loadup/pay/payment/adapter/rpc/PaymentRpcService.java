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

/**
 * Protobuf IDL entry point for the payment execution facade.
 */
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

    private final PaymentRpcMapper paymentRpcMapper;

    public PaymentRpcService(
            PaymentRpcMapper paymentRpcMapper,
            PaymentInputFacade paymentInput,
            PaymentAcceptanceFacade paymentAcceptance,
            PaymentOutcomeFacade paymentOutcome,
            PaymentSettlementFactsFacade paymentSettlementFacts,
            PaymentControlFacade paymentControl,
            PaymentAttemptFacade paymentAttempt,
            RefundSummaryFactsFacade refundSummaryFacts,
            RefundSettlementFactsFacade refundSettlementFacts) {
        this.paymentRpcMapper = paymentRpcMapper;
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
        return paymentRpcMapper.toWirePaymentInputDTO(paymentInput.prepare(
                paymentRpcMapper.fromWirePaymentInputPrepareCommand(request)));
    }

    @Override
    public WirePaymentInputLookupDTO inputLookup(WirePaymentInputLookupQuery request) {
        return paymentRpcMapper.toWirePaymentInputLookupDTO(paymentInput.lookup(
                paymentRpcMapper.fromWirePaymentInputLookupQuery(request)));
    }

    @Override
    public WirePaymentInputExpiryDTO inputExpire(WirePaymentInputExpireCommand request) {
        return paymentRpcMapper.toWirePaymentInputExpiryDTO(paymentControl.expireInput(
                paymentRpcMapper.fromWirePaymentInputExpireCommand(request)));
    }

    @Override
    public WirePaymentExecutionDTO accept(WirePaymentExecuteCommand request) {
        return paymentRpcMapper.toWirePaymentExecutionDTO(paymentAcceptance.accept(
                paymentRpcMapper.fromWirePaymentExecuteCommand(request)));
    }

    @Override
    public WirePaymentExecutionDTO detail(WireIdQuery request) {
        return paymentRpcMapper.toWirePaymentExecutionDTO(
                paymentAcceptance.detail(paymentRpcMapper.fromWireIdQuery(request)));
    }

    @Override
    public WirePaymentOutcomeDTO outcome(WireIdQuery request) {
        return paymentRpcMapper.toWirePaymentOutcomeDTO(
                paymentOutcome.outcome(paymentRpcMapper.fromWireIdQuery(request)));
    }

    @Override
    public WirePaymentSettlementFactsDTO facts(WireIdQuery request) {
        return paymentRpcMapper.toWirePaymentSettlementFactsDTO(
                paymentSettlementFacts.paymentFacts(paymentRpcMapper.fromWireIdQuery(request)));
    }

    @Override
    public WirePaymentCloseDTO close(WirePaymentCloseCommand request) {
        return paymentRpcMapper.toWirePaymentCloseDTO(paymentControl.close(
                paymentRpcMapper.fromWirePaymentCloseCommand(request)));
    }

    @Override
    public WireParentFenceDTO fenceParent(WireParentPaymentFenceCommand request) {
        return paymentRpcMapper.toWireParentFenceDTO(paymentControl.fenceParent(
                paymentRpcMapper.fromWireParentPaymentFenceCommand(request)));
    }

    @Override
    public WireParentFenceDTO parentFenceStatus(WireParentFenceStatusQuery request) {
        return paymentRpcMapper.toWireParentFenceDTO(paymentControl.parentFenceStatus(
                paymentRpcMapper.fromWireParentFenceStatusQuery(request)));
    }

    @Override
    public WirePaymentExecutionDTO startAttempt(WirePaymentAttemptStartCommand request) {
        return paymentRpcMapper.toWirePaymentExecutionDTO(paymentAttempt.startAttempt(
                paymentRpcMapper.fromWirePaymentAttemptStartCommand(request)));
    }

    @Override
    public WireOrderRefundSummaryEventDTO refundSummary(WireIdQuery request) {
        return paymentRpcMapper.toWireOrderRefundSummaryEventDTO(
                refundSummaryFacts.refundSummary(paymentRpcMapper.fromWireIdQuery(request)));
    }

    @Override
    public WireRefundSettlementFactsDTO refundFacts(WireIdQuery request) {
        return paymentRpcMapper.toWireRefundSettlementFactsDTO(
                refundSettlementFacts.refundFacts(paymentRpcMapper.fromWireIdQuery(request)));
    }
}
