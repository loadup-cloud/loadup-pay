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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import java.net.URI;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ChannelActionContractTest {
    @Test
    void alipayH5ActionAcceptsOnlyTheOfficialGatewayShape() {
        var action = new AlipayH5ChannelAction(ApiFixtures.alipayH5Url(), ApiFixtures.EXPIRY);
        assertEquals(Channel.ALIPAY, action.channel());
        assertEquals(PaymentMethod.H5, action.method());
        assertTrue(AlipayH5ChannelAction.isGateway(URI.create(ApiFixtures.alipayH5Url())));
        assertFalse(AlipayH5ChannelAction.isGateway(null));
        assertFalse(AlipayH5ChannelAction.isGateway(URI.create("http://openapi.alipay.com/gateway.do")));
        assertFalse(AlipayH5ChannelAction.isGateway(URI.create("https://other.example/gateway.do")));
        assertFalse(AlipayH5ChannelAction.isGateway(URI.create("https://openapi.alipay.com:8443/gateway.do")));
        assertFalse(AlipayH5ChannelAction.isGateway(URI.create("https://user@openapi.alipay.com/gateway.do")));
        assertFalse(AlipayH5ChannelAction.isGateway(URI.create("https://openapi.alipay.com/gateway.do#fragment")));
        assertFalse(AlipayH5ChannelAction.isGateway(URI.create("https://openapi.alipay.com/other.do")));
    }

    @Test
    void alipayH5ActionRejectsMalformedGatewayQueries() {
        assertThrows(RuntimeException.class, () -> new AlipayH5ChannelAction(null, ApiFixtures.EXPIRY));
        assertThrows(RuntimeException.class, () -> new AlipayH5ChannelAction(" ", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class, () -> new AlipayH5ChannelAction("https://openapi.alipay.com/gateway.do", null));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction("https://openapi.alipay.com/gateway.do", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(ApiFixtures.alipayH5Url() + "&unknown=value", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(
                        ApiFixtures.alipayH5Url().replace("method=alipay.trade.wap.pay", "method=other"),
                        ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(
                        ApiFixtures.alipayH5Url().replace("sign_type=RSA2", "sign_type=RSA"), ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(
                        ApiFixtures.alipayH5Url().replace("charset=utf-8", "charset=gbk"), ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(
                        ApiFixtures.alipayH5Url().replace("format=json", "format=xml"), ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(
                        ApiFixtures.alipayH5Url().replace("version=1.0", "version=2.0"), ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(ApiFixtures.alipayH5Url() + "&biz_content=%7B%7D", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(ApiFixtures.alipayH5Url() + "&sign=", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(
                        ApiFixtures.alipayH5Url().replace("&sign=AQ%3D%3D", "&=AQ%3D%3D"), ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction(
                        "https://openapi.alipay.com/gateway.do?" + "a".repeat(6144), ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new AlipayH5ChannelAction("https://openapi.alipay.com/gateway.do?\u0000", ApiFixtures.EXPIRY));
    }

    @Test
    void wechatH5ActionAcceptsOnlyTheOfficialCheckmwebShape() {
        var action = new WechatH5ChannelAction(ApiFixtures.wechatH5Url(), ApiFixtures.EXPIRY);
        assertEquals(Channel.WECHAT, action.channel());
        assertEquals(PaymentMethod.H5, action.method());
        assertThrows(RuntimeException.class, () -> new WechatH5ChannelAction(null, ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new WechatH5ChannelAction("https://wx.tenpay.com/other?x=1", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new WechatH5ChannelAction(
                        "https://wx.tenpay.com:8443/cgi-bin/mmpayweb-bin/checkmweb?x=1", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new WechatH5ChannelAction(
                        "http://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?x=1", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new WechatH5ChannelAction(
                        "https://user@wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?x=1", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new WechatH5ChannelAction(
                        "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?x=1#f", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new WechatH5ChannelAction(
                        "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb", ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new WechatH5ChannelAction(
                        "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb? ", ApiFixtures.EXPIRY));
        assertThrows(RuntimeException.class, () -> new WechatH5ChannelAction(" ", ApiFixtures.EXPIRY));
        assertThrows(RuntimeException.class, () -> new WechatH5ChannelAction(ApiFixtures.wechatH5Url(), null));
        assertThrows(
                RuntimeException.class,
                () -> new WechatH5ChannelAction(
                        "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?x=" + "a".repeat(4096),
                        ApiFixtures.EXPIRY));
    }

    @Test
    void wechatJsapiActionValidatesServerSignedInvocationFields() {
        var action = wechatJsapi("1700000000", "nonce", "prepay_id=wx123", "RSA", "A".repeat(342));
        assertEquals(Channel.WECHAT, action.channel());
        assertEquals(PaymentMethod.JSAPI, action.method());
        assertThrows(
                RuntimeException.class,
                () -> wechatJsapi("1700000000", "nonce", "prepay_id=wx123", "RSA", "A".repeat(341)));
        assertThrows(
                RuntimeException.class, () -> wechatJsapi("abc", "nonce", "prepay_id=wx123", "RSA", "A".repeat(342)));
        assertThrows(
                RuntimeException.class,
                () -> wechatJsapi("1700000000", "nonce-with-dash", "prepay_id=wx123", "RSA", "A".repeat(342)));
        assertThrows(
                RuntimeException.class,
                () -> wechatJsapi("1700000000", "nonce", "other=wx123", "RSA", "A".repeat(342)));
        assertThrows(
                RuntimeException.class,
                () -> wechatJsapi("1700000000", "nonce", "prepay_id=wx123", "RSA2", "A".repeat(342)));
        assertThrows(
                RuntimeException.class,
                () -> wechatJsapi("1700000000", "nonce", "prepay_id=wx123", "RSA", "A".repeat(685)));
        assertThrows(
                RuntimeException.class,
                () -> new WechatJsapiChannelAction(
                        " ", "1700000000", "nonce", "prepay_id=wx123", "RSA", "A".repeat(342), ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new WechatJsapiChannelAction(
                        "app", "1700000000", "nonce", "prepay_id=wx123", "RSA", "A".repeat(342), null));
    }

    @Test
    void alipayJsapiActionTruncatesTheFrozenDeadlineAndRequiresTradeNumber() {
        var action = new AlipayJsapiChannelAction("trade-1", ApiFixtures.EXPIRY);
        assertEquals("trade-1", action.tradeNo());
        assertEquals(Channel.ALIPAY, action.channel());
        assertEquals(PaymentMethod.JSAPI, action.method());
        assertEquals(
                ApiFixtures.EXPIRY.truncatedTo(ChronoUnit.MICROS).toInstant(),
                action.expiresAt().toInstant());
        assertThrows(RuntimeException.class, () -> new AlipayJsapiChannelAction(" ", ApiFixtures.EXPIRY));
        assertThrows(RuntimeException.class, () -> new AlipayJsapiChannelAction("trade-1", null));
    }

    private static WechatJsapiChannelAction wechatJsapi(
            String timeStamp, String nonce, String packageValue, String signType, String paySign) {
        return new WechatJsapiChannelAction(
                "wx-app", timeStamp, nonce, packageValue, signType, paySign, ApiFixtures.EXPIRY);
    }
}
