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
package io.github.loadup.pay.payment.client.query;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.client.enums.PaymentLedgerKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

/**
 * Query hints only, never financial success evidence or another channel submission permit.
 */
public record PaymentVerificationQuery(@NotBlank @Size(max = 64) String sourceRequestId, @NotNull Channel channel,
                                       @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String accountScopeHash,
                                       @NotNull PaymentLedgerKind kind,
                                       @DiagnosticHidden @NotBlank @Size(max = 64) String merchantPaymentNo,
                                       @DiagnosticHidden @NotBlank @Size(max = 128) String channelTransactionNo,
                                       @DiagnosticHidden @Size(max = 64) String channelRefundNo) {
    public PaymentVerificationQuery {
        if (!text(sourceRequestId, 64) || channel == null || accountScopeHash == null || !accountScopeHash.matches("[0-9a-f]{64}")
                || kind == null || !text(merchantPaymentNo, 64) || !text(channelTransactionNo, 128)
                || (kind == PaymentLedgerKind.REFUND ? !text(channelRefundNo, 64) : channelRefundNo != null))
            throw new CommonException(INVALID_ARGUMENT);
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.chars().noneMatch(Character::isISOControl);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
