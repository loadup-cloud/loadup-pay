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
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDateTime;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutionContextTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 27, 12, 0);
    final Money amount = Money.ofMinor(1000L, "CNY");
    final ChannelBindingRevision route = new ChannelBindingRevision("revision", "merchant", Channel.ALIPAY, AccessMode.DIRECT,
            "a".repeat(64), "private-credential-reference", "v1", new AlipayAccountIdentity("app", "seller", null, null, null), Set.of(PaymentMethod.H5));
    final PaymentAttempt attempt = PaymentAttempt.init("attempt", "payment", 1, amount, route, PaymentMethod.H5, "original-request");
    final StoredPaymentAttempt prepared = new StoredPaymentAttempt(attempt, "operation", "acquire", "attempt-request", "b".repeat(64),
            "d".repeat(64), "private-input-reference", null, null, at, null, 0L);
    final StoredPaymentAttempt submitted = prepared.beginSubmission(1L, "e".repeat(64), RiskDecision.notConnected(), at.plusSeconds(1));
    final PaymentOrder payment = PaymentOrder.init("payment", "order", "allocation", "merchant", amount, "operation", at).prepareAttempt(attempt, null);
    final StoredPaymentAcceptance accepted = acceptance(payment, null);
    final StoredPaymentGate parent = gate(true);
    final StoredPaymentGate child = gate(false);

    enum Changed { PARENT_ORDER, PARENT_TARGET, PARENT_MERCHANT, PARENT_CALLER, PARENT_INIT,
                   PAYMENT_ORDER, PAYMENT_TARGET, PAYMENT_MERCHANT, PAYMENT_CALLER, PAYMENT_INIT }

    @ParameterizedTest @EnumSource(Changed.class)
    void eachGateBindingMismatchIsRejectedBeforeAnExecutionContextCanExist(Changed changed) {
        boolean parentSide = switch (changed) {
            case PARENT_ORDER, PARENT_TARGET, PARENT_MERCHANT, PARENT_CALLER, PARENT_INIT -> true;
            default -> false;
        };
        var original = parentSide ? parent : child;
        var order = switch (changed) { case PARENT_ORDER, PAYMENT_ORDER -> "other-order"; default -> original.gate().orderId(); };
        var target = switch (changed) { case PARENT_TARGET -> "payment"; case PAYMENT_TARGET -> "other-payment"; default -> original.gate().paymentId(); };
        var merchant = switch (changed) { case PARENT_MERCHANT, PAYMENT_MERCHANT -> "other-merchant"; default -> original.payeeMerchantId(); };
        var caller = switch (changed) { case PARENT_CALLER, PAYMENT_CALLER -> "other-caller"; default -> original.callerServiceId(); };
        var state = switch (changed) { case PARENT_INIT, PAYMENT_INIT -> GateState.INIT; default -> original.gate().state(); };
        var bad = new StoredPaymentGate(original.id(), merchant, caller, new PaymentExecutionGate(order, target, state, 0L), original.rowVersion());
        assertThrows(DomainViolation.class, () -> new PaymentGateBinding(accepted, parentSide ? bad : parent, parentSide ? child : bad));
    }

    @Test void onlyBothOpenGatesAndTheUnsubmittedActiveAttemptAllowTheFirstBoundary() {
        var binding = new PaymentGateBinding(accepted, parent, child);
        var context = new PaymentExecutionContext(binding, new PaymentAttemptBinding(accepted, prepared));
        assertSame(context, context.requireActive().requireSubmittable().requireClosable());
        assertThrows(DomainViolation.class, context::requireObservable);
        for (var gates : new PaymentGateBinding[] { new PaymentGateBinding(accepted, fenced(parent), child),
                new PaymentGateBinding(accepted, parent, fenced(child)), new PaymentGateBinding(accepted, fenced(parent), fenced(child)) }) {
            var blocked = new PaymentExecutionContext(gates, new PaymentAttemptBinding(accepted, prepared));
            assertThrows(DomainViolation.class, blocked::requireSubmittable);
            assertSame(blocked, blocked.requireClosable());
        }
        assertEquals(0L, prepared.attempt().executionEpoch());
        assertNull(prepared.executionPermitHash());
    }

    @Test void fencedAndTerminalOriginalFactsStayObservableWithoutBecomingSubmittableOrLocallyClosable() {
        var fenced = new PaymentGateBinding(accepted, fenced(parent), fenced(child));
        var original = new PaymentExecutionContext(fenced, new PaymentAttemptBinding(accepted, submitted));
        assertSame(original, original.requireObservable().requireClosable());
        assertThrows(DomainViolation.class, original::requireSubmittable);
        var successful = submitted.applyOutcome(new ChannelPaymentOutcome(ChannelPaymentOutcome.Kind.SUCCEEDED, attempt.identity(), amount, "evidence"), false);
        var terminal = acceptance(payment.applyAttempt(successful.attempt()), "evidence");
        var observed = new PaymentExecutionContext(new PaymentGateBinding(terminal, fenced(parent), fenced(child)), new PaymentAttemptBinding(terminal, successful));
        assertSame(observed, observed.requireObservable());
        assertThrows(DomainViolation.class, observed::requireSubmittable);
        assertThrows(DomainViolation.class, observed::requireClosable);
        var output = observed.toString();
        assertFalse(output.contains("private-frozen-payload"));
        assertFalse(output.contains("private-input-reference"));
        assertFalse(output.contains("private-credential-reference"));
        assertFalse(output.contains("e".repeat(64)));
    }

    @Test void matchingIdsCannotCombineDifferentPaymentSnapshots() {
        var differentVersion = new PaymentOrder(payment.id(), payment.orderId(), payment.allocationId(), payment.payeeMerchantId(), payment.amount(),
                payment.limitOperationId(), payment.initiatedAt(), payment.state(), payment.activeAttemptId(), payment.successfulAttemptId(), payment.version() + 1L);
        var later = acceptance(differentVersion, null);
        assertThrows(DomainViolation.class, () -> new PaymentExecutionContext(new PaymentGateBinding(accepted, parent, child),
                new PaymentAttemptBinding(later, submitted)));
    }

    private StoredPaymentGate gate(boolean parent) {
        return new StoredPaymentGate(parent ? "parent" : "gate", "merchant", "acquire", PaymentExecutionGate.init("order", parent ? null : "payment").open(), 0L);
    }
    private StoredPaymentGate fenced(StoredPaymentGate value) {
        return new StoredPaymentGate(value.id(), value.payeeMerchantId(), value.callerServiceId(), value.gate().fence(1L), value.rowVersion() + 1L);
    }
    private StoredPaymentAcceptance acceptance(PaymentOrder value, String evidence) {
        return new StoredPaymentAcceptance(value, "admission", "d".repeat(64), "acquire", "private-frozen-payload", "c".repeat(64), evidence);
    }
}
