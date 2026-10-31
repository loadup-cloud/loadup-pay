/*
 * #%L
 * LoadUp Pay Payment Client
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
package io.github.loadup.pay.payment.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.result.ResultCode;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureBlocker;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class PaymentCaptureRpcFields {
    private PaymentCaptureRpcFields() {}

    public static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value);
            if (!date.toString().equals(value) || date.getYear() < 1970 || date.getYear() > 9998) {
                throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
            }
            return date;
        } catch (DateTimeParseException invalid) {
            throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String channel(Channel value) {
        return value == null ? null : value.name();
    }

    public static Channel channel(String value) {
        if (!"WECHAT".equals(value)) {
            throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
        return Channel.WECHAT;
    }

    public static String state(PaymentCaptureState value) {
        return value == null ? null : value.name();
    }

    public static PaymentCaptureState state(String value) {
        try {
            return PaymentCaptureState.valueOf((String) value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String blocker(PaymentCaptureBlocker value) {
        return value == null ? null : value.name();
    }

    public static PaymentCaptureBlocker blocker(String value) {
        try {
            return PaymentCaptureBlocker.valueOf((String) value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String kind(PaymentLedgerKind value) {
        return value == null ? null : value.name();
    }

    public static PaymentLedgerKind kind(String value) {
        try {
            return PaymentLedgerKind.valueOf((String) value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static String status(PaymentLedgerStatus value) {
        return value == null ? null : value.name();
    }

    public static PaymentLedgerStatus status(String value) {
        try {
            return PaymentLedgerStatus.valueOf((String) value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException((ResultCode) PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }
}
