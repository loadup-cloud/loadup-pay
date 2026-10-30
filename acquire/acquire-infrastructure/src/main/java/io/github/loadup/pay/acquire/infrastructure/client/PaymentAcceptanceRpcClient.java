package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.rpc.dubbo.RpcExceptionCodes;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.pay.acquire.app.port.PaymentClosureClient;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentRpc;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.function.Supplier;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.PAYMENT_INPUT_EXPIRED;

@Component
public class PaymentAcceptanceRpcClient implements PaymentAcceptanceClient, PaymentOutcomeClient, PaymentClosureClient {
    @DubboReference(url = "tri://payment-headless:50051")
    private PaymentRpc paymentRpc;

    private final PaymentTransportProperties properties;
    private final AcquireServiceTokenReader tokens;

    public PaymentAcceptanceRpcClient(
            JsonMapper mapper,
            PaymentTransportProperties properties,
            @Qualifier("acquireClock") Clock clock) {
        this.properties = properties;
        this.tokens = new AcquireServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentInputDTO prepareInput(String tenant, PaymentInputPrepareCommand command) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputDTO(paymentRpc.inputPrepare(
                PaymentRpcMapper.INSTANCE.toWirePaymentInputPrepareCommand(command))), true);
    }

    @Override
    public PaymentInputLookupDTO lookupInput(String tenant, PaymentInputLookupQuery query) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupDTO(paymentRpc.inputLookup(
                PaymentRpcMapper.INSTANCE.toWirePaymentInputLookupQuery(query))), true);
    }

    @Override
    public io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO expireInput(String tenant,
                                                                                     io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand command) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpiryDTO(paymentRpc.inputExpire(
                PaymentRpcMapper.INSTANCE.toWirePaymentInputExpireCommand(command))), true);
    }

    @Override
    public PaymentExecutionDTO accept(String tenant, PaymentExecuteCommand command) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentExecutionDTO(paymentRpc.accept(
                PaymentRpcMapper.INSTANCE.toWirePaymentExecuteCommand(command))), true);
    }

    @Override
    public PaymentExecutionDTO detail(String tenant, String paymentId) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentExecutionDTO(paymentRpc.detail(
                PaymentRpcMapper.INSTANCE.toWireIdQuery(new IdQuery(paymentId)))), false);
    }

    @Override
    public PaymentOutcomeDTO outcome(String tenant, String paymentId) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentOutcomeDTO(paymentRpc.outcome(
                PaymentRpcMapper.INSTANCE.toWireIdQuery(new IdQuery(paymentId)))), false);
    }

    @Override
    public PaymentSettlementFactsDTO facts(String tenant, String paymentId) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentSettlementFactsDTO(paymentRpc.facts(
                PaymentRpcMapper.INSTANCE.toWireIdQuery(new IdQuery(paymentId)))), false);
    }

    @Override
    public OrderRefundSummaryEventDTO refundSummary(String tenant, String orderId) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWireOrderRefundSummaryEventDTO(
                paymentRpc.refundSummary(PaymentRpcMapper.INSTANCE.toWireIdQuery(new IdQuery(orderId)))), false);
    }

    @Override
    public ParentFenceDTO fenceParent(String tenant, ParentPaymentFenceCommand command) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWireParentFenceDTO(paymentRpc.fenceParent(
                PaymentRpcMapper.INSTANCE.toWireParentPaymentFenceCommand(command))), false);
    }

    @Override
    public PaymentCloseDTO close(String tenant, PaymentCloseCommand command) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentCloseDTO(paymentRpc.close(
                PaymentRpcMapper.INSTANCE.toWirePaymentCloseCommand(command))), false);
    }

    @Override
    public ParentFenceDTO parentFenceStatus(String tenant, ParentFenceStatusQuery query) {
        return exchange(tenant, () -> PaymentRpcMapper.INSTANCE.fromWireParentFenceDTO(paymentRpc.parentFenceStatus(
                PaymentRpcMapper.INSTANCE.toWireParentFenceStatusQuery(query))), false);
    }

    private <R> R exchange(String tenant, Supplier<R> call, boolean guarded) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            R response = RpcTokenContext.call(credential(tenant), call);
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            if (guarded) {
                var code = switch (RpcExceptionCodes.resultCode(failure)) {
                    case "PAYMENT_IDEMPOTENCY_CONFLICT", "PAYMENT_INPUT_ALREADY_BOUND" -> IDEMPOTENCY_CONFLICT;
                    case "PAYMENT_INPUT_EXPIRED" -> PAYMENT_INPUT_EXPIRED;
                    case "PAYMENT_INVALID_ARGUMENT" -> INVALID_ARGUMENT;
                    default -> DEPENDENCY_UNAVAILABLE;
                };
                throw new CommonException(code);
            }
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential(String tenant) {
        return tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
    }
}
