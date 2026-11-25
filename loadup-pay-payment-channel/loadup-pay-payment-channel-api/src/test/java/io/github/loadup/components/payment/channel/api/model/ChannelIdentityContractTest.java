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
package io.github.loadup.components.payment.channel.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.components.payment.channel.contract.AccessMode;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChannelIdentityContractTest {
    @Test
    void alipayDirectAndServiceProviderIdentitiesExposeTheCollectingAccount() {
        assertEquals(Channel.ALIPAY, ApiFixtures.ALIPAY_DIRECT.channel());
        assertEquals(AccessMode.DIRECT, ApiFixtures.ALIPAY_DIRECT.accessMode());
        assertEquals("seller", ApiFixtures.ALIPAY_DIRECT.collectingAccountId());
        assertEquals(AccessMode.SERVICE_PROVIDER, ApiFixtures.ALIPAY_PROVIDER.accessMode());
        assertEquals("seller", ApiFixtures.ALIPAY_PROVIDER.collectingAccountId());
    }

    @Test
    void alipayIdentityRejectsMissingAndIncompleteFields() {
        assertThrows(RuntimeException.class, () -> new AlipayAccountIdentity(null, "seller", null, null, null));
        assertThrows(RuntimeException.class, () -> new AlipayAccountIdentity("app", " ", null, null, null));
        assertThrows(RuntimeException.class, () -> new AlipayAccountIdentity("app", "seller", " ", null, null));
        assertThrows(RuntimeException.class, () -> new AlipayAccountIdentity("app", "seller", null, "auth-ref", null));
        assertThrows(RuntimeException.class, () -> new AlipayAccountIdentity("app", "seller", null, null, "auth-v1"));
    }

    @Test
    void wechatDirectAndProviderIdentitiesSelectTheFrozenMerchant() {
        assertEquals(Channel.WECHAT, ApiFixtures.WECHAT_DIRECT.channel());
        assertEquals(AccessMode.DIRECT, ApiFixtures.WECHAT_DIRECT.accessMode());
        assertEquals("merchant", ApiFixtures.WECHAT_DIRECT.collectingAccountId());
        assertEquals(AccessMode.SERVICE_PROVIDER, ApiFixtures.WECHAT_PROVIDER.accessMode());
        assertEquals("sub-merchant", ApiFixtures.WECHAT_PROVIDER.collectingAccountId());
    }

    @Test
    void wechatIdentityRejectsMissingSubMerchantAndInvalidText() {
        assertThrows(RuntimeException.class, () -> new WechatAccountIdentity("app", "merchant", "sub-app", null));
        assertThrows(RuntimeException.class, () -> new WechatAccountIdentity(null, "merchant", null, null));
        assertThrows(RuntimeException.class, () -> new WechatAccountIdentity("app", null, null, null));
        assertThrows(RuntimeException.class, () -> new WechatAccountIdentity("app", "merchant", " ", null));
        assertThrows(RuntimeException.class, () -> new WechatAccountIdentity("app", "merchant", null, " "));
    }

    @Test
    void bindingRevisionAcceptsMatchingChannelAndAccessMode() {
        var binding = ApiFixtures.alipayBinding();
        assertEquals(Channel.ALIPAY, binding.channel());
        assertEquals(AccessMode.DIRECT, binding.accessMode());
        assertEquals(2, binding.capabilities().size());
        assertEquals(ApiFixtures.WECHAT_DIRECT, ApiFixtures.wechatBinding().identity());
    }

    @Test
    void bindingRevisionRejectsEachInvalidIdentityOrCapabilityPart() {
        assertThrows(
                RuntimeException.class,
                () -> binding(" ", "merchant-1", Channel.ALIPAY, AccessMode.DIRECT, ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> binding("revision-1", null, Channel.ALIPAY, AccessMode.DIRECT, ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> binding("revision-1", "merchant-1", null, AccessMode.DIRECT, ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> binding("revision-1", "merchant-1", Channel.ALIPAY, null, ApiFixtures.HASH));
        assertThrows(
                RuntimeException.class,
                () -> binding("revision-1", "merchant-1", Channel.ALIPAY, AccessMode.DIRECT, "bad"));
        assertThrows(
                RuntimeException.class,
                () -> binding(
                        "revision-1", "merchant-1", Channel.ALIPAY, AccessMode.DIRECT, ApiFixtures.HASH, " ", "v"));
        assertThrows(
                RuntimeException.class,
                () -> binding(
                        "revision-1", "merchant-1", Channel.ALIPAY, AccessMode.DIRECT, ApiFixtures.HASH, "ref", " "));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelBindingRevision(
                        "revision-1",
                        "merchant-1",
                        Channel.WECHAT,
                        AccessMode.DIRECT,
                        ApiFixtures.HASH,
                        "ref",
                        "v",
                        ApiFixtures.ALIPAY_DIRECT,
                        Set.of(PaymentMethod.JSAPI)));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelBindingRevision(
                        "revision-1",
                        "merchant-1",
                        Channel.ALIPAY,
                        AccessMode.SERVICE_PROVIDER,
                        ApiFixtures.HASH,
                        "ref",
                        "v",
                        ApiFixtures.ALIPAY_DIRECT,
                        Set.of(PaymentMethod.JSAPI)));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelBindingRevision(
                        "revision-1",
                        "merchant-1",
                        Channel.ALIPAY,
                        AccessMode.DIRECT,
                        ApiFixtures.HASH,
                        "ref",
                        "v",
                        null,
                        Set.of(PaymentMethod.JSAPI)));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelBindingRevision(
                        "revision-1",
                        "merchant-1",
                        Channel.ALIPAY,
                        AccessMode.DIRECT,
                        ApiFixtures.HASH,
                        "ref",
                        "v",
                        ApiFixtures.ALIPAY_DIRECT,
                        Set.of()));
    }

    @Test
    void executionContextRequiresABinding() {
        assertEquals(ApiFixtures.alipayBinding(), ApiFixtures.context().binding());
        assertThrows(RuntimeException.class, () -> new ChannelExecutionContext(null));
    }

    @Test
    void paymentIdentityRejectsMissingParts() {
        assertEquals(Channel.ALIPAY, ApiFixtures.IDENTITY.channel());
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentIdentity(
                        " ", "attempt-1", Channel.ALIPAY, "revision-1", ApiFixtures.HASH, "request-1"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentIdentity(
                        "payment-1", null, Channel.ALIPAY, "revision-1", ApiFixtures.HASH, "request-1"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentIdentity(
                        "payment-1", "attempt-1", null, "revision-1", ApiFixtures.HASH, "request-1"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentIdentity(
                        "payment-1", "attempt-1", Channel.ALIPAY, " ", ApiFixtures.HASH, "request-1"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentIdentity(
                        "payment-1", "attempt-1", Channel.ALIPAY, "revision-1", null, "request-1"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentIdentity(
                        "payment-1", "attempt-1", Channel.ALIPAY, "revision-1", ApiFixtures.HASH, " "));
    }

    private static ChannelBindingRevision binding(
            String id, String merchant, Channel channel, AccessMode mode, String hash) {
        return binding(id, merchant, channel, mode, hash, "ref", "v");
    }

    private static ChannelBindingRevision binding(
            String id,
            String merchant,
            Channel channel,
            AccessMode mode,
            String hash,
            String reference,
            String version) {
        return new ChannelBindingRevision(
                id,
                merchant,
                channel,
                mode,
                hash,
                reference,
                version,
                ApiFixtures.ALIPAY_DIRECT,
                Set.of(PaymentMethod.JSAPI));
    }
}
