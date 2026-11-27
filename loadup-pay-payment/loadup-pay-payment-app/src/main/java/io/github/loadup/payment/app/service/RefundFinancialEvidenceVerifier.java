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
package io.github.loadup.payment.app.service;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.contracts.event.payment.PaymentFinancialEvidence;
import io.github.loadup.contracts.event.payment.RefundFinancialEvidence;
import io.github.loadup.contracts.event.payment.RefundState;
import io.github.loadup.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.payment.domain.model.RefundProgressChange;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Verifies permanent source claims inside the original refund transaction before freezing an
 * outbound fact.
 */
@Component
public class RefundFinancialEvidenceVerifier {
    private final RefundResultGateway results;

    public RefundFinancialEvidenceVerifier(RefundResultGateway results) {
        this.results = results;
    }

    public RefundFinancialEvidence freeze(RefundProgressChange change, StoredPaymentAcceptance original) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
        var refund = change.refund();
        var child = change.before().child();
        var payment = original.payment();
        var claim = results.claim(refund.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var global = results.transaction(
                        claim.original().channel(),
                        claim.original().accountScopeHash(),
                        claim.channelRefundTransactionNo())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (refund.state() != RefundState.SUCCEEDED
                || !refund.id().equals(claim.refundId())
                || !child.original().equals(claim.original())
                || !child.channelTransactionNo().equals(claim.originalChannelTransactionNo())
                || !refund.channelRefundNo().equals(claim.channelRefundNo())
                || !refund.amount().equals(claim.amount())
                || !refund.terminalEvidenceId().equals(claim.receiptId())
                || !Objects.equals(change.channelRefundTransactionNo(), claim.channelRefundTransactionNo())
                || !Objects.equals(change.succeededAt(), claim.succeededAt())
                || !claim.equals(global)
                || !payment.id().equals(refund.paymentId())
                || !payment.id().equals(child.original().paymentId())
                || !Objects.equals(
                        payment.successfulAttemptId(), child.original().attemptId())
                || !payment.orderId().equals(change.request().orderId())
                || !payment.payeeMerchantId().equals(change.request().payeeMerchantId())
                || !payment.amount().equals(change.capacity().succeededAmount())
                || !child.originalSucceededAt().equals(change.capacity().succeededAt())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        try {
            var collected = new PaymentFinancialEvidence(
                    1,
                    payment.id(),
                    payment.orderId(),
                    payment.allocationId(),
                    payment.limitOperationId(),
                    payment.payeeMerchantId(),
                    payment.amount().getCent(),
                    payment.amount().getCurrencyValue(),
                    payment.state(),
                    payment.version(),
                    original.terminalEvidenceId(),
                    payment.successfulAttemptId(),
                    BusinessTime.external(payment.initiatedAt()),
                    BusinessTime.external(change.capacity().succeededAt()),
                    false);
            return new RefundFinancialEvidence(
                    1,
                    refund.id(),
                    refund.refundRequestId(),
                    refund.version(),
                    refund.terminalEvidenceId(),
                    refund.amount().getCent(),
                    change.capacity().refundedMinor(),
                    change.capacity().version(),
                    BusinessTime.external(claim.succeededAt()),
                    collected);
        } catch (IllegalArgumentException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
