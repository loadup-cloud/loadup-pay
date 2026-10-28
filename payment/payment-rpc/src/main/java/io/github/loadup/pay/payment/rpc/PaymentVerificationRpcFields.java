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
package io.github.loadup.pay.payment.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.client.enums.PaymentLedgerKind;
import io.github.loadup.pay.payment.client.enums.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.client.enums.PaymentVerificationOutcome;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

/**
 * Strict symbolic protocol scalars. Unknown symbols never become UNKNOWN financial observations.
 */
public final class PaymentVerificationRpcFields {
    private PaymentVerificationRpcFields() {
    }

    public static String channel(Channel value) {
        return value == null ? null : value.name();
    }

    public static Channel channel(String value) {
        try {
            return Channel.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    public static String kind(PaymentLedgerKind value) {
        return value == null ? null : value.name();
    }

    public static PaymentLedgerKind kind(String value) {
        try {
            return PaymentLedgerKind.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    public static String disposition(PaymentVerificationDisposition value) {
        return value == null ? null : value.name();
    }

    public static PaymentVerificationDisposition disposition(String value) {
        try {
            return PaymentVerificationDisposition.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    public static String outcome(PaymentVerificationOutcome value) {
        return value == null ? null : value.name();
    }

    public static PaymentVerificationOutcome outcome(String value) {
        try {
            return PaymentVerificationOutcome.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }
}
