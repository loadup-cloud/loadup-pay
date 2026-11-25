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
package io.github.loadup.components.payment.channel.wechat.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.payment.channel.api.model.WechatH5ChannelAction;
import io.github.loadup.components.payment.channel.api.model.WechatJsapiChannelAction;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WechatActionCodecTest {
    private static final OffsetDateTime EXPIRY = OffsetDateTime.parse("2030-01-01T12:00:00+08:00");
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();

    @Test
    void jsapiCodecRoundTripsAndRejectsUnknownDuplicateOrTrailingFields() {
        var codec = new WechatJsapiActionCodec(json);
        var action = new WechatJsapiChannelAction(
                "wx-app", "1700000000", "nonce", "prepay_id=wx123", "RSA", "A".repeat(342), EXPIRY);
        var payload = codec.encode(action);
        assertEquals("WECHAT_JSAPI", codec.kind());
        assertEquals(WechatJsapiChannelAction.class, codec.actionType());
        assertEquals(action, codec.decode(payload));
        assertThrows(
                RuntimeException.class,
                () -> codec.decode(payload.substring(0, payload.length() - 1) + ",\"unknown\":1}"));
        assertThrows(
                RuntimeException.class,
                () -> codec.decode(payload.substring(0, payload.length() - 1) + ",\"nonceStr\":\"other\"}"));
        assertThrows(RuntimeException.class, () -> codec.decode(payload + " {}"));
        assertThrows(RuntimeException.class, () -> codec.decode(null));
    }

    @Test
    void h5CodecRoundTripsAndRejectsUnknownOrInvalidRedirect() {
        var codec = new WechatH5ActionCodec(json);
        var action = new WechatH5ChannelAction(
                "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=wx123", EXPIRY);
        var payload = codec.encode(action);
        assertEquals("WECHAT_H5", codec.kind());
        assertEquals(WechatH5ChannelAction.class, codec.actionType());
        assertEquals(action, codec.decode(payload));
        assertThrows(RuntimeException.class, () -> codec.decode(payload.replace("wx.tenpay.com", "attacker.example")));
        assertThrows(
                RuntimeException.class,
                () -> codec.decode(payload.substring(0, payload.length() - 1) + ",\"unexpected\":true}"));
    }
}
