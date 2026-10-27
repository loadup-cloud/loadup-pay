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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.pay.payment.domain.model.RecoveryPosition;
import io.github.loadup.pay.payment.domain.model.RecoveryTaskKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDateTime;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryTaskProtocolTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 27, 12, 0);
    enum Flow {
        COLLECTION(PaymentRecoveryService.TYPE, "payment"), REFUND(RefundRecoveryService.TYPE, "request");
        final String type, subject;
        Flow(String type, String subject) { this.type = type; this.subject = subject; }
    }
    enum Changed { ID, TENANT, TYPE, BIZ_ID, EXECUTE_AT, STATUS, ATTEMPTS, MAX_ATTEMPTS, EXPIRED_LEASE, NOT_DUE, REGISTERED_STATUS }

    @ParameterizedTest @EnumSource(Flow.class)
    void bothFlowsCheckTrustedEnvelopeAndCanonicalIdentityBeforeLookup(Flow flow) {
        var task = task(flow);
        assertEquals(new RecoveryTaskKey(flow.subject, 0L), RecoveryTaskProtocol.receive(flow.type, task, "tenant"));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> RecoveryTaskProtocol.receive(flow.type, task, "other-tenant")).getResultCode());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> RecoveryTaskProtocol.receive("other-type", task, "tenant")).getResultCode());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> RecoveryTaskProtocol.receive(flow.type, null, "tenant")).getResultCode());
        var malformed = new DelayedTask(task.id(), task.tenantId(), task.taskType(), flow.subject + ".00", task.executeAt(), task.nextExecuteAt(),
                task.status(), task.attempts(), task.maxAttempts(), task.leaseUntil(), null);
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> RecoveryTaskProtocol.receive(flow.type, malformed, "tenant")).getResultCode());
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void staleOrChangedClaimMetadataIsRejectedForEitherBusinessFlow(Flow flow) {
        var original = task(flow);
        for (var changed : Changed.values()) {
            var altered = new DelayedTask(changed == Changed.ID ? "other-task" : original.id(), changed == Changed.TENANT ? "other-tenant" : original.tenantId(),
                    changed == Changed.TYPE ? "other-type" : original.taskType(), changed == Changed.BIZ_ID ? "other-subject.0" : original.bizId(),
                    changed == Changed.EXECUTE_AT ? at.minusSeconds(1) : original.executeAt(), changed == Changed.NOT_DUE ? at.plusSeconds(1) : original.nextExecuteAt(),
                    changed == Changed.STATUS || changed == Changed.REGISTERED_STATUS ? DelayedTaskStatus.FAILED : original.status(),
                    changed == Changed.ATTEMPTS ? original.attempts() + 1 : original.attempts(), changed == Changed.MAX_ATTEMPTS ? original.maxAttempts() + 1 : original.maxAttempts(),
                    changed == Changed.EXPIRED_LEASE ? at : original.leaseUntil(), null);
            boolean registeredChange = changed == Changed.EXPIRED_LEASE || changed == Changed.NOT_DUE || changed == Changed.REGISTERED_STATUS;
            var received = registeredChange ? original : altered;
            var registered = registeredChange ? altered : original;
            var error = assertThrows(CommonException.class, () -> RecoveryTaskProtocol.requireClaim(flow.type, received, registered, at), changed.name());
            assertEquals(INVALID_STATE, error.getResultCode());
        }
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void activeHeartbeatsMayExtendTheLeaseWithoutChangingTheReceivedSnapshot(Flow flow) {
        var original = task(flow);
        var extended = new DelayedTask(original.id(), original.tenantId(), original.taskType(), original.bizId(), original.executeAt(), original.nextExecuteAt(),
                original.status(), original.attempts(), original.maxAttempts(), at.plusMinutes(10), null);
        assertDoesNotThrow(() -> RecoveryTaskProtocol.requireClaim(flow.type, original, extended, at));
        assertEquals(INVALID_STATE, assertThrows(CommonException.class,
                () -> RecoveryTaskProtocol.requireClaim(flow.type, original, extended, extended.leaseUntil())).getResultCode());
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void sharedPositionMappingNeverSkipsAFutureGenerationOrAdvancesAWrongDueTime(Flow flow) {
        var key = new RecoveryTaskKey(flow.subject, 0L);
        assertTrue(RecoveryTaskProtocol.current(key, new RecoveryPosition(flow.subject, 0L, at, false), at));
        assertFalse(RecoveryTaskProtocol.current(key, new RecoveryPosition(flow.subject, 1L, at.plusSeconds(30), false), at));
        assertFalse(RecoveryTaskProtocol.current(key, new RecoveryPosition(flow.subject, 0L, at, true), at));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> RecoveryTaskProtocol.current(new RecoveryTaskKey(flow.subject, 1L),
                new RecoveryPosition(flow.subject, 0L, at, false), at)).getResultCode());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> RecoveryTaskProtocol.current(key,
                new RecoveryPosition(flow.subject, 0L, at.plusSeconds(1), false), at)).getResultCode());
    }

    private DelayedTask task(Flow flow) {
        return new DelayedTask("task", "tenant", flow.type, flow.subject + ".0", at, at, DelayedTaskStatus.RUNNING, 1, 5, at.plusMinutes(5), null);
    }
}
