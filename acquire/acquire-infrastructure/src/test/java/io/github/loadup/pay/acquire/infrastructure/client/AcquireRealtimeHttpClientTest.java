package io.github.loadup.pay.acquire.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.pay.realtime.client.http.RealtimeHttpService;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.infrastructure.config.AcquireRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    @TempDir
    Path directory;

    AcquireRealtimeHttpClient client;
    Path file;

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
        Files.writeString(file, token);
        return token;
    }

    @BeforeEach
    void setup() throws Exception {
        file = directory.resolve("realtime.jwt");
        credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        client = new AcquireRealtimeHttpClient(
                realtimeHttp, mapper, new AcquireRealtimeProperties("tenant", "acquire", "realtime", file), clock);
        doAnswer(invocation -> {
                    attachments.add(invocation.getArgument(0, String.class));
                    return SuccessResponse.of(
                            new ProjectionAckDTO("delivery", "change", "acquire", "order", 2, 2, "APPLIED", true));
                })
                .when(realtimeHttp)
                .order(anyString(), any());
    }

    @Test
    void usesTheOrderProjectionOperationAndReloadsRotatedMountedCredentials() throws Exception {
        String first =
                credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v1");
        assertTrue(client.apply("tenant", event).accepted());
        String second =
                credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond() + 300, "v2");
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
        verifyNoInteractions(realtimeHttp);
    }

    @Test
    void expiredCredentialAndUnconfiguredTenantCannotSend() throws Exception {
        credential("tenant", "acquire", "realtime", clock.instant().getEpochSecond(), "v1");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        assertThrows(CommonException.class, () -> client.apply("other", event));
        verifyNoInteractions(realtimeHttp);
    }

    @Test
    void localTransactionCannotContainTheProjectionNetworkCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> client.apply("tenant", event));
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
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        doAnswer(invocation -> null).when(realtimeHttp).order(anyString(), any());
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
    }
}
