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
package io.github.loadup.productcenter.domain.model;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import java.math.BigDecimal;
import java.util.Set;

public record OverridePolicy(BigDecimal minimum, BigDecimal maximum, Set<TypedValue> allowedValues) {
    public OverridePolicy {
        allowedValues = Set.copyOf(allowedValues);
    }

    public void validateDefinition(ParameterDefinition definition) {
        if (!definition.editableLayers().contains(ConfigurationLayer.MERCHANT)) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_OVERRIDE_FORBIDDEN);
        }
        ParameterDefinition.checkBounds(definition.type(), minimum, maximum);
        if (definition.minimum() != null && minimum != null && minimum.compareTo(definition.minimum()) < 0) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_MINIMUM_WIDENS_PRODUCT_BOUNDS);
        }
        if (definition.maximum() != null && maximum != null && maximum.compareTo(definition.maximum()) > 0) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_MAXIMUM_WIDENS_PRODUCT_BOUNDS);
        }
        if (allowedValues.size() > 100) {
            throw new CommonException(ContractResultCodeEnum.TOO_MANY_ALLOWED_VALUES);
        }
        for (TypedValue value : allowedValues) {
            definition.validate(value);
            ParameterDefinition.checkValue(definition.type(), value, minimum, maximum, Set.of());
        }
        BigDecimal effectiveMin = minimum == null ? definition.minimum() : minimum;
        BigDecimal effectiveMax = maximum == null ? definition.maximum() : maximum;
        ParameterDefinition.checkBounds(definition.type(), effectiveMin, effectiveMax);
        if (!definition.allowedValues().isEmpty()
                && definition.allowedValues().stream()
                        .noneMatch(v -> (allowedValues.isEmpty() || allowedValues.contains(v))
                                && (effectiveMin == null || v.number().compareTo(effectiveMin) >= 0)
                                && (effectiveMax == null || v.number().compareTo(effectiveMax) <= 0))) {
            throw new CommonException(ContractResultCodeEnum.EMPTY_MERCHANT_CONSTRAINT);
        }
    }

    public void validate(ParameterDefinition definition, TypedValue value) {
        definition.validate(value);
        ParameterDefinition.checkValue(definition.type(), value, minimum, maximum, allowedValues);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
