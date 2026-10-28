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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Serializes publication with acceptance and close using their original parent lock, never a new child gate.
 */
@Component
public class PaymentInputPublication {
    private final PaymentAcceptanceGateway payments;
    private final IdGenerator ids;

    public PaymentInputPublication(PaymentAcceptanceGateway payments, IdGenerator ids) {
        this.payments = payments;
        this.ids = ids;
    }

    public StoredPaymentGate lockParent(PaymentActor actor, PaymentInputPrepareCommand command) {
        writable(actor);
        var parent = payments.lockParent(actor.tenantId(), command.orderId());
        if (parent.isPresent()) {
            bound(parent.get(), actor, command, null);
            return parent.get();
        }
        // A duplicate parent INSERT causes the entire local transaction to roll back before the existing retry.
        var value = new StoredPaymentGate(ids.generate(), command.payeeMerchantId(), actor.serviceId(),
                PaymentExecutionGate.init(command.orderId(), null).open(), 0L);
        payments.insertParent(actor.tenantId(), value);
        return value;
    }

    /**
     * Call only after checking for the winning immutable input under the parent lock; exact replays remain readable.
     */
    public void requireNew(PaymentActor actor, PaymentInputPrepareCommand command, StoredPaymentGate parent) {
        writable(actor);
        bound(parent, actor, command, null);
        if (parent.gate().state() != GateState.OPEN) throw new CommonException(EXECUTION_FENCED);
        var child = payments.lockPayment(actor.tenantId(), command.paymentId());
        if (child.isPresent()) {
            bound(child.get(), actor, command, command.paymentId());
            if (child.get().gate().state() == GateState.FENCED) throw new CommonException(EXECUTION_FENCED);
            // A published input cannot be retrofitted into an already accepted inputless payment.
            if (payments.find(actor.tenantId(), command.paymentId()).isPresent())
                throw new CommonException(INVALID_STATE);
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (payments.find(actor.tenantId(), command.paymentId()).isPresent())
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private void bound(StoredPaymentGate value, PaymentActor actor, PaymentInputPrepareCommand command, String paymentId) {
        if (!value.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!value.payeeMerchantId().equals(command.payeeMerchantId()) || !value.gate().orderId().equals(command.orderId())
                || !Objects.equals(value.gate().paymentId(), paymentId) || value.gate().state() == GateState.INIT)
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private void writable(PaymentActor actor) {
        if (actor == null || !actor.tenantId().equals(TenantUtil.getTenantId()))
            throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
    }
}
