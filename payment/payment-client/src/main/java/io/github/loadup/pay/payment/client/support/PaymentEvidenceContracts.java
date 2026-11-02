package io.github.loadup.pay.payment.client.support;

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;

/** Retains the former IDL's boundary restrictions in addition to each DTO's own invariants. */
public final class PaymentEvidenceContracts {
    private PaymentEvidenceContracts() {}

    public static PaymentStatementDTO statement(PaymentStatementDTO value) {
        PaymentCaptureContracts.require(value != null && value.channel() == Channel.WECHAT
                && value.billDate() != null && value.billDate().getYear() >= 1970 && value.billDate().getYear() <= 9998
                && "wechat-trade-all-20260116".equals(value.statementProfile())
                && "wechat-tradebill-grant-v1".equals(value.format()) && "SHA1".equals(value.officialHashType())
                && value.officialHash() != null && value.officialHash().matches("[0-9a-f]{40}"));
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
        if (value.succeededAt() != null) { PaymentCaptureContracts.timestamp(value.succeededAt()); }
        if (value.acceptedAt() != null) { PaymentCaptureContracts.timestamp(value.acceptedAt()); }
        return value;
    }
}
