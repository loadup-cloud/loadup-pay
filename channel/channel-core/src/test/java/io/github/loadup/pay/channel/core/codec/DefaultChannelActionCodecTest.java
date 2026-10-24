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
package io.github.loadup.pay.channel.core.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.channel.api.model.AlipayJsapiChannelAction;
import io.github.loadup.pay.channel.api.model.ChannelPaymentAction;
import io.github.loadup.pay.channel.api.model.WechatJsapiChannelAction;
import io.github.loadup.pay.channel.api.model.WechatH5ChannelAction;
import io.github.loadup.pay.channel.api.model.ChannelH5Input;
import io.github.loadup.pay.channel.api.model.H5SceneType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Base64;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultChannelActionCodecTest {
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    private final DefaultChannelActionCodec codec = new DefaultChannelActionCodec(json);

    private AlipayJsapiChannelAction action() {
        return new AlipayJsapiChannelAction("trade-123", OffsetDateTime.parse("2030-01-01T12:00:00+08:00"));
    }

    private WechatJsapiChannelAction wechatAction() {
        return new WechatJsapiChannelAction("wx-app-123", "1893456000", "nonce123",
                "prepay_id=synthetic-prepay-token", "RSA", Base64.getEncoder().encodeToString(new byte[256]),
                OffsetDateTime.parse("2030-01-01T12:00:00+08:00"));
    }

    private void assertRejected(String kind, String payload) {
        assertSame(ACTION_SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> codec.decode(kind, payload, codec.checksum(payload)))
                        .getResultCode());
    }

    @Test
    void alipayJsapiActionRoundTripsWithAnExplicitKindAndChecksum() {
        var expected = action();
        String kind = codec.kind(expected);
        String payload = codec.encode(expected);

        assertEquals("ALIPAY_JSAPI", kind);
        assertTrue(payload.contains("trade-123"));
        assertEquals(expected, codec.decode(kind, payload, codec.checksum(payload)));
    }

    @Test
    void wechatJsapiActionRoundTripsWithAnExplicitKindAndChecksum() {
        var expected = wechatAction();
        String kind = codec.kind(expected);
        String payload = codec.encode(expected);

        assertEquals("WECHAT_JSAPI", kind);
        assertTrue(payload.contains("prepay_id=synthetic-prepay-token"));
        assertEquals(expected, codec.decode(kind, payload, codec.checksum(payload)));
    }

    @Test
    void h5SnapshotPreservesTheVerifiedUrlAndCannotChangeKindOrAddUnknownFields() {
        String url = "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=opaque%2Fvalue&sign=a%2Bb%3D&package=1";
        var expected = new WechatH5ChannelAction(url, action().expiresAt());
        String payload = codec.encode(expected);
        assertEquals("WECHAT_H5", codec.kind(expected));
        assertEquals(expected, codec.decode("WECHAT_H5", payload, codec.checksum(payload)));
        assertRejected("WECHAT_JSAPI", payload);
        assertRejected("WECHAT_H5", payload.substring(0, payload.length() - 1) + ",\"redirectUrl\":\"https://other.example/\"}");
        assertRejected("WECHAT_H5", payload.substring(0, payload.length() - 1) + ",\"unexpected\":true}");
        assertRejected("WECHAT_H5", payload.replace("wx.tenpay.com", "attacker.example"));
        assertThrows(CommonException.class, () -> codec.decode("WECHAT_H5", payload, "0".repeat(64)));
        assertFalse(expected.toString().contains("opaque%2Fvalue"));
    }

    @Test
    void h5InputAcceptsIpLiteralsWithoutDnsAndRejectsForwardingChainsAndSpecialAddresses() {
        assertEquals("203.0.113.10", new ChannelH5Input("203.0.113.10", H5SceneType.WAP).payerClientIp());
        assertEquals("2001:db8::1", new ChannelH5Input("2001:db8::1", H5SceneType.IOS).payerClientIp());
        for (String invalid : java.util.List.of("merchant.example", "127.0.0.1", "0.0.0.0", "224.0.0.1", "::", "::1",
                "fe80::1%en0", "[2001:db8::1]", "203.0.113.10, 198.51.100.1", "203.0.113.10:443")) {
            assertThrows(io.github.loadup.commons.error.DomainViolation.class, () -> new ChannelH5Input(invalid, H5SceneType.WAP));
        }
        assertFalse(new ChannelH5Input("203.0.113.10", H5SceneType.WAP).toString().contains("203.0.113.10"));
    }

    @Test
    void onlyTheKnownActionKindAndMatchingPayloadChecksumAreAccepted() {
        String payload = codec.encode(action());
        String checksum = codec.checksum(payload);

        assertThrows(CommonException.class, () -> codec.decode("UNKNOWN", payload, checksum));
        assertThrows(CommonException.class, () -> codec.decode("ALIPAY_JSAPI", payload, "0".repeat(64)));
        assertThrows(CommonException.class,
                () -> codec.decode("ALIPAY_JSAPI", payload.replace("trade-123", "trade-456"), checksum));
    }

    @Test
    void wechatActionRequiresItsKindAndMatchingPayloadChecksum() {
        String payload = codec.encode(wechatAction());

        assertRejected("ALIPAY_JSAPI", payload);
        assertThrows(CommonException.class,
                () -> codec.decode("WECHAT_JSAPI", payload.replace("nonce123", "nonce456"), codec.checksum(payload)));
        assertThrows(CommonException.class,
                () -> codec.decode("WECHAT_JSAPI", payload, "0".repeat(64)));
    }

    @Test
    void wechatStrictJsonRejectsUnknownAndDuplicateFieldsAndInvalidNonceOrSignature() {
        String payload = codec.encode(wechatAction());
        assertRejected("WECHAT_JSAPI", payload.substring(0, payload.length() - 1) + ",\"unexpected\":true}");
        assertRejected("WECHAT_JSAPI", payload.substring(0, payload.length() - 1)
                + ",\"nonceStr\":\"duplicate\"}");
        assertRejected("WECHAT_JSAPI", payload.replace("nonce123", "bad-nonce"));
        assertRejected("WECHAT_JSAPI", payload.replace(wechatAction().paySign(), "short-signature"));
    }

    @Test
    void wechatActionDiagnosticsHidePackageValueAndPaySign() {
        var action = wechatAction();

        assertFalse(action.toString().contains(action.packageValue()));
        assertFalse(action.toString().contains(action.paySign()));
        assertTrue(codec.encode(action).contains(action.packageValue()));
        assertTrue(codec.encode(action).contains(action.paySign()));
    }

    @Test
    void strictJsonRejectsUnknownDuplicateAndTrailingContent() {
        String payload = codec.encode(action());
        assertRejected("ALIPAY_JSAPI", payload.substring(0, payload.length() - 1) + ",\"unexpected\":true}");
        assertRejected("ALIPAY_JSAPI", payload.substring(0, payload.length() - 1) + ",\"tradeNo\":\"other\"}");
        assertRejected("ALIPAY_JSAPI", payload + " {} ");
    }

    @Test
    void requiredTradeNumberAndExpiryMustBePresentAndValid() {
        String validExpiry = "2030-01-01T12:00:00+08:00";
        assertRejected("ALIPAY_JSAPI", "{\"expiresAt\":\"" + validExpiry + "\"}");
        assertRejected("ALIPAY_JSAPI", "{\"tradeNo\":\"\",\"expiresAt\":\"" + validExpiry + "\"}");
        assertRejected("ALIPAY_JSAPI", "{\"tradeNo\":\"trade-123\"}");
        assertRejected("ALIPAY_JSAPI", "{\"tradeNo\":\"trade-123\",\"expiresAt\":null}");
        assertRejected("ALIPAY_JSAPI", "{\"tradeNo\":\"trade-123\",\"expiresAt\":\"\"}");
    }

    @Test
    void oversizedPayloadIsRejectedBeforeDeserializationAndTradeNumberIsHiddenFromDiagnostics() {
        String oversized = " ".repeat(8193);
        assertSame(ACTION_SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> codec.decode("ALIPAY_JSAPI", oversized, codec.checksum(oversized))).getResultCode());

        ChannelPaymentAction decoded = codec.decode("ALIPAY_JSAPI", codec.encode(action()),
                codec.checksum(codec.encode(action())));
        assertInstanceOf(AlipayJsapiChannelAction.class, decoded);
        assertFalse(decoded.toString().contains("trade-123"));
        assertTrue(codec.encode(action()).contains("trade-123"));
    }
}
