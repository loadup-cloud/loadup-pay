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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundSubmissionBoundaryTest {
    final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
    final RefundOrder initial = RefundOrder.init("refund", "request", "payment", DomainChecks.cnyAmount(300, "CNY"), "refund-request");
    final RefundRequest request = RefundRequest.init("request", "order", "merchant", "R001", initial.amount(), List.of(initial));
    final ChannelPaymentIdentity original = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "revision", "a".repeat(64), "payment-request");
    final PaymentRefundCapacity capacity = new PaymentRefundCapacity("payment", "order", "merchant", now.minusHours(1),
            DomainChecks.cnyAmount(1000, "CNY"), 0, 300, 1);

    StoredRefundChild stored(RefundOrder refund) {
        return new StoredRefundChild(refund, 1, original, "original-transaction", now.minusHours(1));
    }

    @Test
    void onlyInitCanCrossTheDurableSubmissionBoundary() {
        var started = initial.beginSubmission();
        assertEquals(RefundState.PROCESSING, started.state());
        assertEquals(1, started.version());
        assertNull(started.terminalEvidenceId());
        assertThrows(DomainViolation.class, started::beginSubmission);
        assertDoesNotThrow(() -> new RefundProgressChange(new RefundExecutionFacts(stored(initial), null, null, null), started, request,
                request.apply(started), capacity, capacity, now, null, null));
    }

    @Test
    void successCannotSkipTheDurableSubmissionBoundary() {
        var outcome = new ChannelRefundOutcome("refund", ChannelRefundOutcome.Kind.SUCCEEDED, initial.amount(), "receipt");
        var success = capacity.applyOutcome(initial, outcome);
        assertThrows(DomainViolation.class, () -> new RefundProgressChange(new RefundExecutionFacts(stored(initial), null, null, null),
                success.refund(), request, request.apply(success.refund()), capacity, success.capacity(), now, "refund-transaction", now));
    }

    @Test
    void unknownCannotBeRegressedToProcessingByAStorageChange() {
        var started = initial.beginSubmission();
        var unknown = started.recordOutcome(new ChannelRefundOutcome("refund", ChannelRefundOutcome.Kind.UNKNOWN, initial.amount(), null));
        var current = request.apply(unknown);
        var forged = new RefundOrder(initial.id(), initial.refundRequestId(), initial.paymentId(), initial.amount(),
                initial.channelRefundNo(), RefundState.PROCESSING, null, unknown.version() + 1);
        assertThrows(DomainViolation.class, () -> new RefundProgressChange(new RefundExecutionFacts(stored(unknown), now, null, null),
                forged, current, current.apply(forged), capacity, capacity, now, null, null));
    }

    @Test
    void terminalEvidenceIsNeverAttachedToAnIntermediateState() {
        assertThrows(DomainViolation.class, () -> new RefundOrder(initial.id(), initial.refundRequestId(), initial.paymentId(),
                initial.amount(), initial.channelRefundNo(), RefundState.UNKNOWN, "unverified-receipt", 1));
    }
}
