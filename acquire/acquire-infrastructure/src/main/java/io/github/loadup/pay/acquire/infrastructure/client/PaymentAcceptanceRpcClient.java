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
    private final PaymentTransportProperties properties;
    private final AcquireServiceTokenReader tokens;
    private final PaymentRpcMapper paymentRpcMapper;
    @DubboReference(url = "tri://payment-headless:50051")
    private PaymentRpc paymentRpc;

    public PaymentAcceptanceRpcClient(
            PaymentRpcMapper paymentRpcMapper,
            JsonMapper mapper,
            PaymentTransportProperties properties,
            Clock clock) {
        this.paymentRpcMapper = paymentRpcMapper;
        this.properties = properties;
        this.tokens = new AcquireServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentInputDTO prepareInput(String tenant, PaymentInputPrepareCommand command) {
        return exchange(tenant, () -> paymentRpcMapper.fromWirePaymentInputDTO(paymentRpc.inputPrepare(
                paymentRpcMapper.toWirePaymentInputPrepareCommand(command))), true);
    }

    @Override
    public PaymentInputLookupDTO lookupInput(String tenant, PaymentInputLookupQuery query) {
        return exchange(tenant, () -> paymentRpcMapper.fromWirePaymentInputLookupDTO(paymentRpc.inputLookup(
                paymentRpcMapper.toWirePaymentInputLookupQuery(query))), true);
    }

    @Override
    public io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO expireInput(String tenant,
                                                                                     io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand command) {
        return exchange(tenant, () -> paymentRpcMapper.fromWirePaymentInputExpiryDTO(paymentRpc.inputExpire(
                paymentRpcMapper.toWirePaymentInputExpireCommand(command))), true);
    }

    @Override
    public PaymentExecutionDTO accept(String tenant, PaymentExecuteCommand command) {
        return exchange(tenant, () -> paymentRpcMapper.fromWirePaymentExecutionDTO(paymentRpc.accept(
                paymentRpcMapper.toWirePaymentExecuteCommand(command))), true);
    }

    @Override
    public PaymentExecutionDTO detail(String tenant, String paymentId) {
        return exchange(tenant, () -> paymentRpcMapper.fromWirePaymentExecutionDTO(paymentRpc.detail(
                paymentRpcMapper.toWireIdQuery(new IdQuery(paymentId)))), false);
    }

    @Override
    public PaymentOutcomeDTO outcome(String tenant, String paymentId) {
        return exchange(tenant, () -> paymentRpcMapper.fromWirePaymentOutcomeDTO(paymentRpc.outcome(
                paymentRpcMapper.toWireIdQuery(new IdQuery(paymentId)))), false);
    }

    @Override
    public PaymentSettlementFactsDTO facts(String tenant, String paymentId) {
        return exchange(tenant, () -> paymentRpcMapper.fromWirePaymentSettlementFactsDTO(paymentRpc.facts(
                paymentRpcMapper.toWireIdQuery(new IdQuery(paymentId)))), false);
    }

    @Override
    public OrderRefundSummaryEventDTO refundSummary(String tenant, String orderId) {
        return exchange(tenant, () -> paymentRpcMapper.fromWireOrderRefundSummaryEventDTO(
                paymentRpc.refundSummary(paymentRpcMapper.toWireIdQuery(new IdQuery(orderId)))), false);
    }

    @Override
    public ParentFenceDTO fenceParent(String tenant, ParentPaymentFenceCommand command) {
        return exchange(tenant, () -> paymentRpcMapper.fromWireParentFenceDTO(paymentRpc.fenceParent(
                paymentRpcMapper.toWireParentPaymentFenceCommand(command))), false);
    }

    @Override
    public PaymentCloseDTO close(String tenant, PaymentCloseCommand command) {
        return exchange(tenant, () -> paymentRpcMapper.fromWirePaymentCloseDTO(paymentRpc.close(
                paymentRpcMapper.toWirePaymentCloseCommand(command))), false);
    }

    @Override
    public ParentFenceDTO parentFenceStatus(String tenant, ParentFenceStatusQuery query) {
        return exchange(tenant, () -> paymentRpcMapper.fromWireParentFenceDTO(paymentRpc.parentFenceStatus(
                paymentRpcMapper.toWireParentFenceStatusQuery(query))), false);
    }

    private <R> R exchange(String tenant, Supplier<R> call, boolean guarded) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            R response = RpcTokenContext.call(credential(tenant), call);
            if (response == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
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
