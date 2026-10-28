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
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptBinding;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.Set;

import static io.github.loadup.pay.payment.domain.model.PaymentAttemptBinding.ObservationRejection.OBSERVATION_BEFORE_SUBMISSION;
import static io.github.loadup.pay.payment.domain.model.PaymentAttemptBinding.ObservationRejection.PAYMENT_AMOUNT_MISMATCH;
import static io.github.loadup.pay.payment.domain.model.PaymentAttemptBinding.ObservationRejection.PAYMENT_IDENTITY_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentAttemptBindingTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 27, 12, 0);
    final Money amount = Money.ofMinor(1000L, "CNY");
    final ChannelBindingRevision route = route("merchant");
    final PaymentAttempt attempt = PaymentAttempt.init("attempt", "payment", 1, amount, route, PaymentMethod.H5, "original-request");
    final StoredPaymentAttempt prepared = new StoredPaymentAttempt(attempt, "operation", "acquire", "attempt-request",
            "b".repeat(64), "d".repeat(64), "input-ref", null, null, at, null, 0L);
    final StoredPaymentAttempt submitted = prepared.beginSubmission(1L, "e".repeat(64), RiskDecision.notConnected(), at.plusSeconds(1));
    final PaymentOrder payment = PaymentOrder.init("payment", "order", "allocation", "merchant", amount, "operation", at).prepareAttempt(attempt, null);
    final StoredPaymentAcceptance accepted = acceptance(payment, null);

    @ParameterizedTest
    @EnumSource(Changed.class)
    void eachIndependentCrossRecordMismatchRejectsTheBindingBeforeAnyDecision(Changed changed) {
        var bad = changed(prepared, changed);
        assertThrows(DomainViolation.class, () -> new PaymentAttemptBinding(accepted, bad));
    }

    @Test
    void preSubmissionAndAlreadyIssuedWinnerHaveDistinctPermissions() {
        var before = new PaymentAttemptBinding(accepted, prepared);
        assertSame(before, before.requireActive().requireSubmittable().requireClosable());
        assertThrows(DomainViolation.class, before::requireObservable);
        assertThrows(DomainViolation.class, before::requireSubmissionRequest);
        var winner = new PaymentAttemptBinding(accepted, submitted);
        assertSame(winner, winner.requireObservable().requireActive().requireSubmissionRequest());
        assertThrows(DomainViolation.class, winner::requireSubmittable);
        var closing = new PaymentAttemptBinding(acceptance(payment.requestClose(), null), submitted);
        assertSame(closing, closing.requireObservable().requireClosable());
        assertThrows(DomainViolation.class, closing::requireSubmittable);
        assertThrows(DomainViolation.class, closing::requireSubmissionRequest);
    }

    @Test
    void terminalOrSupersededAttemptsRemainObservableButCannotBecomeNewSubmissionRights() {
        var successful = submitted.applyOutcome(new ChannelPaymentOutcome(ChannelPaymentOutcome.Kind.SUCCEEDED, attempt.identity(), amount, "evidence"), false);
        var terminal = new PaymentAttemptBinding(acceptance(payment.applyAttempt(successful.attempt()), "evidence"), successful);
        assertSame(terminal, terminal.requireObservable());
        assertThrows(DomainViolation.class, terminal::requireSubmittable);
        assertThrows(DomainViolation.class, terminal::requireClosable);
        assertThrows(DomainViolation.class, terminal::requireSubmissionRequest);
        var moved = new PaymentOrder(payment.id(), payment.orderId(), payment.allocationId(), payment.payeeMerchantId(), payment.amount(),
                payment.limitOperationId(), at, PaymentState.PROCESSING, "another-attempt", null, 2L);
        var original = new PaymentAttemptBinding(acceptance(moved, null), submitted);
        assertSame(original, original.requireObservable());
        assertThrows(DomainViolation.class, original::requireActive);
        assertThrows(DomainViolation.class, original::requireSubmittable);
        assertThrows(DomainViolation.class, original::requireClosable);
        assertEquals(PAYMENT_IDENTITY_MISMATCH, PaymentAttemptBinding.observationRejection(original.accepted(), submitted, attempt.identity(), amount));
    }

    @Test
    void observationTargetPrecedesCorruptionAndAmountOrPreSubmissionConflictsKeepTheirExactReason() {
        var identity = attempt.identity();
        var wrong = new ChannelPaymentIdentity(identity.paymentId(), identity.attemptId(), identity.channel(), identity.bindingRevisionId(),
                identity.accountScopeHash(), "wrong-request");
        var corrupt = changed(submitted, Changed.PAYMENT_DIGEST);
        assertEquals(PAYMENT_IDENTITY_MISMATCH, PaymentAttemptBinding.observationRejection(accepted, corrupt, wrong, amount));
        assertThrows(DomainViolation.class, () -> PaymentAttemptBinding.observationRejection(accepted, corrupt, identity, amount));
        assertEquals(PAYMENT_AMOUNT_MISMATCH, PaymentAttemptBinding.observationRejection(accepted, submitted, identity, Money.ofMinor(1001L, "CNY")));
        assertEquals(OBSERVATION_BEFORE_SUBMISSION, PaymentAttemptBinding.observationRejection(accepted, prepared, identity, amount));
        assertNull(PaymentAttemptBinding.observationRejection(accepted, submitted, identity, amount));
    }

    @Test
    void frozenRouteIsDistinctFromTheAttemptsOwnRequestDigest() {
        var binding = new PaymentAttemptBinding(accepted, prepared);
        assertSame(binding, binding.requireRoute(Channel.ALIPAY, PaymentMethod.H5, "input-ref"));
        assertThrows(DomainViolation.class, () -> binding.requireRoute(Channel.WECHAT, PaymentMethod.H5, "input-ref"));
        assertThrows(DomainViolation.class, () -> binding.requireRoute(Channel.ALIPAY, PaymentMethod.JSAPI, "input-ref"));
        assertThrows(DomainViolation.class, () -> binding.requireRoute(Channel.ALIPAY, PaymentMethod.H5, "other-input"));
        // An attempt's own idempotency digest is a different scope from the payment request digest.
        assertNotEquals(accepted.requestDigest(), prepared.requestDigest());
    }

    private ChannelBindingRevision route(String merchant) {
        return new ChannelBindingRevision("revision", merchant, Channel.ALIPAY, AccessMode.DIRECT, "a".repeat(64), "credential", "v1",
                new AlipayAccountIdentity("app", "seller", null, null, null), Set.of(PaymentMethod.H5, PaymentMethod.JSAPI));
    }

    private StoredPaymentAcceptance acceptance(PaymentOrder value, String evidence) {
        return new StoredPaymentAcceptance(value, "admission", "d".repeat(64), "acquire", "frozen-payload", "c".repeat(64), evidence);
    }

    private StoredPaymentAttempt changed(StoredPaymentAttempt value, Changed changed) {
        var source = value.attempt();
        var modified = new PaymentAttempt(source.id(), changed == Changed.PAYMENT ? "other-payment" : source.paymentId(), source.attemptNo(),
                changed == Changed.AMOUNT ? Money.ofMinor(1001L, "CNY") : source.amount(), changed == Changed.MERCHANT ? route("other-merchant") : source.binding(),
                source.method(), source.channelRequestNo(), source.state(), source.executionEpoch(), source.noCollectionEvidenceId());
        return new StoredPaymentAttempt(modified, changed == Changed.OPERATION ? "other-operation" : value.limitOperationId(),
                changed == Changed.CALLER ? "other-caller" : value.callerServiceId(), value.requestKey(), value.requestDigest(),
                changed == Changed.PAYMENT_DIGEST ? "f".repeat(64) : value.paymentRequestDigest(), value.preparedInputRef(), value.riskDecision(),
                value.executionPermitHash(), value.preparedAt(), value.submissionStartedAt(), value.rowVersion());
    }

    enum Changed {PAYMENT, AMOUNT, MERCHANT, OPERATION, CALLER, PAYMENT_DIGEST}
}
