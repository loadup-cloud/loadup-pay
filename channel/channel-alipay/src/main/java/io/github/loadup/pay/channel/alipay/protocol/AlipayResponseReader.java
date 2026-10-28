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
package io.github.loadup.pay.channel.alipay.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;

/**
 * Strict typed field access after the protocol client has verified the original response bytes.
 */
public final class AlipayResponseReader {
    private final JsonMapper json;

    public AlipayResponseReader(JsonMapper json) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> read(AlipayVerifiedResponse response, AlipayMethod expected) {
        if (response == null || response.method() != expected || response.payload() == null
                || response.payload().getBytes(StandardCharsets.UTF_8).length > 1048576
                || response.sourceDigest() == null || !response.sourceDigest().equals(DigestUtils.sha256(response.payload())))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        try {
            var result = json.readValue(response.payload(), Map.class);
            if (result == null || text(result, "code") == null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            return result;
        } catch (RuntimeException invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }

    public String text(Map<String, Object> response, String key) {
        var value = response.get(key);
        if (value == null) return null;
        if (!(value instanceof String text) || text.isBlank() || text.length() > 4096)
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return text;
    }

    public String requiredText(Map<String, Object> response, String key, int maximum) {
        var value = text(response, key);
        if (value == null || value.length() > maximum) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return value;
    }

    public void matchIfPresent(Map<String, Object> response, String key, String expected) {
        if (response.get(key) != null && !expected.equals(text(response, key)))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
    }

    public void requireMatch(Map<String, Object> response, String key, String expected) {
        if (!expected.equals(text(response, key))) throw new CommonException(CHANNEL_RESPONSE_INVALID);
    }

    public void requireAmount(String wireAmount, long expectedMinor) {
        if (wireAmount == null || !wireAmount.matches("(0|[1-9][0-9]{0,15})(\\.[0-9]{1,2})?"))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        try {
            if (new BigDecimal(wireAmount).movePointRight(2).longValueExact() != expectedMinor)
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
        } catch (ArithmeticException invalidAmount) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }
}

