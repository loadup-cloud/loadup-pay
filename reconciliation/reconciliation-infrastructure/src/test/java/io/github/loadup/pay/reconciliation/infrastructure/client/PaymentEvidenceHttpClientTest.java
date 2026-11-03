/*
 * #%L
 * LoadUp Pay Reconciliation infrastructure
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
package io.github.loadup.pay.reconciliation.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.PageInfo;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.client.http.PaymentCaptureHttpService;
import io.github.loadup.pay.payment.client.http.PaymentCapturePageResponse;
import io.github.loadup.pay.payment.client.http.PaymentStatementHttpService;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus;
import io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class PaymentEvidenceHttpClientTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T04:00:00Z"), ZoneOffset.UTC);
    private final PaymentCaptureHttpService captures = mock(PaymentCaptureHttpService.class);
    private final PaymentStatementHttpService statements = mock(PaymentStatementHttpService.class);

    private PaymentCaptureHttpClient captureClient;
    private PaymentStatementHttpClient statementClient;

    @BeforeEach
    void start() throws Exception {
        ServiceAccessTokens tokens = (tenant, service, audience) -> "header.payload.signature";
        var properties = new ReconciliationPaymentProperties("tenant", "reconciliation", "payment");
        captureClient = new PaymentCaptureHttpClient(captures, properties, tokens);
        statementClient = new PaymentStatementHttpClient(statements, properties, tokens);
    }

    @Test
    void pageFailureKeepsTheOriginalBusinessMappingWhileMalformedSuccessIsADependencyFailure() {
        var query = new PaymentCaptureLinesQuery("capture", 2, 1);
        when(captures.lines(anyString(), any()))
                .thenReturn(new PaymentCapturePageResponse(
                        Result.buildFailure(PaymentResultCodeEnum.INVALID_STATE), null, null));
        assertSame(
                ReconciliationResultCodeEnum.INPUT_NOT_SEALED,
                assertThrows(CommonException.class, () -> captureClient.lines("tenant", query))
                        .getResultCode());
        var row = new PaymentLedgerLineDTO(
                1,
                PaymentLedgerKind.PAYMENT,
                "channel",
                null,
                "merchant-payment",
                100,
                "CNY",
                PaymentLedgerStatus.SUCCEEDED,
                OffsetDateTime.now(clock),
                "payment",
                "receipt",
                1);
        when(captures.lines(anyString(), any()))
                .thenReturn(
                        new PaymentCapturePageResponse(Result.buildSuccess(), List.of(row), new PageInfo(2L, 1L, 2L)));
        assertSame(
                ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> captureClient.lines("tenant", query))
                        .getResultCode());
    }

    @Test
    void aStatementNotReadyIsPreservedButTransportOrAuthenticationFailureIsNot() {
        when(statements.download(anyString(), any()))
                .thenReturn(
                        new SuccessResponse<>(Result.buildFailure(PaymentResultCodeEnum.STATEMENT_NOT_READY), null));
        assertSame(
                ReconciliationResultCodeEnum.STATEMENT_NOT_READY,
                assertThrows(CommonException.class, () -> statementClient.download("tenant", null))
                        .getResultCode());
        when(statements.download(anyString(), any()))
                .thenThrow(new IllegalStateException("loadup-result-code=PAYMENT_STATEMENT_NOT_READY"));
        var error = assertThrows(CommonException.class, () -> statementClient.download("tenant", null));
        assertSame(ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE, error.getResultCode());
        assertNull(error.getCause());
    }

    @Test
    void wrongTenantAndAnOuterTransactionDoNotReachTheProvider() {
        assertThrows(CommonException.class, () -> captureClient.detail("another", "capture"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    ReconciliationResultCodeEnum.INVALID_STATE,
                    assertThrows(CommonException.class, () -> statementClient.download("tenant", null))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(captures, statements);
    }
}
