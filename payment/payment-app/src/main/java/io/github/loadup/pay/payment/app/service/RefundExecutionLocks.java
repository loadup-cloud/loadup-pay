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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.payment.app.model.LockedRefund;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import org.springframework.stereotype.Component;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class RefundExecutionLocks {
    private final RefundGateway refunds;
    private final RefundResultGateway results;
    private final PaymentAcceptanceGateway payments;
    private final PaymentResultGateway collections;
    private final PaymentAttemptGateway attempts;

    public RefundExecutionLocks(RefundGateway refunds, RefundResultGateway results, PaymentAcceptanceGateway payments,
                                PaymentResultGateway collections, PaymentAttemptGateway attempts) {
        this.refunds = refunds;
        this.results = results;
        this.payments = payments;
        this.collections = collections;
        this.attempts = attempts;
    }

    public StoredRefundRequest request(String tenant, String requestId) {
        var reference = refunds.reference(tenant, requestId).orElseThrow(() -> new CommonException(NOT_FOUND));
        var parent = payments.lockParent(tenant, reference.orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!parent.payeeMerchantId().equals(reference.payeeMerchantId()) || !parent.gate().orderId().equals(reference.orderId())
                || parent.gate().paymentId() != null || parent.gate().state() == GateState.INIT)
            throw new CommonException(SNAPSHOT_INVALID);
        return refunds.lock(tenant, requestId);
    }

    public LockedRefund child(String tenant, StoredRefundRequest request, String refundId) {
        var selected = request.allocations().stream().filter(c -> c.refund().id().equals(refundId)).findFirst()
                .orElseThrow(() -> new CommonException(NOT_FOUND));
        var capacity = refunds.lockCapacities(tenant, request.request().orderId()).stream()
                .filter(c -> c.paymentId().equals(selected.refund().paymentId())).findFirst().orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var facts = results.lockChild(tenant, refundId);
        var original = payments.find(tenant, selected.original().paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var claim = collections.attemptClaim(tenant, selected.original().attemptId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var attempt = attempts.find(tenant, selected.original().attemptId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!facts.child().equals(selected) || !capacity.orderId().equals(request.request().orderId())
                || !capacity.payeeMerchantId().equals(request.request().payeeMerchantId())
                || !capacity.succeededAt().equals(selected.originalSucceededAt())
                || !claim.tenantId().equals(tenant) || !claim.identity().equals(selected.original())
                || !claim.amount().equals(capacity.succeededAmount()) || !claim.succeededAt().equals(capacity.succeededAt())
                || !claim.channelTransactionNo().equals(selected.channelTransactionNo())
                || original.payment().state() != PaymentState.SUCCEEDED || !original.payment().orderId().equals(capacity.orderId())
                || !original.payment().payeeMerchantId().equals(capacity.payeeMerchantId()) || !original.payment().amount().equals(capacity.succeededAmount())
                || !selected.original().attemptId().equals(original.payment().successfulAttemptId())
                || attempt.attempt().state() != PaymentAttemptState.SUCCEEDED || !attempt.attempt().identity().equals(selected.original())
                || !attempt.attempt().amount().equals(capacity.succeededAmount()))
            throw new CommonException(SNAPSHOT_INVALID);
        if (!selected.refund().terminal() && capacity.reservedMinor() < selected.refund().amount().getCent())
            throw new CommonException(SNAPSHOT_INVALID);
        return new LockedRefund(request, facts, capacity);
    }

    public LockedRefund lock(String tenant, String requestId, String refundId) {
        return child(tenant, request(tenant, requestId), refundId);
    }
}
