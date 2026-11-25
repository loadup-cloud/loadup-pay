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
package io.github.loadup.components.payment.channel.alipay.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.payment.channel.api.model.AlipayH5ChannelAction;
import io.github.loadup.components.payment.channel.api.model.AlipayJsapiChannelAction;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AlipayActionCodecTest {
    private static final OffsetDateTime EXPIRY = OffsetDateTime.parse("2030-01-01T12:00:00+08:00");
    private final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();

    @Test
    void jsapiCodecRoundTripsAndRejectsUnknownDuplicateOrTrailingFields() {
        var codec = new AlipayJsapiActionCodec(json);
        var action = new AlipayJsapiChannelAction("trade-1", EXPIRY);
        var payload = codec.encode(action);
        assertEquals("ALIPAY_JSAPI", codec.kind());
        assertEquals(AlipayJsapiChannelAction.class, codec.actionType());
        assertEquals(action, codec.decode(payload));
        assertThrows(
                RuntimeException.class,
                () -> codec.decode(payload.substring(0, payload.length() - 1) + ",\"unknown\":1}"));
        assertThrows(
                RuntimeException.class,
                () -> codec.decode(payload.substring(0, payload.length() - 1) + ",\"tradeNo\":\"other\"}"));
        assertThrows(RuntimeException.class, () -> codec.decode(payload + " {}"));
        assertThrows(RuntimeException.class, () -> codec.decode(null));
    }

    @Test
    void h5CodecRoundTripsAndRejectsUnknownOrInvalidRedirect() {
        var codec = new AlipayH5ActionCodec(json);
        var action = new AlipayH5ChannelAction(
                "https://openapi.alipay.com/gateway.do?app_id=app&method=alipay.trade.wap.pay&format=json"
                        + "&charset=utf-8&sign_type=RSA2&sign=AQ%3D%3D&timestamp=2026-10-23+18%3A00%3A00"
                        + "&version=1.0&biz_content=%7B%7D",
                EXPIRY);
        var payload = codec.encode(action);
        assertEquals("ALIPAY_H5", codec.kind());
        assertEquals(AlipayH5ChannelAction.class, codec.actionType());
        assertEquals(action, codec.decode(payload));
        assertThrows(
                RuntimeException.class, () -> codec.decode(payload.replace("openapi.alipay.com", "attacker.example")));
        assertThrows(
                RuntimeException.class,
                () -> codec.decode(payload.substring(0, payload.length() - 1) + ",\"unexpected\":true}"));
    }
}
