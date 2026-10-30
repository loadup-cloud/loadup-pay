package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.infrastructure.config.AcquireRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.rpc.RealtimeRpcMapper;
import io.github.loadup.pay.realtime.rpc.proto.RealtimeRpc;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AcquireRealtimeRpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), BusinessTime.zone());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final RealtimeRpc realtimeRpc = mock(RealtimeRpc.class);
    final List<String> attachments = new ArrayList<>();
    final EventEnvelope<OrderSnapshotEventDTO> event = new EventEnvelope<>("delivery", "change", "acquire", "pay.acquire.snapshot.realtime.v1",
            1, "order", 2, OffsetDateTime.now(clock), new OrderSnapshotEventDTO("order", "merchant", "M001", 10000, "CNY",
            4000, 2000, AcquireOrderState.PENDING, OffsetDateTime.now(clock).minusDays(1), OffsetDateTime.now(clock), 2));
    @TempDir
    Path directory;
    AcquireRealtimeRpcClient client;
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
        client = new AcquireRealtimeRpcClient(mapper, new AcquireRealtimeProperties("tenant", "acquire", "realtime", file), clock);
        ReflectionTestUtils.setField(client, "realtimeRpc", realtimeRpc);
        doAnswer(invocation -> {
            attachments.add(RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
            return RealtimeRpcMapper.INSTANCE.toWireProjectionAckDTO(new ProjectionAckDTO(
                    "delivery", "change", "acquire", "order", 2, 2, "APPLIED", true));
        }).when(realtimeRpc).order(any());
    }

    @Test
    void usesTheOrderProjectionOperationAndReloadsRotatedMountedCredentials() throws Exception {
        String first = credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        assertTrue(client.apply("tenant", event).accepted());
        String second = credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v2");
        assertTrue(client.apply("tenant", event).accepted());
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
    }

    @Test
    void credentialsForAnotherServiceTenantOrAudienceNeverReachTheNetwork() throws Exception {
        credential("tenant", "payment", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        credential("other", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        credential("tenant", "acquire", "payment", clock.instant().getEpochSecond() + 300, "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        verifyNoInteractions(realtimeRpc);
    }

    @Test
    void expiredCredentialAndUnconfiguredTenantCannotSend() throws Exception {
        credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond(), "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        assertThrows(CommonException.class, () -> client.apply("other", event));
        verifyNoInteractions(realtimeRpc);
    }

    @Test
    void localTransactionCannotContainTheProjectionNetworkCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> client.apply("tenant", event));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(realtimeRpc);
    }

    @Test
    void businessFailureOrMissingAckRemainsRetryable() {
        doAnswer(invocation -> {
            throw new RpcException(RpcException.BIZ_EXCEPTION, "loadup-result-code=ACQUIRE_ACTION_FORBIDDEN");
        }).when(realtimeRpc).order(any());
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
    }
}
