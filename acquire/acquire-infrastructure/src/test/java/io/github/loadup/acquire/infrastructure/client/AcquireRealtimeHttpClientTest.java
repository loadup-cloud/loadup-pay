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
package io.github.loadup.acquire.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.acquire.infrastructure.config.AcquireRealtimeProperties;
import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.contracts.event.OrderSnapshotEventDTO;
import io.github.loadup.contracts.model.AcquireOrderState;
import io.github.loadup.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.realtime.client.http.RealtimeHttpService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class AcquireRealtimeHttpClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), BusinessTime.zone());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final RealtimeHttpService realtimeHttp = mock(RealtimeHttpService.class);
    final List<String> attachments = new ArrayList<>();
    final EventEnvelope<OrderSnapshotEventDTO> event = new EventEnvelope<>(
            "delivery",
            "change",
            "acquire",
            "pay.acquire.snapshot.realtime.v1",
            1,
            "order",
            2,
            OffsetDateTime.now(clock),
            new OrderSnapshotEventDTO(
                    "order",
                    "merchant",
                    "M001",
                    10000,
                    "CNY",
                    4000,
                    2000,
                    AcquireOrderState.PENDING,
                    OffsetDateTime.now(clock).minusDays(1),
                    OffsetDateTime.now(clock),
                    2));

    AcquireRealtimeHttpClient client;
    String credential;

    String credential(String tenant, String service, String audience, long expires, String revision) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var claims = Map.of(
                "tenant_id",
                tenant,
                "sub",
                service,
                "service_id",
                service,
                "principal_type",
                "SERVICE",
                "aud",
                audience,
                "exp",
                expires,
                "jti",
                revision);
        String token = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        credential = token;
        return token;
    }

    @BeforeEach
    void setup() throws Exception {
        credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        client = new AcquireRealtimeHttpClient(
                realtimeHttp, (service, audience) -> credential, new AcquireRealtimeProperties("acquire", "realtime"));
        doAnswer(invocation -> {
                    attachments.add(invocation.getArgument(0, String.class));
                    return SuccessResponse.of(
                            new ProjectionAckDTO("delivery", "change", "acquire", "order", 2, 2, "APPLIED", true));
                })
                .when(realtimeHttp)
                .order(anyString(), any());
    }

    @Test
    void usesTheOrderProjectionOperationWithTheCurrentServiceToken() throws Exception {
        String first =
                credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        assertTrue(client.apply(event).accepted());
        String second =
                credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v2");
        assertTrue(client.apply(event).accepted());
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
    }

    @Test
    void localTransactionCannotContainTheProjectionNetworkCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> client.apply(event));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(realtimeHttp);
    }

    @Test
    void businessFailureOrMissingAckRemainsRetryable() {
        doAnswer(invocation -> {
                    return new SuccessResponse<>(Result.buildFailure(CommonResultCodeEnum.ACCESS_DENIED), null);
                })
                .when(realtimeHttp)
                .order(anyString(), any());
        assertThrows(CommonException.class, () -> client.apply(event));
        doAnswer(invocation -> null).when(realtimeHttp).order(anyString(), any());
        assertThrows(CommonException.class, () -> client.apply(event));
    }
}
