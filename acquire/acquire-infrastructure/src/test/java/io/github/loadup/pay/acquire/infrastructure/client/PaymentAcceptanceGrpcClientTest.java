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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
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

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_ACCEPT;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_DETAIL;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_FACTS;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_REFUND_SUMMARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentAcceptanceGrpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"), ZoneId.systemDefault());
    final GrpcTransport rpc = mock(GrpcTransport.class);
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentExecuteCommand command = mock(PaymentExecuteCommand.class);
    @TempDir
    Path directory;
    Path tokenFile;
    PaymentAcceptanceGrpcClient client;

    @BeforeEach
    void setup() throws Exception {
        tokenFile = directory.resolve("payment.jwt");
        client = new PaymentAcceptanceGrpcClient(
                rpc, mapper, new PaymentTransportProperties("tenant", "acquire", "payment", tokenFile), clock);
        writeToken(claims());
        var result = new PaymentExecutionDTO(
                "payment",
                "order",
                "allocation",
                100,
                "CNY",
                PaymentState.INIT,
                null,
                null,
                null,
                0,
                OffsetDateTime.now(clock));
        when(rpc.call(any(), any(), any()))
                .thenReturn(result);
    }

    Map<String, Object> claims() {
        var value = new HashMap<String, Object>();
        value.put("tenant_id", "tenant");
        value.put("sub", "acquire");
        value.put("service_id", "acquire");
        value.put("principal_type", "SERVICE");
        value.put("aud", List.of("payment"));
        value.put("exp", clock.instant().getEpochSecond() + 300);
        return value;
    }

    String writeToken(Map<String, Object> claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String value = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        Files.writeString(tokenFile, value + "\n");
        return value;
    }

    @Test
    void usesTheMountedServiceCredentialAndRereadsItAfterRotation() throws Exception {
        String first = Files.readString(tokenFile).strip();
        assertEquals("payment", client.accept("tenant", command).paymentId());
        var next = claims();
        next.put("exp", clock.instant().getEpochSecond() + 600);
        String second = writeToken(next);
        client.accept("tenant", command);
        var calls = ArgumentCaptor.forClass(String.class);
        verify(rpc, times(2)).call(eq(PAYMENT_ACCEPT), eq(command), calls.capture());
        assertEquals(first, calls.getAllValues().get(0));
        assertEquals(second, calls.getAllValues().get(1));

    }

    @Test
    void detailUsesTheSameCredentialAndAnIdQuery() {
        client.detail("tenant", "payment");
        var calls = ArgumentCaptor.forClass(String.class);
        verify(rpc).call(eq(PAYMENT_DETAIL), eq(new IdQuery("payment")), calls.capture());

    }

    @Test
    void refundAuthorityReadsTheOrderSummaryThroughItsDedicatedOperation() {
        var summary = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        when(rpc.call(eq(PAYMENT_REFUND_SUMMARY), any(), any())).thenReturn(summary);
        assertEquals(summary, client.refundSummary("tenant", "order"));
        verify(rpc).call(eq(PAYMENT_REFUND_SUMMARY), eq(new IdQuery("order")), any());
    }

    @Test
    void eventAuthorityUsesTheRichFactsOperationInsteadOfDiscardingOperationAndPayee() {
        var payment = new PaymentExecutionDTO("payment", "order", "allocation", 100, "CNY", PaymentState.SUCCEEDED,
                "attempt", null, "proof", 2, OffsetDateTime.now(clock));
        when(rpc.call(eq(PAYMENT_FACTS), any(), any())).thenReturn(new PaymentSettlementFactsDTO(payment, "operation", "merchant", false));
        var facts = client.facts("tenant", "payment");
        assertEquals("operation", facts.limitOperationId());
        assertEquals("merchant", facts.payeeMerchantId());
        var calls = ArgumentCaptor.forClass(String.class);
        verify(rpc).call(eq(PAYMENT_FACTS), eq(new IdQuery("payment")), calls.capture());

    }

    @Test
    void mismatchedTenantSubjectServiceTypeOrAudienceNeverReachesTheNetwork() throws Exception {
        for (var field : List.of("tenant_id", "sub", "service_id", "principal_type", "aud")) {
            var value = claims();
            value.put(field, "another");
            writeToken(value);
            assertSame(
                    DEPENDENCY_UNAVAILABLE,
                    assertThrows(CommonException.class, () -> client.accept("tenant", command))
                            .getResultCode());
        }
        verifyNoInteractions(rpc);
    }

    @Test
    void expiredNotYetValidAndFractionalExpiriesAreRejected() throws Exception {
        for (Object expiry :
                List.of(clock.instant().getEpochSecond(), clock.instant().getEpochSecond() - 1, 1.5, "later")) {
            var value = claims();
            value.put("exp", expiry);
            writeToken(value);
            assertSame(
                    DEPENDENCY_UNAVAILABLE,
                    assertThrows(CommonException.class, () -> client.accept("tenant", command))
                            .getResultCode());
        }
        var value = claims();
        value.put("nbf", clock.instant().getEpochSecond() + 1);
        writeToken(value);
        assertThrows(CommonException.class, () -> client.accept("tenant", command));
        verifyNoInteractions(rpc);
    }

    @Test
    void missingOrOversizedTokenFailsWithoutExposingTheCredential() throws Exception {
        Files.writeString(tokenFile, "synthetic-secret".repeat(2000));
        var failure = assertThrows(CommonException.class, () -> client.accept("tenant", command));
        assertFalse(failure.toString().contains("synthetic-secret"));
        assertNull(failure.getCause());
        Files.delete(tokenFile);
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.accept("tenant", command))
                        .getResultCode());
        verifyNoInteractions(rpc);
    }

    @Test
    void remoteBusinessAndTransportFailuresCannotMasqueradeAsAcceptance() {
        when(rpc.call(eq(PAYMENT_ACCEPT), any(), any()))
                .thenThrow(remoteFailure((ACTION_FORBIDDEN).getCode()))
                .thenThrow(new IllegalArgumentException("synthetic-secret"));
        for (int i = 0; i < 2; i++) {
            var failure = assertThrows(CommonException.class, () -> client.accept("tenant", command));
            assertSame(DEPENDENCY_UNAVAILABLE, failure.getResultCode());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("synthetic-secret"));
        }
    }

    @Test
    void tenantRoutingIsCheckedBeforeReadingAnyCredential() {
        assertFalse(client.supportsTenant("other"));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> client.accept("other", command))
                        .getResultCode());
        verifyNoInteractions(rpc);
    }

    @Test
    void outerTransactionsAreRejectedBeforeAnyNetworkRequest() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.accept("tenant", command))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(rpc);
    }
    @Test
    void inputLookupUsesTypedRpcAndNeverTreatsTransportFailureAsInputAbsence() {
        var query = new io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery("payment", "order", "allocation", "merchant", 100L,
                "CNY", "action", new io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO(
                io.github.loadup.commons.payment.Channel.WECHAT, io.github.loadup.commons.payment.PaymentMethod.H5));
        var operation = io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_INPUT_LOOKUP;
        var absent = new io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO(false, null);
        when(rpc.call(eq(operation), eq(query), any())).thenReturn(absent);
        assertSame(absent, client.lookupInput("tenant", query));
        when(rpc.call(eq(operation), eq(query), any())).thenThrow(remoteFailure("PAYMENT_NOT_FOUND"));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.lookupInput("tenant", query)).getResultCode());
        when(rpc.call(eq(operation), eq(query), any())).thenThrow(remoteFailure("PAYMENT_IDEMPOTENCY_CONFLICT"));
        assertSame(io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> client.lookupInput("tenant", query)).getResultCode());
    }

    private static io.grpc.StatusRuntimeException remoteFailure(String code) {
        var trailers = new Metadata();
        trailers.put(RpcServerSupport.RESULT_CODE, code);
        return Status.FAILED_PRECONDITION.asRuntimeException(trailers);
    }
}
