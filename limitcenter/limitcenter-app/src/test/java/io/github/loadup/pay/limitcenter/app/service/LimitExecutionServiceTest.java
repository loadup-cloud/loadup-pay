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
package io.github.loadup.pay.limitcenter.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.limitcenter.app.port.ExecutionPermitIssuer;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.pay.limitcenter.domain.model.LimitExecutionGrant;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LimitExecutionServiceTest {
    final LimitServiceFixture f = new LimitServiceFixture();
    final ExecutionPermitIssuer issuer = mock(ExecutionPermitIssuer.class);
    final String permit = "d".repeat(64);
    final LimitExecutionService service = service(f.clock);

    LimitExecutionService service(Clock clock) {
        return new LimitExecutionService(f.transactions, f.reservations, f.policies, issuer, f.converter, clock);
    }

    @AfterEach
    void close() {
        f.close();
    }

    StoredLimitReservation reserved() {
        return new StoredLimitReservation(
                LimitReservation.init(
                                "reservation",
                                "operation",
                                "payment-id",
                                "order",
                                "merchant",
                                DomainChecks.amount(40, "CNY"),
                                "policy",
                                1,
                                f.now.minusDays(1),
                                f.now.plusSeconds(120))
                        .reserve(),
                f.policy(1, 100).policyHash(),
                "b".repeat(64),
                "payment");
    }

    StoredLimitReservation executing() {
        var stored = reserved();
        return stored.transitionTo(stored.reservation().beginExecution("attempt", f.now));
    }

    LimitExecutionGrant grant() {
        return new LimitExecutionGrant(
                "operation", "payment-id", "attempt", 1, "begin-key", "old-key", DigestUtils.sha256(permit));
    }

    void stored(StoredLimitReservation value) {
        when(f.reservations.findByOperation("tenant", "operation")).thenReturn(Optional.of(value));
        when(f.reservations.lockGate("tenant", "operation"))
                .thenReturn(Optional.of(
                        LimitOperationGate.init("operation", "payment-id").open()));
        when(f.policies.lockHead("tenant", "policy"))
                .thenReturn(Optional.of(new LimitPolicyHead("policy", "merchant", 2, 2)));
        when(f.reservations.findExecutionGrant("tenant", "operation", 1)).thenReturn(Optional.of(grant()));
        when(issuer.activeKeyId()).thenReturn("new-key");
        when(issuer.issue(eq("tenant"), any(), eq("attempt"), eq(1L), eq("begin-key"), anyString()))
                .thenReturn(permit);
    }

    LimitBeginExecutionCommand begin() {
        return new LimitBeginExecutionCommand("operation", "attempt", "begin-key", null);
    }

    LimitExecutionVerifyCommand verification(long amount, String token) {
        return new LimitExecutionVerifyCommand("operation", "payment-id", "attempt", 1, amount, "CNY", token);
    }

    @Test
    void firstBeginPersistsTheIssuanceReceiptAndExecutionEpochTogether() {
        stored(reserved());
        var result = service.beginExecution(begin());
        assertEquals(1, result.executionEpoch());
        assertEquals(permit, result.permit());
        verify(f.reservations)
                .insertExecutionGrant(
                        eq("tenant"),
                        argThat(grant -> grant.permitKeyId().equals("new-key")
                                && grant.permitHash().equals(DigestUtils.sha256(permit))
                                && !grant.toString().contains(permit)));
        verify(f.reservations)
                .updateReservation(
                        eq("tenant"),
                        argThat(value -> value.reservation().state() == LimitReservationState.EXECUTING
                                && value.reservation().activeAttemptId().equals("attempt")),
                        eq(1L));
        assertEquals(1, f.manager.commits);
    }

    @Test
    void replayAfterReservationExpiryUsesTheOriginalKeyAndDoesNotIncrementEpoch() {
        stored(executing());
        var late = service(Clock.offset(f.clock, Duration.ofDays(1)));
        assertEquals(permit, late.beginExecution(begin()).permit());
        verify(issuer).issue(eq("tenant"), any(), eq("attempt"), eq(1L), eq("begin-key"), eq("old-key"));
        verify(f.reservations, never()).updateReservation(anyString(), any(), anyLong());
        verify(f.reservations, never()).insertExecutionGrant(anyString(), any());
    }

    @Test
    void aChangedRequestKeyCannotRemintTheSameAttemptPermit() {
        stored(executing());
        var error = assertThrows(
                CommonException.class,
                () -> service.beginExecution(new LimitBeginExecutionCommand("operation", "attempt", "changed", null)));
        assertSame(LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT, error.getResultCode());
        verifyNoInteractions(issuer);
    }

    @Test
    void aPublicEvidenceIdDoesNotAuthorizeReplacingAnUnknownAttempt() {
        stored(executing());
        var error = assertThrows(
                CommonException.class,
                () -> service.beginExecution(
                        new LimitBeginExecutionCommand("operation", "another", "new", "claimed-evidence")));
        assertSame(LimitCenterResultCodeEnum.OUTCOME_UNKNOWN, error.getResultCode());
        verify(f.reservations, never()).insertExecutionGrant(anyString(), any());
    }

    @Test
    void theDeadlineIsExclusiveAndARejectedBeginDoesNotImplicitlyReleaseQuota() {
        stored(reserved());
        var atDeadline = service(Clock.offset(f.clock, Duration.ofSeconds(120)));
        var error = assertThrows(CommonException.class, () -> atDeadline.beginExecution(begin()));
        assertSame(LimitCenterResultCodeEnum.RESERVATION_EXPIRED, error.getResultCode());
        verify(f.reservations, never()).updateBuckets(anyString(), any(), any());
        verifyNoInteractions(issuer);
    }

    @Test
    void anUnavailableSigningKeyCannotActivateTheReservation() {
        stored(reserved());
        when(issuer.issue(anyString(), any(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new CommonException(LimitCenterResultCodeEnum.PERMIT_UNAVAILABLE));
        assertThrows(CommonException.class, () -> service.beginExecution(begin()));
        verify(f.reservations, never()).updateReservation(anyString(), any(), anyLong());
        verify(f.reservations, never()).insertExecutionGrant(anyString(), any());
        assertEquals(1, f.manager.rollbacks);
    }

    @Test
    void validExecutionRemainsVerifiableAfterTheReservationTtl() {
        stored(executing());
        assertTrue(service(Clock.offset(f.clock, Duration.ofDays(1)))
                .verifyExecution(verification(40, permit))
                .valid());
        verifyNoInteractions(issuer);
    }

    @Test
    void changedAmountOrTokenNeverPassesVerification() {
        stored(executing());
        assertFalse(service.verifyExecution(verification(41, permit)).valid());
        assertFalse(service.verifyExecution(verification(40, "e".repeat(64))).valid());
    }

    @Test
    void stateIsReadAgainAfterWaitingForTheGate() {
        var expired = reserved().transitionTo(reserved().reservation().expire(f.now.plusSeconds(120)));
        stored(reserved());
        when(f.reservations.findByOperation("tenant", "operation"))
                .thenReturn(Optional.of(reserved()), Optional.of(expired));
        var error = assertThrows(CommonException.class, () -> service.beginExecution(begin()));
        assertSame(LimitCenterResultCodeEnum.RESERVATION_EXPIRED, error.getResultCode());
        verifyNoInteractions(issuer);
    }

    @Test
    void reusingAKeyIdWithDifferentSecretFailsInsteadOfChangingTheOriginalPermit() {
        stored(executing());
        when(issuer.issue(anyString(), any(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn("f".repeat(64));
        var error = assertThrows(CommonException.class, () -> service.beginExecution(begin()));
        assertSame(LimitCenterResultCodeEnum.PERMIT_UNAVAILABLE, error.getResultCode());
    }
}
