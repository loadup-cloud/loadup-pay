package io.github.loadup.pay.payment.infrastructure.client;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.client.http.RealtimeHttpService;
import java.time.Clock;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentRealtimeHttpClient implements PaymentRealtimeClient {
    private final PaymentRealtimeProperties properties;
    private final PaymentServiceTokenReader tokens;
    private final RealtimeHttpService realtimeHttp;

    public PaymentRealtimeHttpClient(
            RealtimeHttpService realtimeHttp, JsonMapper mapper, PaymentRealtimeProperties properties, Clock clock) {
        this.realtimeHttp = realtimeHttp;
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public ProjectionAckDTO apply(String tenant, EventEnvelope<PaymentSnapshotEventDTO> event) {
        return exchange(
                tenant,
                authorization -> realtimeHttp.payment(authorization, event));
    }

    @Override
    public ProjectionAckDTO applyRefund(String tenant, EventEnvelope<RefundSnapshotEventDTO> event) {
        return exchange(
                tenant,
                authorization -> realtimeHttp.refund(authorization, event));
    }

    @Override
    public ProjectionAckDTO applyRefundSummary(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(
                tenant,
                authorization -> realtimeHttp.refundSummary(authorization, event));
    }

    private ProjectionAckDTO exchange(String tenant, Function<String, SuccessResponse<ProjectionAckDTO>> call) {
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
