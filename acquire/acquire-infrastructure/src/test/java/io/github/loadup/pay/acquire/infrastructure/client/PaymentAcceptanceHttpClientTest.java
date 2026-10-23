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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.*;
import io.github.loadup.components.http.*;
import io.github.loadup.pay.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentAcceptanceHttpClientTest {
    @TempDir
    Path directory;

    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    final HttpTemplate http = mock(HttpTemplate.class);
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentExecuteCommand command = mock(PaymentExecuteCommand.class);
    Path tokenFile;
    PaymentAcceptanceHttpClient client;

    @BeforeEach
    void setup() throws Exception {
        tokenFile = directory.resolve("payment.jwt");
        client = new PaymentAcceptanceHttpClient(
                http, mapper, new PaymentTransportProperties("tenant", "acquire", "payment", tokenFile), clock);
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
        when(http.exchange(eq("payment"), anyString(), any(HttpCall.class)))
                .thenReturn(ResponseEntity.ok(mapper.writeValueAsBytes(SuccessResponse.of(result))));
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
        var calls = ArgumentCaptor.forClass(HttpCall.class);
        verify(http, times(2)).exchange(eq("payment"), eq("accept"), calls.capture());
        assertEquals("Bearer " + first, calls.getAllValues().get(0).headers().get("Authorization"));
        assertEquals("Bearer " + second, calls.getAllValues().get(1).headers().get("Authorization"));
        assertSame(command, calls.getValue().body());
    }

    @Test
    void detailUsesTheSameCredentialAndAnIdQuery() {
        client.detail("tenant", "payment");
        var calls = ArgumentCaptor.forClass(HttpCall.class);
        verify(http).exchange(eq("payment"), eq("detail"), calls.capture());
        assertEquals(new IdQuery("payment"), calls.getValue().body());
    }

    @Test
    void eventAuthorityUsesTheRichFactsOperationInsteadOfDiscardingOperationAndPayee() {
        var payment = new PaymentExecutionDTO("payment", "order", "allocation", 100, "CNY", PaymentState.SUCCEEDED,
                "attempt", null, "proof", 2, OffsetDateTime.now(clock));
        when(http.exchange(eq("payment"), eq("facts"), any(HttpCall.class))).thenReturn(ResponseEntity.ok(
                mapper.writeValueAsBytes(SuccessResponse.of(new PaymentSettlementFactsDTO(payment, "operation", "merchant", false)))));
        var facts = client.facts("tenant", "payment");
        assertEquals("operation", facts.limitOperationId());
        assertEquals("merchant", facts.payeeMerchantId());
        var calls = ArgumentCaptor.forClass(HttpCall.class);
        verify(http).exchange(eq("payment"), eq("facts"), calls.capture());
        assertEquals(new IdQuery("payment"), calls.getValue().body());
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
        verifyNoInteractions(http);
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
        verifyNoInteractions(http);
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
        verifyNoInteractions(http);
    }

    @Test
    void remoteBusinessAndTransportFailuresCannotMasqueradeAsAcceptance() {
        when(http.exchange(eq("payment"), eq("accept"), any(HttpCall.class)))
                .thenReturn(ResponseEntity.ok(mapper.writeValueAsBytes(FailureResponse.of(ACTION_FORBIDDEN))))
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
        verifyNoInteractions(http);
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
        verifyNoInteractions(http);
    }
}
