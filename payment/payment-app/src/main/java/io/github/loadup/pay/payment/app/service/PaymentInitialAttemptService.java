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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.payment.app.converter.PaymentAttemptConverter;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.PAYMENT_EXPIRED;

/**
 * Shared first-attempt creation; caller owns the original parent/payment gates. No submission occurs here.
 */
@Service
public class PaymentInitialAttemptService {
    private final PaymentRouteSelector routes;
    private final PaymentInputAccess inputs;
    private final PaymentAttemptGateway attempts;
    private final PaymentControlGateway payments;
    private final PaymentAcceptanceCodec codec;
    private final PaymentAttemptConverter converter;
    private final PaymentControlConverter changes;
    private final IdGenerator ids;
    private final Clock clock;
    private final PaymentEvents events;

    public PaymentInitialAttemptService(PaymentRouteSelector routes, PaymentInputAccess inputs, PaymentAttemptGateway attempts,
                                        PaymentControlGateway payments, PaymentAcceptanceCodec codec, PaymentAttemptConverter converter,
                                        PaymentControlConverter changes, IdGenerator ids, @Qualifier("paymentClock") Clock clock, PaymentEvents events) {
        this.routes = routes;
        this.inputs = inputs;
        this.attempts = attempts;
        this.payments = payments;
        this.codec = codec;
        this.converter = converter;
        this.changes = changes;
        this.ids = ids;
        this.clock = clock;
        this.events = events;
    }

    public StoredPaymentAcceptance create(String tenant, StoredPaymentAcceptance accepted, String requestKey, String digest) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (!tenant.equals(io.github.loadup.commons.util.TenantUtil.getTenantId()))
            throw new CommonException(ACTION_FORBIDDEN);
        if (accepted.payment().activeAttemptId() != null || accepted.payment().state() != PaymentState.INIT)
            throw new CommonException(INVALID_STATE);
        var original = codec.decode(accepted);
        if (!clock.instant().isBefore(original.payableUntil().toInstant())) throw new CommonException(PAYMENT_EXPIRED);
        var binding = routes.select(tenant, original);
        var preparedAt = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
        inputs.requireNewAcceptance(tenant, accepted.callerServiceId(), original, preparedAt);
        var attempt = PaymentAttempt.init(ids.generate(), accepted.payment().id(), 1, accepted.payment().amount(),
                binding, original.choice().method(), UUID.randomUUID().toString().replace("-", ""));
        var stored = converter.toStored(attempt, accepted.payment().limitOperationId(), accepted.callerServiceId(),
                requestKey, digest, accepted.requestDigest(), original.preparedInputRef(), preparedAt);
        var updated = changes.withPayment(accepted, accepted.payment().prepareAttempt(attempt, null), null);
        attempts.insert(tenant, stored);
        payments.updatePayment(tenant, updated, accepted.payment().version());
        events.publishSnapshot(tenant, updated, preparedAt);
        return updated;
    }
}
