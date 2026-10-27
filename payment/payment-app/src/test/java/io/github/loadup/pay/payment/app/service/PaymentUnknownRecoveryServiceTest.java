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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.UnknownRecoveryOperationsConverter;
import io.github.loadup.pay.payment.app.model.UnknownRecoveryEntry;
import io.github.loadup.pay.payment.app.model.UnknownRecoveryWindow;
import io.github.loadup.pay.payment.app.port.UnknownRecoveryReader;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryAttentionLevel;
import io.github.loadup.pay.payment.client.query.PaymentUnknownRecoveryQuery;
import io.github.loadup.pay.payment.domain.policy.RecoveryEscalationPolicy;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind.*;
import static io.github.loadup.pay.payment.client.enums.PaymentRecoveryAttentionLevel.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentUnknownRecoveryServiceTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 27, 12, 0);
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(UnknownRecoveryOperationsConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final UnknownRecoveryReader reader = mock(UnknownRecoveryReader.class);
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "pay-ops", Set.of("pay:payment:recovery:read")));
    final PaymentUnknownRecoveryService service = new PaymentUnknownRecoveryService(actor::get, reader,
            context.getBean(UnknownRecoveryOperationsConverter.class), new RecoveryEscalationPolicy(900, 86400),
            new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions()), validation.getValidator(),
            Clock.fixed(at.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()), "pay-ops");

    @AfterEach void close() { validation.close(); context.close(); }

    @Test void trustedReadSnapshotReturnsTheExtraRowAsACursorWithoutClaimingATotalCount() {
        var urgent = entry(COLLECTION, "pay-a", "pay-a", at.minusDays(1));
        var review = entry(COLLECTION, "pay-b", "pay-b", at.minusMinutes(15));
        var next = entry(COLLECTION, "pay-c", "pay-c", review.firstUnknownAt());
        when(reader.list(eq("tenant"), eq(COLLECTION), any())).thenAnswer(call -> {
            assertEquals("tenant", TenantUtil.getTenantId());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            UnknownRecoveryWindow window = call.getArgument(2);
            assertEquals(at.minusMinutes(15), window.cutoff());
            return window.afterRecordId() == null ? List.of(urgent, review, next) : List.of(next);
        });
        var first = service.list(new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, null, null, 2));
        assertEquals(List.of("pay-a", "pay-b"), first.items().stream().map(value -> value.recordId()).toList());
        assertEquals(URGENT, first.items().getFirst().attentionLevel());
        assertEquals(86400L, first.items().getFirst().ageSeconds());
        assertEquals(REVIEW, first.items().getLast().attentionLevel());
        assertTrue(first.hasMore());
        assertEquals("pay-b", first.nextRecordId());
        assertEquals(BusinessTime.external(review.firstUnknownAt()), first.nextFirstUnknownAt());
        assertEquals(900L, first.reviewAfterSeconds());
        assertEquals(86400L, first.urgentAfterSeconds());
        var second = service.list(new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, first.nextFirstUnknownAt(), first.nextRecordId(), 2));
        assertEquals(List.of("pay-c"), second.items().stream().map(value -> value.recordId()).toList());
        assertFalse(second.hasMore()); assertNull(second.nextFirstUnknownAt()); assertNull(second.nextRecordId());
        assertNull(TenantUtil.getTenantId()); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test void childRefundsRetainTheirIndividualIdentityAndTheParentTaskIdentity() {
        var one = entry(REFUND_REQUEST, "refund-a", "request", at.minusDays(2));
        var two = new UnknownRecoveryEntry(REFUND_REQUEST, "refund-b", "request", "order", "merchant", one.amount(), one.firstUnknownAt(), 2L, null, null);
        when(reader.list(eq("tenant"), eq(REFUND_REQUEST), any())).thenReturn(List.of(one, two));
        var page = service.list(new PaymentUnknownRecoveryQuery(REFUND_REQUEST, NORMAL, null, null, 10));
        assertEquals(List.of("refund-a", "refund-b"), page.items().stream().map(value -> value.recordId()).toList());
        assertEquals("request", page.items().getFirst().subjectId());
        assertEquals(3L, page.items().getFirst().recoveryGeneration());
        assertFalse(page.items().getFirst().recoveryCompleted());
        assertNull(page.items().getLast().recoveryGeneration());
        assertNull(page.items().getLast().recoveryCompleted());
    }

    @Test void merchantOrUnprivilegedServicesAreRejectedBeforeAnyDataAccess() {
        actor.set(new PaymentActor("tenant", "merchant-service", Set.of("pay:payment:recovery:read")));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.list(null)).getResultCode());
        actor.set(new PaymentActor("tenant", "pay-ops", Set.of()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.list(null)).getResultCode());
        verifyNoInteractions(reader);
    }

    @Test void clockRegressionKeepsExistingFutureStampedUnknownRowsVisibleAndPageable() {
        var one = entry(COLLECTION, "pay-a", "pay-a", at.plusHours(1));
        var two = entry(COLLECTION, "pay-b", "pay-b", one.firstUnknownAt());
        when(reader.list(eq("tenant"), eq(COLLECTION), any())).thenAnswer(call -> {
            UnknownRecoveryWindow window = call.getArgument(2);
            assertNull(window.cutoff());
            return window.afterRecordId() == null ? List.of(one, two) : List.of(two);
        });
        var first = service.list(new PaymentUnknownRecoveryQuery(COLLECTION, NORMAL, null, null, 1));
        assertEquals(0L, first.items().getFirst().ageSeconds());
        assertEquals(NORMAL, first.items().getFirst().attentionLevel());
        var next = service.list(new PaymentUnknownRecoveryQuery(COLLECTION, NORMAL, first.nextFirstUnknownAt(), first.nextRecordId(), 1));
        assertEquals("pay-b", next.items().getFirst().recordId());
    }

    @Test void malformedCursorPairsPrecisionAndUnboundedPageSizesNeverReachTheReader() {
        var external = BusinessTime.external(at);
        for (var query : List.of(new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, external, null, 10),
                new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, null, "pay-a", 10),
                new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, external, " ", 10),
                new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, external.minusSeconds(1).plusNanos(1), "pay-a", 10),
                new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, null, null, 0),
                new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, null, null, 101)))
            assertEquals(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.list(query)).getResultCode());
        verifyNoInteractions(reader);
    }

    @Test void inconsistentReaderResultsFailInsteadOfPublishingAnIncompleteOrIncorrectQueue() {
        var row = entry(COLLECTION, "pay-a", "pay-a", at.minusHours(1));
        var query = new PaymentUnknownRecoveryQuery(COLLECTION, REVIEW, null, null, 1);
        doReturn(List.of(row, row)).when(reader).list(anyString(), any(), any());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.list(query)).getResultCode());
        doReturn(List.of(entry(COLLECTION, "pay-a", "pay-a", at))).when(reader).list(anyString(), any(), any());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.list(query)).getResultCode());
        doReturn(List.of(entry(REFUND_REQUEST, "refund-a", "request", row.firstUnknownAt()))).when(reader).list(anyString(), any(), any());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.list(query)).getResultCode());
        assertNull(TenantUtil.getTenantId());
    }

    private UnknownRecoveryEntry entry(PaymentRecoveryKind kind, String id, String subject, LocalDateTime firstAt) {
        return new UnknownRecoveryEntry(kind, id, subject, "order", "merchant", DomainChecks.cnyAmount(1000L, "CNY"), firstAt, 2L, 3L, false);
    }
}
