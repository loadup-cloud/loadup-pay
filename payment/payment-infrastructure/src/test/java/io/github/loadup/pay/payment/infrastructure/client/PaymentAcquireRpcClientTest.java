package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.rpc.AcquireRpcMapper;
import io.github.loadup.pay.acquire.rpc.AcquireRpcMapperImpl;
import io.github.loadup.pay.acquire.rpc.proto.AcquireRpc;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.infrastructure.config.PaymentAcquireProperties;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapperImpl;
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

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentAcquireRpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final AcquireRpc acquireRpc = mock(AcquireRpc.class);
    final AcquireRpcMapper acquireRpcMapper =
            new AcquireRpcMapperImpl(new PaymentRpcMapperImpl(new ProductRpcMapperImpl()));
    final List<String> attachments = new ArrayList<>();
    final EventEnvelope<PaymentOutcomeEventDTO> event = new EventEnvelope<>("delivery", "proof", "payment",
            "pay.payment.outcome.acquire.v1", 1, "payment", 2, OffsetDateTime.now(clock),
            new PaymentOutcomeEventDTO("payment", "order", "allocation", "operation", "merchant", 1000, "CNY",
                    PaymentState.SUCCEEDED, 2, "proof", false));
    @TempDir
    Path directory;
    Path tokenFile;
    PaymentAcquireRpcClient client;

    private static RpcException remoteFailure(String code) {
        return new RpcException(RpcException.BIZ_EXCEPTION, "loadup-result-code=" + code);
    }

    Map<String, Object> claims() {
        return new HashMap<>(Map.of("tenant_id", "tenant", "sub", "payment", "service_id", "payment", "principal_type", "SERVICE",
                "aud", List.of("acquire"), "exp", clock.instant().getEpochSecond() + 300));
    }

    String token(Map<String, Object> claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var value = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        Files.writeString(tokenFile, value);
        return value;
    }

    @BeforeEach
    void setup() throws Exception {
        tokenFile = directory.resolve("acquire.jwt");
        token(claims());
        client = new PaymentAcquireRpcClient(acquireRpcMapper, mapper, new PaymentAcquireProperties("tenant", "payment", "acquire", tokenFile), clock);
        ReflectionTestUtils.setField(client, "acquireRpc", acquireRpc);
        doAnswer(invocation -> {
            attachments.add(RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
            return acquireRpcMapper.toWirePaymentOutcomeAckDTO(
                    new PaymentOutcomeAckDTO("delivery", "proof", "payment", 2, "proof", 3, true));
        }).when(acquireRpc).paymentOutcome(any());
    }

    @Test
    void sendsTheOriginalEnvelopeWithADedicatedServiceCredential() {
        assertTrue(client.accept("tenant", event).accepted());
        verify(acquireRpc).paymentOutcome(any());
        assertEquals(1, attachments.size());
        assertTrue(attachments.getFirst().split("[.]").length == 3);
    }

    @Test
    void feeTargetUsesItsOwnTypedOperationAndTheSameRotatableServiceCredential() {
        var fee = new EventEnvelope<>("fee-delivery", "fee-business", "payment", "pay.payment.refund.fee.v1", 1, "order", 3,
                OffsetDateTime.now(clock), new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 0, 3));
        var ack = new FeeRefundAckDTO("fee-delivery", "fee-business", "order", 3, 4, true);
        doAnswer(invocation -> acquireRpcMapper.toWireFeeRefundAckDTO(ack))
                .when(acquireRpc).feeRefund(any());
        assertEquals(ack, client.acceptFeeRefund("tenant", fee));
        verify(acquireRpc).feeRefund(any());
    }

    @Test
    void mountedCredentialRotationIsObservedOnTheNextDelivery() throws Exception {
        var first = Files.readString(tokenFile);
        client.accept("tenant", event);
        var next = claims();
        next.put("exp", clock.instant().getEpochSecond() + 600);
        var second = token(next);
        client.accept("tenant", event);
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
    }

    @Test
    void wrongTenantOrAudienceNeverReachesAcquire() throws Exception {
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> client.accept("other-tenant", event)).getResultCode());
        var wrong = claims();
        wrong.put("aud", "limitcenter");
        token(wrong);
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.accept("tenant", event)).getResultCode());
        verifyNoInteractions(acquireRpc);
    }

    @Test
    void expiredOrMalformedMountedTokensAreNotSent() throws Exception {
        var expired = claims();
        expired.put("exp", clock.instant().getEpochSecond());
        token(expired);
        assertThrows(CommonException.class, () -> client.accept("tenant", event));
        Files.writeString(tokenFile, "not-a-jwt");
        assertThrows(CommonException.class, () -> client.accept("tenant", event));
        verifyNoInteractions(acquireRpc);
    }

    @Test
    void transportFailureDoesNotBecomeAValidSemanticAck() {
        doThrow(remoteFailure("ACQUIRE_ACTION_FORBIDDEN")).when(acquireRpc).paymentOutcome(any());
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.accept("tenant", event)).getResultCode());
    }

    @Test
    void outerTransactionCannotRemainOpenDuringTheCrossServiceCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> client.accept("tenant", event)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(acquireRpc);
    }
}
