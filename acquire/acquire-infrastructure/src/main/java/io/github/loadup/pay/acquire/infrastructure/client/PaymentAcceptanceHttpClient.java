package io.github.loadup.pay.acquire.infrastructure.client;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.PAYMENT_INPUT_EXPIRED;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.pay.acquire.app.port.PaymentClosureClient;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.http.PaymentHttpService;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
import io.github.loadup.pay.security.ServiceAccessTokens;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentAcceptanceHttpClient
        implements PaymentAcceptanceClient, PaymentOutcomeClient, PaymentClosureClient {
    private final PaymentTransportProperties properties;
    private final ServiceAccessTokens tokens;
    private final PaymentHttpService paymentHttp;

    public PaymentAcceptanceHttpClient(
            PaymentHttpService paymentHttp, ServiceAccessTokens tokens, PaymentTransportProperties properties) {
        this.paymentHttp = paymentHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentInputDTO prepareInput(String tenant, PaymentInputPrepareCommand command) {
        return exchange(tenant, authorization -> paymentHttp.inputPrepare(authorization, command), true);
    }

    @Override
    public PaymentInputLookupDTO lookupInput(String tenant, PaymentInputLookupQuery query) {
        return exchange(tenant, authorization -> paymentHttp.inputLookup(authorization, query), true);
    }

    @Override
    public PaymentInputExpiryDTO expireInput(String tenant, PaymentInputExpireCommand command) {
        return exchange(tenant, authorization -> paymentHttp.inputExpire(authorization, command), true);
    }

    @Override
    public PaymentExecutionDTO accept(String tenant, PaymentExecuteCommand command) {
        return exchange(tenant, authorization -> paymentHttp.accept(authorization, command), true);
    }

    @Override
    public PaymentExecutionDTO detail(String tenant, String paymentId) {
        return exchange(tenant, authorization -> paymentHttp.detail(authorization, new IdQuery(paymentId)), false);
    }

    @Override
    public PaymentOutcomeDTO outcome(String tenant, String paymentId) {
        return exchange(tenant, authorization -> paymentHttp.outcome(authorization, new IdQuery(paymentId)), false);
    }

    @Override
    public PaymentSettlementFactsDTO facts(String tenant, String paymentId) {
        return exchange(tenant, authorization -> paymentHttp.facts(authorization, new IdQuery(paymentId)), false);
    }

    @Override
    public OrderRefundSummaryEventDTO refundSummary(String tenant, String orderId) {
        return exchange(tenant, authorization -> paymentHttp.refundSummary(authorization, new IdQuery(orderId)), false);
    }

    @Override
    public ParentFenceDTO fenceParent(String tenant, ParentPaymentFenceCommand command) {
        return exchange(tenant, authorization -> paymentHttp.fenceParent(authorization, command), false);
    }

    @Override
    public PaymentCloseDTO close(String tenant, PaymentCloseCommand command) {
        return exchange(tenant, authorization -> paymentHttp.close(authorization, command), false);
    }

    @Override
    public ParentFenceDTO parentFenceStatus(String tenant, ParentFenceStatusQuery query) {
        return exchange(tenant, authorization -> paymentHttp.parentFenceStatus(authorization, query), false);
    }

    private <R> R exchange(String tenant, Function<String, SuccessResponse<R>> call, boolean guarded) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            return HttpServiceResponses.requiredData(call.apply("Bearer " + credential(tenant)));
        } catch (RuntimeException failure) {
            if (guarded) {
                var code =
                        switch (HttpServiceResponses.resultCode(failure)) {
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
        return tokens.token(tenant, properties.serviceId(), properties.audience());
    }
}
