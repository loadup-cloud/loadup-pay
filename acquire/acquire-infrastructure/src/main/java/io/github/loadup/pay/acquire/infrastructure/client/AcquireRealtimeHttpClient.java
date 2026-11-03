/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.infrastructure.client;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.pay.acquire.app.port.AcquireRealtimeClient;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.infrastructure.config.AcquireRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.client.http.RealtimeHttpService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AcquireRealtimeHttpClient implements AcquireRealtimeClient {
    private final AcquireRealtimeProperties properties;
    private final ServiceAccessTokens tokens;
    private final RealtimeHttpService realtimeHttp;

    public AcquireRealtimeHttpClient(
            RealtimeHttpService realtimeHttp, ServiceAccessTokens tokens, AcquireRealtimeProperties properties) {
        this.realtimeHttp = realtimeHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public ProjectionAckDTO apply(EventEnvelope<OrderSnapshotEventDTO> event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            var token = tokens.token(properties.serviceId(), properties.audience());
            return HttpServiceResponses.requiredData(realtimeHttp.order("Bearer " + token, event));
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
