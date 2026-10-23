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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentAcquireProperties;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.grpc.Metadata;
import io.grpc.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.rpc.AcquireRpcOperations.ACQUIRE_PAYMENT_OUTCOME;
import static io.github.loadup.pay.acquire.rpc.AcquireRpcOperations.ACQUIRE_FEE_REFUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentAcquireGrpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final GrpcTransport rpc = mock(GrpcTransport.class);
    final EventEnvelope<PaymentOutcomeEventDTO> event = new EventEnvelope<>("delivery", "proof", "payment",
            "pay.payment.outcome.acquire.v1", 1, "payment", 2, OffsetDateTime.now(clock),
            new PaymentOutcomeEventDTO("payment", "order", "allocation", "operation", "merchant", 1000, "CNY",
                    PaymentState.SUCCEEDED, 2, "proof", false));
    @TempDir
    Path directory;
    Path tokenFile;
    PaymentAcquireGrpcClient client;

    Map<String, Object> claims() {
        return new HashMap<>(Map.of("tenant_id", "tenant", "sub", "payment", "service_id", "payment", "principal_type", "SERVICE",
                "aud", List.of("acquire"), "exp", clock.instant().getEpochSecond() + 300));
    }

    String token(Map<String, Object> claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var value = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        Files.writeString(tokenFile, value);
        return value;
    }

    @BeforeEach
    void setup() throws Exception {
        tokenFile = directory.resolve("acquire.jwt");
        token(claims());
        client = new PaymentAcquireGrpcClient(rpc, mapper, new PaymentAcquireProperties("tenant", "payment", "acquire", tokenFile), clock);
        when(rpc.call(eq(ACQUIRE_PAYMENT_OUTCOME), any(), any())).thenReturn(new PaymentOutcomeAckDTO("delivery", "proof", "payment", 2, "proof", 3, true));
    }

    @Test
    void sendsTheOriginalEnvelopeWithADedicatedServiceCredential() {
        assertTrue(client.accept("tenant", event).accepted());
        var call = ArgumentCaptor.forClass(String.class);
        verify(rpc).call(eq(ACQUIRE_PAYMENT_OUTCOME), eq(event), call.capture());

        assertTrue(call.getValue().split("[.]").length == 3);
    }

    @Test
    void feeTargetUsesItsOwnTypedOperationAndTheSameRotatableServiceCredential() {
        var fee = new EventEnvelope<>("fee-delivery", "fee-business", "payment", "pay.payment.refund.fee.v1", 1, "order", 3,
                OffsetDateTime.now(clock), new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 0, 3));
        var ack = new FeeRefundAckDTO("fee-delivery", "fee-business", "order", 3, 4, true);
        when(rpc.call(eq(ACQUIRE_FEE_REFUND), any(), any())).thenReturn(ack);
        assertEquals(ack, client.acceptFeeRefund("tenant", fee));
        verify(rpc).call(eq(ACQUIRE_FEE_REFUND), eq(fee), any());
    }

    @Test
    void mountedCredentialRotationIsObservedOnTheNextDelivery() throws Exception {
        var first = Files.readString(tokenFile);
        client.accept("tenant", event);
        var next = claims();
        next.put("exp", clock.instant().getEpochSecond() + 600);
        var second = token(next);
        client.accept("tenant", event);
        var calls = ArgumentCaptor.forClass(String.class);
        verify(rpc, times(2)).call(eq(ACQUIRE_PAYMENT_OUTCOME), eq(event), calls.capture());
        assertEquals(first, calls.getAllValues().getFirst());
        assertEquals(second, calls.getAllValues().getLast());
    }

    @Test
    void wrongTenantOrAudienceNeverReachesAcquire() throws Exception {
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> client.accept("other-tenant", event)).getResultCode());
        var wrong = claims();
        wrong.put("aud", "limitcenter");
        token(wrong);
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.accept("tenant", event)).getResultCode());
        verifyNoInteractions(rpc);
    }

    @Test
    void expiredOrMalformedMountedTokensAreNotSent() throws Exception {
        var expired = claims();
        expired.put("exp", clock.instant().getEpochSecond());
        token(expired);
        assertThrows(CommonException.class, () -> client.accept("tenant", event));
        Files.writeString(tokenFile, "not-a-jwt");
        assertThrows(CommonException.class, () -> client.accept("tenant", event));
        verifyNoInteractions(rpc);
    }

    @Test
    void transportFailureDoesNotBecomeAValidSemanticAck() {
        when(rpc.call(eq(ACQUIRE_PAYMENT_OUTCOME), any(), any()))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.accept("tenant", event)).getResultCode());
    }

    @Test
    void outerTransactionCannotRemainOpenDuringTheCrossServiceCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> client.accept("tenant", event)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(rpc);
    }
    private static io.grpc.StatusRuntimeException remoteFailure(String code) {
        var trailers = new Metadata();
        trailers.put(RpcServerSupport.RESULT_CODE, code);
        return Status.FAILED_PRECONDITION.asRuntimeException(trailers);
    }
}
