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
import java.math.BigInteger;
import java.util.Objects;

public record TypedValue(ValueType type, String value) {
    public TypedValue {
        Objects.requireNonNull(type, "value type");
        Objects.requireNonNull(value, "value");
        if (value.length() > 1024) {
            throw new CommonException(ContractResultCodeEnum.VALUE_EXCEEDS_SIZE_LIMIT);
        }
        value = switch (type) {
            case STRING -> value;
            case INTEGER -> new BigInteger(value).toString();
            case DECIMAL -> {
                BigDecimal decimal = new BigDecimal(value);
                if (Math.abs((long) decimal.scale()) > 128 || decimal.precision() > 128) {
                    throw new CommonException(ContractResultCodeEnum.DECIMAL_EXCEEDS_PRECISION_LIMIT);
                }
                yield decimal.stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> {
                if (!value.equals("true") && !value.equals("false")) {
                    throw new CommonException(ContractResultCodeEnum.BOOLEAN_MUST_BE_TRUE_OR_FALSE);
                }
                yield value;
            }
        };
    }

    public static TypedValue text(String value) {
        return new TypedValue(ValueType.STRING, value);
    }

    public static TypedValue decimal(String value) {
        return new TypedValue(ValueType.DECIMAL, value);
    }

    public static TypedValue integer(long value) {
        return new TypedValue(ValueType.INTEGER, Long.toString(value));
    }

    public static TypedValue bool(boolean value) {
        return new TypedValue(ValueType.BOOLEAN, Boolean.toString(value));
    }

    public boolean numeric() {
        return type == ValueType.INTEGER || type == ValueType.DECIMAL;
    }

    public BigDecimal number() {
        if (!numeric()) {
            throw new CommonException(ContractResultCodeEnum.NOT_A_NUMERIC_VALUE);
        }
        return new BigDecimal(value);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
