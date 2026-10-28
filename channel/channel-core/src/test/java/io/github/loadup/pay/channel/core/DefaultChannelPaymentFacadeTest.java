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
package io.github.loadup.pay.channel.core;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.resilience4j.ResilienceRegistries;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.AlipayJsapiChannelAction;
import io.github.loadup.pay.channel.api.model.ChannelAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelPaymentAction;
import io.github.loadup.pay.channel.api.model.ChannelPaymentCodeInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRecovery;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.pay.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.api.spi.ChannelProvider;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.RECOVERY_BUSY;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.RECOVERY_CONFIGURATION_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DefaultChannelPaymentFacadeTest {
    private static final Money AMOUNT = Money.ofMinor(1250, "CNY");
    private static final String HASH = "a".repeat(64);
    private static final OffsetDateTime PAYABLE_UNTIL = OffsetDateTime.parse("2026-10-23T12:00:00Z");

    private final ChannelProvider alipay = provider(Channel.ALIPAY);
    private final ChannelProvider wechat = provider(Channel.WECHAT);
    private final DefaultChannelPaymentFacade facade = new DefaultChannelPaymentFacade(List.of(alipay, wechat), concurrency());

    private static void assertBusy(Runnable operation) {
        assertEquals(RECOVERY_BUSY, assertThrows(CommonException.class, operation::run).getResultCode());
    }

    private static ResilienceRegistries limitedRegistries() {
        var registries = ResilienceRegistries.ofDefaults();
        for (var channel : Channel.values())
            registries.bulkheadRegistry().bulkhead(ChannelRecoveryConcurrency.instanceName(channel),
                    BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
        return registries;
    }

    private static ChannelProvider provider(Channel channel) {
        var provider = mock(ChannelProvider.class);
        when(provider.channel()).thenReturn(channel);
        return provider;
    }

    private static ChannelExecutionContext context(Channel channel, String bindingId, String hash) {
        ChannelAccountIdentity account = channel == Channel.ALIPAY
                ? new AlipayAccountIdentity("app", "seller", null, null, null)
                : new WechatAccountIdentity("app", "merchant", null, null);
        var binding = new ChannelBindingRevision(bindingId, "merchant", channel, AccessMode.DIRECT, hash,
                "credential", "1", account, Set.of(PaymentMethod.PAYMENT_CODE));
        return new ChannelExecutionContext("tenant", binding);
    }

    private static PreparedChannelPayment payment(Channel channel, String bindingId, String hash) {
        var identity = new ChannelPaymentIdentity("payment-1", "attempt-1", channel, bindingId, hash, "request-1");
        return new PreparedChannelPayment(identity, AMOUNT, PaymentMethod.PAYMENT_CODE, 1, "prepared-input");
    }

    private static PaymentObservation paymentObservation(ChannelPaymentIdentity identity, Money amount) {
        return new PaymentObservation(identity, ChannelPaymentOutcome.Kind.ACCEPTED, amount, null, null,
                PaymentObservation.Source.SUBMIT_RESPONSE, "receipt", "d".repeat(64));
    }

    private static ChannelPaymentSubmission submission(ChannelPaymentIdentity identity, Money amount) {
        return new ChannelPaymentSubmission(identity, amount, paymentObservation(identity, amount), null);
    }

    private static PaymentObservation actionObservation(ChannelPaymentIdentity identity, Money amount, String tradeNo) {
        return new PaymentObservation(identity, ChannelPaymentOutcome.Kind.ACCEPTED, amount, tradeNo, null,
                PaymentObservation.Source.SUBMIT_RESPONSE, "receipt", "f".repeat(64));
    }

    private static ChannelPaymentRequest request(PreparedChannelPayment payment) {
        return new ChannelPaymentRequest(payment, "subject", PAYABLE_UNTIL, new ChannelPaymentCodeInput("123456"));
    }

    private static ChannelPaymentValidation validation(ChannelPaymentIdentity identity) {
        return new ChannelPaymentValidation(identity, AMOUNT, PaymentMethod.PAYMENT_CODE, "subject", PAYABLE_UNTIL,
                new ChannelPaymentCodeInput("123456"));
    }

    private static ChannelPaymentRecovery recovery(ChannelPaymentIdentity identity) {
        return new ChannelPaymentRecovery(identity, AMOUNT, PaymentMethod.PAYMENT_CODE, 2, PAYABLE_UNTIL);
    }

    private static ChannelPaymentSubmission recovered(ChannelPaymentIdentity identity, Money amount,
                                                      PaymentObservation.Source source, ChannelPaymentAction action) {
        String tradeNo = action instanceof AlipayJsapiChannelAction alipay ? alipay.tradeNo() : null;
        var observation = new PaymentObservation(identity, ChannelPaymentOutcome.Kind.ACCEPTED, amount, tradeNo, null,
                source, "recovery-receipt", "f".repeat(64));
        return new ChannelPaymentSubmission(identity, amount, observation, action);
    }

    private static PreparedChannelRefund refund(ChannelPaymentIdentity original) {
        return new PreparedChannelRefund(original, "refund-1", "channel-refund-1", Money.ofMinor(500, "CNY"),
                AMOUNT, "channel-payment-1");
    }

    private static RefundObservation refundObservation(PreparedChannelRefund refund) {
        return new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(),
                refund.originalChannelTransactionNo(), ChannelRefundOutcome.Kind.ACCEPTED, refund.amount(), null,
                null, RefundObservation.Source.SUBMIT_RESPONSE, "receipt", "e".repeat(64), null);
    }

    private static ChannelRecoveryConcurrency concurrency() {
        return new ChannelRecoveryConcurrency(ResilienceRegistries.ofDefaults());
    }

    @Test
    void aSelectedProviderFailureDoesNotSubmitToAnotherChannel() {
        var context = context(Channel.WECHAT, "wx-binding", HASH);
        var request = request(payment(Channel.WECHAT, "wx-binding", HASH));
        var failure = new CommonException(
                io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID);
        when(wechat.submit(context, request)).thenThrow(failure);
        clearInvocations(alipay, wechat);

        TenantUtil.runWithTenant("tenant", () ->
                assertSame(failure, assertThrows(CommonException.class, () -> facade.submit(context, request))));

        verify(wechat).submit(context, request);
        verifyNoMoreInteractions(wechat);
        verifyNoInteractions(alipay);
    }

    @Test
    void registryOrderCannotOverrideTheRequestedChannel() {
        var reversed = new DefaultChannelPaymentFacade(List.of(wechat, alipay), concurrency());
        var context = context(Channel.ALIPAY, "ali-binding", HASH);
        var request = request(payment(Channel.ALIPAY, "ali-binding", HASH));
        var result = submission(request.payment().identity(), AMOUNT);
        when(alipay.submit(context, request)).thenReturn(result);
        clearInvocations(alipay, wechat);

        TenantUtil.runWithTenant("tenant", () -> assertSame(result, reversed.submit(context, request)));

        verify(alipay).submit(context, request);
        verifyNoMoreInteractions(alipay);
        verifyNoInteractions(wechat);
    }

    @Test
    void routesPaymentAndRefundOperationsByIdentityChannel() {
        var alipayContext = context(Channel.ALIPAY, "ali-binding", HASH);
        var wechatContext = context(Channel.WECHAT, "wx-binding", HASH);
        var aliPayment = payment(Channel.ALIPAY, "ali-binding", HASH);
        var wxPayment = payment(Channel.WECHAT, "wx-binding", HASH);
        var aliTarget = new ChannelPaymentTarget(aliPayment.identity(), AMOUNT);
        var wxTarget = new ChannelPaymentTarget(wxPayment.identity(), AMOUNT);
        var aliRequest = request(aliPayment);
        var wxRequest = request(wxPayment);
        when(alipay.submit(alipayContext, aliRequest)).thenReturn(submission(aliPayment.identity(), AMOUNT));
        when(wechat.submit(wechatContext, wxRequest)).thenReturn(submission(wxPayment.identity(), AMOUNT));
        when(alipay.query(alipayContext, aliTarget)).thenReturn(paymentObservation(aliPayment.identity(), AMOUNT));
        when(wechat.query(wechatContext, wxTarget)).thenReturn(paymentObservation(wxPayment.identity(), AMOUNT));
        when(alipay.close(alipayContext, aliTarget)).thenReturn(paymentObservation(aliPayment.identity(), AMOUNT));
        when(wechat.close(wechatContext, wxTarget)).thenReturn(paymentObservation(wxPayment.identity(), AMOUNT));

        var aliRefund = refund(aliPayment.identity());
        var wxRefund = refund(wxPayment.identity());
        var aliRefundResult = refundObservation(aliRefund);
        var wxRefundResult = refundObservation(wxRefund);
        when(alipay.canRefund(aliRefund)).thenReturn(true);
        when(wechat.canRefund(wxRefund)).thenReturn(true);
        when(alipay.submitRefund(alipayContext, aliRefund)).thenReturn(aliRefundResult);
        when(wechat.submitRefund(wechatContext, wxRefund)).thenReturn(wxRefundResult);
        when(alipay.queryRefund(alipayContext, aliRefund)).thenReturn(aliRefundResult);
        when(wechat.queryRefund(wechatContext, wxRefund)).thenReturn(wxRefundResult);

        TenantUtil.runWithTenant("tenant", () -> {
            assertEquals(aliPayment.identity(), facade.submit(alipayContext, aliRequest).identity());
            assertEquals(wxPayment.identity(), facade.submit(wechatContext, wxRequest).identity());
            facade.query(alipayContext, aliTarget);
            facade.query(wechatContext, wxTarget);
            facade.close(alipayContext, aliTarget);
            facade.close(wechatContext, wxTarget);
            assertTrue(facade.canRefund(aliRefund));
            assertTrue(facade.canRefund(wxRefund));
            facade.submitRefund(alipayContext, aliRefund);
            facade.submitRefund(wechatContext, wxRefund);
            facade.queryRefund(alipayContext, aliRefund);
            facade.queryRefund(wechatContext, wxRefund);
        });

        verify(alipay).submit(alipayContext, aliRequest);
        verify(wechat).submit(wechatContext, wxRequest);
        verify(alipay).query(alipayContext, aliTarget);
        verify(wechat).query(wechatContext, wxTarget);
        verify(alipay).close(alipayContext, aliTarget);
        verify(wechat).close(wechatContext, wxTarget);
        verify(alipay).submitRefund(alipayContext, aliRefund);
        verify(wechat).submitRefund(wechatContext, wxRefund);
        verify(alipay).queryRefund(alipayContext, aliRefund);
        verify(wechat).queryRefund(wechatContext, wxRefund);
    }

    @Test
    void rejectsDuplicateStrategiesAndTreatsMissingProviderAsUnsupported() {
        assertThrows(CommonException.class, () -> new DefaultChannelPaymentFacade(List.of(alipay, provider(Channel.ALIPAY)), concurrency()));
        var withoutWechat = new DefaultChannelPaymentFacade(List.of(alipay), concurrency());
        var wxRefund = refund(payment(Channel.WECHAT, "wx-binding", HASH).identity());
        var wxIdentity = wxRefund.original();
        assertFalse(withoutWechat.canRefund(wxRefund));
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class,
                    () -> withoutWechat.query(context(Channel.WECHAT, "wx-binding", HASH), new ChannelPaymentTarget(wxIdentity, AMOUNT)));
            assertThrows(CommonException.class,
                    () -> withoutWechat.validate(context(Channel.WECHAT, "wx-binding", HASH), validation(wxIdentity)));
        });
    }

    @Test
    void validationRoutesToTheProviderSelectedByFrozenIdentity() {
        var alipayContext = context(Channel.ALIPAY, "ali-binding", HASH);
        var wechatContext = context(Channel.WECHAT, "wx-binding", HASH);
        var alipayValidation = validation(payment(Channel.ALIPAY, "ali-binding", HASH).identity());
        var wechatValidation = validation(payment(Channel.WECHAT, "wx-binding", HASH).identity());
        clearInvocations(alipay, wechat);

        TenantUtil.runWithTenant("tenant", () -> {
            facade.validate(alipayContext, alipayValidation);
            facade.validate(wechatContext, wechatValidation);
        });

        verify(alipay).validate(alipayContext, alipayValidation);
        verify(wechat).validate(wechatContext, wechatValidation);
        verifyNoMoreInteractions(alipay, wechat);
    }

    @Test
    void recoveryRoutesByFrozenIdentityAndRejectsMissingOrMismatchedProviderBindings() {
        var alipayContext = context(Channel.ALIPAY, "ali-binding", HASH);
        var wechatContext = context(Channel.WECHAT, "wx-binding", HASH);
        var aliRecovery = recovery(payment(Channel.ALIPAY, "ali-binding", HASH).identity());
        var wxRecovery = recovery(payment(Channel.WECHAT, "wx-binding", HASH).identity());
        var aliResult = recovered(aliRecovery.identity(), AMOUNT, PaymentObservation.Source.QUERY_RESPONSE, null);
        var wxResult = recovered(wxRecovery.identity(), AMOUNT, PaymentObservation.Source.QUERY_RESPONSE, null);
        when(alipay.recover(alipayContext, aliRecovery)).thenReturn(aliResult);
        when(wechat.recover(wechatContext, wxRecovery)).thenReturn(wxResult);
        clearInvocations(alipay, wechat);

        TenantUtil.runWithTenant("tenant", () -> {
            assertSame(aliResult, facade.recover(alipayContext, aliRecovery));
            assertSame(wxResult, facade.recover(wechatContext, wxRecovery));
            assertThrows(CommonException.class,
                    () -> facade.recover(context(Channel.WECHAT, "wx-binding", HASH), aliRecovery));
            assertThrows(CommonException.class,
                    () -> facade.recover(context(Channel.ALIPAY, "wrong-revision", HASH), aliRecovery));
        });
        TenantUtil.runWithTenant("other-tenant", () -> assertThrows(CommonException.class,
                () -> facade.recover(alipayContext, aliRecovery)));

        verify(alipay).recover(alipayContext, aliRecovery);
        verify(wechat).recover(wechatContext, wxRecovery);
        verifyNoMoreInteractions(alipay, wechat);

        var withoutWechat = new DefaultChannelPaymentFacade(List.of(alipay), concurrency());
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> withoutWechat.recover(wechatContext, wxRecovery)));
    }

    @Test
    void recoveryRejectsInvalidIdentityAmountObservationSourceAndActionBounds() {
        var context = context(Channel.ALIPAY, "ali-binding", HASH);
        var request = recovery(payment(Channel.ALIPAY, "ali-binding", HASH).identity());
        var wrongIdentity = new ChannelPaymentIdentity("other-payment", request.identity().attemptId(), Channel.ALIPAY,
                request.identity().bindingRevisionId(), request.identity().accountScopeHash(), request.identity().channelRequestNo());
        when(alipay.recover(context, request)).thenReturn(recovered(wrongIdentity, AMOUNT,
                PaymentObservation.Source.QUERY_RESPONSE, null));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> facade.recover(context, request)));

        var wrongAmount = Money.ofMinor(2000, "CNY");
        when(alipay.recover(context, request)).thenReturn(recovered(request.identity(), wrongAmount,
                PaymentObservation.Source.QUERY_RESPONSE, null));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> facade.recover(context, request)));

        when(alipay.recover(context, request)).thenReturn(recovered(request.identity(), AMOUNT,
                PaymentObservation.Source.SUBMIT_RESPONSE, null));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> facade.recover(context, request)));

        var wrongMethodAction = new AlipayJsapiChannelAction("trade-1", request.payableUntil().minusMinutes(1));
        when(alipay.recover(context, request)).thenReturn(recovered(request.identity(), AMOUNT,
                PaymentObservation.Source.QUERY_RESPONSE, wrongMethodAction));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> facade.recover(context, request)));

        var lateAction = new AlipayJsapiChannelAction("trade-1", request.payableUntil().plusSeconds(1));
        when(alipay.recover(context, request)).thenReturn(recovered(request.identity(), AMOUNT,
                PaymentObservation.Source.QUERY_RESPONSE, lateAction));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> facade.recover(context, request)));
    }

    @Test
    void bindingAndTenantMismatchesAreRejectedBeforeProviderCalls() {
        clearInvocations(alipay, wechat);
        var identity = payment(Channel.ALIPAY, "ali-binding", HASH).identity();
        var target = new ChannelPaymentTarget(identity, AMOUNT);
        var validation = validation(identity);
        TenantUtil.runWithTenant("other-tenant", () -> assertThrows(CommonException.class,
                () -> facade.query(context(Channel.ALIPAY, "ali-binding", HASH), target)));
        TenantUtil.runWithTenant("other-tenant", () -> assertThrows(CommonException.class,
                () -> facade.validate(context(Channel.ALIPAY, "ali-binding", HASH), validation)));

        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> facade.query(context(Channel.WECHAT, "wx-binding", HASH), target));
            assertThrows(CommonException.class, () -> facade.query(context(Channel.ALIPAY, "wrong-revision", HASH), target));
            assertThrows(CommonException.class, () -> facade.query(context(Channel.ALIPAY, "ali-binding", "b".repeat(64)), target));
            assertThrows(CommonException.class, () -> facade.validate(context(Channel.WECHAT, "wx-binding", HASH), validation));
            assertThrows(CommonException.class, () -> facade.validate(context(Channel.ALIPAY, "wrong-revision", HASH), validation));
            assertThrows(CommonException.class, () -> facade.validate(context(Channel.ALIPAY, "ali-binding", "b".repeat(64)), validation));
        });
        verifyNoInteractions(alipay, wechat);
    }

    @Test
    void externalOperationsRejectActiveTransactionsWhileCanRefundStaysLocal() {
        clearInvocations(alipay, wechat);
        var context = context(Channel.ALIPAY, "ali-binding", HASH);
        var payment = payment(Channel.ALIPAY, "ali-binding", HASH);
        var target = new ChannelPaymentTarget(payment.identity(), AMOUNT);
        var refund = refund(payment.identity());
        when(alipay.canRefund(refund)).thenReturn(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> facade.submit(context, request(payment)));
            assertThrows(CommonException.class, () -> facade.query(context, target));
            assertThrows(CommonException.class, () -> facade.close(context, target));
            assertThrows(CommonException.class, () -> facade.submitRefund(context, refund));
            assertThrows(CommonException.class, () -> facade.queryRefund(context, refund));
            assertTrue(facade.canRefund(refund));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verify(alipay).canRefund(refund);
        verifyNoMoreInteractions(alipay);
    }

    @Test
    void rejectsProviderResponsesWithWrongPaymentIdentityOrAmountAndWrongRefundBindings() {
        var context = context(Channel.ALIPAY, "ali-binding", HASH);
        var payment = payment(Channel.ALIPAY, "ali-binding", HASH);
        var wrongIdentity = new ChannelPaymentIdentity("other-payment", payment.identity().attemptId(), Channel.ALIPAY,
                payment.identity().bindingRevisionId(), payment.identity().accountScopeHash(), payment.identity().channelRequestNo());
        var paymentRequest = request(payment);
        when(alipay.submit(context, paymentRequest)).thenReturn(submission(payment.identity(), Money.ofMinor(2000, "CNY")));
        var target = new ChannelPaymentTarget(payment.identity(), AMOUNT);
        when(alipay.query(context, target)).thenReturn(paymentObservation(wrongIdentity, AMOUNT));
        when(alipay.close(context, target)).thenReturn(paymentObservation(payment.identity(), Money.ofMinor(2000, "CNY")));
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> facade.submit(context, paymentRequest));
            assertThrows(CommonException.class, () -> facade.query(context, target));
            assertThrows(CommonException.class, () -> facade.close(context, target));
        });
        when(alipay.query(context, target)).thenReturn(paymentObservation(payment.identity(), Money.ofMinor(2000, "CNY")));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> facade.query(context, target)));

        var refund = refund(payment.identity());
        when(alipay.canRefund(refund)).thenReturn(true);
        when(alipay.submitRefund(context, refund)).thenReturn(new RefundObservation("other-refund", refund.original(),
                refund.channelRefundNo(), refund.originalChannelTransactionNo(), ChannelRefundOutcome.Kind.ACCEPTED,
                refund.amount(), null, null, RefundObservation.Source.SUBMIT_RESPONSE, "receipt", "c".repeat(64), null));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> facade.submitRefund(context, refund)));
    }

    @Test
    void rejectsSubmissionWithWrongIdentityOrActionMethodOrExpiry() {
        var context = context(Channel.ALIPAY, "ali-binding", HASH);
        var payment = payment(Channel.ALIPAY, "ali-binding", HASH);
        var paymentRequest = request(payment);
        var otherIdentity = new ChannelPaymentIdentity("other-payment", payment.identity().attemptId(), Channel.ALIPAY,
                payment.identity().bindingRevisionId(), payment.identity().accountScopeHash(), payment.identity().channelRequestNo());
        when(alipay.submit(context, paymentRequest)).thenReturn(submission(otherIdentity, AMOUNT));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> facade.submit(context, paymentRequest)));

        var jsapiRequest = paymentRequest;
        var wrongMethodAction = new AlipayJsapiChannelAction("trade-1", PAYABLE_UNTIL.minusMinutes(1));
        when(alipay.submit(context, jsapiRequest)).thenReturn(new ChannelPaymentSubmission(payment.identity(), AMOUNT,
                actionObservation(payment.identity(), AMOUNT, "trade-1"), wrongMethodAction));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> facade.submit(context, jsapiRequest)));

        var lateAction = new AlipayJsapiChannelAction("trade-2", PAYABLE_UNTIL.plusSeconds(1));
        when(alipay.submit(context, jsapiRequest)).thenReturn(new ChannelPaymentSubmission(payment.identity(), AMOUNT,
                actionObservation(payment.identity(), AMOUNT, "trade-2"), lateAction));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> facade.submit(context, jsapiRequest)));
    }

    @Test
    void refundRecoveryRoutesByOriginalIdentityAndAcceptsQueryOrNonterminalSubmitObservations() {
        var context = context(Channel.WECHAT, "wx-binding", HASH);
        var refund = refund(payment(Channel.WECHAT, "wx-binding", HASH).identity());
        when(wechat.canRefund(refund)).thenReturn(true);
        var submitted = refundObservation(refund);
        var queried = new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(), refund.originalChannelTransactionNo(),
                ChannelRefundOutcome.Kind.ACCEPTED, refund.amount(), null, null, RefundObservation.Source.QUERY_RESPONSE, "query", "e".repeat(64), null);
        when(wechat.recoverRefund(context, refund)).thenReturn(queried, submitted);
        TenantUtil.runWithTenant("tenant", () -> {
            assertSame(queried, facade.recoverRefund(context, refund));
            assertSame(submitted, facade.recoverRefund(context, refund));
        });
        verify(wechat, times(2)).recoverRefund(context, refund);
        verify(alipay, never()).recoverRefund(any(), any());
    }

    @Test
    void refundRecoveryRejectsCallbackAndTerminalSubmitFactsAndWrongAmounts() {
        var context = context(Channel.ALIPAY, "ali-binding", HASH);
        var refund = refund(payment(Channel.ALIPAY, "ali-binding", HASH).identity());
        when(alipay.canRefund(refund)).thenReturn(true);
        var callback = new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(), refund.originalChannelTransactionNo(),
                ChannelRefundOutcome.Kind.ACCEPTED, refund.amount(), null, null, RefundObservation.Source.CALLBACK, "callback", "e".repeat(64), null);
        var terminalSubmit = new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(), refund.originalChannelTransactionNo(),
                ChannelRefundOutcome.Kind.SUCCEEDED, refund.amount(), "vendor-refund", java.time.LocalDateTime.of(2026, 10, 24, 12, 0),
                RefundObservation.Source.SUBMIT_RESPONSE, "submit", "e".repeat(64), null);
        var wrongAmount = new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(), refund.originalChannelTransactionNo(),
                ChannelRefundOutcome.Kind.ACCEPTED, Money.ofMinor(1, "CNY"), null, null, RefundObservation.Source.QUERY_RESPONSE, "query", "e".repeat(64), null);
        when(alipay.recoverRefund(context, refund)).thenReturn(callback, terminalSubmit, wrongAmount);
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> facade.recoverRefund(context, refund));
            assertThrows(CommonException.class, () -> facade.recoverRefund(context, refund));
            assertThrows(CommonException.class, () -> facade.recoverRefund(context, refund));
        });
    }

    @Test
    void allRecoveryOperationsShareTheChannelBudgetBeforeProviderNetworkCalls() {
        var registries = limitedRegistries();
        var limited = new DefaultChannelPaymentFacade(List.of(alipay, wechat), new ChannelRecoveryConcurrency(registries));
        var context = context(Channel.WECHAT, "wx-binding", HASH);
        var identity = payment(Channel.WECHAT, "wx-binding", HASH).identity();
        var target = new ChannelPaymentTarget(identity, AMOUNT);
        var refund = refund(identity);
        when(wechat.canRefund(refund)).thenReturn(true);
        var budget = registries.bulkheadRegistry().bulkhead(ChannelRecoveryConcurrency.instanceName(Channel.WECHAT));
        assertTrue(budget.tryAcquirePermission());
        try {
            TenantUtil.runWithTenant("tenant", () -> {
                assertBusy(() -> limited.query(context, target));
                assertBusy(() -> limited.recover(context, recovery(identity)));
                assertBusy(() -> limited.close(context, target));
                assertBusy(() -> limited.queryRefund(context, refund));
                assertBusy(() -> limited.recoverRefund(context, refund));
            });
            TenantUtil.runWithTenant("other-tenant", () -> assertEquals(ACTION_FORBIDDEN,
                    assertThrows(CommonException.class, () -> limited.query(context, target)).getResultCode()));
            verify(wechat, never()).query(any(), any());
            verify(wechat, never()).recover(any(), any());
            verify(wechat, never()).close(any(), any());
            verify(wechat, never()).queryRefund(any(), any());
            verify(wechat, never()).recoverRefund(any(), any());
        } finally {
            budget.onComplete();
        }
    }

    @Test
    void firstSubmissionsAndLocalCapabilitiesCannotLoseTheirTicketToRecoverySaturation() {
        var registries = limitedRegistries();
        var limited = new DefaultChannelPaymentFacade(List.of(alipay, wechat), new ChannelRecoveryConcurrency(registries));
        var context = context(Channel.WECHAT, "wx-binding", HASH);
        var payment = payment(Channel.WECHAT, "wx-binding", HASH);
        var request = request(payment);
        var refund = refund(payment.identity());
        when(wechat.canRefund(refund)).thenReturn(true);
        when(wechat.submit(context, request)).thenReturn(submission(payment.identity(), AMOUNT));
        when(wechat.submitRefund(context, refund)).thenReturn(refundObservation(refund));
        var budget = registries.bulkheadRegistry().bulkhead(ChannelRecoveryConcurrency.instanceName(Channel.WECHAT));
        assertTrue(budget.tryAcquirePermission());
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertTrue(limited.canRefund(refund));
            } finally {
                TransactionSynchronizationManager.clear();
            }
            TenantUtil.runWithTenant("tenant", () -> {
                limited.validate(context, validation(payment.identity()));
                assertEquals(payment.identity(), limited.submit(context, request).identity());
                assertEquals(refund.refundId(), limited.submitRefund(context, refund).refundId());
            });
            assertEquals(0, budget.getMetrics().getAvailableConcurrentCalls());
            verify(wechat).submit(context, request);
            verify(wechat).submitRefund(context, refund);
        } finally {
            budget.onComplete();
        }
    }

    @Test
    void realConcurrentQueriesRejectExcessRecoveryWithoutBlockingAnotherChannel() throws Exception {
        var registries = limitedRegistries();
        var limited = new DefaultChannelPaymentFacade(List.of(alipay, wechat), new ChannelRecoveryConcurrency(registries));
        var wx = context(Channel.WECHAT, "wx-binding", HASH);
        var ali = context(Channel.ALIPAY, "ali-binding", HASH);
        var wxTarget = new ChannelPaymentTarget(payment(Channel.WECHAT, "wx-binding", HASH).identity(), AMOUNT);
        var aliTarget = new ChannelPaymentTarget(payment(Channel.ALIPAY, "ali-binding", HASH).identity(), AMOUNT);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(wechat.query(wx, wxTarget)).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(20, TimeUnit.SECONDS));
            return paymentObservation(wxTarget.identity(), AMOUNT);
        });
        when(alipay.query(ali, aliTarget)).thenReturn(paymentObservation(aliTarget.identity(), AMOUNT));
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> TenantUtil.callWithTenant("tenant", () -> limited.query(wx, wxTarget)));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                TenantUtil.runWithTenant("tenant", () -> {
                    assertBusy(() -> limited.recover(wx, recovery(wxTarget.identity())));
                    assertBusy(() -> limited.close(wx, wxTarget));
                    assertEquals(aliTarget.identity(), limited.query(ali, aliTarget).identity());
                });
            } finally {
                release.countDown();
            }
            assertEquals(wxTarget.identity(), first.get(20, TimeUnit.SECONDS).identity());
            TenantUtil.runWithTenant("tenant", () -> assertEquals(wxTarget.identity(), limited.query(wx, wxTarget).identity()));
            verify(wechat, times(2)).query(wx, wxTarget);
            verify(wechat, never()).recover(any(), any());
            verify(wechat, never()).close(any(), any());
        } finally {
            release.countDown();
        }
    }

    @Test
    void providerFailureAndInvalidResponseCannotLeakARecoveryPermit() {
        var registries = limitedRegistries();
        var limited = new DefaultChannelPaymentFacade(List.of(alipay, wechat), new ChannelRecoveryConcurrency(registries));
        var context = context(Channel.ALIPAY, "ali-binding", HASH);
        var target = new ChannelPaymentTarget(payment(Channel.ALIPAY, "ali-binding", HASH).identity(), AMOUNT);
        var failure = new CommonException(CHANNEL_SIGNATURE_INVALID);
        when(alipay.query(context, target)).thenThrow(failure);
        TenantUtil.runWithTenant("tenant", () -> assertSame(failure,
                assertThrows(CommonException.class, () -> limited.query(context, target))));
        var budget = registries.bulkheadRegistry().bulkhead(ChannelRecoveryConcurrency.instanceName(Channel.ALIPAY));
        assertEquals(1, budget.getMetrics().getAvailableConcurrentCalls());
        doReturn(paymentObservation(target.identity(), Money.ofMinor(1, "CNY"))).when(alipay).query(context, target);
        TenantUtil.runWithTenant("tenant", () -> assertEquals(CHANNEL_RESPONSE_INVALID,
                assertThrows(CommonException.class, () -> limited.query(context, target)).getResultCode()));
        assertEquals(1, budget.getMetrics().getAvailableConcurrentCalls());
        doReturn(paymentObservation(target.identity(), AMOUNT)).when(alipay).query(context, target);
        TenantUtil.runWithTenant("tenant", () -> assertEquals(target.identity(), limited.query(context, target).identity()));
    }

    @Test
    void blockingRecoveryBudgetsAreRejectedDuringAssembly() {
        var registries = ResilienceRegistries.ofDefaults();
        registries.bulkheadRegistry().bulkhead(ChannelRecoveryConcurrency.instanceName(Channel.ALIPAY),
                BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ofSeconds(1)).build());
        assertEquals(RECOVERY_CONFIGURATION_INVALID,
                assertThrows(CommonException.class, () -> new ChannelRecoveryConcurrency(registries)).getResultCode());
    }

}
