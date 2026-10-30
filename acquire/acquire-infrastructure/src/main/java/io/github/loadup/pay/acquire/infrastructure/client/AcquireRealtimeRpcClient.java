package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.acquire.app.port.AcquireRealtimeClient;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.infrastructure.config.AcquireRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.rpc.RealtimeRpcMapper;
import io.github.loadup.pay.realtime.rpc.proto.RealtimeRpc;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

@Component
public class AcquireRealtimeRpcClient implements AcquireRealtimeClient {
    private final AcquireRealtimeProperties properties;
    private final AcquireServiceTokenReader tokens;
    private final RealtimeRpcMapper realtimeRpcMapper;
    @DubboReference(url = "tri://realtime-headless:50051")
    private RealtimeRpc realtimeRpc;

    public AcquireRealtimeRpcClient(RealtimeRpcMapper realtimeRpcMapper, JsonMapper mapper, AcquireRealtimeProperties properties,
                                    Clock clock) {
        this.realtimeRpcMapper = realtimeRpcMapper;
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
            var response = RpcTokenContext.call(
                    token,
                    () -> realtimeRpcMapper.fromWireProjectionAckDTO(realtimeRpc.order(
                            realtimeRpcMapper.toWirePayEventEnvelopeOfOrderSnapshotEventDTO(event))));
            if (response == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
