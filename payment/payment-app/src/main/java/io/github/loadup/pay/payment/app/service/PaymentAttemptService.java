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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.converter.PaymentAttemptConverter;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.facade.PaymentAttemptFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.OUTCOME_UNKNOWN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class PaymentAttemptService implements PaymentAttemptFacade {
    private final PaymentActorProvider actors;
    private final PaymentExecutionLocks locks;
    private final PaymentAttemptGateway attempts;
    private final PaymentControlGateway payments;
    private final PaymentAcceptanceCodec codec;
    private final PaymentRouteSelector routes;
    private final PaymentLimitClient limits;
    private final PaymentInputAccess inputs;
    private final PaymentAttemptConverter converter;
    private final PaymentControlConverter changes;
    private final PaymentAcceptanceConverter views;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final String acquireService;

    public PaymentAttemptService(PaymentActorProvider actors, PaymentExecutionLocks locks, PaymentAttemptGateway attempts,
                                 PaymentControlGateway payments, PaymentAcceptanceCodec codec, PaymentRouteSelector routes, PaymentInputAccess inputs, PaymentLimitClient limits,
                                 PaymentAttemptConverter converter, PaymentControlConverter changes, PaymentAcceptanceConverter views,
                                 PaymentAcceptanceTransactions transactions, IdGenerator ids, Validator validator,
                                 @Qualifier("paymentClock") Clock clock, @Value("${loadup.modules.payment.acquire-service-id:acquire}") String acquireService) {
        this.actors = actors;
        this.locks = locks;
        this.attempts = attempts;
        this.payments = payments;
        this.codec = codec;
        this.routes = routes;
        this.inputs = inputs;
        this.limits = limits;
        this.converter = converter;
        this.changes = changes;
        this.views = views;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.acquireService = DomainChecks.id(acquireService);
    }

    @Override
    public PaymentExecutionDTO startAttempt(PaymentAttemptStartCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!acquireService.equals(actor.serviceId()) || !actor.permissions().contains("pay:payment:attempt:start"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!limits.supportsTenant(actor.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.write(actor.tenantId(), () -> {
            DomainChecks.id(command.paymentId());
            DomainChecks.text(command.requestKey(), 128);
            var locked = locks.lock(actor.tenantId(), command.paymentId(), actor.serviceId());
            var accepted = locked.payment();
            var original = codec.decode(accepted);
            String digest = digest(actor, command, accepted.requestDigest());
            var prior = attempts.findRequest(actor.tenantId(), command.paymentId(), command.requestKey());
            if (prior.isPresent()) {
                var attempt = prior.get();
                if (!attempt.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
                if (!attempt.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
                if (!attempt.paymentRequestDigest().equals(accepted.requestDigest()))
                    throw new CommonException(SNAPSHOT_INVALID);
                return views.toDTO(accepted);
            }
            locks.requireOpen(locked);
            if (!command.choice().equals(original.choice()) || !Objects.equals(command.preparedInputRef(), original.preparedInputRef()))
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            if (accepted.payment().activeAttemptId() != null) throw new CommonException(OUTCOME_UNKNOWN);
            if (accepted.payment().state() != PaymentState.INIT) throw new CommonException(INVALID_STATE);
            if (command.previousAttemptEvidenceId() != null) throw new CommonException(INVALID_ARGUMENT);
            var binding = routes.select(actor.tenantId(), original);
            var preparedAt = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
            inputs.requireNewAcceptance(actor.tenantId(), actor.serviceId(), original, preparedAt);
            var attempt = PaymentAttempt.init(ids.generate(), command.paymentId(), 1, accepted.payment().amount(),
                    binding, original.choice().method(), UUID.randomUUID().toString().replace("-", ""));
            var stored = converter.toStored(attempt, accepted.payment().limitOperationId(), actor.serviceId(),
                    command.requestKey(), digest, accepted.requestDigest(), original.preparedInputRef(), preparedAt);
            var updated = changes.withPayment(accepted, accepted.payment().prepareAttempt(attempt, null), null);
            attempts.insert(actor.tenantId(), stored);
            payments.updatePayment(actor.tenantId(), updated, accepted.payment().version());
            return views.toDTO(updated);
        });
    }

    private String digest(PaymentActor actor, PaymentAttemptStartCommand command, String paymentDigest) {
        var framed = new StringBuilder();
        for (String field : Arrays.asList("payment-attempt-start-v1", actor.tenantId(), actor.serviceId(), command.paymentId(),
                command.requestKey(), command.choice().channel().name(), command.choice().method().name(),
                command.preparedInputRef(), command.previousAttemptEvidenceId(), paymentDigest)) {
            if (field == null) framed.append("-1:");
            else framed.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
        return codec.checksum(framed.toString());
    }
}
