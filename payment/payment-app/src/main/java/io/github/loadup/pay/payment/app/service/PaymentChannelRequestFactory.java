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
import io.github.loadup.pay.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.PAYMENT_EXPIRED;

/**
 * Resolves only the original winner's submitted attempt and bound input; never an HTTP entry point.
 */
@Component
public class PaymentChannelRequestFactory {
    private final PaymentAcceptanceGateway payments;
    private final PaymentAttemptGateway attempts;
    private final PaymentAcceptanceCodec codec;
    private final PaymentInputAccess inputs;
    private final PaymentChannelInputConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final Clock clock;

    public PaymentChannelRequestFactory(PaymentAcceptanceGateway payments, PaymentAttemptGateway attempts,
                                        PaymentAcceptanceCodec codec, PaymentInputAccess inputs, PaymentChannelInputConverter converter,
                                        PaymentAcceptanceTransactions transactions, Clock clock) {
        this.payments = payments;
        this.attempts = attempts;
        this.codec = codec;
        this.inputs = inputs;
        this.converter = converter;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ChannelPaymentRequest create(String tenant, PreparedChannelPayment prepared) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (prepared == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        return transactions.withTenantContext(tenant, () -> {
            var accepted = payments.find(tenant, prepared.identity().paymentId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var stored = attempts.find(tenant, prepared.identity().attemptId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var binding = PaymentExecutionBindings.submissionRequest(accepted, stored);
            var attempt = stored.attempt();
            if (!attempt.identity().equals(prepared.identity()) || !attempt.amount().equals(prepared.amount())
                    || attempt.method() != prepared.method() || attempt.executionEpoch() != prepared.executionEpoch()
                    || !Objects.equals(stored.preparedInputRef(), prepared.preparedInputRef())) {
                throw new CommonException(INVALID_STATE);
            }
            var original = codec.decode(accepted);
            PaymentExecutionBindings.route(binding, original.choice().channel(), original.choice().method(), original.preparedInputRef());
            if (!clock.instant().isBefore(original.payableUntil().toInstant())) {
                throw new CommonException(PAYMENT_EXPIRED);
            }
            var input = inputs.openForAttempt(tenant, prepared.identity().paymentId(), prepared.identity().attemptId());
            return converter.toRequest(prepared, original, converter.toInput(input));
        });
    }
}

