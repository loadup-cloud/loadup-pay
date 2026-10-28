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
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class PaymentExecutionLocks {
    private final PaymentAcceptanceGateway gates;
    private final PaymentControlGateway payments;
    private final PaymentAcceptanceCodec codec;

    public PaymentExecutionLocks(PaymentAcceptanceGateway gates, PaymentControlGateway payments, PaymentAcceptanceCodec codec) {
        this.gates = gates;
        this.payments = payments;
        this.codec = codec;
    }

    public LockedPayment lock(String tenant, String paymentId, String originalService) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        var initial = gates.find(tenant, paymentId).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!initial.callerServiceId().equals(originalService)) throw new CommonException(ACTION_FORBIDDEN);
        var parent = gates.lockParent(tenant, initial.payment().orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var gate = gates.lockPayment(tenant, paymentId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var current = payments.lockPayment(tenant, paymentId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        codec.decode(current);
        if (!paymentId.equals(initial.payment().id()) || !paymentId.equals(current.payment().id())
                || !current.callerServiceId().equals(originalService) || !initial.payment().orderId().equals(current.payment().orderId()))
            throw new CommonException(SNAPSHOT_INVALID);
        return PaymentExecutionBindings.locked(current, parent, gate);
    }

    public void requireOpen(LockedPayment locked) {
        PaymentExecutionBindings.openGates(locked);
    }
}
