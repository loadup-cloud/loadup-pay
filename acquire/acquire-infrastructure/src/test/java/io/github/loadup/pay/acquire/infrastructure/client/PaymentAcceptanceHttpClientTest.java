/*
 * #%L
 * LoadUp Pay Acquire Infrastructure
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
package io.github.loadup.pay.acquire.infrastructure.client;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpCallException;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.http.PaymentHttpService;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
import io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class PaymentAcceptanceHttpClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentExecuteCommand command = mock(PaymentExecuteCommand.class);
    final PaymentHttpService paymentHttp = mock(PaymentHttpService.class);
    final List<String> attachments = new ArrayList<>();

    String credential;
    PaymentAcceptanceHttpClient client;

    private static RuntimeException remoteFailure(String code) {
        var result = Arrays.stream(PaymentResultCodeEnum.values())
                .filter(value -> value.getCode().equals(code))
                .findFirst()
                .orElse(PaymentResultCodeEnum.ACTION_FORBIDDEN);
        return assertThrows(
                HttpCallException.class,
                () -> HttpServiceResponses.requiredData(new SuccessResponse<>(Result.buildFailure(result), null)));
    }

    @BeforeEach
    void setup() throws Exception {
        client = new PaymentAcceptanceHttpClient(
                paymentHttp,
                (tenant, service, audience) -> credential,
                new PaymentTransportProperties("tenant", "acquire", "payment"));
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
        doAnswer(invocation -> {
                    attachments.add(invocation.getArgument(0, String.class));
                    return SuccessResponse.of(result);
                })
                .when(paymentHttp)
                .accept(anyString(), any());
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
        credential = value;
        return value;
    }

    @Test
    void fetchesTheCurrentServiceTokenForEachAttempt() throws Exception {
        String first = credential;
        assertEquals("payment", client.accept("tenant", command).paymentId());
        var next = claims();
        next.put("exp", clock.instant().getEpochSecond() + 600);
        String second = writeToken(next);
        client.accept("tenant", command);
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
    }

    @Test
    void detailAndOutcomeUseTheTypedIdQuery() {
        var execution = new PaymentExecutionDTO(
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
        doReturn(SuccessResponse.of(execution)).when(paymentHttp).detail(anyString(), any());
        assertEquals("payment", client.detail("tenant", "payment").paymentId());
        verify(paymentHttp).detail(anyString(), eq(new IdQuery("payment")));
    }

    @Test
    void refundAuthorityReadsTheOrderSummaryThroughItsDedicatedOperation() {
        var summary = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        doReturn(SuccessResponse.of(summary)).when(paymentHttp).refundSummary(anyString(), any());
        assertEquals(summary, client.refundSummary("tenant", "order"));
        verify(paymentHttp).refundSummary(anyString(), eq(new IdQuery("order")));
    }

    @Test
    void eventAuthorityUsesTheRichFactsOperationInsteadOfDiscardingOperationAndPayee() {
        var payment = new PaymentExecutionDTO(
                "payment",
                "order",
                "allocation",
                100,
                "CNY",
                PaymentState.SUCCEEDED,
                "attempt",
                null,
                "proof",
                2,
                OffsetDateTime.now(clock));
        doReturn(SuccessResponse.of(new PaymentSettlementFactsDTO(payment, "operation", "merchant", false)))
                .when(paymentHttp)
                .facts(anyString(), any());
        var facts = client.facts("tenant", "payment");
        assertEquals("operation", facts.limitOperationId());
        assertEquals("merchant", facts.payeeMerchantId());
    }

    @Test
    void remoteBusinessAndTransportFailuresCannotMasqueradeAsAcceptance() {
        doThrow(remoteFailure(ACTION_FORBIDDEN.getCode()))
                .doThrow(new IllegalArgumentException("synthetic-secret"))
                .when(paymentHttp)
                .accept(anyString(), any());
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
        verifyNoInteractions(paymentHttp);
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
        verifyNoInteractions(paymentHttp);
    }

    @Test
    void inputLookupUsesTypedHttpAndNeverTreatsTransportFailureAsInputAbsence() {
        var query = new PaymentInputLookupQuery(
                "payment",
                "order",
                "allocation",
                "merchant",
                100L,
                "CNY",
                "action",
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5));
        var absent = new PaymentInputLookupDTO(false, null);
        doReturn(SuccessResponse.of(absent)).when(paymentHttp).inputLookup(anyString(), any());
        assertEquals(absent, client.lookupInput("tenant", query));
        doThrow(remoteFailure("PAYMENT_NOT_FOUND")).when(paymentHttp).inputLookup(anyString(), any());
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.lookupInput("tenant", query))
                        .getResultCode());
        doThrow(remoteFailure("PAYMENT_IDEMPOTENCY_CONFLICT")).when(paymentHttp).inputLookup(anyString(), any());
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> client.lookupInput("tenant", query))
                        .getResultCode());
    }

    @Test
    void inputExpiryUsesItsTypedOperationAndFailureNeverFabricatesAClosure() {
        var expire = mock(PaymentInputExpireCommand.class);
        var active = new PaymentInputExpiryDTO("payment", "order", PaymentInputExpiryDisposition.INPUT_ACTIVE, null);
        doReturn(SuccessResponse.of(active)).when(paymentHttp).inputExpire(anyString(), any());
        assertEquals(active, client.expireInput("tenant", expire));
        doThrow(remoteFailure("PAYMENT_NOT_FOUND")).when(paymentHttp).inputExpire(anyString(), any());
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.expireInput("tenant", expire))
                        .getResultCode());
        doThrow(remoteFailure("PAYMENT_IDEMPOTENCY_CONFLICT")).when(paymentHttp).inputExpire(anyString(), any());
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> client.expireInput("tenant", expire))
                        .getResultCode());
    }
}
