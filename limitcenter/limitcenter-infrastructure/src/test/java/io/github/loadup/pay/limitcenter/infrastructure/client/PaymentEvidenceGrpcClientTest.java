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
package io.github.loadup.pay.limitcenter.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.limitcenter.infrastructure.config.PaymentEvidenceProperties;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_REFUND_FACTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentEvidenceGrpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final GrpcTransport rpc = mock(GrpcTransport.class);
    final RefundSettlementFactsDTO facts = new RefundSettlementFactsDTO(
            new RefundOutcomeEventDTO("refund", "request", "payment", "order", "operation", "merchant", 1000, "CNY",
                    RefundState.SUCCEEDED, 3, 1000, "refund-evidence"),
            2000, 4, "payment-evidence", "attempt", OffsetDateTime.parse("2026-10-23T12:00:00+08:00"), 5);
    @TempDir
    Path directory;
    PaymentEvidenceGrpcClient client;
    Path tokenFile;

    String token() throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var header = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256")));
        var claims = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("tenant_id", "tenant", "principal_type", "SERVICE",
                "sub", "limitcenter", "service_id", "limitcenter", "aud", List.of("payment"),
                "exp", clock.instant().getEpochSecond() + 300)));
        var value = header + "." + claims + ".c3ludGhldGlj";
        Files.writeString(tokenFile, value);
        return value;
    }

    @BeforeEach
    void setup() throws Exception {
        tokenFile = directory.resolve("payment-evidence.jwt");
        token();
        client = new PaymentEvidenceGrpcClient(rpc, mapper,
                new PaymentEvidenceProperties("tenant", "limitcenter", "payment", tokenFile), clock);
        when(rpc.call(eq(PAYMENT_REFUND_FACTS), any(), any())).thenReturn(facts);
    }

    @Test
    void refundFactsUsesItsNamedOperationTypedQueryAndMountedCredential() throws Exception {
        assertEquals(facts, client.refundFacts("tenant", "refund"));
        var credential = ArgumentCaptor.forClass(String.class);
        verify(rpc).call(eq(PAYMENT_REFUND_FACTS), eq(new io.github.loadup.commons.request.query.IdQuery("refund")), credential.capture());
        assertEquals(Files.readString(tokenFile), credential.getValue());
    }

    @Test
    void refundFactsCannotRouteAnotherTenantOrRemainInsideAnOuterTransaction() {
        assertSame(ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> client.refundFacts("other-tenant", "refund")).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.refundFacts("tenant", "refund")).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(rpc);
    }
}
