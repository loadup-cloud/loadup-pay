package io.github.loadup.pay.acquire.infrastructure.client;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.acquire.app.port.AcquireRealtimeClient;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.infrastructure.config.AcquireRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.client.http.RealtimeHttpService;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AcquireRealtimeHttpClient implements AcquireRealtimeClient {
    private final AcquireRealtimeProperties properties;
    private final AcquireServiceTokenReader tokens;
    private final RealtimeHttpService realtimeHttp;

    public AcquireRealtimeHttpClient(
            RealtimeHttpService realtimeHttp, JsonMapper mapper, AcquireRealtimeProperties properties, Clock clock) {
        this.realtimeHttp = realtimeHttp;
        this.properties = properties;
        this.tokens = new AcquireServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public ProjectionAckDTO apply(String tenant, EventEnvelope<OrderSnapshotEventDTO> event) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            var token = tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
            return HttpServiceResponses.requiredData(realtimeHttp.order("Bearer " + token, event));
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
