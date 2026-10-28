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
package io.github.loadup.pay.payment.client.dto;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationOutcome;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;

import java.time.OffsetDateTime;
import java.util.Set;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

/**
 * One point-in-time read, not a mutation receipt. UNKNOWN or missing local coverage never means no collection.
 */
public record PaymentVerificationDTO(PaymentVerificationQuery request, PaymentVerificationDisposition disposition,
                                     String bindingRevisionId, String paymentId, String attemptId, String refundId,
                                     Long sourceVersion, String localState,
                                     PaymentVerificationOutcome outcome, Money amount,
                                     @DiagnosticHidden String observedChannelTransactionNo,
                                     @DiagnosticHidden String observedRefundTransactionNo, OffsetDateTime succeededAt,
                                     String sourceDigest,
                                     OffsetDateTime startedAt, OffsetDateTime finishedAt, OffsetDateTime acceptedAt) {
    public PaymentVerificationDTO {
        if (request == null || disposition == null || !time(startedAt) || !time(finishedAt) || finishedAt.isBefore(startedAt))
            invalid();
        if (disposition == PaymentVerificationDisposition.OBSERVED) {
            boolean refund = request.kind() == PaymentLedgerKind.REFUND;
            if (!text(bindingRevisionId, 64) || !text(paymentId, 64) || !text(attemptId, 64) || (refund ? !text(refundId, 64) : refundId != null)
                    || sourceVersion == null || sourceVersion < 0 || !text(localState, 32) || outcome == null || amount == null
                    || amount.getCent() <= 0 || !"CNY".equals(amount.getCurrencyValue()) || sourceDigest == null || !sourceDigest.matches("[0-9a-f]{64}"))
                invalid();
            if (refund && !Set.of(PaymentVerificationOutcome.ACCEPTED, PaymentVerificationOutcome.SUCCEEDED, PaymentVerificationOutcome.FAILED_WITH_NO_REFUND, PaymentVerificationOutcome.UNKNOWN).contains(outcome)
                    || !refund && outcome == PaymentVerificationOutcome.FAILED_WITH_NO_REFUND) invalid();
            boolean succeeded = outcome == PaymentVerificationOutcome.SUCCEEDED || outcome == PaymentVerificationOutcome.CLOSED_WITH_FUNDS_RETURNED;
            if (succeeded != (succeededAt != null) || succeededAt != null && !time(succeededAt)
                    || (succeeded || refund) && !text(observedChannelTransactionNo, 128)
                    || observedChannelTransactionNo != null && !text(observedChannelTransactionNo, 128)
                    || (refund && succeeded ? !text(observedRefundTransactionNo, 128) : observedRefundTransactionNo != null))
                invalid();
            if (acceptedAt != null && (!refund || !time(acceptedAt) || succeededAt != null && acceptedAt.isAfter(succeededAt)))
                invalid();
        } else if (acceptedAt != null || bindingRevisionId != null || paymentId != null || attemptId != null || refundId != null || sourceVersion != null || localState != null
                || outcome != null || amount != null || observedChannelTransactionNo != null || observedRefundTransactionNo != null || succeededAt != null || sourceDigest != null)
            invalid();
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean time(OffsetDateTime value) {
        return value != null && value.getNano() % 1000 == 0;
    }

    private static void invalid() {
        throw new CommonException(INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
