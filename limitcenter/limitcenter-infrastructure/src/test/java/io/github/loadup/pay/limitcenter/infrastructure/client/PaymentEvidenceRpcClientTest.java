package io.github.loadup.pay.limitcenter.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.limitcenter.infrastructure.config.PaymentEvidenceProperties;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapperImpl;
import io.github.loadup.pay.payment.rpc.proto.PaymentRpc;
import io.github.loadup.pay.product.rpc.ProductRpcMapperImpl;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentEvidenceRpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final PaymentRpcMapper paymentRpcMapper = new PaymentRpcMapperImpl(new ProductRpcMapperImpl());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentRpc paymentRpc = mock(PaymentRpc.class);
    final RefundSettlementFactsDTO facts = new RefundSettlementFactsDTO(
            new RefundOutcomeEventDTO("refund", "request", "payment", "order", "operation", "merchant", 1000, "CNY",
                    RefundState.SUCCEEDED, 3, 1000, "refund-evidence"),
            2000, 4, "payment-evidence", "attempt", OffsetDateTime.parse("2026-10-23T12:00:00+08:00"), 5);
    @TempDir
    Path directory;
    PaymentEvidenceRpcClient client;
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
        client = new PaymentEvidenceRpcClient(paymentRpcMapper, mapper,
                new PaymentEvidenceProperties("tenant", "limitcenter", "payment", tokenFile), clock);
        ReflectionTestUtils.setField(client, "paymentRpc", paymentRpc);
        doAnswer(invocation -> {
            assertEquals("Bearer " + Files.readString(tokenFile),
                    RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
            return paymentRpcMapper.toWireRefundSettlementFactsDTO(facts);
        }).when(paymentRpc).refundFacts(any());
    }

    @Test
    void refundFactsUsesItsNamedOperationTypedQueryAndMountedCredential() {
        assertEquals(facts, client.refundFacts("tenant", "refund"));
        verify(paymentRpc).refundFacts(paymentRpcMapper.toWireIdQuery(new IdQuery("refund")));
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
        verifyNoInteractions(paymentRpc);
    }
}
