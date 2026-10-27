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

import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.AlipayJsapiChannelAction;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelPaymentAction;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentActionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentActionGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAction;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.OUTCOME_UNKNOWN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentActionServiceTest {
    private static final String TENANT = "tenant";
    private static final String HASH = "d".repeat(64);
    private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.parse("2026-10-22T14:00:00Z");

    private final PaymentActionGateway actions = mock(PaymentActionGateway.class);
    private final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    private final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    private final PaymentAcceptanceConverter views = mock(PaymentAcceptanceConverter.class);
    private final PaymentChannelInputConverter channelViews = mock(PaymentChannelInputConverter.class);
    private final PaymentActionService service = new PaymentActionService(actions, attempts, codec, views, channelViews,
            Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()));

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void recordsOnlyTheFirstMatchingActionAndTreatsAnIdenticalActionAsIdempotent() {
        var fixture = fixture(PaymentMethod.JSAPI, 1, Money.ofMinor(4000, "CNY"));
        var saved = new AtomicReference<StoredPaymentAction>();
        when(actions.find(TENANT, "attempt")).thenAnswer(call -> Optional.ofNullable(saved.get()));
        doAnswer(call -> {
            saved.set(call.getArgument(1));
            return null;
        }).when(actions).insert(eq(TENANT), any());

        inTransaction(() -> service.record(TENANT, fixture.locked(), fixture.original(),
                submission(fixture, "trade-1", EXPIRES_AT), "receipt-1"));
        var first = saved.get();
        assertEquals(fixture.original().identity(), first.identity());
        assertEquals(fixture.original().amount(), first.amount());
        assertEquals(1, first.executionEpoch());
        assertEquals("receipt-1", first.receiptId());
        assertEquals(fixture.action(), first.action());

        inTransaction(() -> service.record(TENANT, fixture.locked(), fixture.original(),
                submission(fixture, "trade-1", EXPIRES_AT), "receipt-2"));

        assertSame(first, saved.get());
        verify(actions, org.mockito.Mockito.times(1)).insert(eq(TENANT), any());
    }

    @Test
    void rejectsASecondActionWithChangedTradeNumberOrExpiry() {
        var fixture = fixture(PaymentMethod.JSAPI, 1, Money.ofMinor(4000, "CNY"));
        var saved = new AtomicReference<>(storedAction(fixture, "trade-1", EXPIRES_AT));
        when(actions.find(TENANT, "attempt")).thenAnswer(call -> Optional.ofNullable(saved.get()));

        var changedTrade = submission(fixture, "trade-2", EXPIRES_AT);
        var tradeFailure = assertThrows(io.github.loadup.commons.error.CommonException.class,
                () -> inTransaction(() -> service.record(TENANT, fixture.locked(), fixture.original(), changedTrade, "receipt-2")));
        assertEquals(OUTCOME_UNKNOWN, tradeFailure.getResultCode());

        var changedExpiry = submission(fixture, "trade-1", EXPIRES_AT.plusMinutes(1));
        var expiryFailure = assertThrows(io.github.loadup.commons.error.CommonException.class,
                () -> inTransaction(() -> service.record(TENANT, fixture.locked(), fixture.original(), changedExpiry, "receipt-3")));
        assertEquals(OUTCOME_UNKNOWN, expiryFailure.getResultCode());
        verify(actions, never()).insert(anyString(), any());
    }

    @Test
    void validatesAttemptEpochAmountCallerAndMethodBeforeRecording() {
        var fixture = fixture(PaymentMethod.JSAPI, 1, Money.ofMinor(4000, "CNY"));
        when(actions.find(TENANT, "attempt")).thenReturn(Optional.empty());

        var wrongEpoch = fixture(PaymentMethod.JSAPI, 2, Money.ofMinor(4000, "CNY"));
        var epochFailure = assertThrows(io.github.loadup.commons.error.CommonException.class,
                () -> inTransaction(() -> service.record(TENANT, wrongEpoch.locked(), fixture.original(),
                        submission(fixture, "trade-1", EXPIRES_AT), "receipt-1")));
        assertEquals(OUTCOME_UNKNOWN, epochFailure.getResultCode());

        var wrongAmount = fixture(PaymentMethod.JSAPI, 1, Money.ofMinor(4000, "CNY"));
        var largerOriginal = prepared(wrongAmount, Money.ofMinor(4001, "CNY"), PaymentMethod.JSAPI, 1);
        var amountFailure = assertThrows(io.github.loadup.commons.error.CommonException.class,
                () -> inTransaction(() -> service.record(TENANT, wrongAmount.locked(), largerOriginal,
                        submission(wrongAmount, "trade-1", EXPIRES_AT, largerOriginal.amount()), "receipt-2")));
        assertEquals(OUTCOME_UNKNOWN, amountFailure.getResultCode());

        var wrongCaller = withAttemptCaller(fixture, "another-caller");
        var callerFailure = assertThrows(io.github.loadup.commons.error.CommonException.class,
                () -> inTransaction(() -> service.record(TENANT, wrongCaller.locked(), wrongCaller.original(),
                        submission(wrongCaller, "trade-1", EXPIRES_AT), "receipt-3")));
        assertEquals(SNAPSHOT_INVALID, callerFailure.getResultCode());

        var wrongMethod = fixture(PaymentMethod.PAYMENT_CODE, 1, Money.ofMinor(4000, "CNY"));
        var methodFailure = assertThrows(io.github.loadup.commons.error.CommonException.class,
                () -> inTransaction(() -> service.record(TENANT, wrongMethod.locked(), wrongMethod.original(),
                        submission(wrongMethod, "trade-1", EXPIRES_AT), "receipt-4")));
        assertEquals(INVALID_ARGUMENT, methodFailure.getResultCode());
        verify(actions, never()).insert(anyString(), any());
    }

    @Test
    void ignoresActionsForAnotherActiveAttemptExpiredActionsAndInactivePaymentsOrGates() {
        var fixture = fixture(PaymentMethod.JSAPI, 1, Money.ofMinor(4000, "CNY"));
        when(actions.find(anyString(), anyString())).thenReturn(Optional.empty());

        var anotherAttempt = withActiveAttempt(fixture, "new-attempt");
        inTransaction(() -> service.record(TENANT, anotherAttempt.locked(), fixture.original(),
                submission(fixture, "trade-1", EXPIRES_AT), "receipt-1"));

        inTransaction(() -> service.record(TENANT, fixture.locked(), fixture.original(),
                submission(fixture, "trade-2", OffsetDateTime.parse("2026-10-22T11:00:00Z")), "receipt-2"));

        var closing = acceptance(fixture.acceptance(), fixture.acceptance().payment().requestClose(), null);
        inTransaction(() -> service.record(TENANT, locked(closing, AttemptFixtures.gate(true), AttemptFixtures.gate(false)), fixture.original(),
                submission(fixture, "trade-3", EXPIRES_AT), "receipt-3"));

        var fencedGate = fenced(AttemptFixtures.gate(false));
        inTransaction(() -> service.record(TENANT, locked(fixture.acceptance().payment(), AttemptFixtures.gate(true), fencedGate),
                fixture.original(), submission(fixture, "trade-4", EXPIRES_AT), "receipt-4"));

        var closedPayment = terminalAcceptance(fixture.acceptance());
        inTransaction(() -> service.record(TENANT, locked(closedPayment, AttemptFixtures.gate(true), AttemptFixtures.gate(false)),
                fixture.original(), submission(fixture, "trade-5", EXPIRES_AT), "receipt-5"));

        verify(actions, never()).insert(anyString(), any());
    }

    @Test
    void presentReturnsTheCurrentUnexpiredActionAndHidesExpiredOrStaleActions() {
        var fixture = fixture(PaymentMethod.JSAPI, 1, Money.ofMinor(4000, "CNY"));
        var base = dto(null);
        var actionDto = new PaymentActionDTO(io.github.loadup.pay.payment.client.enums.PaymentActionType.INVOKE_JSAPI,
                EXPIRES_AT, null);
        var decorated = dto(actionDto);
        when(views.toDTO(fixture.acceptance())).thenReturn(base);
        when(channelViews.toAction(fixture.action())).thenReturn(actionDto);
        when(views.withAction(base, actionDto)).thenReturn(decorated);
        when(actions.find(TENANT, "attempt")).thenReturn(Optional.of(storedAction(fixture, "trade-1", EXPIRES_AT)));
        when(attempts.find(TENANT, "attempt")).thenReturn(Optional.of(fixture.storedAttempt()));
        when(codec.decode(fixture.acceptance())).thenReturn(fixture.command());

        assertSame(decorated, inTransaction(() -> service.present(TENANT, fixture.locked())));

        var expired = storedAction(fixture, "trade-1", OffsetDateTime.parse("2026-10-22T11:00:00Z"));
        when(actions.find(TENANT, "attempt")).thenReturn(Optional.of(expired));
        assertSame(base, inTransaction(() -> service.present(TENANT, fixture.locked())));

        var stale = withActiveAttempt(fixture, "new-attempt");
        when(views.toDTO(stale.acceptance())).thenReturn(base);
        assertSame(base, inTransaction(() -> service.present(TENANT, stale.locked())));
        verify(actions).find(TENANT, "new-attempt");
    }

    private static Fixture fixture(PaymentMethod method, long epoch, Money amount) {
        var command = PaymentFixtures.command();
        var binding = new ChannelBindingRevision("binding-revision", "merchant", Channel.ALIPAY, AccessMode.DIRECT,
                "a".repeat(64), "credential", "v1", new AlipayAccountIdentity("app", "seller", null, null, null),
                Set.of(PaymentMethod.JSAPI, PaymentMethod.PAYMENT_CODE));
        var attempt = new PaymentAttempt("attempt", "payment", 1, Money.ofMinor(4000, "CNY"), binding, method,
                "channel-request", PaymentAttemptState.SUBMITTING, epoch, null);
        var template = AttemptFixtures.prepared().beginSubmission(epoch, HASH, RiskDecision.notConnected(), AttemptFixtures.NOW);
        var storedAttempt = new StoredPaymentAttempt(attempt, template.limitOperationId(), template.callerServiceId(),
                template.requestKey(), template.requestDigest(), template.paymentRequestDigest(), template.preparedInputRef(),
                template.riskDecision(), template.executionPermitHash(), template.preparedAt(), template.submissionStartedAt(),
                template.rowVersion());
        var acceptance = AttemptFixtures.accepted(true);
        var locked = locked(acceptance.payment(), AttemptFixtures.gate(true), AttemptFixtures.gate(false));
        var original = prepared(new Fixture(acceptance, storedAttempt, locked, null, command, null), amount, method, 1);
        var action = new AlipayJsapiChannelAction("trade-1", EXPIRES_AT);
        return new Fixture(acceptance, storedAttempt, locked, original, command, action);
    }

    private static PreparedChannelPayment prepared(Fixture fixture, Money amount, PaymentMethod method, long epoch) {
        return new PreparedChannelPayment(fixture.storedAttempt().attempt().identity(), amount, method, epoch,
                fixture.storedAttempt().preparedInputRef());
    }

    private static ChannelPaymentSubmission submission(Fixture fixture, String tradeNo, OffsetDateTime expiry) {
        return submission(fixture, tradeNo, expiry, fixture.original().amount());
    }

    private static ChannelPaymentSubmission submission(Fixture fixture, String tradeNo, OffsetDateTime expiry, Money amount) {
        var action = new AlipayJsapiChannelAction(tradeNo, expiry);
        var observation = new PaymentObservation(fixture.original().identity(), ChannelPaymentOutcome.Kind.ACCEPTED,
                amount, tradeNo, null, PaymentObservation.Source.SUBMIT_RESPONSE, "receipt", "f".repeat(64));
        return new ChannelPaymentSubmission(fixture.original().identity(), amount, observation, action);
    }

    private static StoredPaymentAction storedAction(Fixture fixture, String tradeNo, OffsetDateTime expiry) {
        return new StoredPaymentAction(fixture.original().identity(), fixture.original().amount(),
                fixture.original().executionEpoch(), new AlipayJsapiChannelAction(tradeNo, expiry), "receipt-1",
                "f".repeat(64), AttemptFixtures.NOW);
    }

    private static Fixture withAttemptCaller(Fixture fixture, String caller) {
        var old = fixture.storedAttempt();
        var attempt = copyAttempt(old, old.attempt(), caller, old.paymentRequestDigest());
        return new Fixture(fixture.acceptance(), attempt, fixture.locked(), fixture.original(), fixture.command(), fixture.action());
    }

    private static Fixture withActiveAttempt(Fixture fixture, String activeAttemptId) {
        var old = fixture.acceptance();
        var prior = old.payment();
        var payment = new PaymentOrder(prior.id(), prior.orderId(), prior.allocationId(), prior.payeeMerchantId(), prior.amount(),
                prior.limitOperationId(), prior.initiatedAt(), prior.state(), activeAttemptId, null, prior.version());
        var acceptance = acceptance(old, payment, null);
        return new Fixture(acceptance, fixture.storedAttempt(), locked(payment, AttemptFixtures.gate(true), AttemptFixtures.gate(false)),
                fixture.original(), fixture.command(), fixture.action());
    }

    private static StoredPaymentAttempt copyAttempt(StoredPaymentAttempt source, PaymentAttempt attempt, String caller,
            String paymentRequestDigest) {
        return new StoredPaymentAttempt(attempt, source.limitOperationId(), caller, source.requestKey(), source.requestDigest(),
                paymentRequestDigest, source.preparedInputRef(), source.riskDecision(), source.executionPermitHash(),
                source.preparedAt(), source.submissionStartedAt(), source.rowVersion());
    }

    private static LockedPayment locked(PaymentOrder payment, StoredPaymentGate parent, StoredPaymentGate gate) {
        var source = AttemptFixtures.accepted(true);
        return PaymentExecutionBindings.locked(acceptance(source, payment, null), parent, gate);
    }

    private static LockedPayment locked(StoredPaymentAcceptance acceptance, StoredPaymentGate parent, StoredPaymentGate gate) {
        return PaymentExecutionBindings.locked(acceptance, parent, gate);
    }

    private static StoredPaymentAcceptance acceptance(StoredPaymentAcceptance source, PaymentOrder payment, String evidenceId) {
        return new StoredPaymentAcceptance(payment, source.admissionId(), source.requestDigest(), source.callerServiceId(),
                source.intentPayload(), source.payloadHash(), evidenceId);
    }

    private static StoredPaymentAcceptance terminalAcceptance(StoredPaymentAcceptance source) {
        var prior = source.payment();
        var closed = new PaymentOrder(prior.id(), prior.orderId(), prior.allocationId(), prior.payeeMerchantId(), prior.amount(),
                prior.limitOperationId(), prior.initiatedAt(), PaymentState.CLOSED, prior.activeAttemptId(), null,
                prior.version() + 1);
        return acceptance(source, closed, "terminal-proof");
    }

    private static StoredPaymentGate fenced(StoredPaymentGate source) {
        return new StoredPaymentGate(source.id(), source.payeeMerchantId(), source.callerServiceId(),
                source.gate().fence(1), source.rowVersion());
    }

    private static PaymentExecutionDTO dto(PaymentActionDTO action) {
        return new PaymentExecutionDTO("payment", "order", "allocation", 4000, "CNY",
                io.github.loadup.pay.payment.client.enums.PaymentState.PROCESSING, "attempt", action, null, 1,
                BusinessTime.external(AttemptFixtures.NOW));
    }

    private void inTransaction(Runnable work) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            TenantUtil.runWithTenant(TENANT, work);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            return TenantUtil.callWithTenant(TENANT, work::get);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    private record Fixture(StoredPaymentAcceptance acceptance, StoredPaymentAttempt storedAttempt, LockedPayment locked,
            PreparedChannelPayment original, PaymentExecuteCommand command, ChannelPaymentAction action) {}
}
