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

import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;

import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentLifecycleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0);

    private PaymentOrder order() {
        return PaymentOrder.init(
                "payment", "order", "allocation", "merchant", Money.ofMinor(1000, "CNY"), "operation", NOW);
    }

    private PaymentAttempt attempt(String id, int number) {
        var binding = new ChannelBindingRevision(
                "binding",
                "merchant",
                Channel.ALIPAY,
                AccessMode.SERVICE_PROVIDER,
                "a".repeat(64),
                "secret-reference",
                "v1",
                new io.github.loadup.pay.channel.api.model.AlipayAccountIdentity("app", "seller", null, "auth-reference", "v1"),
                Set.of(PaymentMethod.H5));
        return PaymentAttempt.init(
                id, "payment", number, Money.ofMinor(1000, "CNY"), binding, PaymentMethod.H5, "req-" + id);
    }

    private ChannelPaymentOutcome outcome(PaymentAttempt attempt, ChannelPaymentOutcome.Kind kind) {
        return new ChannelPaymentOutcome(kind, attempt.identity(), attempt.amount(), "receipt");
    }

    @Test
    void unknownRetainsTheSameAttemptAndCannotBeReplaced() {
        var first = attempt("a1", 1).beginSubmission(1);
        var unknown = first.recordOutcome(outcome(first, ChannelPaymentOutcome.Kind.UNKNOWN), false);
        var payment = order().prepareAttempt(attempt("a1", 1), null).applyAttempt(unknown);
        assertEquals(PaymentState.UNKNOWN, payment.state());
        assertThrows(DomainViolation.class, () -> payment.prepareAttempt(attempt("a2", 2), unknown));
        assertThrows(
                DomainViolation.class,
                () -> unknown.recordOutcome(outcome(first, ChannelPaymentOutcome.Kind.NO_COLLECTION), false));
        var failed = unknown.recordOutcome(outcome(first, ChannelPaymentOutcome.Kind.NO_COLLECTION), true);
        var retried = payment.prepareAttempt(attempt("a2", 2), failed);
        assertEquals("a2", retried.activeAttemptId());
        assertEquals(payment.limitOperationId(), retried.limitOperationId());
    }

    @Test
    void anInflightSuccessCanFinishWhileClosingAndCannotBeReversed() {
        var submitted = attempt("a1", 1).beginSubmission(1);
        var payment = order().prepareAttempt(attempt("a1", 1), null).requestClose();
        var success = submitted.recordOutcome(outcome(submitted, ChannelPaymentOutcome.Kind.SUCCEEDED), false);
        var paid = payment.applyAttempt(success);
        assertEquals(PaymentState.SUCCEEDED, paid.state());
        assertSame(paid, paid.applyAttempt(success));
        assertThrows(DomainViolation.class, () -> paid.close(new NoCollectionEvidence("no-risk", "payment", true)));
        assertThrows(
                DomainViolation.class,
                () -> success.recordOutcome(outcome(submitted, ChannelPaymentOutcome.Kind.NO_COLLECTION), true));
    }

    @Test
    void channelIdentityAndAmountMustMatchTheFrozenAttempt() {
        var submitted = attempt("a1", 1).beginSubmission(1);
        var wrongIdentity = new ChannelPaymentOutcome(
                ChannelPaymentOutcome.Kind.SUCCEEDED,
                attempt("other", 1).identity(),
                Money.ofMinor(1000, "CNY"),
                "receipt");
        assertThrows(DomainViolation.class, () -> submitted.recordOutcome(wrongIdentity, false));
        assertThrows(
                DomainViolation.class,
                () -> submitted.recordOutcome(
                        new ChannelPaymentOutcome(
                                ChannelPaymentOutcome.Kind.SUCCEEDED,
                                submitted.identity(),
                                Money.ofMinor(999, "CNY"),
                                "receipt"),
                        false));
        assertThrows(
                DomainViolation.class,
                () -> submitted.recordOutcome(
                        outcome(submitted, ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED), true));
    }

    @Test
    void aFenceArrivingBeforeCreationCanNeverBeReopened() {
        var fenced = PaymentExecutionGate.init("order", null).fence(4);
        assertThrows(DomainViolation.class, fenced::open);
        assertSame(fenced, fenced.fence(2));
        var open = PaymentExecutionGate.init("order", "payment").open();
        assertDoesNotThrow(open::requireOpen);
        assertThrows(DomainViolation.class, () -> open.fence(1).requireOpen());
    }
}
