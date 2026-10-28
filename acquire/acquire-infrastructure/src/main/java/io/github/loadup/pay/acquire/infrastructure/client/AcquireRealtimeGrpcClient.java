/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.pay.acquire.app.port.AcquireRealtimeClient;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.infrastructure.config.AcquireRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.realtime.rpc.RealtimeRpcOperations.REALTIME_ORDER;

@Component
public class AcquireRealtimeGrpcClient implements AcquireRealtimeClient {
    private final GrpcTransport rpc;
    private final AcquireRealtimeProperties properties;
    private final AcquireServiceTokenReader tokens;

    public AcquireRealtimeGrpcClient(GrpcTransport rpc, JsonMapper mapper, AcquireRealtimeProperties properties,
                                     @Qualifier("acquireClock") Clock clock) {
        this.rpc = rpc;
        this.properties = properties;
        this.tokens = new AcquireServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public ProjectionAckDTO apply(String tenant, EventEnvelope<OrderSnapshotEventDTO> event) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var token = tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
            var response = rpc.call(REALTIME_ORDER, event, token);
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
