package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
import io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapperImpl;
import io.github.loadup.pay.payment.rpc.proto.PaymentRpc;
import io.github.loadup.pay.product.rpc.ProductRpcMapperImpl;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentAcceptanceRpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentExecuteCommand command = mock(PaymentExecuteCommand.class);
    final PaymentRpc paymentRpc = mock(PaymentRpc.class);
    final PaymentRpcMapper paymentRpcMapper = new PaymentRpcMapperImpl(new ProductRpcMapperImpl());
    final List<String> attachments = new ArrayList<>();
    @TempDir
    Path directory;
    Path tokenFile;
    PaymentAcceptanceRpcClient client;

    private static RpcException remoteFailure(String code) {
        return new RpcException(RpcException.BIZ_EXCEPTION, "loadup-result-code=" + code);
    }

    @BeforeEach
    void setup() throws Exception {
        tokenFile = directory.resolve("payment.jwt");
        client = new PaymentAcceptanceRpcClient(paymentRpcMapper,
                mapper, new PaymentTransportProperties("tenant", "acquire", "payment", tokenFile), clock);
        ReflectionTestUtils.setField(client, "paymentRpc", paymentRpc);
        writeToken(claims());
        var result = new PaymentExecutionDTO(
                "payment", "order", "allocation", 100, "CNY", PaymentState.INIT,
                null, null, null, 0, OffsetDateTime.now(clock));
        doAnswer(invocation -> {
            attachments.add(RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
            return paymentRpcMapper.toWirePaymentExecutionDTO(result);
        }).when(paymentRpc).accept(any());
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
        Files.writeString(tokenFile, value + "\n");
        return value;
    }

    @Test
    void usesTheMountedServiceCredentialAndRereadsItAfterRotation() throws Exception {
        String first = Files.readString(tokenFile).strip();
        assertEquals("payment", client.accept("tenant", command).paymentId());
        var next = claims();
        next.put("exp", clock.instant().getEpochSecond() + 600);
        String second = writeToken(next);
        client.accept("tenant", command);
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
        assertNull(RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
    }

    @Test
    void detailAndOutcomeUseTheTypedIdQuery() {
        var execution = new PaymentExecutionDTO("payment", "order", "allocation", 100, "CNY", PaymentState.INIT,
                null, null, null, 0, OffsetDateTime.now(clock));
        doReturn(paymentRpcMapper.toWirePaymentExecutionDTO(execution))
                .when(paymentRpc).detail(any());
        assertEquals("payment", client.detail("tenant", "payment").paymentId());
        verify(paymentRpc).detail(paymentRpcMapper.toWireIdQuery(new IdQuery("payment")));
    }

    @Test
    void refundAuthorityReadsTheOrderSummaryThroughItsDedicatedOperation() {
        var summary = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        doReturn(paymentRpcMapper.toWireOrderRefundSummaryEventDTO(summary))
                .when(paymentRpc).refundSummary(any());
        assertEquals(summary, client.refundSummary("tenant", "order"));
        verify(paymentRpc).refundSummary(paymentRpcMapper.toWireIdQuery(new IdQuery("order")));
    }

    @Test
    void eventAuthorityUsesTheRichFactsOperationInsteadOfDiscardingOperationAndPayee() {
        var payment = new PaymentExecutionDTO("payment", "order", "allocation", 100, "CNY", PaymentState.SUCCEEDED,
                "attempt", null, "proof", 2, OffsetDateTime.now(clock));
        doReturn(paymentRpcMapper.toWirePaymentSettlementFactsDTO(
                new PaymentSettlementFactsDTO(payment, "operation", "merchant", false)))
                .when(paymentRpc).facts(any());
        var facts = client.facts("tenant", "payment");
        assertEquals("operation", facts.limitOperationId());
        assertEquals("merchant", facts.payeeMerchantId());
    }

    @Test
    void mismatchedTenantSubjectServiceTypeOrAudienceNeverReachesTheNetwork() throws Exception {
        for (var field : List.of("tenant_id", "sub", "service_id", "principal_type", "aud")) {
            var value = claims();
            value.put(field, "another");
            writeToken(value);
            assertSame(
                    DEPENDENCY_UNAVAILABLE,
                    assertThrows(CommonException.class, () -> client.accept("tenant", command))
                            .getResultCode());
        }
        verifyNoInteractions(paymentRpc);
    }

    @Test
    void expiredNotYetValidAndFractionalExpiriesAreRejected() throws Exception {
        for (Object expiry :
                List.of(clock.instant().getEpochSecond(), clock.instant().getEpochSecond() - 1, 1.5, "later")) {
            var value = claims();
            value.put("exp", expiry);
            writeToken(value);
            assertSame(
                    DEPENDENCY_UNAVAILABLE,
                    assertThrows(CommonException.class, () -> client.accept("tenant", command))
                            .getResultCode());
        }
        var value = claims();
        value.put("nbf", clock.instant().getEpochSecond() + 1);
        writeToken(value);
        assertThrows(CommonException.class, () -> client.accept("tenant", command));
        verifyNoInteractions(paymentRpc);
    }

    @Test
    void missingOrOversizedTokenFailsWithoutExposingTheCredential() throws Exception {
        Files.writeString(tokenFile, "synthetic-secret".repeat(2000));
        var failure = assertThrows(CommonException.class, () -> client.accept("tenant", command));
        assertFalse(failure.toString().contains("synthetic-secret"));
        assertNull(failure.getCause());
        Files.delete(tokenFile);
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.accept("tenant", command))
                        .getResultCode());
        verifyNoInteractions(paymentRpc);
    }

    @Test
    void remoteBusinessAndTransportFailuresCannotMasqueradeAsAcceptance() {
        doThrow(remoteFailure(ACTION_FORBIDDEN.getCode()))
                .doThrow(new IllegalArgumentException("synthetic-secret"))
                .when(paymentRpc).accept(any());
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
        verifyNoInteractions(paymentRpc);
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
        verifyNoInteractions(paymentRpc);
    }

    @Test
    void inputLookupUsesTypedRpcAndNeverTreatsTransportFailureAsInputAbsence() {
        var query = new PaymentInputLookupQuery("payment", "order", "allocation", "merchant", 100L,
                "CNY", "action", new PaymentChoiceDTO(
                io.github.loadup.commons.payment.Channel.WECHAT, io.github.loadup.commons.payment.PaymentMethod.H5));
        var absent = new PaymentInputLookupDTO(false, null);
        doReturn(paymentRpcMapper.toWirePaymentInputLookupDTO(absent))
                .when(paymentRpc).inputLookup(any());
        assertEquals(absent, client.lookupInput("tenant", query));
        doThrow(remoteFailure("PAYMENT_NOT_FOUND")).when(paymentRpc).inputLookup(any());
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.lookupInput("tenant", query)).getResultCode());
        doThrow(remoteFailure("PAYMENT_IDEMPOTENCY_CONFLICT")).when(paymentRpc).inputLookup(any());
        assertSame(IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> client.lookupInput("tenant", query)).getResultCode());
    }

    @Test
    void inputExpiryUsesItsTypedOperationAndFailureNeverFabricatesAClosure() {
        var expire = mock(io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand.class);
        var active = new PaymentInputExpiryDTO("payment", "order", PaymentInputExpiryDisposition.INPUT_ACTIVE, null);
        doReturn(paymentRpcMapper.toWirePaymentInputExpiryDTO(active))
                .when(paymentRpc).inputExpire(any());
        assertEquals(active, client.expireInput("tenant", expire));
        doThrow(remoteFailure("PAYMENT_NOT_FOUND")).when(paymentRpc).inputExpire(any());
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.expireInput("tenant", expire)).getResultCode());
        doThrow(remoteFailure("PAYMENT_IDEMPOTENCY_CONFLICT")).when(paymentRpc).inputExpire(any());
        assertSame(IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> client.expireInput("tenant", expire)).getResultCode());
    }
}
