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

import io.github.loadup.productcenter.domain.model.ConfigurationLayer;
import io.github.loadup.productcenter.domain.model.TypedValue;
import io.github.loadup.productcenter.domain.model.ValueType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ContractMappingSupport {
    private final ContractCodec codec;

    public ContractMappingSupport(ContractCodec codec) {
        this.codec = codec;
    }

    public static TypedValue value(String type, String value) {
        return value == null ? null : new TypedValue(ValueType.valueOf(type), value);
    }

    public static Set<TypedValue> values(String type, Set<String> values) {
        return values == null
                ? Set.of()
                : values.stream().map(v -> value(type, v)).collect(Collectors.toUnmodifiableSet());
    }

    public static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return TypedValue.decimal(value).number();
    }

    public static Set<ConfigurationLayer> layers(Set<String> values) {
        return values == null
                ? Set.of()
                : values.stream().map(ConfigurationLayer::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    public Map<String, Object> definition(String content) {
        return codec.definition(content);
    }
}
