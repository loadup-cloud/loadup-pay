/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.components.payment.channel.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.components.payment.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.components.payment.channel.api.model.AlipayH5ChannelAction;
import io.github.loadup.components.payment.channel.api.model.AlipayJsapiChannelAction;
import io.github.loadup.components.payment.channel.api.model.ChannelBindingRevision;
import io.github.loadup.components.payment.channel.api.model.ChannelExecutionContext;
import io.github.loadup.components.payment.channel.api.model.ChannelNotificationHint;
import io.github.loadup.components.payment.channel.api.model.ChannelNotificationInput;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentCodeInput;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentRecovery;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.components.payment.channel.api.model.ChannelRefundNotificationHint;
import io.github.loadup.components.payment.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.components.payment.channel.api.model.ChannelStatementReceipt;
import io.github.loadup.components.payment.channel.api.model.ChannelStatementRequest;
import io.github.loadup.components.payment.channel.api.model.ChannelStatementScope;
import io.github.loadup.components.payment.channel.api.model.NoChannelInput;
import io.github.loadup.components.payment.channel.api.model.PaymentObservation;
import io.github.loadup.components.payment.channel.api.model.PreparedChannelPayment;
import io.github.loadup.components.payment.channel.api.model.PreparedChannelRefund;
import io.github.loadup.components.payment.channel.api.model.RefundObservation;
import io.github.loadup.components.payment.channel.api.model.VerifiedChannelRefundNotification;
import io.github.loadup.components.payment.channel.api.model.VerifiedChannelStatement;
import io.github.loadup.components.payment.channel.api.spi.ChannelNotificationProvider;
import io.github.loadup.components.payment.channel.api.spi.ChannelProvider;
import io.github.loadup.components.payment.channel.api.spi.ChannelStatementProvider;
import io.github.loadup.components.payment.channel.contract.AccessMode;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import io.github.loadup.components.resilience4j.ResilienceRegistries;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class FacadeEdgeCaseTest {
    private static final Money AMOUNT = Money.ofMinor(1250, "CNY");
    private static final String HASH = "a".repeat(64);
    private static final OffsetDateTime PAYABLE_UNTIL = OffsetDateTime.parse("2026-10-23T12:00:00Z");
    private static final ChannelPaymentIdentity IDENTITY =
            new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.ALIPAY, "revision-1", HASH, "request-1");

    private final ChannelProvider paymentProvider = paymentProvider();
    private final ChannelNotificationProvider notificationProvider = notificationProvider();
    private final ChannelStatementProvider statementProvider = statementProvider();
    private final DefaultChannelPaymentFacade paymentFacade =
            new DefaultChannelPaymentFacade(List.of(paymentProvider), new ChannelRecoveryConcurrency(registries()));
    private final DefaultChannelNotificationFacade notificationFacade =
            new DefaultChannelNotificationFacade(List.of(notificationProvider));
    private final DefaultChannelStatementFacade statementFacade =
            new DefaultChannelStatementFacade(List.of(statementProvider));
    private final ChannelExecutionContext context = context("revision-1", HASH);
    private final ChannelNotificationInput input =
            new ChannelNotificationInput("application/json", Map.of("X-Signature", List.of("value")), new byte[] {1});

    @Test
    void paymentFacadeRejectsNullArgumentsForEveryOperation() {
        var prepared = prepared(PaymentMethod.PAYMENT_CODE);
        var request =
                new ChannelPaymentRequest(prepared, "subject", PAYABLE_UNTIL, new ChannelPaymentCodeInput("code"));
        var validation = new ChannelPaymentValidation(
                IDENTITY,
                AMOUNT,
                PaymentMethod.PAYMENT_CODE,
                "subject",
                PAYABLE_UNTIL,
                new ChannelPaymentCodeInput("code"));
        var target = new ChannelPaymentTarget(IDENTITY, AMOUNT);
        var recovery = new ChannelPaymentRecovery(IDENTITY, AMOUNT, PaymentMethod.PAYMENT_CODE, 1, PAYABLE_UNTIL);
        var refund = refund();
        assertThrows(CommonException.class, () -> paymentFacade.validate(context, null));
        assertThrows(CommonException.class, () -> paymentFacade.submit(context, null));
        assertThrows(CommonException.class, () -> paymentFacade.query(context, null));
        assertThrows(CommonException.class, () -> paymentFacade.recover(context, null));
        assertThrows(CommonException.class, () -> paymentFacade.close(context, null));
        assertThrows(CommonException.class, () -> paymentFacade.canRefund(null));
        assertThrows(CommonException.class, () -> paymentFacade.submitRefund(context, null));
        assertThrows(CommonException.class, () -> paymentFacade.queryRefund(context, null));
        assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(context, null));
        assertFalse(paymentFacade.canRefund(refund));
        assertThrows(CommonException.class, () -> paymentFacade.validate(null, validation));
        assertThrows(CommonException.class, () -> paymentFacade.submit(null, request));
        assertThrows(CommonException.class, () -> paymentFacade.query(null, target));
        assertThrows(CommonException.class, () -> paymentFacade.recover(null, recovery));
        assertThrows(CommonException.class, () -> paymentFacade.close(null, target));
        assertThrows(CommonException.class, () -> paymentFacade.submitRefund(null, refund));
        assertThrows(CommonException.class, () -> paymentFacade.queryRefund(null, refund));
        assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(null, refund));
    }

    @Test
    void paymentFacadeRejectsTransactionAndBindingMismatchForEveryRoutedOperation() {
        var prepared = prepared(PaymentMethod.PAYMENT_CODE);
        var request =
                new ChannelPaymentRequest(prepared, "subject", PAYABLE_UNTIL, new ChannelPaymentCodeInput("code"));
        var validation = new ChannelPaymentValidation(
                IDENTITY,
                AMOUNT,
                PaymentMethod.PAYMENT_CODE,
                "subject",
                PAYABLE_UNTIL,
                new ChannelPaymentCodeInput("code"));
        var target = new ChannelPaymentTarget(IDENTITY, AMOUNT);
        var recovery = new ChannelPaymentRecovery(IDENTITY, AMOUNT, PaymentMethod.PAYMENT_CODE, 1, PAYABLE_UNTIL);
        var refund = refund();
        var wrongBinding = context("revision-2", HASH);
        var wrongHash = context("revision-1", "b".repeat(64));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> paymentFacade.validate(context, validation));
            assertThrows(CommonException.class, () -> paymentFacade.submit(context, request));
            assertThrows(CommonException.class, () -> paymentFacade.query(context, target));
            assertThrows(CommonException.class, () -> paymentFacade.recover(context, recovery));
            assertThrows(CommonException.class, () -> paymentFacade.close(context, target));
            assertThrows(CommonException.class, () -> paymentFacade.submitRefund(context, refund));
            assertThrows(CommonException.class, () -> paymentFacade.queryRefund(context, refund));
            assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(context, refund));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        for (var invalid : List.of(wrongBinding, wrongHash)) {
            assertThrows(CommonException.class, () -> paymentFacade.validate(invalid, validation));
            assertThrows(CommonException.class, () -> paymentFacade.submit(invalid, request));
            assertThrows(CommonException.class, () -> paymentFacade.query(invalid, target));
            assertThrows(CommonException.class, () -> paymentFacade.recover(invalid, recovery));
            assertThrows(CommonException.class, () -> paymentFacade.close(invalid, target));
            assertThrows(CommonException.class, () -> paymentFacade.submitRefund(invalid, refund));
            assertThrows(CommonException.class, () -> paymentFacade.queryRefund(invalid, refund));
            assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(invalid, refund));
        }
    }

    @Test
    void paymentFacadeRejectsUnsupportedCapabilityAndInvalidProviderResults() {
        var unsupported = new ChannelPaymentValidation(
                IDENTITY, AMOUNT, PaymentMethod.H5, "subject", PAYABLE_UNTIL, new NoChannelInput());
        assertThrows(CommonException.class, () -> paymentFacade.validate(context, unsupported));
        var request = new ChannelPaymentRequest(
                prepared(PaymentMethod.PAYMENT_CODE), "subject", PAYABLE_UNTIL, new ChannelPaymentCodeInput("code"));
        when(paymentProvider.submit(context, request)).thenReturn(null);
        assertThrows(CommonException.class, () -> paymentFacade.submit(context, request));
        var target = new ChannelPaymentTarget(IDENTITY, AMOUNT);
        when(paymentProvider.query(context, target)).thenReturn(null);
        assertThrows(CommonException.class, () -> paymentFacade.query(context, target));
        when(paymentProvider.close(context, target)).thenReturn(null);
        assertThrows(CommonException.class, () -> paymentFacade.close(context, target));
        var recovery = new ChannelPaymentRecovery(IDENTITY, AMOUNT, PaymentMethod.PAYMENT_CODE, 1, PAYABLE_UNTIL);
        when(paymentProvider.recover(context, recovery)).thenReturn(null);
        assertThrows(CommonException.class, () -> paymentFacade.recover(context, recovery));
    }

    @Test
    void paymentFacadeRejectsInvalidSubmitActionAndRefundResults() {
        var request = new ChannelPaymentRequest(
                prepared(PaymentMethod.PAYMENT_CODE), "subject", PAYABLE_UNTIL, new ChannelPaymentCodeInput("code"));
        var observation = acceptedObservation(IDENTITY);
        var wrongChannelAction = new AlipayH5ChannelAction(
                "https://openapi.alipay.com/gateway.do?app_id=app&method=alipay.trade.wap.pay&format=json"
                        + "&charset=utf-8&sign_type=RSA2&sign=AQ%3D%3D&timestamp=2026-10-23+18%3A00%3A00"
                        + "&version=1.0&biz_content=%7B%7D",
                PAYABLE_UNTIL.plusMinutes(1));
        when(paymentProvider.submit(context, request))
                .thenReturn(new ChannelPaymentSubmission(IDENTITY, AMOUNT, null, wrongChannelAction));
        assertThrows(CommonException.class, () -> paymentFacade.submit(context, request));
        var lowerAmount = Money.ofMinor(1000, "CNY");
        var lowerObservation = new PaymentObservation(
                IDENTITY,
                ChannelPaymentOutcome.Kind.ACCEPTED,
                lowerAmount,
                null,
                null,
                PaymentObservation.Source.QUERY_RESPONSE,
                "receipt-1",
                HASH);
        when(paymentProvider.submit(context, request))
                .thenReturn(new ChannelPaymentSubmission(IDENTITY, lowerAmount, lowerObservation, null));
        assertThrows(CommonException.class, () -> paymentFacade.submit(context, request));
        var refund = refund();
        when(paymentProvider.canRefund(refund)).thenReturn(true);
        when(paymentProvider.submitRefund(context, refund)).thenReturn(null);
        assertThrows(CommonException.class, () -> paymentFacade.submitRefund(context, refund));
        when(paymentProvider.queryRefund(context, refund)).thenReturn(null);
        assertThrows(CommonException.class, () -> paymentFacade.queryRefund(context, refund));
        when(paymentProvider.recoverRefund(context, refund)).thenReturn(null);
        assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(context, refund));
        when(paymentProvider.recoverRefund(context, refund))
                .thenReturn(new RefundObservation(
                        refund.refundId(),
                        refund.original(),
                        refund.channelRefundNo(),
                        refund.originalChannelTransactionNo(),
                        ChannelRefundOutcome.Kind.SUCCEEDED,
                        refund.amount(),
                        "refund-tx",
                        LocalDateTime.now(),
                        RefundObservation.Source.SUBMIT_RESPONSE,
                        "receipt",
                        HASH,
                        null));
        assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(context, refund));
    }

    @Test
    void paymentFacadeCoversUnsupportedSubmitRecoverExpiryAndRefundBranches() {
        var h5Prepared = prepared(PaymentMethod.H5);
        var h5Request = new ChannelPaymentRequest(h5Prepared, "subject", PAYABLE_UNTIL, new NoChannelInput());
        assertThrows(CommonException.class, () -> paymentFacade.submit(context, h5Request));
        var expiryAction = new AlipayH5ChannelAction(alipayH5Url(), PAYABLE_UNTIL.plusMinutes(1));
        when(paymentProvider.submit(context, h5Request))
                .thenReturn(new ChannelPaymentSubmission(IDENTITY, AMOUNT, null, expiryAction));
        assertThrows(CommonException.class, () -> paymentFacade.submit(context, h5Request));
        var recover = new ChannelPaymentRecovery(IDENTITY, AMOUNT, PaymentMethod.H5, 1, PAYABLE_UNTIL);
        when(paymentProvider.recover(context, recover))
                .thenReturn(new ChannelPaymentSubmission(IDENTITY, AMOUNT, null, expiryAction));
        assertThrows(CommonException.class, () -> paymentFacade.recover(context, recover));
        var jsapiRecovery = new ChannelPaymentRecovery(IDENTITY, AMOUNT, PaymentMethod.JSAPI, 1, PAYABLE_UNTIL);
        var jsapiObservation = acceptedObservation(IDENTITY, "trade-1");
        var expiredJsapi = new AlipayJsapiChannelAction("trade-1", PAYABLE_UNTIL.plusMinutes(1));
        when(paymentProvider.recover(context, jsapiRecovery))
                .thenReturn(new ChannelPaymentSubmission(IDENTITY, AMOUNT, jsapiObservation, expiredJsapi));
        assertThrows(CommonException.class, () -> paymentFacade.recover(context, jsapiRecovery));
        var refund = refund();
        when(paymentProvider.canRefund(refund)).thenReturn(false);
        assertThrows(CommonException.class, () -> paymentFacade.submitRefund(context, refund));
        assertThrows(CommonException.class, () -> paymentFacade.queryRefund(context, refund));
        assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(context, refund));
        when(paymentProvider.canRefund(refund)).thenReturn(true);
        var failed = new RefundObservation(
                refund.refundId(),
                refund.original(),
                refund.channelRefundNo(),
                refund.originalChannelTransactionNo(),
                ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND,
                refund.amount(),
                null,
                null,
                RefundObservation.Source.SUBMIT_RESPONSE,
                "receipt",
                HASH,
                null);
        when(paymentProvider.recoverRefund(context, refund)).thenReturn(failed);
        assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(context, refund));
        var wrongOriginal = new RefundObservation(
                refund.refundId(),
                wrongIdentity(),
                refund.channelRefundNo(),
                refund.originalChannelTransactionNo(),
                ChannelRefundOutcome.Kind.ACCEPTED,
                refund.amount(),
                null,
                null,
                RefundObservation.Source.SUBMIT_RESPONSE,
                "receipt",
                HASH,
                null);
        when(paymentProvider.submitRefund(context, refund)).thenReturn(wrongOriginal);
        assertThrows(CommonException.class, () -> paymentFacade.submitRefund(context, refund));
        var wrongRefundNo = new RefundObservation(
                refund.refundId(),
                refund.original(),
                "other-refund-no",
                refund.originalChannelTransactionNo(),
                ChannelRefundOutcome.Kind.ACCEPTED,
                refund.amount(),
                null,
                null,
                RefundObservation.Source.SUBMIT_RESPONSE,
                "receipt",
                HASH,
                null);
        when(paymentProvider.queryRefund(context, refund)).thenReturn(wrongRefundNo);
        assertThrows(CommonException.class, () -> paymentFacade.queryRefund(context, refund));
        var wrongTransactionNo = new RefundObservation(
                refund.refundId(),
                refund.original(),
                refund.channelRefundNo(),
                "other-trade",
                ChannelRefundOutcome.Kind.ACCEPTED,
                refund.amount(),
                null,
                null,
                RefundObservation.Source.SUBMIT_RESPONSE,
                "receipt",
                HASH,
                null);
        when(paymentProvider.recoverRefund(context, refund)).thenReturn(wrongTransactionNo);
        assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(context, refund));
        var wrongAmount = new RefundObservation(
                refund.refundId(),
                refund.original(),
                refund.channelRefundNo(),
                refund.originalChannelTransactionNo(),
                ChannelRefundOutcome.Kind.ACCEPTED,
                Money.ofMinor(499, "CNY"),
                null,
                null,
                RefundObservation.Source.SUBMIT_RESPONSE,
                "receipt",
                HASH,
                null);
        when(paymentProvider.recoverRefund(context, refund)).thenReturn(wrongAmount);
        assertThrows(CommonException.class, () -> paymentFacade.recoverRefund(context, refund));
    }

    @Test
    void notificationFacadeRejectsNullBindingAndInvalidResults() {
        assertThrows(CommonException.class, () -> notificationFacade.identify(null, input));
        assertThrows(CommonException.class, () -> notificationFacade.identify(context, null));
        assertThrows(CommonException.class, () -> notificationFacade.verify(null, target(), input));
        assertThrows(CommonException.class, () -> notificationFacade.verify(context, null, input));
        assertThrows(CommonException.class, () -> notificationFacade.verify(context, target(), null));
        assertThrows(CommonException.class, () -> notificationFacade.identifyRefund(null, input));
        assertThrows(CommonException.class, () -> notificationFacade.identifyRefund(context, null));
        assertThrows(CommonException.class, () -> notificationFacade.verifyRefund(null, refund(), input));
        assertThrows(CommonException.class, () -> notificationFacade.verifyRefund(context, null, input));
        assertThrows(CommonException.class, () -> notificationFacade.verifyRefund(context, refund(), null));
        assertThrows(CommonException.class, () -> notificationFacade.identify(context, input));
        assertThrows(CommonException.class, () -> notificationFacade.verify(context, target(), input));
        assertThrows(CommonException.class, () -> notificationFacade.identifyRefund(context, input));
        assertThrows(CommonException.class, () -> notificationFacade.verifyRefund(context, refund(), input));
        assertThrows(CommonException.class, () -> notificationFacade.acknowledgement(Channel.WECHAT, true));
        when(notificationProvider.identify(context, input)).thenReturn(null);
        assertThrows(CommonException.class, () -> notificationFacade.identify(context, input));
        when(notificationProvider.identifyRefund(context, input)).thenReturn(null);
        assertThrows(CommonException.class, () -> notificationFacade.identifyRefund(context, input));
        when(notificationProvider.acknowledgement(Channel.ALIPAY, true)).thenReturn(null);
        assertThrows(CommonException.class, () -> notificationFacade.acknowledgement(Channel.ALIPAY, true));
        when(notificationProvider.identify(context, input)).thenReturn(new ChannelNotificationHint("request-1"));
        when(notificationProvider.verify(context, target(), input)).thenReturn(null);
        assertThrows(CommonException.class, () -> notificationFacade.verify(context, target(), input));
        when(notificationProvider.verifyRefund(context, refund(), input)).thenReturn(null);
        assertThrows(CommonException.class, () -> notificationFacade.verifyRefund(context, refund(), input));
    }

    @Test
    void statementFacadeRejectsEachProvenanceMismatchAndAcceptsMatchingBytes() throws Exception {
        var scope = statementScope();
        var content = "source".getBytes();
        var request = new ChannelStatementRequest(scope, 1024);
        when(statementProvider.download(context, request))
                .thenReturn(statement("other-revision", scope, content, sha(content), HASH));
        assertThrows(CommonException.class, () -> statementFacade.download(context, request));
        var otherScope = new ChannelStatementScope(Channel.ALIPAY, HASH, LocalDate.of(2026, 10, 24), "profile");
        when(statementProvider.download(context, request))
                .thenReturn(statement("revision-1", otherScope, content, sha(content), HASH));
        assertThrows(CommonException.class, () -> statementFacade.download(context, request));
        when(statementProvider.download(context, request))
                .thenReturn(statement("revision-1", scope, content, "b".repeat(64), HASH));
        assertThrows(CommonException.class, () -> statementFacade.download(context, request));
        when(statementProvider.download(context, request))
                .thenReturn(statement("revision-1", scope, content, sha(content), "b".repeat(64)));
        assertThrows(CommonException.class, () -> statementFacade.download(context, request));
        var tooSmall = new ChannelStatementRequest(scope, 1);
        when(statementProvider.download(context, tooSmall))
                .thenReturn(statement("revision-1", scope, content, sha(content), HASH));
        assertThrows(CommonException.class, () -> statementFacade.download(context, tooSmall));
        var matching = statement("revision-1", scope, content, sha(content), sha(new byte[] {1}));
        when(statementProvider.download(context, request)).thenReturn(matching);
        assertEquals(matching, statementFacade.download(context, request));
    }

    @Test
    void notificationFacadeRejectsTransactionsBindingMismatchAndResultMismatch() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> notificationFacade.identify(context, input));
            assertThrows(CommonException.class, () -> notificationFacade.verify(context, target(), input));
            assertThrows(CommonException.class, () -> notificationFacade.identifyRefund(context, input));
            assertThrows(CommonException.class, () -> notificationFacade.verifyRefund(context, refund(), input));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        var wrongBinding = context("revision-2", HASH);
        assertThrows(CommonException.class, () -> notificationFacade.verify(wrongBinding, target(), input));
        assertThrows(CommonException.class, () -> notificationFacade.verifyRefund(wrongBinding, refund(), input));
        var refundHint = new ChannelRefundNotificationHint("request-1", "refund-1");
        when(notificationProvider.identifyRefund(context, input)).thenReturn(refundHint);
        assertEquals(refundHint, notificationFacade.identifyRefund(context, input));
        var wrongRefund = new PreparedChannelRefund(
                IDENTITY, "refund-2", "refund-no", Money.ofMinor(500, "CNY"), AMOUNT, "trade-1");
        var wrongNotification =
                new VerifiedChannelRefundNotification(wrongRefund, "notification-1", "refund-tx", "SUCCESS", HASH);
        when(notificationProvider.verifyRefund(context, refund(), input)).thenReturn(wrongNotification);
        assertThrows(CommonException.class, () -> notificationFacade.verifyRefund(context, refund(), input));
        var correct = new VerifiedChannelRefundNotification(refund(), "notification-1", "refund-tx", "SUCCESS", HASH);
        when(notificationProvider.verifyRefund(context, refund(), input)).thenReturn(correct);
        assertEquals(correct, notificationFacade.verifyRefund(context, refund(), input));
    }

    @Test
    void statementFacadeRejectsNullContextRequestScopeAndInvalidResults() {
        var request = new ChannelStatementRequest(statementScope(), 1024);
        assertThrows(CommonException.class, () -> statementFacade.download(null, request));
        assertThrows(CommonException.class, () -> statementFacade.download(context, null));
        assertThrows(CommonException.class, () -> statementFacade.download(context, request));
        var wrongChannel = new ChannelStatementRequest(
                new ChannelStatementScope(Channel.WECHAT, HASH, LocalDate.of(2026, 10, 23), "profile"), 1024);
        assertThrows(CommonException.class, () -> statementFacade.download(context, wrongChannel));
        var wrongHash = new ChannelStatementRequest(
                new ChannelStatementScope(Channel.ALIPAY, "b".repeat(64), LocalDate.of(2026, 10, 23), "profile"), 1024);
        assertThrows(CommonException.class, () -> statementFacade.download(context, wrongHash));
        when(statementProvider.download(context, request)).thenReturn(null);
        assertThrows(CommonException.class, () -> statementFacade.download(context, request));
    }

    private static ChannelExecutionContext context(String revision, String hash) {
        var account = new AlipayAccountIdentity("app", "seller", null, null, null);
        return new ChannelExecutionContext(new ChannelBindingRevision(
                revision,
                "merchant",
                Channel.ALIPAY,
                AccessMode.DIRECT,
                hash,
                "credential",
                "1",
                account,
                Set.of(PaymentMethod.PAYMENT_CODE)));
    }

    private static PreparedChannelPayment prepared(PaymentMethod method) {
        return new PreparedChannelPayment(IDENTITY, AMOUNT, method, 1, "input");
    }

    private static PreparedChannelRefund refund() {
        return new PreparedChannelRefund(
                IDENTITY, "refund-1", "refund-no", Money.ofMinor(500, "CNY"), AMOUNT, "trade-1");
    }

    private static ChannelPaymentTarget target() {
        return new ChannelPaymentTarget(IDENTITY, AMOUNT);
    }

    private static PaymentObservation acceptedObservation(ChannelPaymentIdentity identity) {
        return acceptedObservation(identity, null);
    }

    private static PaymentObservation acceptedObservation(ChannelPaymentIdentity identity, String transactionNo) {
        return new PaymentObservation(
                identity,
                ChannelPaymentOutcome.Kind.ACCEPTED,
                AMOUNT,
                transactionNo,
                null,
                PaymentObservation.Source.QUERY_RESPONSE,
                "receipt-1",
                HASH);
    }

    private static ChannelPaymentIdentity wrongIdentity() {
        return new ChannelPaymentIdentity(
                "other-payment", "other-attempt", Channel.ALIPAY, "other-revision", HASH, "other-request");
    }

    private static String alipayH5Url() {
        return "https://openapi.alipay.com/gateway.do?app_id=app&method=alipay.trade.wap.pay&format=json"
                + "&charset=utf-8&sign_type=RSA2&sign=AQ%3D%3D&timestamp=2026-10-23+18%3A00%3A00"
                + "&version=1.0&biz_content=%7B%7D";
    }

    private static ChannelStatementScope statementScope() {
        return new ChannelStatementScope(Channel.ALIPAY, HASH, LocalDate.of(2026, 10, 23), "profile");
    }

    private static VerifiedChannelStatement statement(
            String bindingId, ChannelStatementScope scope, byte[] content, String contentHash, String evidenceHash) {
        var at = OffsetDateTime.parse("2026-10-28T12:00:00+08:00");
        return new VerifiedChannelStatement(
                bindingId,
                scope,
                new ChannelStatementReceipt(
                        "csv", HASH, evidenceHash, "SHA1", "a", contentHash, at, at, new byte[] {1}),
                content);
    }

    private static String sha(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static ChannelProvider paymentProvider() {
        var provider = mock(ChannelProvider.class);
        when(provider.channel()).thenReturn(Channel.ALIPAY);
        return provider;
    }

    private static ChannelNotificationProvider notificationProvider() {
        var provider = mock(ChannelNotificationProvider.class);
        when(provider.channel()).thenReturn(Channel.ALIPAY);
        return provider;
    }

    private static ChannelStatementProvider statementProvider() {
        var provider = mock(ChannelStatementProvider.class);
        when(provider.channel()).thenReturn(Channel.ALIPAY);
        return provider;
    }

    private static ResilienceRegistries registries() {
        var registries = ResilienceRegistries.ofDefaults();
        for (var channel : Channel.values()) {
            registries
                    .bulkheadRegistry()
                    .bulkhead(
                            ChannelRecoveryConcurrency.instanceName(channel),
                            BulkheadConfig.custom()
                                    .maxConcurrentCalls(1)
                                    .maxWaitDuration(Duration.ZERO)
                                    .build());
        }
        return registries;
    }
}
