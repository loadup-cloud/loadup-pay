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

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.limitcenter.domain.model.LimitRestoreResult;
import io.github.loadup.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.payment.infrastructure.config.PaymentLimitProperties;
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

class PaymentLimitHttpClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final LimitCenterHttpService limitCenterHttp = mock(LimitCenterHttpService.class);
    final List<String> attachments = new ArrayList<>();
    final LimitConfirmCommand confirm = new LimitConfirmCommand(
            "operation", "payment", 2, "evidence", "evidence", 1000, "CNY", OffsetDateTime.now(clock));
    final LimitReleaseCommand release =
            new LimitReleaseCommand("operation", "payment", 2, "evidence", "evidence", "PAYMENT_FAILED");
    final LimitRefundRestoreCommand refundRestore = new LimitRefundRestoreCommand(
            "operation", "payment", "refund", 3, "refund-event", 1000, 1500, "CNY", "refund-evidence");

    PaymentLimitHttpClient client;
    String credential;

    Map<String, Object> claims() {
        return new HashMap<>(Map.of(
                "tenant_id",
                "tenant",
                "principal_type",
                "SERVICE",
                "sub",
                "payment",
                "service_id",
                "payment",
                "aud",
                List.of("limitcenter"),
                "exp",
                clock.instant().getEpochSecond() + 300));
    }

    String token(Map<String, Object> claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var token = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        credential = token;
        return token;
    }

    LimitReservationDTO confirmed() {
        return new LimitReservationDTO(
                "reservation",
                "operation",
                "payment",
                LimitReservationState.CONFIRMED,
                "policy",
                1,
                OffsetDateTime.now(clock),
                2);
    }

    @BeforeEach
    void setup() throws Exception {
        token(claims());
        client = new PaymentLimitHttpClient(
                limitCenterHttp,
                (service, audience) -> credential,
                new PaymentLimitProperties("payment", "limitcenter"));
        doAnswer(invocation -> {
                    attachments.add(invocation.getArgument(0, String.class));
                    return SuccessResponse.of(confirmed());
                })
                .when(limitCenterHttp)
                .confirm(anyString(), any());
        doAnswer(invocation -> {
                    attachments.add(invocation.getArgument(0, String.class));
                    return SuccessResponse.of(new LimitReleaseDTO("operation", "payment", true, null, 2, "evidence"));
                })
                .when(limitCenterHttp)
                .release(anyString(), any());
    }

    @Test
    void confirmUsesTheConfiguredOperationAndOriginalTypedCommand() {
        assertEquals(LimitReservationState.CONFIRMED, client.confirm(confirm).state());
        verify(limitCenterHttp).confirm(anyString(), eq(confirm));
        assertEquals(1, attachments.size());
        assertTrue(attachments.getFirst().startsWith("Bearer "));
    }

    @Test
    void releaseDecodesAFencedReceiptWithoutFabricatingAReservation() {
        var ack = client.release(release);
        assertTrue(ack.fenced());
        assertNull(ack.reservation());
        assertEquals(2, ack.paymentVersion());
        verify(limitCenterHttp).release(anyString(), eq(release));
    }

    @Test
    void refundRestoreUsesItsNamedOperationAndOriginalTypedCommandWithServiceCredential() throws Exception {
        var ack = new LimitRestoreDTO(
                "operation",
                "payment",
                "refund",
                3,
                "refund-event",
                "refund-evidence",
                1500,
                1500,
                4,
                LimitRestoreResult.RESTORED,
                1000,
                true);
        doAnswer(invocation -> {
                    assertEquals("Bearer " + credential, invocation.getArgument(0, String.class));
                    return SuccessResponse.of(ack);
                })
                .when(limitCenterHttp)
                .restoreRefund(anyString(), any());
        assertEquals(ack, client.restoreRefund(refundRestore));
        verify(limitCenterHttp).restoreRefund(anyString(), eq(refundRestore));
    }

    @Test
    void serviceTokenProviderIsConsultedBetweenRetriesAndOperations() throws Exception {
        var first = token(claims());
        client.confirm(confirm);
        var rotated = claims();
        rotated.put("exp", clock.instant().getEpochSecond() + 600);
        var second = token(rotated);
        client.release(release);
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
    }

    @Test
    void limitRejectionCannotBeInterpretedAsSuccessfulSettlement() {
        doAnswer(invocation -> new SuccessResponse<>(Result.buildFailure(LimitCenterResultCodeEnum.EXCEEDED), null))
                .when(limitCenterHttp)
                .confirm(anyString(), any());
        assertSame(
                PaymentResultCodeEnum.LIMIT_EXCEEDED,
                assertThrows(CommonException.class, () -> client.confirm(confirm))
                        .getResultCode());
    }

    @Test
    void onlyTheExistingLimitFailureWhitelistIsMappedToLocalBusinessCodes() {
        var mappings = Map.of(
                LimitCenterResultCodeEnum.EXCEEDED, PaymentResultCodeEnum.LIMIT_EXCEEDED,
                LimitCenterResultCodeEnum.RESERVATION_EXPIRED, PaymentResultCodeEnum.LIMIT_RESERVATION_EXPIRED,
                LimitCenterResultCodeEnum.EXECUTION_FENCED, PaymentResultCodeEnum.EXECUTION_FENCED,
                LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT, PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT,
                LimitCenterResultCodeEnum.ACTION_FORBIDDEN, DEPENDENCY_UNAVAILABLE);
        mappings.forEach((remote, local) -> {
            doAnswer(invocation -> new SuccessResponse<>(Result.buildFailure(remote), null))
                    .when(limitCenterHttp)
                    .confirm(anyString(), any());
            assertSame(
                    local,
                    assertThrows(CommonException.class, () -> client.confirm(confirm))
                            .getResultCode());
        });
        doAnswer(invocation -> {
                    throw new IllegalStateException("loadup-result-code=LIMIT_EXCEEDED");
                })
                .when(limitCenterHttp)
                .confirm(anyString(), any());
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.confirm(confirm))
                        .getResultCode());
    }

    @Test
    void localTransactionCannotRemainOpenAcrossTheSettlementCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.release(release))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(limitCenterHttp);
    }

    @Test
    void refundRestoreCannotRunInsideAnOuterTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.restoreRefund(refundRestore))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(limitCenterHttp);
    }
}
