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
package io.github.loadup.pay.payment.client.support;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

/**
 * Provider-owned, versioned wire evidence framing. Public JSON serializers never participate in capture identity.
 */
public final class PaymentCaptureContentHash {
    private PaymentCaptureContentHash() {
    }

    public static String compute(String captureId, Channel channel, String accountScopeHash, LocalDate billDate, String profile,
                                 String zone, OffsetDateTime startedAt, OffsetDateTime finishedAt, List<PaymentLedgerLineDTO> lines) {
        return frame(captureId, channel, accountScopeHash, billDate, profile, zone, startedAt, finishedAt, lines, new Frame("payment-ledger-capture-v1")).finish();
    }

    /**
     * The archive is exactly the versioned length-framed source bytes whose SHA-256 is contentHash.
     */
    public static byte[] archive(PaymentCaptureDTO capture, List<PaymentLedgerLineDTO> lines, int maxBytes) {
        if (maxBytes < 1 || !matches(capture, lines)) throw new CommonException(INVALID_ARGUMENT);
        var bytes = new java.io.ByteArrayOutputStream();
        var hash = frame(capture.captureId(), capture.channel(), capture.accountScopeHash(), capture.billDate(), capture.statementProfile(), capture.systemZone(),
                capture.startedAt(), capture.finishedAt(), lines, new Frame("payment-ledger-capture-v1", bytes, maxBytes));
        if (!capture.contentHash().equals(hash.finish())) throw new CommonException(INVALID_ARGUMENT);
        return bytes.toByteArray();
    }

    private static Frame frame(String captureId, Channel channel, String accountScopeHash, LocalDate billDate, String profile,
                               String zone, OffsetDateTime startedAt, OffsetDateTime finishedAt, List<PaymentLedgerLineDTO> lines, Frame hash) {
        hash.field(captureId);
        hash.field(channel.name());
        hash.field(accountScopeHash);
        hash.field(billDate.toString());
        hash.field(profile);
        hash.field(zone);
        hash.field(startedAt.toInstant().toString());
        hash.field(finishedAt.toInstant().toString());
        long ordinal = 0, payments = 0, paymentAmount = 0, refunds = 0, refundAmount = 0;
        for (var row : lines) {
            if (row.ordinal() != ++ordinal || row.amountMinor() <= 0 || !"CNY".equals(row.currency()) || row.sourceVersion() < 0)
                throw new CommonException(INVALID_ARGUMENT);
            hash.field(Long.toString(row.ordinal()));
            hash.field(row.kind().name());
            hash.field(row.channelTransactionNo());
            hash.field(row.channelRefundNo());
            hash.field(row.merchantPaymentNo());
            hash.field(Long.toString(row.amountMinor()));
            hash.field(row.currency());
            hash.field(row.status().name());
            hash.field(row.sourceAt().toInstant().toString());
            hash.field(row.sourceId());
            hash.field(row.evidenceId());
            hash.field(Long.toString(row.sourceVersion()));
            if (row.kind() == PaymentLedgerKind.PAYMENT) {
                payments++;
                paymentAmount = Math.addExact(paymentAmount, row.amountMinor());
            } else {
                refunds++;
                refundAmount = Math.addExact(refundAmount, row.amountMinor());
            }
        }
        hash.field(Long.toString(ordinal));
        hash.field(Long.toString(payments));
        hash.field(Long.toString(paymentAmount));
        hash.field(Long.toString(refunds));
        hash.field(Long.toString(refundAmount));
        return hash;
    }

    public static boolean matches(PaymentCaptureDTO capture, List<PaymentLedgerLineDTO> lines) {
        if (capture == null || capture.state() != PaymentCaptureState.SEALED || capture.paymentCount() == null || capture.refundCount() == null
                || capture.paymentAmountMinor() == null || capture.refundAmountMinor() == null || capture.contentHash() == null
                || Math.addExact(capture.paymentCount(), capture.refundCount()) != lines.size()) return false;
        long paymentCount = 0, paymentAmount = 0, refundCount = 0, refundAmount = 0;
        for (var row : lines) {
            if (row.kind() == PaymentLedgerKind.PAYMENT) {
                paymentCount++;
                paymentAmount = Math.addExact(paymentAmount, row.amountMinor());
            } else {
                refundCount++;
                refundAmount = Math.addExact(refundAmount, row.amountMinor());
            }
        }
        return capture.paymentCount() == paymentCount && capture.paymentAmountMinor() == paymentAmount
                && capture.refundCount() == refundCount && capture.refundAmountMinor() == refundAmount
                && capture.contentHash().equals(compute(capture.captureId(), capture.channel(), capture.accountScopeHash(), capture.billDate(), capture.statementProfile(),
                capture.systemZone(), capture.startedAt(), capture.finishedAt(), lines));
    }

    public static String rowId(String captureId, long ordinal) {
        var hash = new Frame("payment-ledger-row-id-v1");
        hash.field(captureId);
        hash.field(Long.toString(ordinal));
        return hash.finish();
    }

    public static String key(String kind, String transaction, String refund) {
        var hash = new Frame("payment-ledger-row-key-v1");
        hash.field(kind);
        hash.field(transaction);
        hash.field(refund);
        return hash.finish();
    }

    private static final class Frame {
        final MessageDigest digest;
        final java.io.ByteArrayOutputStream archive;
        final int maxBytes;

        Frame(String format) {
            this(format, null, 0);
        }

        Frame(String format, java.io.ByteArrayOutputStream archive, int maxBytes) {
            this.archive = archive;
            this.maxBytes = maxBytes;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException unavailable) {
                throw new AssertionError(unavailable);
            }
            field(format);
        }

        void field(String value) {
            byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
            byte[] prefix = ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array();
            if (archive != null) {
                if ((long) archive.size() + prefix.length + (bytes == null ? 0 : bytes.length) > maxBytes)
                    throw new CommonException(INVALID_ARGUMENT);
                archive.writeBytes(prefix);
                if (bytes != null) archive.writeBytes(bytes);
            }
            digest.update(prefix);
            if (bytes != null) digest.update(bytes);
        }

        String finish() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
