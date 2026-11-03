package io.github.loadup.pay.limitcenter.infrastructure.client;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
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
import io.github.loadup.pay.limitcenter.infrastructure.config.PaymentEvidenceProperties;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.pay.payment.client.http.PaymentHttpService;
import io.github.loadup.pay.payment.domain.model.RefundState;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class PaymentEvidenceHttpClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentHttpService paymentHttp = mock(PaymentHttpService.class);
    final RefundSettlementFactsDTO facts = new RefundSettlementFactsDTO(
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

    @TempDir
    Path directory;

    PaymentEvidenceHttpClient client;
    Path tokenFile;

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
        Files.writeString(tokenFile, value);
        return value;
    }

    @BeforeEach
    void setup() throws Exception {
        tokenFile = directory.resolve("payment-evidence.jwt");
        token();
        client = new PaymentEvidenceHttpClient(
                paymentHttp,
                mapper,
                new PaymentEvidenceProperties("tenant", "limitcenter", "payment", tokenFile),
                clock);
        doAnswer(invocation -> {
                    assertEquals("Bearer " + Files.readString(tokenFile), invocation.getArgument(0, String.class));
                    return SuccessResponse.of(facts);
                })
                .when(paymentHttp)
                .refundFacts(anyString(), any());
    }

    @Test
    void refundFactsUsesItsNamedOperationTypedQueryAndMountedCredential() {
        assertEquals(facts, client.refundFacts("tenant", "refund"));
        verify(paymentHttp).refundFacts(anyString(), eq(new IdQuery("refund")));
    }

    @Test
    void refundFactsCannotRouteAnotherTenantOrRemainInsideAnOuterTransaction() {
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> client.refundFacts("other-tenant", "refund"))
                        .getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.refundFacts("tenant", "refund"))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(paymentHttp);
    }
}
