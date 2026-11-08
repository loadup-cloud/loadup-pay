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
package io.github.loadup.payment.infrastructure.client;

import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.acquire.client.http.AcquireHttpService;
import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.contracts.event.OrderRefundSummaryEventDTO;
import io.github.loadup.contracts.event.PaymentOutcomeEventDTO;
import io.github.loadup.contracts.model.PaymentState;
import io.github.loadup.payment.infrastructure.config.PaymentAcquireProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class PaymentAcquireHttpClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final AcquireHttpService acquireHttp = mock(AcquireHttpService.class);
    final List<String> attachments = new ArrayList<>();
    final EventEnvelope<PaymentOutcomeEventDTO> event = new EventEnvelope<>(
            "delivery",
            "proof",
            "payment",
            "pay.payment.outcome.acquire.v1",
            1,
            "payment",
            2,
            OffsetDateTime.now(clock),
            new PaymentOutcomeEventDTO(
                    "payment",
                    "order",
                    "allocation",
                    "operation",
                    "merchant",
                    1000,
                    "CNY",
                    PaymentState.SUCCEEDED,
                    2,
                    "proof",
                    false));

    String credential;
    PaymentAcquireHttpClient client;

    Map<String, Object> claims() {
        return new HashMap<>(Map.of(
                "tenant_id",
                "tenant",
                "sub",
                "payment",
                "service_id",
                "payment",
                "principal_type",
                "SERVICE",
                "aud",
                List.of("acquire"),
                "exp",
                clock.instant().getEpochSecond() + 300));
    }

    String token(Map<String, Object> claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var value = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        credential = value;
        return value;
    }

    @BeforeEach
    void setup() throws Exception {
        token(claims());
        client = new PaymentAcquireHttpClient(
                acquireHttp, (service, audience) -> credential, new PaymentAcquireProperties("payment", "acquire"));
        doAnswer(invocation -> {
                    attachments.add(invocation.getArgument(0, String.class));
                    return SuccessResponse.of(
                            new PaymentOutcomeAckDTO("delivery", "proof", "payment", 2, "proof", 3, true));
                })
                .when(acquireHttp)
                .paymentOutcome(anyString(), any());
    }

    @Test
    void sendsTheOriginalEnvelopeWithADedicatedServiceCredential() {
        assertTrue(client.accept(event).accepted());
        verify(acquireHttp).paymentOutcome(anyString(), any());
        assertEquals(1, attachments.size());
        assertTrue(attachments.getFirst().split("[.]").length == 3);
    }

    @Test
    void feeTargetUsesItsOwnTypedOperationAndTheSameRotatableServiceCredential() {
        var fee = new EventEnvelope<>(
                "fee-delivery",
                "fee-business",
                "payment",
                "pay.payment.refund.fee.v1",
                1,
                "order",
                3,
                OffsetDateTime.now(clock),
                new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 0, 3));
        var ack = new FeeRefundAckDTO("fee-delivery", "fee-business", "order", 3, 4, true);
        doAnswer(invocation -> SuccessResponse.of(ack)).when(acquireHttp).feeRefund(anyString(), any());
        assertEquals(ack, client.acceptFeeRefund(fee));
        verify(acquireHttp).feeRefund(anyString(), any());
    }

    @Test
    void newServiceTokenIsUsedOnTheNextDelivery() throws Exception {
        var first = credential;
        client.accept(event);
        var next = claims();
        next.put("exp", clock.instant().getEpochSecond() + 600);
        var second = token(next);
        client.accept(event);
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
    }

    @Test
    void transportFailureDoesNotBecomeAValidSemanticAck() {
        doAnswer(invocation -> new SuccessResponse<>(Result.buildFailure(CommonResultCodeEnum.ACCESS_DENIED), null))
                .when(acquireHttp)
                .paymentOutcome(anyString(), any());
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.accept(event)).getResultCode());
    }

    @Test
    void outerTransactionCannotRemainOpenDuringTheCrossServiceCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.accept(event))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(acquireHttp);
    }
}
