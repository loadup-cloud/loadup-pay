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

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.payment.channel.api.codec.ChannelActionTypeCodec;
import io.github.loadup.components.payment.channel.api.model.WechatJsapiChannelAction;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

public final class WechatJsapiActionCodec implements ChannelActionTypeCodec<WechatJsapiChannelAction> {
    private final JsonMapper json;

    public WechatJsapiActionCodec(JsonMapper json) {
        this.json = JsonUtil.strictProtocol(json.rebuild())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
    }

    @Override
    public String kind() {
        return "WECHAT_JSAPI";
    }

    @Override
    public Class<WechatJsapiChannelAction> actionType() {
        return WechatJsapiChannelAction.class;
    }

    @Override
    public String encode(WechatJsapiChannelAction action) {
        return json.writerFor(WechatJsapiChannelAction.class).writeValueAsString(action);
    }

    @Override
    public WechatJsapiChannelAction decode(String payload) {
        return json.readValue(payload, WechatJsapiChannelAction.class);
    }
}
