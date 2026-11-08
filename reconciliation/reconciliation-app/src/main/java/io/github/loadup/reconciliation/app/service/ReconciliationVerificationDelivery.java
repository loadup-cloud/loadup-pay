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
package io.github.loadup.reconciliation.app.service;

import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.reconciliation.app.converter.ReconciliationVerificationConverter;
import io.github.loadup.reconciliation.app.port.ReconciliationPaymentVerification;
import io.github.loadup.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.reconciliation.domain.gateway.ReconciliationVerificationGateway;
import io.github.loadup.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.reconciliation.domain.model.ReconciliationVerificationState;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Read-only owner RPC outside the local transaction; first valid observation wins independently of native ACK.
 */
@Component
public class ReconciliationVerificationDelivery {
    private final OutboxTemplate outbox;
    private final ReconciliationVerificationGateway requests;
    private final ReconciliationTransactions transactions;
    private final ReconciliationReviewService reviews;
    private final ReconciliationPaymentVerification payment;
    private final ReconciliationVerificationConverter converter;

    public ReconciliationVerificationDelivery(
            OutboxTemplate outbox,
            ReconciliationVerificationGateway requests,
            ReconciliationTransactions transactions,
            ReconciliationReviewService reviews,
            ReconciliationPaymentVerification payment,
            ReconciliationVerificationConverter converter) {
        this.outbox = outbox;
        this.requests = requests;
        this.transactions = transactions;
        this.reviews = reviews;
        this.payment = payment;
        this.converter = converter;
    }

    public void deliver(OutboxEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (event == null
                || event.message() == null
                || event.id() == null
                || event.message().version() != 1
                || !ReconciliationVerificationMessages.TYPE.equals(
                        event.message().type())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var message = event.message();
        String tenant = TenantUtil.getTenantId();
        var id = ReconciliationVerificationMessages.decode(message.payload());
        if (!id.equals(message.businessId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var original = transactions.readSnapshot(
                tenant, () -> requests.find(id).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)));
        var nativeRow = ReconciliationVerificationService.delivery(outbox, original);
        if (!event.id().equals(original.deliveryId())
                || !"PROCESSING".equals(nativeRow.status())
                || nativeRow.attempts() < 1) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        System.err.println("DIAG deliver activeTx=" + TransactionSynchronizationManager.isActualTransactionActive()
                + " state=" + original.state() + " delivery=" + nativeRow.status());
        if (original.state() == ReconciliationVerificationState.RECORDED) {
            return;
        }
        transactions.readSnapshot(tenant, () -> approved(tenant, original, false));
        var query = converter.toQuery(original);
        io.github.loadup.reconciliation.client.dto.PaymentVerificationDTO result;
        try {
            result = payment.verify(query);
        } catch (RuntimeException failure) {
            System.err.println("DIAG verify threw " + failure);
            throw failure;
        }
        if (result == null || !query.equals(result.request())) {
            throw new CommonException(SCOPE_MISMATCH);
        }
        var receipt = converter.toReceipt(result);
        System.err.println("DIAG verified result disposition=" + result.disposition());
        transactions.writeLocal(tenant, () -> {
            approved(tenant, original, true);
            var current = requests.lock(original.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (current.state() == ReconciliationVerificationState.RECORDED) {
                return false;
            }
            if (!original.equals(current)) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var actual = ReconciliationVerificationService.delivery(outbox, current);
            if (!"PROCESSING".equals(actual.status()) || actual.attempts() != nativeRow.attempts()) {
                throw new CommonException(INVALID_STATE);
            }
            requests.update(current.complete(receipt), current.version());
            System.err.println("DIAG recorded receipt disposition=" + receipt.disposition());
            return true;
        });
    }

    private boolean approved(String tenant, ReconciliationVerificationRequest value, boolean lock) {
        var actor = new ReconciliationActor(tenant, value.callerServiceId(), Set.of());
        var original = reviews.approvedForVerification(
                actor,
                value.reference().batchId(),
                value.reference().differenceId(),
                value.proposalId(),
                value.approvalEventId(),
                value.reviewVersion(),
                lock);
        if (!value.reference().equals(original.reference())
                || !value.scope().equals(original.scope())
                || !value.key().equals(original.key())
                || !value.merchantPaymentNo().equals(original.merchantPaymentNo())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return true;
    }
}
