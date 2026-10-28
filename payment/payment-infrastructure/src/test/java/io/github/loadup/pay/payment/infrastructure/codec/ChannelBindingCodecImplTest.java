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
package io.github.loadup.pay.payment.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.payment.app.converter.ChannelBindingConverter;
import io.github.loadup.pay.payment.client.command.ChannelBindingPublishCommand;
import io.github.loadup.pay.payment.client.dto.AlipayAccountIdentityDTO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelBindingCodecImplTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ChannelBindingConverter.class.getPackageName());
    final ChannelBindingCodecImpl codec = new ChannelBindingCodecImpl(JsonMapper.builder().build(),
            context.getBean(ChannelBindingConverter.class), validation.getValidator());

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void readsMysqlJsonWhitespaceAndKeyOrderWithoutWeakeningTypedHashVerification() {
        var identity = new AlipayAccountIdentity("app", "seller", null, null, null);
        String expectedHash = codec.checksum(codec.encodeIdentity(identity));
        String reordered = """
                {"sellerId": "seller", "type": "ALIPAY_V1", "appId": "app",
                 "operatorAppId": null, "authorizationVersion": null, "authorizationReference": null}
                """;
        assertEquals(identity, codec.decodeIdentity(reordered, expectedHash));
        String altered = codec.encodeIdentity(new AlipayAccountIdentity("app", "different", null, null, null));
        assertThrows(CommonException.class, () -> codec.decodeIdentity(altered, expectedHash));
    }

    @Test
    void credentialAuthorizationRotationDoesNotChangeTheReceivingAccountScope() {
        var first = new AlipayAccountIdentity("app", "seller", "operating-app", "authorization", "v1");
        var rotated = new AlipayAccountIdentity("app", "seller", "operating-app", "authorization", "v2");
        assertEquals(codec.accountScopeHash(Channel.ALIPAY, first),
                codec.accountScopeHash(Channel.ALIPAY, rotated));
        var directAlias = new AlipayAccountIdentity("another-app", "seller", null, null, null);
        assertEquals(codec.accountScopeHash(Channel.ALIPAY, first), codec.accountScopeHash(Channel.ALIPAY, directAlias));
    }

    @Test
    void providerSubMerchantIdentityIsPartOfTheScope() {
        var first = new WechatAccountIdentity("provider-app", "provider", null, "sub-one");
        var second = new WechatAccountIdentity("provider-app", "provider", null, "sub-two");
        assertNotEquals(codec.accountScopeHash(Channel.WECHAT, first),
                codec.accountScopeHash(Channel.WECHAT, second));
        var direct = new WechatAccountIdentity("merchant-app", "sub-one", null, null);
        var anotherProvider = new WechatAccountIdentity("another-provider-app", "another-provider", "sub-app", "sub-one");
        assertEquals(codec.accountScopeHash(Channel.WECHAT, first), codec.accountScopeHash(Channel.WECHAT, direct));
        assertEquals(codec.accountScopeHash(Channel.WECHAT, first), codec.accountScopeHash(Channel.WECHAT, anotherProvider));
    }

    @Test
    void rejectsChannelAndAccountTypeMismatchBeforeCalculatingFinancialScope() {
        var alipay = new AlipayAccountIdentity("app", "seller", null, null, null);
        var wechat = new WechatAccountIdentity("app", "merchant", null, null);
        assertThrows(CommonException.class, () -> codec.accountScopeHash(Channel.WECHAT, alipay));
        assertThrows(CommonException.class, () -> codec.accountScopeHash(Channel.ALIPAY, wechat));
        assertThrows(CommonException.class, () -> codec.accountScopeHash(null, wechat));
        assertThrows(CommonException.class, () -> codec.accountScopeHash(Channel.WECHAT, null));
    }

    @Test
    void publishDigestIsIndependentOfCapabilitySetIterationOrder() {
        var first = command(new LinkedHashSet<>(List.of(PaymentMethod.H5, PaymentMethod.JSAPI)));
        var second = command(new LinkedHashSet<>(List.of(PaymentMethod.JSAPI, PaymentMethod.H5)));
        assertEquals(codec.requestDigest("tenant", "pay-ops", "PUBLISH", first), codec.requestDigest("tenant", "pay-ops", "PUBLISH", second));
    }

    ChannelBindingPublishCommand command(Set<PaymentMethod> methods) {
        return new ChannelBindingPublishCommand("merchant", "binding", Channel.ALIPAY, AccessMode.DIRECT,
                new AlipayAccountIdentityDTO("app", "seller", null, null, null), "credential", "v1", methods, 100, "publish", 0);
    }
}
