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
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.app.converter.PaymentInputConverter;
import io.github.loadup.pay.payment.app.port.PaymentInputCipher;
import io.github.loadup.pay.payment.app.security.*;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.facade.PaymentInputFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

@Service
public class PaymentInputService implements PaymentInputFacade {
    private final PaymentActorProvider actors;
    private final PaymentInputGateway store;
    private final PaymentInputCipher cipher;
    private final PaymentInputConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final Validator validator;
    private final Clock clock;
    private final String acquireService;

    public PaymentInputService(PaymentActorProvider actors, PaymentInputGateway store, PaymentInputCipher cipher,
            PaymentInputConverter converter, PaymentAcceptanceTransactions transactions, Validator validator,
            @Qualifier("paymentClock") Clock clock,
            @Value("${loadup.modules.payment.acquire-service-id:acquire}") String acquireService) {
        this.actors = actors; this.store = store; this.cipher = cipher; this.converter = converter;
        this.transactions = transactions; this.validator = validator; this.clock = clock;
        this.acquireService = DomainChecks.id(acquireService);
    }
    @Override public PaymentInputDTO prepare(PaymentInputPrepareCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!acquireService.equals(actor.serviceId()) || !actor.permissions().contains("pay:payment:input:prepare"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.read(actor.tenantId(), () -> {
            validate(command);
            var prior = store.findByPayment(actor.tenantId(), command.paymentId());
            if (prior.isPresent()) return replay(actor, command, prior.get());
            var preparedAt = now();
            String reference = cipher.newReference();
            String keyId = cipher.activeFingerprintKeyId();
            String digest = cipher.fingerprint(actor.tenantId(), actor.serviceId(), command, keyId);
            // KMS is remote and must complete before any business database locks are acquired.
            var encrypted = cipher.encrypt(actor.tenantId(), actor.serviceId(), reference, command);
            var input = converter.toInput(command, reference, actor.serviceId(), keyId, digest, encrypted,
                    preparedAt, preparedAt.plus(cipher.lifetime()));
            return transactions.write(actor.tenantId(), () -> {
                var winner = store.findByPayment(actor.tenantId(), command.paymentId());
                if (winner.isPresent()) return replay(actor, command, winner.get());
                if (input.expired(now())) throw new CommonException(INPUT_EXPIRED);
                store.insert(actor.tenantId(), input);
                return converter.toDTO(input);
            });
        });
    }
    private PaymentInputDTO replay(PaymentActor actor, PaymentInputPrepareCommand command, PaymentInput prior) {
        if (!actor.serviceId().equals(prior.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
        String supplied = cipher.fingerprint(actor.tenantId(), actor.serviceId(), command, prior.fingerprintKeyId());
        if (!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.US_ASCII), prior.inputDigest().getBytes(StandardCharsets.US_ASCII)))
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        // Retain the original reference and expiry, including after expiry or attempt binding.
        return converter.toDTO(prior);
    }
    private void validate(PaymentInputPrepareCommand command) {
        DomainChecks.id(command.paymentId()); DomainChecks.id(command.orderId());
        DomainChecks.id(command.allocationId()); DomainChecks.id(command.payeeMerchantId());
        DomainChecks.text(command.inputKey(), 128); DomainChecks.positive(command.amountMinor());
        DomainChecks.amount(command.amountMinor(), command.currency());
        if (command.choice().method() == PaymentMethod.PAYMENT_CODE
                && command.interaction() instanceof PaymentCodeInteractionDTO code) {
            DomainChecks.text(code.paymentCode(), 4096);
        } else if (command.choice().method() == PaymentMethod.JSAPI
                && command.interaction() instanceof ChannelIdentityInteractionDTO identity
                && identity.channel() == command.choice().channel()) {
            DomainChecks.text(identity.authorizationTicket(), 4096); DomainChecks.id(identity.appScope());
        } else throw new CommonException(INVALID_ARGUMENT);
    }
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
