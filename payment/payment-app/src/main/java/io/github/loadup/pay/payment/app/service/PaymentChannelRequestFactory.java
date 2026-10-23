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
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** Resolves only the original winner's submitted attempt and bound input; never an HTTP entry point. */
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
            PaymentAcceptanceTransactions transactions, @Qualifier("paymentClock") Clock clock) {
        this.payments = payments;
        this.attempts = attempts;
        this.codec = codec;
        this.inputs = inputs;
        this.converter = converter;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ChannelPaymentRequest create(String tenant, PreparedChannelPayment prepared) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (prepared == null) throw new CommonException(INVALID_ARGUMENT);
        return transactions.read(tenant, () -> {
            var accepted = payments.find(tenant, prepared.identity().paymentId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var stored = attempts.find(tenant, prepared.identity().attemptId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var attempt = stored.attempt();
            if (accepted.payment().state() != PaymentState.PROCESSING || attempt.state() != PaymentAttemptState.SUBMITTING
                    || !attempt.id().equals(accepted.payment().activeAttemptId())
                    || !attempt.identity().equals(prepared.identity()) || !attempt.amount().equals(prepared.amount())
                    || !accepted.payment().id().equals(attempt.paymentId())
                    || !accepted.payment().amount().equals(prepared.amount())
                    || !accepted.payment().payeeMerchantId().equals(attempt.binding().payeeMerchantId())
                    || attempt.method() != prepared.method() || attempt.executionEpoch() != prepared.executionEpoch()
                    || !Objects.equals(stored.preparedInputRef(), prepared.preparedInputRef())
                    || !accepted.payment().limitOperationId().equals(stored.limitOperationId())
                    || stored.executionPermitHash() == null || stored.riskDecision() == null
                    || stored.riskDecision().decision() == io.github.loadup.pay.payment.domain.model.RiskDecisionType.DENY
                    || !accepted.requestDigest().equals(stored.paymentRequestDigest())
                    || !accepted.callerServiceId().equals(stored.callerServiceId()))
                throw new CommonException(INVALID_STATE);
            var original = codec.decode(accepted);
            if (!Objects.equals(original.preparedInputRef(), prepared.preparedInputRef()))
                throw new CommonException(SNAPSHOT_INVALID);
            if (!clock.instant().isBefore(original.payableUntil().toInstant())) throw new CommonException(PAYMENT_EXPIRED);
            var input = inputs.openForAttempt(tenant, prepared.identity().paymentId(), prepared.identity().attemptId());
            return converter.toRequest(prepared, original, converter.toInput(input));
        });
    }
}

