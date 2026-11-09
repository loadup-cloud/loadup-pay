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
package io.github.loadup.acquire.infrastructure.client;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.acquire.app.model.payment.command.PaymentExecuteCommand;
import io.github.loadup.acquire.app.model.payment.command.PaymentInputExpireCommand;
import io.github.loadup.acquire.app.model.payment.dto.PaymentInputExpiryDTO;
import io.github.loadup.acquire.app.model.payment.dto.PaymentInputLookupDTO;
import io.github.loadup.acquire.app.model.payment.enums.PaymentInputExpiryDisposition;
import io.github.loadup.acquire.app.model.payment.query.PaymentInputLookupQuery;
import io.github.loadup.acquire.client.dto.payment.PaymentChoiceDTO;
import io.github.loadup.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.acquire.infrastructure.converter.RemoteContractConverter;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpCallException;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.contracts.event.payment.OrderRefundSummaryEventDTO;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.payment.client.http.PaymentHttpService;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RemoteContractMapperTestConfiguration.class)
class PaymentAcceptanceHttpClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentExecuteCommand command = new PaymentExecuteCommand(
            "payment",
            "order",
            "allocation",
            "admission",
            "merchant",
            100,
            "CNY",
            "a".repeat(64),
            new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5),
            null,
            null,
            "subject",
            OffsetDateTime.now(Clock.systemUTC()));
    final PaymentHttpService paymentHttp = mock(PaymentHttpService.class);
    final List<String> attachments = new ArrayList<>();

    @Autowired
    RemoteContractConverter contracts;

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
                (service, audience) -> credential,
                new PaymentTransportProperties("acquire", "payment"),
                contracts);
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
        assertEquals("payment", client.accept(command).paymentId());
        var next = claims();
        next.put("exp", clock.instant().getEpochSecond() + 600);
        String second = writeToken(next);
        client.accept(command);
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
        var requests = ArgumentCaptor.forClass(io.github.loadup.payment.client.command.PaymentExecuteCommand.class);
        verify(paymentHttp, times(2)).accept(anyString(), requests.capture());
        assertEquals("payment", requests.getAllValues().get(0).paymentId());
        assertEquals("order", requests.getAllValues().get(0).orderId());
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
        assertEquals("payment", client.detail("payment").paymentId());
        verify(paymentHttp).detail(anyString(), eq(new IdQuery("payment")));
    }

    @Test
    void refundAuthorityReadsTheOrderSummaryThroughItsDedicatedOperation() {
        var summary = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        doReturn(SuccessResponse.of(summary)).when(paymentHttp).refundSummary(anyString(), any());
        assertEquals(summary, client.refundSummary("order"));
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
        var facts = client.facts("payment");
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
            var failure = assertThrows(CommonException.class, () -> client.accept(command));
            assertSame(DEPENDENCY_UNAVAILABLE, failure.getResultCode());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("synthetic-secret"));
        }
    }

    @Test
    void outerTransactionsAreRejectedBeforeAnyNetworkRequest() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.accept(command))
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
        doReturn(SuccessResponse.of(new io.github.loadup.payment.client.dto.PaymentInputLookupDTO(false, null)))
                .when(paymentHttp)
                .inputLookup(anyString(), any());
        assertEquals(absent, client.lookupInput(query));
        var remoteQuery = ArgumentCaptor.forClass(io.github.loadup.payment.client.query.PaymentInputLookupQuery.class);
        verify(paymentHttp).inputLookup(anyString(), remoteQuery.capture());
        assertEquals(query.paymentId(), remoteQuery.getValue().paymentId());
        assertEquals(query.allocationId(), remoteQuery.getValue().allocationId());
        doThrow(remoteFailure("PAYMENT_NOT_FOUND")).when(paymentHttp).inputLookup(anyString(), any());
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.lookupInput(query))
                        .getResultCode());
        doThrow(remoteFailure("PAYMENT_IDEMPOTENCY_CONFLICT")).when(paymentHttp).inputLookup(anyString(), any());
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> client.lookupInput(query))
                        .getResultCode());
    }

    @Test
    void inputExpiryUsesItsTypedOperationAndFailureNeverFabricatesAClosure() {
        var expire = new PaymentInputExpireCommand(
                "input-ref",
                "payment",
                "order",
                "allocation",
                "merchant",
                100,
                "CNY",
                "action",
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5));
        var active = new PaymentInputExpiryDTO("payment", "order", PaymentInputExpiryDisposition.INPUT_ACTIVE, null);
        doReturn(SuccessResponse.of(new io.github.loadup.payment.client.dto.PaymentInputExpiryDTO(
                        "payment",
                        "order",
                        io.github.loadup.payment.client.enums.PaymentInputExpiryDisposition.INPUT_ACTIVE,
                        null)))
                .when(paymentHttp)
                .inputExpire(anyString(), any());
        assertEquals(active, client.expireInput(expire));
        var remoteExpire =
                ArgumentCaptor.forClass(io.github.loadup.payment.client.command.PaymentInputExpireCommand.class);
        verify(paymentHttp).inputExpire(anyString(), remoteExpire.capture());
        assertEquals("input-ref", remoteExpire.getValue().inputRef());
        assertEquals("payment", remoteExpire.getValue().paymentId());
        doThrow(remoteFailure("PAYMENT_NOT_FOUND")).when(paymentHttp).inputExpire(anyString(), any());
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.expireInput(expire))
                        .getResultCode());
        doThrow(remoteFailure("PAYMENT_IDEMPOTENCY_CONFLICT")).when(paymentHttp).inputExpire(anyString(), any());
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> client.expireInput(expire))
                        .getResultCode());
    }
}
