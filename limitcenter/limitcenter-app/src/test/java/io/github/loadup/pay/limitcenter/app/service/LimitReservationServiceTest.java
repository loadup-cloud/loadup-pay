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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitWriteRaceException;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LimitReservationServiceTest {
    final LimitServiceFixture f = new LimitServiceFixture();

    @AfterEach
    void close() {
        f.close();
    }

    void active(long used, int currentVersion, long threshold) {
        when(f.reservations.lockOrCreateGate("tenant", "operation", "payment-id"))
                .thenReturn(LimitOperationGate.init("operation", "payment-id"));
        when(f.policies.lockHead("tenant", "policy"))
                .thenReturn(Optional.of(new LimitPolicyHead("policy", "merchant", currentVersion, currentVersion)));
        when(f.policies.findRevision("tenant", "policy", 1)).thenReturn(Optional.of(f.policy(1, 100)));
        when(f.policies.lockRule(eq("tenant"), any()))
                .thenReturn(Optional.of(new RuntimeLimitRule(
                        "policy",
                        currentVersion,
                        f.policy(currentVersion, threshold).rules().getFirst(),
                        currentVersion)));
        when(f.reservations.lockBuckets(eq("tenant"), anyList())).thenAnswer(call -> {
            List<LimitBucketKey> keys = call.getArgument(1);
            var values = new HashMap<LimitBucketKey, LimitBucket>();
            for (var key : keys) {
                values.put(key, new LimitBucket(key, used, 0, 4));
            }
            return new LimitBucketSet(values);
        });
    }

    StoredLimitReservation stored(String caller) {
        var value = LimitReservation.init(
                        "reservation",
                        "operation",
                        "payment-id",
                        "order",
                        "merchant",
                        DomainChecks.cnyAmount(40, "CNY"),
                        "policy",
                        1,
                        f.now.minusDays(1),
                        f.now.plusSeconds(120))
                .reserve();
        return new StoredLimitReservation(value, f.policy(1, 100).policyHash(), "b".repeat(64), caller);
    }

    @Test
    void originalInitiationSelectsBucketsWhileExpiryStartsAtFirstReservation() {
        active(0, 1, 100);
        f.reservationService.reserve(f.command(40));
        verify(f.reservations)
                .insertReservation(
                        eq("tenant"),
                        argThat(stored -> stored.reservation().initiatedAt().equals(f.now.minusDays(1))
                                && stored.reservation().reserveExpiresAt().equals(f.now.plusSeconds(120))),
                        anyList(),
                        any(),
                        argThat(after ->
                                after.buckets().values().iterator().next().held() == 40));
        assertEquals(1, f.manager.commits);
    }

    @Test
    void replayDoesNotAddHoldsOrRefreshExpiry() {
        when(f.reservations.lockOrCreateGate("tenant", "operation", "payment-id"))
                .thenReturn(LimitOperationGate.init("operation", "payment-id").open());
        when(f.reservations.findByOperation("tenant", "operation")).thenReturn(Optional.of(stored("payment")));
        assertEquals("reservation", f.reservationService.reserve(f.command(40)).reservationId());
        verifyNoInteractions(f.policies);
        verify(f.reservations, never()).lockBuckets(anyString(), anyList());
    }

    @Test
    void changedAmountConflictsEvenWhenCallerKeepsTheSameDigest() {
        when(f.reservations.lockOrCreateGate("tenant", "operation", "payment-id"))
                .thenReturn(LimitOperationGate.init("operation", "payment-id").open());
        when(f.reservations.findByOperation("tenant", "operation")).thenReturn(Optional.of(stored("payment")));
        var error = assertThrows(CommonException.class, () -> f.reservationService.reserve(f.command(41)));
        assertSame(LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT, error.getResultCode());
    }

    @Test
    void olderFrozenRevisionStillUsesTheLatestThresholdAgainstExistingUsage() {
        active(60, 2, 80);
        var error = assertThrows(CommonException.class, () -> f.reservationService.reserve(f.command(30)));
        assertSame(LimitCenterResultCodeEnum.EXCEEDED, error.getResultCode());
        verify(f.reservations, never()).insertReservation(anyString(), any(), anyList(), any(), any());
        assertEquals(1, f.manager.rollbacks);
    }

    @Test
    void fencedTombstoneCannotBeReopenedByALateReservation() {
        when(f.reservations.lockOrCreateGate("tenant", "operation", "payment-id"))
                .thenReturn(LimitOperationGate.init("operation", "payment-id").fence(1, "evidence"));
        var error = assertThrows(CommonException.class, () -> f.reservationService.reserve(f.command(40)));
        assertSame(LimitCenterResultCodeEnum.EXECUTION_FENCED, error.getResultCode());
        verifyNoInteractions(f.policies);
    }

    @Test
    void detailCannotCrossServiceOwnership() {
        when(f.reservations.findByOperation("tenant", "operation")).thenReturn(Optional.of(stored("another-service")));
        var error = assertThrows(
                CommonException.class, () -> f.reservationService.detail(new LimitOperationQuery("operation")));
        assertSame(LimitCenterResultCodeEnum.ACTION_FORBIDDEN, error.getResultCode());
    }

    @Test
    void aUniquenessRaceRollsBackBeforeStartingTheRetry() {
        AtomicInteger calls = new AtomicInteger();
        String result = f.transactions.writeLocal(f.actor, LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT, () -> {
            if (calls.incrementAndGet() == 1) {
                throw new LimitWriteRaceException("synthetic collision");
            }
            assertEquals(1, f.manager.rollbacks);
            assertEquals(2, f.manager.begins);
            return "winner";
        });
        assertEquals("winner", result);
        assertEquals(1, f.manager.commits);
    }

    @Test
    void persistentContentionStopsAfterThreeRolledBackAttempts() {
        var error = assertThrows(
                CommonException.class,
                () -> f.transactions.writeLocal(f.actor, LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT, () -> {
                    throw new LimitWriteRaceException("synthetic collision");
                }));
        assertSame(LimitCenterResultCodeEnum.CONCURRENT_UPDATE, error.getResultCode());
        assertEquals(3, f.manager.rollbacks);
    }

    @Test
    void ambientTransactionsCannotSilentlyChangeTheAtomicityBoundary() {
        new TransactionTemplate(f.manager).execute(status -> {
            assertThrows(CommonException.class, () -> f.reservationService.reserve(f.command(40)));
            return null;
        });
        verifyNoInteractions(f.policies, f.reservations);
    }
}
