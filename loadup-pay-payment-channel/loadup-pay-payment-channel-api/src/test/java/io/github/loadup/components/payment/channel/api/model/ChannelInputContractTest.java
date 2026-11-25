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

import io.github.loadup.components.payment.channel.contract.Channel;
import org.junit.jupiter.api.Test;

class ChannelInputContractTest {
    @Test
    void h5InputAcceptsPublicIpLiteralsOnly() {
        assertEquals(H5SceneType.WAP, new ChannelH5Input("203.0.113.10", H5SceneType.WAP).sceneType());
        assertEquals(H5SceneType.IOS, new ChannelH5Input("2001:db8::1", H5SceneType.IOS).sceneType());
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("127.0.0.1", H5SceneType.WAP));
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("0.0.0.0", H5SceneType.WAP));
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("224.0.0.1", H5SceneType.WAP));
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("fe80::1%eth0", H5SceneType.WAP));
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("[::1]", H5SceneType.WAP));
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("", H5SceneType.WAP));
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("not-an-ip", H5SceneType.WAP));
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("1".repeat(46), H5SceneType.WAP));
        assertThrows(RuntimeException.class, () -> new ChannelH5Input("203.0.113.10", null));
    }

    @Test
    void payerIdentityInputRequiresChannelTypeAndText() {
        var input = new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.USER_ID, "buyer-1", "app-scope");
        assertEquals(Channel.ALIPAY, input.channel());
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPayerIdentityInput(null, PayerIdentityType.USER_ID, "buyer-1", "app-scope"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPayerIdentityInput(Channel.ALIPAY, null, "buyer-1", "app-scope"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.USER_ID, " ", "app-scope"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.USER_ID, "buyer-1", " "));
    }

    @Test
    void paymentCodeInputIsBoundedAndNoChannelInputIsExplicit() {
        assertEquals("code", new ChannelPaymentCodeInput("code").paymentCode());
        assertThrows(RuntimeException.class, () -> new ChannelPaymentCodeInput(" "));
        assertThrows(RuntimeException.class, () -> new ChannelPaymentCodeInput("x".repeat(4097)));
        assertEquals("NoChannelInput", new NoChannelInput().getClass().getSimpleName());
    }
}
