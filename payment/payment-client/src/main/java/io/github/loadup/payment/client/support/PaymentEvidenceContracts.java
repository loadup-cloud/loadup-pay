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
package io.github.loadup.payment.client.support;

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.payment.client.dto.PaymentVerificationDTO;

public final class PaymentEvidenceContracts {
    private PaymentEvidenceContracts() {}

    public static PaymentStatementDTO statement(PaymentStatementDTO value) {
        PaymentCaptureContracts.require(value != null
                && value.channel() == Channel.WECHAT
                && value.billDate() != null
                && value.billDate().getYear() >= 1970
                && value.billDate().getYear() <= 9998
                && "wechat-trade-all-20260116".equals(value.statementProfile())
                && "wechat-tradebill-grant-v1".equals(value.format())
                && "SHA1".equals(value.officialHashType())
                && value.officialHash() != null
                && value.officialHash().matches("[0-9a-f]{40}"));
        PaymentCaptureContracts.text(value.bindingRevisionId(), 64);
        PaymentCaptureContracts.hash(value.accountScopeHash());
        PaymentCaptureContracts.timestamp(value.receivedAt());
        PaymentCaptureContracts.timestamp(value.downloadedAt());
        return value;
    }

    public static PaymentVerificationDTO verification(PaymentVerificationDTO value) {
        PaymentCaptureContracts.require(value != null);
        PaymentCaptureContracts.timestamp(value.startedAt());
        PaymentCaptureContracts.timestamp(value.finishedAt());
        if (value.succeededAt() != null) {
            PaymentCaptureContracts.timestamp(value.succeededAt());
        }
        if (value.acceptedAt() != null) {
            PaymentCaptureContracts.timestamp(value.acceptedAt());
        }
        return value;
    }
}
