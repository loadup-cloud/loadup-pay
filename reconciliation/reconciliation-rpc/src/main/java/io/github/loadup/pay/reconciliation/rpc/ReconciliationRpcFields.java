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
package io.github.loadup.pay.reconciliation.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationBatchState;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationSide;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;

/**
 * Protocol scalar parsing only; account authorization belongs to the application.
 */
public final class ReconciliationRpcFields {
    private ReconciliationRpcFields() {
    }

    public static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static LocalDate date(String value) {
        if (value == null) return null;
        try {
            var date = LocalDate.parse(value);
            if (!date.toString().equals(value) || date.getYear() < 1970 || date.getYear() > 9998)
                throw new CommonException(INVALID_ARGUMENT);
            return date;
        } catch (DateTimeParseException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    public static String channel(Channel value) {
        return value == null ? null : value.name();
    }

    public static Channel channel(String value) {
        try {
            var channel = Channel.valueOf(value);
            if (channel != Channel.ALIPAY && channel != Channel.WECHAT) throw new CommonException(INVALID_ARGUMENT);
            return channel;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    public static String side(ReconciliationSide value) {
        return value == null ? null : value.name();
    }

    public static ReconciliationSide side(String value) {
        try {
            return ReconciliationSide.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    public static String state(ReconciliationBatchState value) {
        return value == null ? null : value.name();
    }

    public static ReconciliationBatchState state(String value) {
        try {
            return ReconciliationBatchState.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }
}
