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
package io.github.loadup.payment.app.service;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.contracts.event.FrozenEventTypes;
import io.github.loadup.payment.app.converter.RefundSettlementConverter;
import io.github.loadup.payment.app.dispatch.PaymentDeliveryRoutes;
import io.github.loadup.payment.app.model.limitcenter.enums.LimitRestoreResult;
import io.github.loadup.payment.app.port.PaymentLimitClient;
import io.github.loadup.payment.app.port.RefundProjectionEnvelopeCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Replays a successful child independently of fee and realtime targets.
 */
@Component
public class RefundLimitDelivery {
    private final TaskTemplate outbox;
    private final RefundProjectionEnvelopeCodec codec;
    private final PaymentLimitClient client;
    private final RefundSettlementConverter converter;

    public RefundLimitDelivery(
            TaskTemplate outbox,
            RefundProjectionEnvelopeCodec codec,
            PaymentLimitClient client,
            RefundSettlementConverter converter) {
        this.outbox = outbox;
        this.codec = codec;
        this.client = client;
        this.converter = converter;
    }

    public void deliver(TaskExecution event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (event == null
                || event.payloadVersion() != 1
                || !PaymentDeliveryRoutes.REFUND_LIMIT.equals(event.taskType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var message = event;
        var row = outbox.find(event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id())
                || !row.taskType().equals(message.taskType())
                || !row.bizId().equals(message.bizId())
                || row.status() != TaskStatus.RUNNING) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var envelope = codec.limit(message.payload());
        if (!row.bizId().equals(envelope.aggregateId())
                || !(FrozenEventTypes.accepts(
                                PaymentDeliveryRoutes.factType(row.taskType()), row.taskType(), envelope.eventType())
                        || BusinessEventTypes.REFUND_OUTCOME.equals(envelope.eventType()))) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (envelope.data() == null
                || (envelope.data().financialEvidence() != null)
                        != BusinessEventTypes.REFUND_FINANCIAL.equals(envelope.eventType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        try {
            envelope.data().requireEnvelope(envelope);
        } catch (DomainViolation invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var command = converter.toRestore(envelope);
        var ack = client.restoreRefund(command);
        if (ack == null
                || !command.operationId().equals(ack.operationId())
                || !command.paymentId().equals(ack.paymentId())
                || !command.refundId().equals(ack.refundId())
                || command.refundVersion() != ack.refundVersion()
                || !command.businessEventId().equals(ack.businessEventId())
                || !command.refundEvidenceId().equals(ack.refundEvidenceId())
                || command.cumulativePaymentRefundedMinor() != ack.cumulativePaymentRefundedMinor()
                || ack.verifiedPaymentRefundedMinor() < ack.cumulativePaymentRefundedMinor()
                || ack.verifiedPaymentRefundVersion() <= 0
                || ack.restoredAmountMinor() < 0
                || ack.restoredAmountMinor() > ack.verifiedPaymentRefundedMinor()
                || ack.result()
                        != (ack.restoredAmountMinor() > 0 || ack.restoredPaymentCount()
                                ? LimitRestoreResult.RESTORED
                                : LimitRestoreResult.NO_ACTION)) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
