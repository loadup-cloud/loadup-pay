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
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.limitcenter.app.port.ExecutionPermitIssuer;
import io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.domain.policy.LimitWindowPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LimitExpiryServiceTest {
    final LimitServiceFixture f = new LimitServiceFixture();
    final LimitExecutionService service = new LimitExecutionService(
            f.transactions, f.reservations, f.policies, mock(ExecutionPermitIssuer.class), f.converter, f.clock);

    @AfterEach
    void close() {
        f.close();
    }

    StoredLimitReservation reserved(LocalDateTime expiry) {
        var value = LimitReservation.init(
                        "reservation",
                        "operation",
                        "payment-id",
                        "order",
                        "merchant",
                        DomainChecks.amount(40, "CNY"),
                        "policy",
                        1,
                        f.now.minusDays(1),
                        expiry)
                .reserve();
        return new StoredLimitReservation(value, f.policy(1, 100).policyHash(), "b".repeat(64), "payment");
    }

    void stored(StoredLimitReservation stored) {
        when(f.reservations.findByOperation("tenant", "operation")).thenReturn(Optional.of(stored));
        when(f.reservations.lockGate("tenant", "operation"))
                .thenReturn(Optional.of(
                        LimitOperationGate.init("operation", "payment-id").open()));
        when(f.policies.lockHead("tenant", "policy"))
                .thenReturn(Optional.of(new LimitPolicyHead("policy", "merchant", 2, 2)));
        when(f.policies.findRevision("tenant", "policy", 1)).thenReturn(Optional.of(f.policy(1, 100)));
    }

    LimitReservationItem item() {
        var rule = f.policy(1, 100).rules().getFirst();
        var key = new LimitBucketKey(
                rule.ruleScopeId(),
                rule.merchantId(),
                rule.metric(),
                rule.currency(),
                rule.window(),
                new LimitWindowPolicy().at(rule.window(), f.now.minusDays(1), rule.weekStart()));
        return new LimitReservationItem("item", "reservation", key, 40, 0, 0);
    }

    @Test
    void expiryReturnsOnlyThisReservationsHoldAndPreservesOtherUsage() {
        stored(reserved(f.now));
        var item = item();
        when(f.reservations.loadItems("tenant", "reservation")).thenReturn(List.of(item));
        when(f.reservations.lockExistingBuckets(eq("tenant"), anyList()))
                .thenReturn(new LimitBucketSet(Map.of(item.bucket(), new LimitBucket(item.bucket(), 10, 60, 4))));
        assertEquals(
                io.github.loadup.pay.limitcenter.client.enums.LimitReservationState.EXPIRED,
                service.expire(new LimitExpireCommand("operation")).state());
        verify(f.reservations)
                .updateBuckets(
                        eq("tenant"),
                        any(),
                        argThat(after -> after.buckets().get(item.bucket()).held() == 20
                                && after.buckets().get(item.bucket()).used() == 10));
        verify(f.reservations)
                .updateReservation(
                        eq("tenant"),
                        argThat(next -> next.reservation().state() == LimitReservationState.EXPIRED),
                        eq(1L));
    }

    @Test
    void repeatedExpiryDoesNotReleaseAnythingTwice() {
        var original = reserved(f.now);
        stored(original.transitionTo(original.reservation().expire(f.now)));
        service.expire(new LimitExpireCommand("operation"));
        verify(f.reservations, never()).loadItems(anyString(), anyString());
        verify(f.reservations, never()).updateBuckets(anyString(), any(), any());
    }

    @Test
    void unknownExecutingPaymentKeepsItsHoldAfterTheReservationDeadline() {
        var original = reserved(f.now.minusSeconds(1));
        stored(original.transitionTo(original.reservation().beginExecution("attempt", f.now.minusSeconds(2))));
        assertEquals(
                io.github.loadup.pay.limitcenter.client.enums.LimitReservationState.EXECUTING,
                service.expire(new LimitExpireCommand("operation")).state());
        verify(f.reservations, never()).loadItems(anyString(), anyString());
        verify(f.reservations, never()).updateBuckets(anyString(), any(), any());
    }

    @Test
    void anEarlyExpiryRequestIsAnUnchangedObservation() {
        stored(reserved(f.now.plusSeconds(1)));
        assertEquals(
                io.github.loadup.pay.limitcenter.client.enums.LimitReservationState.RESERVED,
                service.expire(new LimitExpireCommand("operation")).state());
        verify(f.reservations, never()).updateReservation(anyString(), any(), anyLong());
    }

    @Test
    void aCorruptedOriginalItemCannotReleaseAnotherQuantity() {
        stored(reserved(f.now));
        var item = item();
        when(f.reservations.loadItems("tenant", "reservation"))
                .thenReturn(List.of(new LimitReservationItem("item", "reservation", item.bucket(), 41, 0, 0)));
        assertThrows(CommonException.class, () -> service.expire(new LimitExpireCommand("operation")));
        verify(f.reservations, never()).lockExistingBuckets(anyString(), anyList());
    }

    @Test
    void aLateReservationWriteConflictRollsBackTheWholeExpiryTransaction() {
        stored(reserved(f.now));
        var item = item();
        when(f.reservations.loadItems("tenant", "reservation")).thenReturn(List.of(item));
        when(f.reservations.lockExistingBuckets(eq("tenant"), anyList()))
                .thenReturn(new LimitBucketSet(Map.of(item.bucket(), new LimitBucket(item.bucket(), 10, 60, 4))));
        doThrow(new CommonException(
                io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE))
                .when(f.reservations)
                .updateReservation(anyString(), any(), anyLong());
        assertThrows(CommonException.class, () -> service.expire(new LimitExpireCommand("operation")));
        assertEquals(1, f.manager.rollbacks);
        assertEquals(0, f.manager.commits);
    }
}
