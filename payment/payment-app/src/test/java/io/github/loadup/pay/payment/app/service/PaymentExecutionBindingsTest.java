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
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.domain.model.PaymentAttemptBinding.ObservationRejection.OBSERVATION_BEFORE_SUBMISSION;
import static io.github.loadup.pay.payment.domain.model.PaymentAttemptBinding.ObservationRejection.PAYMENT_IDENTITY_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentExecutionBindingsTest {
    @ParameterizedTest
    @EnumSource(Changed.class)
    void eachSharedMismatchUsesSnapshotFailureExceptForTheOriginalSubmissionRequestBoundary(Changed changed) {
        var accepted = AttemptFixtures.accepted(true);
        var prepared = changed(AttemptFixtures.prepared(), changed);
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> PaymentExecutionBindings.bound(accepted, prepared)).getResultCode());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> PaymentExecutionBindings.active(accepted, prepared)).getResultCode());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> PaymentExecutionBindings.submittable(accepted, prepared)).getResultCode());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> PaymentExecutionBindings.closable(accepted, prepared)).getResultCode());
        var submitted = prepared.beginSubmission(1L, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> PaymentExecutionBindings.observable(accepted, submitted)).getResultCode());
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> PaymentExecutionBindings.submissionRequest(accepted, submitted)).getResultCode());
    }

    @Test
    void observationConflictIsDataForTheExistingAuditPathRatherThanAnIdempotencyException() {
        var accepted = AttemptFixtures.accepted(true);
        var prepared = AttemptFixtures.prepared();
        var submitted = prepared.beginSubmission(1L, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        var identity = submitted.attempt().identity();
        var wrong = new ChannelPaymentIdentity(identity.paymentId(), identity.attemptId(), identity.channel(), identity.bindingRevisionId(),
                identity.accountScopeHash(), "wrong-request");
        var corrupt = changed(submitted, Changed.PAYMENT_DIGEST);
        assertEquals(PAYMENT_IDENTITY_MISMATCH, PaymentExecutionBindings.observationRejection(accepted, corrupt, wrong, submitted.attempt().amount()));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> PaymentExecutionBindings.observationRejection(accepted, corrupt, identity, submitted.attempt().amount())).getResultCode());
        assertEquals(OBSERVATION_BEFORE_SUBMISSION, PaymentExecutionBindings.observationRejection(accepted, prepared, identity, prepared.attempt().amount()));
        assertNull(PaymentExecutionBindings.observationRejection(accepted, submitted, identity, submitted.attempt().amount()));
    }

    @Test
    void pureBindingDoesNotProduceAReplacementY2WinnerAndRouteMismatchStaysSnapshotInvalid() {
        var accepted = AttemptFixtures.accepted(true);
        var prepared = AttemptFixtures.prepared();
        var binding = PaymentExecutionBindings.submittable(accepted, prepared);
        assertSame(prepared, binding.stored());
        assertEquals(0L, prepared.attempt().executionEpoch());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> PaymentExecutionBindings.route(binding,
                prepared.attempt().binding().channel(), prepared.attempt().method(), "other-input")).getResultCode());
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> PaymentExecutionBindings.submissionRequest(accepted, prepared)).getResultCode());
    }

    @Test
    void gateFailuresAndModeFailuresKeepTheirDistinctPaymentErrorCodes() {
        var accepted = AttemptFixtures.accepted(true);
        var prepared = AttemptFixtures.prepared();
        var parent = AttemptFixtures.gate(true);
        var child = AttemptFixtures.gate(false);
        var fencedParent = new StoredPaymentGate(parent.id(), parent.payeeMerchantId(), parent.callerServiceId(), parent.gate().fence(1L), 1L);
        var locked = PaymentExecutionBindings.locked(accepted, fencedParent, child);
        assertEquals(EXECUTION_FENCED, assertThrows(CommonException.class, () -> PaymentExecutionBindings.openGates(locked)).getResultCode());
        assertEquals(EXECUTION_FENCED, assertThrows(CommonException.class,
                () -> PaymentExecutionBindings.submittableContext(locked, prepared)).getResultCode());
        var submitted = prepared.beginSubmission(1L, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        assertSame(locked.binding(), PaymentExecutionBindings.observableContext(locked, submitted).gates());
        assertSame(locked.binding(), PaymentExecutionBindings.closableContext(locked, submitted).gates());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> PaymentExecutionBindings.submittableContext(locked, changed(prepared, Changed.CALLER))).getResultCode());
        var wrongMerchant = new StoredPaymentGate(child.id(), "other-merchant", child.callerServiceId(), child.gate(), child.rowVersion());
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> PaymentExecutionBindings.locked(accepted, parent, wrongMerchant)).getResultCode());
        assertEquals(0L, prepared.attempt().executionEpoch());
    }

    private StoredPaymentAttempt changed(StoredPaymentAttempt value, Changed changed) {
        var source = value.attempt();
        var route = source.binding();
        if (changed == Changed.MERCHANT)
            route = new ChannelBindingRevision(route.id(), "other-merchant", route.channel(), route.accessMode(),
                    route.accountScopeHash(), route.credentialReference(), route.credentialVersion(), route.identity(), route.capabilities());
        var modified = new PaymentAttempt(source.id(), changed == Changed.PAYMENT ? "other-payment" : source.paymentId(), source.attemptNo(),
                changed == Changed.AMOUNT ? Money.ofMinor(source.amount().getCent() + 1L, "CNY") : source.amount(), route,
                source.method(), source.channelRequestNo(), source.state(), source.executionEpoch(), source.noCollectionEvidenceId());
        return new StoredPaymentAttempt(modified, changed == Changed.OPERATION ? "other-operation" : value.limitOperationId(),
                changed == Changed.CALLER ? "other-caller" : value.callerServiceId(), value.requestKey(), value.requestDigest(),
                changed == Changed.PAYMENT_DIGEST ? "f".repeat(64) : value.paymentRequestDigest(), value.preparedInputRef(), value.riskDecision(),
                value.executionPermitHash(), value.preparedAt(), value.submissionStartedAt(), value.rowVersion());
    }

    enum Changed {PAYMENT, AMOUNT, MERCHANT, OPERATION, CALLER, PAYMENT_DIGEST}
}
