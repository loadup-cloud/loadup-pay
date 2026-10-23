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
package io.github.loadup.pay.channel.wechat.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigInteger;
import java.util.Map;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;

/** Reads only already verified response payloads; never infers payment from an HTTP status alone. */
public final class WechatResponseReader {
    private final JsonMapper json;
    public WechatResponseReader(JsonMapper json) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
    public Map<?, ?> read(WechatVerifiedResponse response, WechatMethod expected) {
        if (response == null || response.method() != expected || response.status() < 100 || response.status() > 599
                || response.payload() == null || response.payload().isEmpty() || response.payload().length() > 1048576
                || response.sourceDigest() == null || !response.sourceDigest().matches("[0-9a-f]{64}"))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        try {
            var value = json.readValue(response.payload(), Map.class);
            if (value == null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            return value;
        } catch (RuntimeException invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
    }
    public String text(Map<?, ?> value, String name, int maximum) {
        Object field = value.get(name);
        if (!(field instanceof String text) || text.isBlank() || text.length() > maximum
                || text.chars().anyMatch(Character::isISOControl)) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return text;
    }
    public String optionalText(Map<?, ?> value, String name, int maximum) {
        return value.get(name) == null ? null : text(value, name, maximum);
    }
    public void match(Map<?, ?> value, String name, String expected) {
        if (!expected.equals(text(value, name, 128))) throw new CommonException(CHANNEL_RESPONSE_INVALID);
    }
    public void account(Map<?, ?> value, WechatAccountIdentity account) {
        if (account.subMerchantId() == null) {
            match(value, "appid", account.appId()); match(value, "mchid", account.merchantId());
        } else {
            match(value, "sp_appid", account.appId()); match(value, "sp_mchid", account.merchantId());
            match(value, "sub_mchid", account.subMerchantId());
            if (account.subAppId() != null) match(value, "sub_appid", account.subAppId());
            else if (value.get("sub_appid") != null && !"".equals(value.get("sub_appid")))
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }
    public void amount(Map<?, ?> value, long expected) {
        if (!(value.get("amount") instanceof Map<?, ?> amount)) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        match(amount, "currency", "CNY");
        Object total = amount.get("total");
        if (!(total instanceof Integer || total instanceof Long || total instanceof BigInteger)
                || !new BigInteger(total.toString()).equals(BigInteger.valueOf(expected)))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
    }
}
