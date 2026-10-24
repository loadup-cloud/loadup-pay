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
import io.github.loadup.pay.payment.app.converter.PaymentInputConverter;
import io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.app.port.PaymentInputCipher;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
import io.github.loadup.pay.payment.client.facade.PaymentInputFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INPUT_EXPIRED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;

@Service
public class PaymentInputService implements PaymentInputFacade {
    private final PaymentActorProvider actors;
    private final PaymentInputGateway store;
    private final PaymentInputCipher cipher;
    private final PaymentInputConverter converter;
    private final PaymentChannelInputConverter channelInputs;
    private final PaymentAcceptanceTransactions transactions;
    private final Validator validator;
    private final Clock clock;
    private final String acquireService;

    public PaymentInputService(PaymentActorProvider actors, PaymentInputGateway store, PaymentInputCipher cipher,
                               PaymentInputConverter converter, PaymentChannelInputConverter channelInputs,
                               PaymentAcceptanceTransactions transactions, Validator validator,
                               @Qualifier("paymentClock") Clock clock,
                               @Value("${loadup.modules.payment.acquire-service-id:acquire}") String acquireService) {
        this.actors = actors;
        this.store = store;
        this.cipher = cipher;
        this.converter = converter;
        this.channelInputs = channelInputs;
        this.transactions = transactions;
        this.validator = validator;
        this.clock = clock;
        this.acquireService = DomainChecks.id(acquireService);
    }

    @Override
    public PaymentInputDTO prepare(PaymentInputPrepareCommand command) {
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

    @Override
    public PaymentInputLookupDTO lookup(PaymentInputLookupQuery query) {
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!acquireService.equals(actor.serviceId()) || !actor.permissions().contains("pay:payment:input:read"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(query.paymentId()); DomainChecks.id(query.orderId()); DomainChecks.id(query.allocationId());
            DomainChecks.id(query.payeeMerchantId()); DomainChecks.text(query.inputKey(), 128);
            DomainChecks.positive(query.amountMinor()); DomainChecks.cnyAmount(query.amountMinor(), query.currency());
            var known = store.findByPayment(actor.tenantId(), query.paymentId());
            if (known.isEmpty()) return converter.toLookup(false, null);
            var input = known.get();
            if (!actor.serviceId().equals(input.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
            if (!input.paymentId().equals(query.paymentId()) || !input.orderId().equals(query.orderId())
                    || !input.allocationId().equals(query.allocationId()) || !input.payeeMerchantId().equals(query.payeeMerchantId())
                    || !input.inputKey().equals(query.inputKey()) || input.amount().getCent() != query.amountMinor()
                    || !input.amount().getCurrencyValue().equals(query.currency()) || input.channel() != query.choice().channel()
                    || input.method() != query.choice().method()) throw new CommonException(IDEMPOTENCY_CONFLICT);
            // This is metadata, including for bound/expired inputs. Never decrypt, fingerprint, renew or mutate it.
            return converter.toLookup(true, converter.toDTO(input));
        });
    }

    private void validate(PaymentInputPrepareCommand command) {
        DomainChecks.id(command.paymentId());
        DomainChecks.id(command.orderId());
        DomainChecks.id(command.allocationId());
        DomainChecks.id(command.payeeMerchantId());
        DomainChecks.text(command.inputKey(), 128);
        DomainChecks.positive(command.amountMinor());
        DomainChecks.cnyAmount(command.amountMinor(), command.currency());
        if (command.choice().method() == PaymentMethod.PAYMENT_CODE
                && command.interaction() instanceof PaymentCodeInteractionDTO code) {
            DomainChecks.text(code.paymentCode(), 4096);
        } else if (command.choice().method() == PaymentMethod.JSAPI
                && command.interaction() instanceof ChannelIdentityInteractionDTO identity
                && identity.channel() == command.choice().channel()) {
            DomainChecks.text(identity.identifier(), 128);
            if (identity.identityType() == null) throw new CommonException(INVALID_ARGUMENT);
            DomainChecks.id(identity.appScope());
        } else if (command.choice().method() == PaymentMethod.H5
                && command.interaction() instanceof H5SceneInteractionDTO scene) {
            channelInputs.toH5(scene);
        } else throw new CommonException(INVALID_ARGUMENT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
