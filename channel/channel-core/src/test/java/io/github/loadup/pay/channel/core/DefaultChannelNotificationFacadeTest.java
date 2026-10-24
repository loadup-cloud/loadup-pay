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
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.api.spi.ChannelNotificationProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultChannelNotificationFacadeTest {
    private static final Money AMOUNT = Money.ofMinor(125_000, "CNY");
    private static final String HASH = "a".repeat(64);
    private static final String ORDER_NO = "order-request-18";

    private final ChannelNotificationProvider alipay = provider(Channel.ALIPAY);
    private final ChannelNotificationProvider wechat = provider(Channel.WECHAT);
    private final DefaultChannelNotificationFacade facade = new DefaultChannelNotificationFacade(List.of(alipay, wechat));
    private final ChannelNotificationInput input = new ChannelNotificationInput("application/x-www-form-urlencoded",
            Map.of(), "notify=untrusted".getBytes(StandardCharsets.US_ASCII));

    @Test
    void routesIdentifyVerifyAndAcknowledgementToTheSelectedStrategy() {
        var context = context(Channel.ALIPAY, "revision-1", HASH);
        var target = target(Channel.ALIPAY, "revision-1", HASH, ORDER_NO, AMOUNT);
        var hint = new ChannelNotificationHint(ORDER_NO);
        var verified = verified(target.identity(), AMOUNT);
        var acknowledgement = new ChannelNotificationAcknowledgement(200, "text/plain; charset=utf-8", "success");
        var rejectedAcknowledgement = new ChannelNotificationAcknowledgement(500, "text/plain; charset=utf-8", "failure");
        when(alipay.identify(context, input)).thenReturn(hint);
        when(alipay.verify(context, target, input)).thenReturn(verified);
        when(alipay.acknowledgement(Channel.ALIPAY, true)).thenReturn(acknowledgement);
        when(alipay.acknowledgement(Channel.ALIPAY, false)).thenReturn(rejectedAcknowledgement);
        clearInvocations(alipay, wechat);

        assertSame(hint, TenantUtil.callWithTenant("tenant", () -> facade.identify(context, input)));
        assertSame(verified, TenantUtil.callWithTenant("tenant", () -> facade.verify(context, target, input)));
        assertSame(acknowledgement, facade.acknowledgement(Channel.ALIPAY, true));
        assertSame(rejectedAcknowledgement, facade.acknowledgement(Channel.ALIPAY, false));

        verify(alipay).identify(context, input);
        verify(alipay).verify(context, target, input);
        verify(alipay).acknowledgement(Channel.ALIPAY, true);
        verify(alipay).acknowledgement(Channel.ALIPAY, false);
        verifyNoInteractions(wechat);
    }

    @Test
    void duplicateAndMissingProvidersAreRejected() {
        assertThrows(CommonException.class,
                () -> new DefaultChannelNotificationFacade(List.of(alipay, provider(Channel.ALIPAY))));
        var withoutWechat = new DefaultChannelNotificationFacade(List.of(alipay));
        var context = context(Channel.WECHAT, "wx-revision", HASH);
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> withoutWechat.identify(context, input)));
        assertThrows(CommonException.class, () -> withoutWechat.acknowledgement(Channel.WECHAT, false));
        var target = target(Channel.WECHAT, "wx-revision", HASH, ORDER_NO, AMOUNT);
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> withoutWechat.verify(context, target, input)));
    }

    @Test
    void verifyRejectsTenantAndFrozenBindingMismatchBeforeCallingProvider() {
        clearInvocations(alipay, wechat);
        var context = context(Channel.ALIPAY, "revision-1", HASH);
        var target = target(Channel.ALIPAY, "revision-1", HASH, ORDER_NO, AMOUNT);
        TenantUtil.runWithTenant("other", () -> assertThrows(CommonException.class,
                () -> facade.verify(context, target, input)));
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> facade.verify(context(Channel.WECHAT, "wx-revision", HASH), target, input));
            assertThrows(CommonException.class, () -> facade.verify(context(Channel.ALIPAY, "wrong-revision", HASH), target, input));
            assertThrows(CommonException.class, () -> facade.verify(context(Channel.ALIPAY, "revision-1", "b".repeat(64)), target, input));
        });
        verifyNoInteractions(alipay, wechat);
    }

    @Test
    void verifyRejectsProviderIdentityOrAmountMismatch() {
        var context = context(Channel.ALIPAY, "revision-1", HASH);
        var target = target(Channel.ALIPAY, "revision-1", HASH, ORDER_NO, AMOUNT);
        var wrongIdentity = new ChannelPaymentIdentity("other-payment", target.identity().attemptId(), Channel.ALIPAY,
                target.identity().bindingRevisionId(), target.identity().accountScopeHash(), ORDER_NO);
        when(alipay.verify(context, target, input)).thenReturn(verified(wrongIdentity, AMOUNT));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> facade.verify(context, target, input)));
        when(alipay.verify(context, target, input)).thenReturn(verified(target.identity(), Money.ofMinor(125_001, "CNY")));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                () -> facade.verify(context, target, input)));
    }

    private static ChannelNotificationProvider provider(Channel channel) {
        var provider = mock(ChannelNotificationProvider.class);
        when(provider.channel()).thenReturn(channel);
        return provider;
    }

    private static ChannelExecutionContext context(Channel channel, String revision, String hash) {
        ChannelAccountIdentity identity = channel == Channel.ALIPAY
                ? new AlipayAccountIdentity("app", "seller", null, null, null)
                : new WechatAccountIdentity("app", "merchant", null, null);
        var binding = new ChannelBindingRevision(revision, "merchant", channel, AccessMode.DIRECT, hash,
                "credential", "1", identity, Set.of(PaymentMethod.PAYMENT_CODE));
        return new ChannelExecutionContext("tenant", binding);
    }

    private static ChannelPaymentTarget target(Channel channel, String revision, String hash, String requestNo, Money amount) {
        return new ChannelPaymentTarget(new ChannelPaymentIdentity("payment-1", "attempt-1", channel, revision, hash, requestNo), amount);
    }

    private static VerifiedChannelNotification verified(ChannelPaymentIdentity identity, Money amount) {
        return new VerifiedChannelNotification(identity, amount, "notification-18", "trade-18", "TRADE_SUCCESS", "f".repeat(64));
    }
}
