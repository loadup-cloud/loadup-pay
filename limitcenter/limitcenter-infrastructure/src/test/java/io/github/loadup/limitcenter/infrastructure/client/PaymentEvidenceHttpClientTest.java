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
package io.github.loadup.limitcenter.infrastructure.client;

import static io.github.loadup.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.contracts.event.payment.RefundOutcomeEventDTO;
import io.github.loadup.contracts.event.payment.RefundState;
import io.github.loadup.limitcenter.infrastructure.config.PaymentEvidenceProperties;
import io.github.loadup.limitcenter.infrastructure.converter.RemoteContractConverter;
import io.github.loadup.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.payment.client.http.PaymentHttpService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RemoteContractMapperTestConfiguration.class)
class PaymentEvidenceHttpClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentHttpService paymentHttp = mock(PaymentHttpService.class);
    final RefundSettlementFactsDTO remoteFacts = new RefundSettlementFactsDTO(
            new RefundOutcomeEventDTO(
                    "refund",
                    "request",
                    "payment",
                    "order",
                    "operation",
                    "merchant",
                    1000,
                    "CNY",
                    RefundState.SUCCEEDED,
                    3,
                    1000,
                    "refund-evidence"),
            2000,
            4,
            "payment-evidence",
            "attempt",
            OffsetDateTime.parse("2026-10-23T12:00:00+08:00"),
            5);

    PaymentEvidenceHttpClient client;
    String credential;

    @Autowired
    RemoteContractConverter contracts;

    String token() throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var header = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256")));
        var claims = encoder.encodeToString(mapper.writeValueAsBytes(Map.of(
                "tenant_id",
                "tenant",
                "principal_type",
                "SERVICE",
                "sub",
                "limitcenter",
                "service_id",
                "limitcenter",
                "aud",
                List.of("payment"),
                "exp",
                clock.instant().getEpochSecond() + 300)));
        var value = header + "." + claims + ".c3ludGhldGlj";
        credential = value;
        return value;
    }

    @BeforeEach
    void setup() throws Exception {
        token();
        client = new PaymentEvidenceHttpClient(
                paymentHttp,
                (service, audience) -> credential,
                new PaymentEvidenceProperties("limitcenter", "payment"),
                contracts);
        doAnswer(invocation -> {
                    assertEquals("Bearer " + credential, invocation.getArgument(0, String.class));
                    return SuccessResponse.of(remoteFacts);
                })
                .when(paymentHttp)
                .refundFacts(anyString(), any());
    }

    @Test
    void refundFactsUsesItsNamedOperationTypedQueryAndServiceCredential() {
        var expected = contracts.toLocal(remoteFacts);
        assertEquals(expected, client.refundFacts("refund"));
        verify(paymentHttp).refundFacts(anyString(), eq(new IdQuery("refund")));
    }

    @Test
    void refundFactsCannotRemainInsideAnOuterTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.refundFacts("refund"))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(paymentHttp);
    }
}
