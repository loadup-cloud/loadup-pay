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
package io.github.loadup.productcenter.app.support;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.domain.model.Condition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ContractCodec {
    private final JsonMapper mapper;

    public ContractCodec(JsonMapper mapper) {
        this.mapper = mapper.rebuild()
                .addMixIn(Condition.class, ConditionMixin.class)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public String write(Object value) {
        String content = mapper.writeValueAsString(value);
        if (content.getBytes(StandardCharsets.UTF_8).length > 131072) {
            throw new CommonException(ContractResultCodeEnum.CONTRACT_CONTENT_EXCEEDS_LIMIT);
        }
        return content;
    }

    public <T> T read(String content, Class<T> type) {
        return mapper.readValue(content, type);
    }

    public Map<String, Object> definition(String content) {
        return mapper.readValue(content, new TypeReference<Map<String, Object>>() {});
    }

    public String digest(Map<String, Object> value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(write(canonical(value)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new CommonException(CommonResultCodeEnum.SYS_ERROR, e);
        }
    }

    private Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put((String) key, canonical(item)));
            return sorted;
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(Object::toString).sorted().toList();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonical).toList();
        }
        return value;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Condition.All.class, name = "ALL"),
        @JsonSubTypes.Type(value = Condition.Any.class, name = "ANY"),
        @JsonSubTypes.Type(value = Condition.Not.class, name = "NOT"),
        @JsonSubTypes.Type(value = Condition.Exists.class, name = "EXISTS"),
        @JsonSubTypes.Type(value = Condition.Compare.class, name = "COMPARE")
    })
    abstract static class ConditionMixin {}
}
