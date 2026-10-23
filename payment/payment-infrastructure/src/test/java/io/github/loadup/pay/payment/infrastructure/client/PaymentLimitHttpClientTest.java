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
package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.*;
import io.github.loadup.pay.payment.infrastructure.config.PaymentLimitProperties;
import io.github.loadup.pay.limitcenter.client.command.*;
import io.github.loadup.pay.limitcenter.client.dto.*;
import io.github.loadup.pay.limitcenter.client.enums.LimitReservationState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentLimitHttpClientTest {
    @TempDir Path directory;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final HttpTemplate http = mock(HttpTemplate.class);
    final LimitConfirmCommand confirm = new LimitConfirmCommand("operation", "payment", 2, "evidence", "evidence", 1000, "CNY",
            OffsetDateTime.now(clock));
    final LimitReleaseCommand release = new LimitReleaseCommand("operation", "payment", 2, "evidence", "evidence", "PAYMENT_FAILED");
    PaymentLimitHttpClient client;
    Path tokenFile;
    Map<String, Object> claims() {
        return new HashMap<>(Map.of("tenant_id", "tenant", "principal_type", "SERVICE", "sub", "payment", "service_id", "payment",
                "aud", List.of("limitcenter"), "exp", clock.instant().getEpochSecond() + 300));
    }
    String token(Map<String, Object> claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var token = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        Files.writeString(tokenFile, token); return token;
    }
    LimitReservationDTO confirmed() {
        return new LimitReservationDTO("reservation", "operation", "payment", LimitReservationState.CONFIRMED, "policy", 1,
                OffsetDateTime.now(clock), 2);
    }
    @BeforeEach void setup() throws Exception {
        tokenFile = directory.resolve("limitcenter.jwt"); token(claims());
        client = new PaymentLimitHttpClient(http, mapper, new PaymentLimitProperties("tenant", "payment", "limitcenter", tokenFile), clock);
        when(http.exchange(eq("limitcenter"), eq("confirm"), any(HttpCall.class)))
                .thenReturn(ResponseEntity.ok(mapper.writeValueAsBytes(SuccessResponse.of(confirmed()))));
        when(http.exchange(eq("limitcenter"), eq("release"), any(HttpCall.class)))
                .thenReturn(ResponseEntity.ok(mapper.writeValueAsBytes(SuccessResponse.of(
                        new LimitReleaseDTO("operation", "payment", true, null, 2, "evidence")))));
    }
    @Test void confirmUsesTheConfiguredOperationAndOriginalTypedCommand() {
        assertEquals(LimitReservationState.CONFIRMED, client.confirm("tenant", confirm).state());
        var call = ArgumentCaptor.forClass(HttpCall.class);
        verify(http).exchange(eq("limitcenter"), eq("confirm"), call.capture());
        assertSame(confirm, call.getValue().body());
        assertTrue(call.getValue().headers().get("Authorization").startsWith("Bearer "));
    }
    @Test void releaseDecodesAFencedReceiptWithoutFabricatingAReservation() {
        var ack = client.release("tenant", release);
        assertTrue(ack.fenced()); assertNull(ack.reservation()); assertEquals(2, ack.paymentVersion());
        verify(http).exchange(eq("limitcenter"), eq("release"), any(HttpCall.class));
    }
    @Test void mountedServiceTokenIsRereadBetweenRetriesAndOperations() throws Exception {
        String first = Files.readString(tokenFile); client.confirm("tenant", confirm);
        var rotated = claims(); rotated.put("exp", clock.instant().getEpochSecond() + 600);
        String second = token(rotated); client.release("tenant", release);
        var calls = ArgumentCaptor.forClass(HttpCall.class);
        verify(http, times(2)).exchange(eq("limitcenter"), anyString(), calls.capture());
        assertEquals("Bearer " + first, calls.getAllValues().getFirst().headers().get("Authorization"));
        assertEquals("Bearer " + second, calls.getAllValues().getLast().headers().get("Authorization"));
    }
    @Test void anotherTenantOrWrongAudienceDoesNotReachTheNetwork() throws Exception {
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> client.confirm("other-tenant", confirm)).getResultCode());
        var wrong = claims(); wrong.put("aud", "acquire"); token(wrong);
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.release("tenant", release)).getResultCode());
        verifyNoInteractions(http);
    }
    @Test void transportFailureCannotBeInterpretedAsSuccessfulSettlement() {
        when(http.exchange(eq("limitcenter"), eq("confirm"), any(HttpCall.class)))
                .thenReturn(ResponseEntity.status(503).body(new byte[0]));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.confirm("tenant", confirm)).getResultCode());
    }
    @Test void localTransactionCannotRemainOpenAcrossTheSettlementCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> client.release("tenant", release)).getResultCode());
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(http);
    }
}
