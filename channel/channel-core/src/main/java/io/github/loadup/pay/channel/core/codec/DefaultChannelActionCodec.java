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
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.channel.api.model.AlipayJsapiChannelAction;
import io.github.loadup.pay.channel.api.model.AlipayH5ChannelAction;
import io.github.loadup.pay.channel.api.model.WechatJsapiChannelAction;
import io.github.loadup.pay.channel.api.model.WechatH5ChannelAction;
import io.github.loadup.pay.channel.api.model.ChannelPaymentAction;
import io.github.loadup.pay.channel.api.codec.ChannelActionCodec;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_SNAPSHOT_INVALID;

public final class DefaultChannelActionCodec implements ChannelActionCodec {
    private final JsonMapper json;

    public DefaultChannelActionCodec(JsonMapper json) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }

    @Override
    public String kind(ChannelPaymentAction action) {
        return switch (action) {
            case AlipayH5ChannelAction ignored -> "ALIPAY_H5";
            case AlipayJsapiChannelAction ignored -> "ALIPAY_JSAPI";
            case WechatJsapiChannelAction ignored -> "WECHAT_JSAPI";
            case WechatH5ChannelAction ignored -> "WECHAT_H5";
        };
    }

    @Override
    public String encode(ChannelPaymentAction action) {
        var payload = switch (action) {
            case AlipayH5ChannelAction h5 -> json.writerFor(AlipayH5ChannelAction.class).writeValueAsString(h5);
            case AlipayJsapiChannelAction alipay -> json.writerFor(AlipayJsapiChannelAction.class).writeValueAsString(alipay);
            case WechatJsapiChannelAction wechat -> json.writerFor(WechatJsapiChannelAction.class).writeValueAsString(wechat);
            case WechatH5ChannelAction h5 -> json.writerFor(WechatH5ChannelAction.class).writeValueAsString(h5);
        };
        if (payload.length() > 8192) throw new CommonException(ACTION_SNAPSHOT_INVALID);
        return payload;
    }

    @Override
    public String checksum(String payload) { return DigestUtils.sha256(payload); }

    @Override
    public ChannelPaymentAction decode(String kind, String payload, String payloadHash) {
        try {
            if (payload == null || payload.length() > 8192 || payloadHash == null || !payloadHash.equals(checksum(payload))
                    || kind == null) throw new CommonException(ACTION_SNAPSHOT_INVALID);
            ChannelPaymentAction result = switch (kind) {
                case "ALIPAY_H5" -> json.readValue(payload, AlipayH5ChannelAction.class);
                case "ALIPAY_JSAPI" -> json.readValue(payload, AlipayJsapiChannelAction.class);
                case "WECHAT_JSAPI" -> json.readValue(payload, WechatJsapiChannelAction.class);
                case "WECHAT_H5" -> json.readValue(payload, WechatH5ChannelAction.class);
                default -> throw new CommonException(ACTION_SNAPSHOT_INVALID);
            };
            if (result == null) throw new CommonException(ACTION_SNAPSHOT_INVALID);
            return result;
        } catch (RuntimeException invalid) {
            throw new CommonException(ACTION_SNAPSHOT_INVALID);
        }
    }
}

