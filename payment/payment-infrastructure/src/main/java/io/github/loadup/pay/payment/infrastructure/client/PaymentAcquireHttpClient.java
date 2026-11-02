package io.github.loadup.pay.payment.infrastructure.client;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.acquire.client.http.AcquireHttpService;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentAcquireProperties;
import java.time.Clock;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentAcquireHttpClient implements PaymentAcquireClient {
    private final PaymentAcquireProperties properties;
    private final PaymentServiceTokenReader tokens;
    private final AcquireHttpService acquireHttp;

    public PaymentAcquireHttpClient(
            AcquireHttpService acquireHttp, JsonMapper mapper, PaymentAcquireProperties properties, Clock clock) {
        this.acquireHttp = acquireHttp;
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentOutcomeAckDTO accept(String tenant, EventEnvelope<PaymentOutcomeEventDTO> event) {
        return exchange(
                tenant,
                authorization -> acquireHttp.paymentOutcome(authorization, event));
    }

    @Override
    public FeeRefundAckDTO acceptFeeRefund(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(
                tenant,
                authorization -> acquireHttp.feeRefund(authorization, event));
    }

    @Override
    public RefundNotificationAckDTO acceptRefundNotification(
            String tenant, EventEnvelope<RefundMerchantEventDTO> event) {
        return exchange(
                tenant,
                authorization -> acquireHttp.refundNotification(authorization, event));
    }

    private <R> R exchange(String tenant, Function<String, SuccessResponse<R>> call) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            var token = tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
            return HttpServiceResponses.requiredData(call.apply("Bearer " + token));
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
