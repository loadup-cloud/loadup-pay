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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.limitcenter.domain.model.LimitRestoreResult;
import io.github.loadup.pay.payment.app.converter.RefundSettlementConverter;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.app.port.RefundProjectionEnvelopeCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Replays a successful child independently of fee and realtime targets.
 */
@Component
public class RefundLimitDelivery {
    private final OutboxTemplate outbox;
    private final RefundProjectionEnvelopeCodec codec;
    private final PaymentLimitClient client;
    private final RefundSettlementConverter converter;

    public RefundLimitDelivery(OutboxTemplate outbox, RefundProjectionEnvelopeCodec codec, PaymentLimitClient client,
                               RefundSettlementConverter converter) {
        this.outbox = outbox;
        this.codec = codec;
        this.client = client;
        this.converter = converter;
    }

    public void deliver(OutboxEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (event == null || event.message() == null || event.message().version() != 1
                || !RefundProjectionEvents.LIMIT.equals(event.message().type())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var message = event.message();
        String tenant = TenantUtil.getTenantId();
        var row = outbox.find(event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id()) || !row.type().equals(message.type())
                || !row.businessId().equals(message.businessId()) || !"PROCESSING".equals(row.status())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (!client.supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var envelope = codec.limit(message.payload());
        if (!row.businessId().equals(envelope.aggregateId()) || !row.type().equals(envelope.eventType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var command = converter.toRestore(envelope);
        var ack = client.restoreRefund(tenant, command);
        if (ack == null || !command.operationId().equals(ack.operationId()) || !command.paymentId().equals(ack.paymentId())
                || !command.refundId().equals(ack.refundId()) || command.refundVersion() != ack.refundVersion()
                || !command.businessEventId().equals(ack.businessEventId()) || !command.refundEvidenceId().equals(ack.refundEvidenceId())
                || command.cumulativePaymentRefundedMinor() != ack.cumulativePaymentRefundedMinor()
                || ack.verifiedPaymentRefundedMinor() < ack.cumulativePaymentRefundedMinor() || ack.verifiedPaymentRefundVersion() <= 0
                || ack.restoredAmountMinor() < 0 || ack.restoredAmountMinor() > ack.verifiedPaymentRefundedMinor()
                || ack.result() != (ack.restoredAmountMinor() > 0 || ack.restoredPaymentCount()
                ? LimitRestoreResult.RESTORED : LimitRestoreResult.NO_ACTION)) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
