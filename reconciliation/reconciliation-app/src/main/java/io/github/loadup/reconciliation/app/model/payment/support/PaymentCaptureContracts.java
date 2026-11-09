/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.reconciliation.app.model.payment.support;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.reconciliation.app.model.payment.dto.PaymentCaptureDTO;
import io.github.loadup.reconciliation.app.model.payment.dto.PaymentLedgerLineDTO;
import io.github.loadup.reconciliation.app.model.payment.enums.PaymentCaptureState;
import io.github.loadup.reconciliation.app.model.payment.enums.PaymentLedgerKind;
import io.github.loadup.reconciliation.app.model.payment.enums.PaymentLedgerStatus;
import io.github.loadup.reconciliation.app.model.payment.error.PaymentResultCodeEnum;
import io.github.loadup.reconciliation.app.model.payment.query.PaymentCaptureLinesQuery;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;

public final class PaymentCaptureContracts {
    private PaymentCaptureContracts() {}

    public static PaymentCaptureDTO capture(PaymentCaptureDTO value) {
        require(value != null);
        text(value.captureId(), 64);
        text(value.nativeTaskId(), 64);
        require(value.channel() == Channel.WECHAT
                && value.billDate() != null
                && value.billDate().getYear() >= 1970
                && value.billDate().getYear() <= 9998
                && "wechat-trade-all-20260116".equals(value.statementProfile()));
        hash(value.accountScopeHash());
        try {
            require(value.systemZone() != null
                    && ZoneId.of(value.systemZone()).getId().equals(value.systemZone()));
        } catch (DateTimeException invalid) {
            throw new CommonException(PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
        require(value.state() != null
                && value.nativeStatus() != null
                && Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED")
                        .contains(value.nativeStatus())
                && value.attempts() != null
                && value.maxAttempts() != null
                && value.attempts() >= 0
                && value.maxAttempts() >= 1
                && value.attempts() <= value.maxAttempts());
        boolean initial = value.state() == PaymentCaptureState.INIT;
        boolean sealed = value.state() == PaymentCaptureState.SEALED;
        boolean blocked = value.state() == PaymentCaptureState.BLOCKED;
        require(value.version() == (initial ? 0 : 1)
                && initial == (value.startedAt() == null)
                && initial == (value.finishedAt() == null)
                && sealed == (value.paymentCount() != null)
                && sealed == (value.paymentAmountMinor() != null)
                && sealed == (value.refundCount() != null)
                && sealed == (value.refundAmountMinor() != null)
                && sealed == (value.contentHash() != null)
                && blocked == (value.blocker() != null)
                && blocked == (value.blockerSubjectId() != null));
        if (!initial) {
            timestamp(value.startedAt());
            timestamp(value.finishedAt());
            require(!value.finishedAt().isBefore(value.startedAt()));
        }
        if (sealed) {
            hash(value.contentHash());
            require(value.paymentCount() >= 0
                    && value.refundCount() >= 0
                    && value.paymentAmountMinor() >= value.paymentCount()
                    && value.refundAmountMinor() >= value.refundCount()
                    && (value.paymentCount() == 0) == (value.paymentAmountMinor() == 0)
                    && (value.refundCount() == 0) == (value.refundAmountMinor() == 0));
            try {
                Math.addExact(value.paymentCount(), value.refundCount());
            } catch (ArithmeticException invalid) {
                throw new CommonException(PaymentResultCodeEnum.INVALID_ARGUMENT);
            }
        }
        if (blocked) {
            text(value.blockerSubjectId(), 64);
        }
        return value;
    }

    public static PageDTO<PaymentLedgerLineDTO> page(
            PageDTO<PaymentLedgerLineDTO> page, PaymentCaptureLinesQuery query) {
        require(page != null && page.getData() != null && page.getPageInfo() != null && query != null);
        var info = page.getPageInfo();
        require(info.pageSize() <= 100
                && info.pageIndex() == query.page().longValue()
                && info.pageSize() == query.size().longValue());
        try {
            long offset = Math.multiplyExact(info.pageIndex() - 1, info.pageSize());
            long expected = Math.min(info.pageSize(), Math.max(0, info.totalCount() - offset));
            require(page.getData().size() == expected);
            for (var row : page.getData()) {
                line(row);
                require(row.ordinal() == Math.incrementExact(offset));
                offset++;
            }
        } catch (ArithmeticException invalid) {
            throw new CommonException(PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
        return page;
    }

    public static void line(PaymentLedgerLineDTO value) {
        require(value != null
                && value.ordinal() >= 1
                && value.amountMinor() >= 1
                && value.sourceVersion() >= 0
                && "CNY".equals(value.currency())
                && value.kind() != null
                && value.status() != null
                && (value.kind() == PaymentLedgerKind.REFUND) == (value.channelRefundNo() != null)
                && (value.kind() != PaymentLedgerKind.PAYMENT || value.status() == PaymentLedgerStatus.SUCCEEDED));
        text(value.channelTransactionNo(), 128);
        text(value.merchantPaymentNo(), 64);
        text(value.sourceId(), 64);
        text(value.evidenceId(), 64);
        if (value.channelRefundNo() != null) {
            text(value.channelRefundNo(), 64);
        }
        timestamp(value.sourceAt());
    }

    static void timestamp(OffsetDateTime value) {
        require(value != null
                && value.getNano() % 1000 == 0
                && value.toEpochSecond() >= -62135596800L
                && value.toEpochSecond() <= 253402300799L);
    }

    static void hash(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"));
    }

    static void text(String value, int limit) {
        require(value != null
                && !value.isBlank()
                && value.length() <= limit
                && value.chars().noneMatch(Character::isISOControl));
    }

    static void require(boolean valid) {
        if (!valid) {
            throw new CommonException(PaymentResultCodeEnum.INVALID_ARGUMENT);
        }
    }
}
