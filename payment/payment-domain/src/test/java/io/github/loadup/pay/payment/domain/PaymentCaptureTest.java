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
package io.github.loadup.pay.payment.domain;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.domain.model.PaymentCapture;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureBlocker;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureRead;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureScope;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerLine;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentCaptureTest {
    final LocalDate day = LocalDate.of(2026, 10, 26);
    final PaymentCaptureScope scope = new PaymentCaptureScope(Channel.WECHAT, "a".repeat(64), day, PaymentCaptureScope.WECHAT_ALL, "Asia/Shanghai");
    final LocalDateTime readAt = day.plusDays(1).atTime(10, 0);

    @Test
    void completeSnapshotFreezesGrossAmountsAndCannotBeReplaced() {
        var rows = new ArrayList<>(List.of(payment(1, "txn", 1000, day.atTime(10, 0)), refund(2, "txn", "refund", 200)));
        var read = new PaymentCaptureRead(scope, readAt, readAt.plusSeconds(1), rows, null, null);
        rows.clear();
        assertEquals(2, read.lines().size());
        var capture = initial().complete(read, "b".repeat(64));
        assertEquals(PaymentCaptureState.SEALED, capture.state());
        assertEquals(1L, capture.version());
        assertEquals(1000L, capture.paymentAmountMinor());
        assertEquals(200L, capture.refundAmountMinor());
        assertEquals(1L, capture.paymentCount());
        assertEquals(1L, capture.refundCount());
        assertThrows(DomainViolation.class, () -> capture.complete(read, "c".repeat(64)));
        assertThrows(UnsupportedOperationException.class, () -> read.lines().clear());
    }

    @Test
    void blockerHasNoExportableTotalsHashOrPartialRows() {
        var blocked = new PaymentCaptureRead(scope, readAt, readAt, List.of(), PaymentCaptureBlocker.REFUND_ACCEPTANCE_MISSING, "refund");
        var capture = initial().complete(blocked, null);
        assertEquals(PaymentCaptureState.BLOCKED, capture.state());
        assertNull(capture.contentHash());
        assertNull(capture.paymentCount());
        assertNull(capture.paymentAmountMinor());
        assertNull(capture.refundCount());
        assertNull(capture.refundAmountMinor());
        assertThrows(DomainViolation.class, () -> initial().complete(blocked, "b".repeat(64)));
        assertThrows(DomainViolation.class, () -> new PaymentCaptureRead(scope, readAt, readAt,
                List.of(payment(1, "txn", 1, day.atTime(10, 0))), PaymentCaptureBlocker.RECORD_LIMIT, "payment"));
    }

    @Test
    void emptyCompleteInputIsDifferentFromBlockedAndHasExplicitZeroTotals() {
        var capture = initial().complete(new PaymentCaptureRead(scope, readAt, readAt, List.of(), null, null), "b".repeat(64));
        assertEquals(PaymentCaptureState.SEALED, capture.state());
        assertEquals(0L, capture.paymentCount());
        assertEquals(0L, capture.paymentAmountMinor());
        assertEquals(0L, capture.refundCount());
        assertEquals(0L, capture.refundAmountMinor());
    }

    @Test
    void dateBoundariesAreChannelProtocolInstantsInTheFrozenSystemZone() {
        var other = new PaymentCaptureScope(Channel.WECHAT, "a".repeat(64), day, PaymentCaptureScope.WECHAT_ALL, "America/New_York");
        assertEquals(day.minusDays(1).atTime(12, 0), other.windowStart());
        assertEquals(day.atTime(12, 0), other.windowEnd());
        assertEquals(day.atStartOfDay(), scope.windowStart());
        assertDoesNotThrow(() -> read(List.of(payment(1, "start", 1, scope.windowStart()))));
        assertThrows(DomainViolation.class, () -> read(List.of(payment(1, "end", 1, scope.windowEnd()))));
        assertThrows(DomainViolation.class, () -> read(List.of(payment(1, "before", 1, scope.windowStart().minusNanos(1000)))));
    }

    @Test
    void naturalKeysAndOrdinalsCannotDuplicateOrLeaveGaps() {
        assertThrows(DomainViolation.class, () -> read(List.of(payment(2, "txn", 1, day.atTime(1, 0)))));
        assertThrows(DomainViolation.class, () -> read(List.of(payment(1, "txn", 1, day.atTime(1, 0)), payment(2, "txn", 1, day.atTime(2, 0)))));
        assertThrows(DomainViolation.class, () -> read(List.of(refund(1, "txn", "r", 1), refund(2, "txn", "r", 1))));
        assertDoesNotThrow(() -> read(List.of(payment(1, "txn", 2, day.atTime(1, 0)), refund(2, "txn", "r1", 1), refund(3, "txn", "r2", 1))));
    }

    @Test
    void wrongScopeAndUnsupportedProfilesCannotBeCompleted() {
        assertThrows(DomainViolation.class, () -> new PaymentCaptureScope(Channel.ALIPAY, "a".repeat(64), day, PaymentCaptureScope.WECHAT_ALL, "Asia/Shanghai"));
        var other = new PaymentCaptureScope(Channel.WECHAT, "b".repeat(64), day, PaymentCaptureScope.WECHAT_ALL, "Asia/Shanghai");
        assertThrows(DomainViolation.class, () -> initial().complete(new PaymentCaptureRead(other, readAt, readAt, List.of(), null, null), "c".repeat(64)));
    }

    private PaymentCapture initial() {
        return PaymentCapture.init("capture", "reconciliation", "request", "d".repeat(64), scope);
    }

    private PaymentCaptureRead read(List<PaymentLedgerLine> rows) {
        return new PaymentCaptureRead(scope, readAt, readAt, rows, null, null);
    }

    private PaymentLedgerLine payment(long n, String txn, long amount, LocalDateTime at) {
        return new PaymentLedgerLine(n, PaymentLedgerKind.PAYMENT, txn, null, "request", Money.ofMinor(amount, "CNY"), PaymentLedgerStatus.SUCCEEDED, at, "payment", "receipt", 2L);
    }

    private PaymentLedgerLine refund(long n, String txn, String refund, long amount) {
        return new PaymentLedgerLine(n, PaymentLedgerKind.REFUND, txn, refund, "request", Money.ofMinor(amount, "CNY"), PaymentLedgerStatus.PROCESSING, day.atTime(23, 59), refund, "accepted", 1L);
    }
}
