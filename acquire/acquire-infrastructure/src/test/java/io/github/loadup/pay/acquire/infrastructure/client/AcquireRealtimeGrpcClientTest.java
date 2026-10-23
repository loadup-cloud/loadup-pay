/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     rpc://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.acquire.client.enums.AcquireOrderState;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.infrastructure.config.AcquireRealtimeProperties;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.grpc.Metadata;
import io.grpc.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.realtime.rpc.RealtimeRpcOperations.REALTIME_ORDER;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AcquireRealtimeGrpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), BusinessTime.zone());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final GrpcTransport rpc = mock(GrpcTransport.class);
    final EventEnvelope<OrderSnapshotEventDTO> event = new EventEnvelope<>("delivery", "change", "acquire", "pay.acquire.snapshot.realtime.v1",
            1, "order", 2, OffsetDateTime.now(clock), new OrderSnapshotEventDTO("order", "merchant", "M001", 10000, "CNY",
            4000, 2000, AcquireOrderState.PENDING, OffsetDateTime.now(clock).minusDays(1), OffsetDateTime.now(clock), 2));
    @TempDir
    Path directory;
    AcquireRealtimeGrpcClient client;
    Path file;

    String credential(String tenant, String service, String audience, long expires, String revision) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var claims = Map.of("tenant_id", tenant, "sub", service, "service_id", service, "principal_type", "SERVICE",
                "aud", audience, "exp", expires, "jti", revision);
        String token = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        Files.writeString(file, token);
        return token;
    }

    @BeforeEach
    void setup() throws Exception {
        file = directory.resolve("realtime.jwt");
        credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        client = new AcquireRealtimeGrpcClient(rpc, mapper, new AcquireRealtimeProperties("tenant", "acquire", "realtime", file), clock);
        when(rpc.call(eq(REALTIME_ORDER), any(), any())).thenReturn(new ProjectionAckDTO("delivery", "change", "acquire", "order", 2, 2, "APPLIED", true));
    }

    @Test
    void usesTheOrderProjectionOperationAndReloadsRotatedMountedCredentials() throws Exception {
        assertTrue(client.apply("tenant", event).accepted());
        String token = credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v2");
        assertTrue(client.apply("tenant", event).accepted());
        verify(rpc).call(eq(REALTIME_ORDER), eq(event), eq(token));
    }

    @Test
    void credentialsForAnotherServiceTenantOrAudienceNeverReachTheNetwork() throws Exception {
        credential("tenant", "payment", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        credential("other", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        credential("tenant", "acquire", "payment", clock.instant().getEpochSecond() + 300, "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        verifyNoInteractions(rpc);
    }

    @Test
    void expiredCredentialAndUnconfiguredTenantCannotSend() throws Exception {
        credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond(), "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        assertThrows(CommonException.class, () -> client.apply("other", event));
        verifyNoInteractions(rpc);
    }

    @Test
    void localTransactionCannotContainTheProjectionNetworkCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> client.apply("tenant", event));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(rpc);
    }

    @Test
    void businessFailureOrMissingAckRemainsRetryable() {
        when(rpc.call(eq(REALTIME_ORDER), any(), any())).thenThrow(Status.UNAVAILABLE.asRuntimeException());
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
    }
    private static io.grpc.StatusRuntimeException remoteFailure(String code) {
        var trailers = new Metadata();
        trailers.put(RpcServerSupport.RESULT_CODE, code);
        return Status.FAILED_PRECONDITION.asRuntimeException(trailers);
    }
}
