/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     rpc://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.client.enums.LimitReservationState;
import io.github.loadup.pay.limitcenter.client.enums.LimitRestoreResult;
import io.github.loadup.pay.payment.infrastructure.config.PaymentLimitProperties;
import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_CONFIRM;
import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_RELEASE;
import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_RESTORE_REFUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentLimitGrpcClientTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-23T04:00:00Z"), ZoneId.systemDefault());
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final GrpcTransport rpc = mock(GrpcTransport.class);
    final LimitConfirmCommand confirm = new LimitConfirmCommand("operation", "payment", 2, "evidence", "evidence", 1000, "CNY",
            OffsetDateTime.now(clock));
    final LimitReleaseCommand release = new LimitReleaseCommand("operation", "payment", 2, "evidence", "evidence", "PAYMENT_FAILED");
    final LimitRefundRestoreCommand refundRestore = new LimitRefundRestoreCommand("operation", "payment", "refund", 3,
            "refund-event", 1000, 1500, "CNY", "refund-evidence");
    @TempDir
    Path directory;
    PaymentLimitGrpcClient client;
    Path tokenFile;

    private static io.grpc.StatusRuntimeException remoteFailure(String code) {
        var trailers = new Metadata();
        trailers.put(RpcServerSupport.RESULT_CODE, code);
        return Status.FAILED_PRECONDITION.asRuntimeException(trailers);
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
        client = new PaymentLimitGrpcClient(rpc, mapper, new PaymentLimitProperties("tenant", "payment", "limitcenter", tokenFile), clock);
        when(rpc.call(eq(LIMITCENTER_CONFIRM), any(), any()))
                .thenReturn(confirmed());
        when(rpc.call(eq(LIMITCENTER_RELEASE), any(), any()))
                .thenReturn(
                        new LimitReleaseDTO("operation", "payment", true, null, 2, "evidence"));
    }

    @Test
    void confirmUsesTheConfiguredOperationAndOriginalTypedCommand() {
        assertEquals(LimitReservationState.CONFIRMED, client.confirm("tenant", confirm).state());
        var call = ArgumentCaptor.forClass(String.class);
        verify(rpc).call(eq(LIMITCENTER_CONFIRM), eq(confirm), call.capture());

        assertTrue(call.getValue().split("[.]").length == 3);
    }

    @Test
    void releaseDecodesAFencedReceiptWithoutFabricatingAReservation() {
        var ack = client.release("tenant", release);
        assertTrue(ack.fenced());
        assertNull(ack.reservation());
        assertEquals(2, ack.paymentVersion());
        verify(rpc).call(eq(LIMITCENTER_RELEASE), any(), any());
    }

    @Test
    void refundRestoreUsesItsNamedOperationAndOriginalTypedCommandWithMountedCredential() throws Exception {
        var ack = new LimitRestoreDTO("operation", "payment", "refund", 3, "refund-event", "refund-evidence",
                1500, 1500, 4, LimitRestoreResult.RESTORED, 1000, true);
        when(rpc.call(eq(LIMITCENTER_RESTORE_REFUND), any(), any())).thenReturn(ack);

        assertEquals(ack, client.restoreRefund("tenant", refundRestore));
        verify(rpc).call(eq(LIMITCENTER_RESTORE_REFUND), eq(refundRestore), eq(Files.readString(tokenFile)));
    }

    @Test
    void mountedServiceTokenIsRereadBetweenRetriesAndOperations() throws Exception {
        String first = Files.readString(tokenFile);
        client.confirm("tenant", confirm);
        var rotated = claims();
        rotated.put("exp", clock.instant().getEpochSecond() + 600);
        String second = token(rotated);
        client.release("tenant", release);
        var calls = ArgumentCaptor.forClass(String.class);
        verify(rpc, times(2)).call(any(), any(), calls.capture());
        assertEquals(first, calls.getAllValues().getFirst());
        assertEquals(second, calls.getAllValues().getLast());
    }

    @Test
    void anotherTenantOrWrongAudienceDoesNotReachTheNetwork() throws Exception {
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> client.confirm("other-tenant", confirm)).getResultCode());
        var wrong = claims();
        wrong.put("aud", "acquire");
        token(wrong);
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.release("tenant", release)).getResultCode());
        verifyNoInteractions(rpc);
    }

    @Test
    void transportFailureCannotBeInterpretedAsSuccessfulSettlement() {
        when(rpc.call(eq(LIMITCENTER_CONFIRM), any(), any()))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> client.confirm("tenant", confirm)).getResultCode());
    }

    @Test
    void localTransactionCannotRemainOpenAcrossTheSettlementCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> client.release("tenant", release)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(rpc);
    }

    @Test
    void refundRestoreCannotRunInsideAnOuterTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> client.restoreRefund("tenant", refundRestore)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(rpc);
    }
}
