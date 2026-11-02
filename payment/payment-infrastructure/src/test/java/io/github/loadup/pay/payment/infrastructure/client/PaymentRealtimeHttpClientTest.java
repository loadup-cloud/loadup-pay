package io.github.loadup.pay.payment.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.pay.realtime.client.http.RealtimeHttpService;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.infrastructure.config.PaymentRealtimeProperties;
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

class PaymentRealtimeHttpClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), BusinessTime.zone());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final RealtimeHttpService realtimeHttp = mock(RealtimeHttpService.class);
    final List<String> attachments = new ArrayList<>();
    final EventEnvelope<PaymentSnapshotEventDTO> event = new EventEnvelope<>(
            "delivery",
            "proof",
            "payment",
            "pay.payment.snapshot.realtime.v1",
            1,
            "payment",
            2,
            OffsetDateTime.now(clock),
            new PaymentSnapshotEventDTO(
                    "payment",
                    "order",
                    "merchant",
                    1000,
                    "CNY",
                    PaymentState.SUCCEEDED,
                    Channel.WECHAT,
                    AccessMode.DIRECT,
                    OffsetDateTime.now(clock),
                    OffsetDateTime.now(clock),
                    2));

    @TempDir
    Path directory;

    PaymentRealtimeHttpClient client;
    Path file;

    void credential(String audience) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var claims = Map.of(
                "tenant_id",
                "tenant",
                "sub",
                "payment",
                "service_id",
                "payment",
                "principal_type",
                "SERVICE",
                "aud",
                audience,
                "exp",
                clock.instant().getEpochSecond() + 300);
        Files.writeString(
                file,
                encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                        + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj");
    }

    @BeforeEach
    void setup() throws Exception {
        file = directory.resolve("realtime.jwt");
        credential("realtime");
        client = new PaymentRealtimeHttpClient(
                realtimeHttp, mapper, new PaymentRealtimeProperties("tenant", "payment", "realtime", file), clock);
        doAnswer(invocation -> {
                    attachments.add(invocation.getArgument(0, String.class));
                    return SuccessResponse.of(
                            new ProjectionAckDTO("delivery", "proof", "payment", "payment", 2, 2, "APPLIED", true));
                })
                .when(realtimeHttp)
                .payment(anyString(), any());
    }

    @Test
    void usesTheNamedProjectionOperationAndTypedAck() {
        assertTrue(client.apply("tenant", event).accepted());
        verify(realtimeHttp).payment(anyString(), any());
        assertEquals(1, attachments.size());
        assertTrue(attachments.getFirst().startsWith("Bearer "));
    }

    @Test
    void childAndMonetarySummaryUseTheirIndependentNamedOperations() {
        var at = OffsetDateTime.now(clock);
        var refund = new EventEnvelope<>(
                "refund-delivery",
                "refund-change",
                "payment",
                "pay.payment.refund.realtime.v1",
                1,
                "refund",
                0,
                at,
                new RefundSnapshotEventDTO(
                        "refund",
                        "request",
                        "payment",
                        "order",
                        "merchant",
                        1000,
                        "CNY",
                        RefundState.INIT,
                        at,
                        null,
                        0));
        var summary = new EventEnvelope<>(
                "summary-delivery",
                "summary-change",
                "payment",
                "pay.payment.refund-summary.realtime.v1",
                1,
                "order",
                1,
                at,
                new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 0, 1000, 1));
        var refundAck =
                new ProjectionAckDTO("refund-delivery", "refund-change", "payment", "refund", 0, 0, "APPLIED", true);
        var summaryAck =
                new ProjectionAckDTO("summary-delivery", "summary-change", "payment", "order", 1, 1, "APPLIED", true);
        doAnswer(invocation -> SuccessResponse.of(refundAck))
                .when(realtimeHttp)
                .refund(anyString(), any());
        doAnswer(invocation -> SuccessResponse.of(summaryAck))
                .when(realtimeHttp)
                .refundSummary(anyString(), any());
        assertEquals(refundAck, client.applyRefund("tenant", refund));
        assertEquals(summaryAck, client.applyRefundSummary("tenant", summary));
        verify(realtimeHttp).refund(anyString(), any());
        verify(realtimeHttp).refundSummary(anyString(), any());
    }

    @Test
    void anotherAudienceCannotUseTheMountedCredential() throws Exception {
        credential("acquire");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        verifyNoInteractions(realtimeHttp);
    }

    @Test
    void unconfiguredTenantCannotBeRoutedToTheReadModel() {
        assertThrows(CommonException.class, () -> client.apply("other-tenant", event));
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
    void businessFailureIsNotProjectedAsSuccess() {
        doAnswer(invocation -> new SuccessResponse<>(Result.buildFailure(CommonResultCodeEnum.CONFLICT), null))
                .when(realtimeHttp)
                .payment(anyString(), any());
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
    }
}
