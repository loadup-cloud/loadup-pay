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
package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.infrastructure.config.PaymentRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

import static io.github.loadup.pay.realtime.rpc.RealtimeRpcOperations.REALTIME_PAYMENT;
import static io.github.loadup.pay.realtime.rpc.RealtimeRpcOperations.REALTIME_REFUND;
import static io.github.loadup.pay.realtime.rpc.RealtimeRpcOperations.REALTIME_REFUND_SUMMARY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentRealtimeGrpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), BusinessTime.zone());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final GrpcTransport rpc = mock(GrpcTransport.class);
    final EventEnvelope<PaymentSnapshotEventDTO> event = new EventEnvelope<>("delivery", "proof", "payment", "pay.payment.snapshot.realtime.v1",
            1, "payment", 2, OffsetDateTime.now(clock), new PaymentSnapshotEventDTO("payment", "order", "merchant", 1000, "CNY",
            PaymentState.SUCCEEDED, Channel.WECHAT, AccessMode.DIRECT, OffsetDateTime.now(clock), OffsetDateTime.now(clock), 2));
    @TempDir
    Path directory;
    PaymentRealtimeGrpcClient client;
    Path file;

    private static io.grpc.StatusRuntimeException remoteFailure(String code) {
        var trailers = new Metadata();
        trailers.put(RpcServerSupport.RESULT_CODE, code);
        return Status.FAILED_PRECONDITION.asRuntimeException(trailers);
    }

    void credential(String audience) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var claims = Map.of("tenant_id", "tenant", "sub", "payment", "service_id", "payment", "principal_type", "SERVICE",
                "aud", audience, "exp", clock.instant().getEpochSecond() + 300);
        Files.writeString(file, encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj");
    }

    @BeforeEach
    void setup() throws Exception {
        file = directory.resolve("realtime.jwt");
        credential("realtime");
        client = new PaymentRealtimeGrpcClient(rpc, mapper, new PaymentRealtimeProperties("tenant", "payment", "realtime", file), clock);
        when(rpc.call(eq(REALTIME_PAYMENT), any(), any())).thenReturn(new ProjectionAckDTO("delivery", "proof", "payment", "payment", 2, 2, "APPLIED", true));
    }

    @Test
    void usesTheNamedProjectionOperationAndTypedAck() {
        assertTrue(client.apply("tenant", event).accepted());
        verify(rpc).call(eq(REALTIME_PAYMENT), eq(event), argThat(token -> token != null && !token.isBlank()));
    }

    @Test
    void childAndMonetarySummaryUseTheirIndependentNamedOperations() {
        var at = OffsetDateTime.now(clock);
        var refund = new EventEnvelope<>("refund-delivery", "refund-change", "payment", "pay.payment.refund.realtime.v1", 1, "refund", 0, at,
                new RefundSnapshotEventDTO("refund", "request", "payment", "order", "merchant", 1000, "CNY", RefundState.INIT, at, null, 0));
        var summary = new EventEnvelope<>("summary-delivery", "summary-change", "payment", "pay.payment.refund-summary.realtime.v1", 1, "order", 1, at,
                new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 0, 1000, 1));
        when(rpc.call(eq(REALTIME_REFUND), any(), any())).thenReturn(new ProjectionAckDTO("refund-delivery", "refund-change", "payment", "refund", 0, 0, "APPLIED", true));
        when(rpc.call(eq(REALTIME_REFUND_SUMMARY), any(), any())).thenReturn(new ProjectionAckDTO("summary-delivery", "summary-change", "payment", "order", 1, 1, "APPLIED", true));
        assertTrue(client.applyRefund("tenant", refund).accepted());
        assertTrue(client.applyRefundSummary("tenant", summary).accepted());
        verify(rpc).call(eq(REALTIME_REFUND), eq(refund), any());
        verify(rpc).call(eq(REALTIME_REFUND_SUMMARY), eq(summary), any());
    }

    @Test
    void anotherAudienceCannotUseTheMountedCredential() throws Exception {
        credential("acquire");
        assertThrows(CommonException.class, () -> client.apply("tenant", event));
        verifyNoInteractions(rpc);
    }

    @Test
    void unconfiguredTenantCannotBeRoutedToTheReadModel() {
        assertThrows(CommonException.class, () -> client.apply("other-tenant", event));
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
}
