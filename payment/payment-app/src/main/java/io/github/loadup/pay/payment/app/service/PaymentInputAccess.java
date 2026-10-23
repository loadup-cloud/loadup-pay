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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentInputCipher;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInputState;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INPUT_ALREADY_BOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INPUT_EXPIRED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Internal input lifecycle. Decryption does not grant a channel submission or retry permit.
 */
@Component
public class PaymentInputAccess {
    private final PaymentInputGateway inputs;
    private final PaymentAcceptanceGateway payments;
    private final PaymentAcceptanceCodec codec;
    private final PaymentInputCipher cipher;
    private final PaymentAcceptanceTransactions transactions;
    private final Clock clock;

    public PaymentInputAccess(PaymentInputGateway inputs, PaymentAcceptanceGateway payments,
                              PaymentAcceptanceCodec codec, PaymentInputCipher cipher, PaymentAcceptanceTransactions transactions,
                              @Qualifier("paymentClock") Clock clock) {
        this.inputs = inputs;
        this.payments = payments;
        this.codec = codec;
        this.cipher = cipher;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Called only after holding the original parent and single-payment gates.
     */
    public void requireNewAcceptance(String tenant, String service, PaymentExecuteCommand command, LocalDateTime now) {
        requireTransaction();
        if (!requiresInput(command)) return;
        var input = inputs.lock(tenant, command.preparedInputRef()).orElseThrow(() -> new CommonException(NOT_FOUND));
        requireBinding(service, command, input);
        if (input.expired(now)) throw new CommonException(INPUT_EXPIRED);
        if (input.state() != PaymentInputState.INIT) throw new CommonException(INPUT_ALREADY_BOUND);
    }

    /**
     * Joins the Y2 payment/attempt CAS transaction; no network or cryptographic call is performed here.
     */
    public void bindToAttempt(String tenant, StoredPaymentAcceptance accepted, String attemptId, LocalDateTime now) {
        requireTransaction();
        DomainChecks.id(attemptId);
        var command = codec.decode(accepted);
        if (!requiresInput(command)) return;
        var input = inputs.lock(tenant, command.preparedInputRef()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        requireBinding(accepted.callerServiceId(), command, input);
        if (input.expired(now)) throw new CommonException(INPUT_EXPIRED);
        if (input.boundAttemptId() != null && !input.boundAttemptId().equals(attemptId))
            throw new CommonException(INPUT_ALREADY_BOUND);
        var next = input.bind(attemptId, now);
        if (next != input) inputs.bind(tenant, next, input.rowVersion());
    }

    /**
     * Only an already active attempt can read its bound input; there is deliberately no HTTP decrypt endpoint.
     */
    public PaymentInteractionDTO openForAttempt(String tenant, String paymentId, String attemptId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.read(tenant, () -> {
            DomainChecks.id(paymentId);
            DomainChecks.id(attemptId);
            var accepted = payments.find(tenant, paymentId).orElseThrow(() -> new CommonException(NOT_FOUND));
            if (accepted.payment().state() != PaymentState.PROCESSING
                    || !attemptId.equals(accepted.payment().activeAttemptId()))
                throw new CommonException(INVALID_STATE);
            var command = codec.decode(accepted);
            if (!requiresInput(command)) return new NoInteractionDTO();
            var input = inputs.find(tenant, command.preparedInputRef()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            requireBinding(accepted.callerServiceId(), command, input);
            if (input.state() != PaymentInputState.BOUND || !attemptId.equals(input.boundAttemptId()))
                throw new CommonException(INPUT_ALREADY_BOUND);
            if (input.expired(now())) throw new CommonException(INPUT_EXPIRED);
            var interaction = cipher.decrypt(tenant, input);
            if (input.expired(now())) throw new CommonException(INPUT_EXPIRED);
            return interaction;
        });
    }

    private boolean requiresInput(PaymentExecuteCommand command) {
        if (command.choice().method() == PaymentMethod.H5) {
            if (command.preparedInputRef() != null) throw new CommonException(INVALID_ARGUMENT);
            return false;
        }
        if (command.preparedInputRef() == null) throw new CommonException(INVALID_ARGUMENT);
        DomainChecks.id(command.preparedInputRef());
        return true;
    }

    private void requireBinding(String service, PaymentExecuteCommand command, PaymentInput input) {
        if (!service.equals(input.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!input.id().equals(command.preparedInputRef()) || !input.paymentId().equals(command.paymentId())
                || !input.orderId().equals(command.orderId()) || !input.allocationId().equals(command.allocationId())
                || !input.payeeMerchantId().equals(command.payeeMerchantId())
                || input.amount().getCent() != command.amountMinor()
                || !input.amount().getCurrencyValue().equals(command.currency())
                || input.channel() != command.choice().channel() || input.method() != command.choice().method())
            throw new CommonException(IDEMPOTENCY_CONFLICT);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
