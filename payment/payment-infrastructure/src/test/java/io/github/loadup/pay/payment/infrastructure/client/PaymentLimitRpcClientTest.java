package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.limitcenter.domain.model.LimitRestoreResult;
import io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcMapper;
import io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpc;
import io.github.loadup.pay.payment.infrastructure.config.PaymentLimitProperties;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentLimitRpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final LimitCenterRpc limitCenterRpc = mock(LimitCenterRpc.class);
    final List<String> attachments = new ArrayList<>();
    final LimitConfirmCommand confirm = new LimitConfirmCommand("operation", "payment", 2, "evidence", "evidence", 1000, "CNY",
            OffsetDateTime.now(clock));
    final LimitReleaseCommand release = new LimitReleaseCommand("operation", "payment", 2, "evidence", "evidence", "PAYMENT_FAILED");
    final LimitRefundRestoreCommand refundRestore = new LimitRefundRestoreCommand("operation", "payment", "refund", 3,
            "refund-event", 1000, 1500, "CNY", "refund-evidence");
    @TempDir
    Path directory;
    PaymentLimitRpcClient client;
    Path tokenFile;

    private static RpcException remoteFailure(String code) {
        return new RpcException(RpcException.BIZ_EXCEPTION, "loadup-result-code=" + code);
    }

    Map<String, Object> claims() {
        return new HashMap<>(Map.of("tenant_id", "tenant", "principal_type", "SERVICE", "sub", "payment", "service_id", "payment",
                "aud", List.of("limitcenter"), "exp", clock.instant().getEpochSecond() + 300));
    }

    String token(Map<String, Object> claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var token = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256"))) + "."
                + encoder.encodeToString(mapper.writeValueAsBytes(claims)) + ".c3ludGhldGlj";
        Files.writeString(tokenFile, token);
        return token;
    }

    LimitReservationDTO confirmed() {
        return new LimitReservationDTO("reservation", "operation", "payment", LimitReservationState.CONFIRMED, "policy", 1,
                OffsetDateTime.now(clock), 2);
    }

    @BeforeEach
    void setup() throws Exception {
        tokenFile = directory.resolve("limitcenter.jwt");
        token(claims());
        client = new PaymentLimitRpcClient(mapper, new PaymentLimitProperties("tenant", "payment", "limitcenter", tokenFile), clock);
        ReflectionTestUtils.setField(client, "limitCenterRpc", limitCenterRpc);
        doAnswer(invocation -> {
            attachments.add(RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
            return LimitCenterRpcMapper.INSTANCE.toWireLimitReservationDTO(confirmed());
        }).when(limitCenterRpc).confirm(any());
        doAnswer(invocation -> {
            attachments.add(RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
            return LimitCenterRpcMapper.INSTANCE.toWireLimitReleaseDTO(
                    new LimitReleaseDTO("operation", "payment", true, null, 2, "evidence"));
        }).when(limitCenterRpc).release(any());
    }

    @Test
    void confirmUsesTheConfiguredOperationAndOriginalTypedCommand() {
        assertEquals(LimitReservationState.CONFIRMED, client.confirm("tenant", confirm).state());
        verify(limitCenterRpc).confirm(any());
        assertEquals(1, attachments.size());
        assertTrue(attachments.getFirst().startsWith("Bearer "));
    }

    @Test
    void releaseDecodesAFencedReceiptWithoutFabricatingAReservation() {
        var ack = client.release("tenant", release);
        assertTrue(ack.fenced());
        assertNull(ack.reservation());
        assertEquals(2, ack.paymentVersion());
        verify(limitCenterRpc).release(any());
    }

    @Test
    void refundRestoreUsesItsNamedOperationAndOriginalTypedCommandWithMountedCredential() throws Exception {
        var ack = new LimitRestoreDTO("operation", "payment", "refund", 3, "refund-event", "refund-evidence",
                1500, 1500, 4, LimitRestoreResult.RESTORED, 1000, true);
        doAnswer(invocation -> {
            assertEquals("Bearer " + Files.readString(tokenFile),
                    RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
            return LimitCenterRpcMapper.INSTANCE.toWireLimitRestoreDTO(ack);
        }).when(limitCenterRpc).restoreRefund(any());
        assertEquals(ack, client.restoreRefund("tenant", refundRestore));
        verify(limitCenterRpc).restoreRefund(any());
    }

    @Test
    void mountedServiceTokenIsRereadBetweenRetriesAndOperations() throws Exception {
        var first = token(claims());
        client.confirm("tenant", confirm);
        var rotated = claims();
        rotated.put("exp", clock.instant().getEpochSecond() + 600);
        var second = token(rotated);
        client.release("tenant", release);
        assertEquals(List.of("Bearer " + first, "Bearer " + second), attachments);
    }

    @Test
    void anotherTenantOrWrongAudienceDoesNotReachTheNetwork() throws Exception {
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> client.confirm("other-tenant", confirm)).getResultCode());
        var wrong = claims();
        wrong.put("aud", "acquire");
        token(wrong);
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.release("tenant", release)).getResultCode());
        verifyNoInteractions(limitCenterRpc);
    }

    @Test
    void transportFailureCannotBeInterpretedAsSuccessfulSettlement() {
        doThrow(remoteFailure("LIMIT_EXCEEDED")).when(limitCenterRpc).confirm(any());
        assertSame(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_EXCEEDED,
                assertThrows(CommonException.class, () -> client.confirm("tenant", confirm)).getResultCode());
    }

    @Test
    void localTransactionCannotRemainOpenAcrossTheSettlementCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> client.release("tenant", release)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(limitCenterRpc);
    }

    @Test
    void refundRestoreCannotRunInsideAnOuterTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> client.restoreRefund("tenant", refundRestore)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(limitCenterRpc);
    }
}
