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
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.payment.app.converter.PaymentCloseEvidenceConverter;
import io.github.loadup.pay.payment.app.model.PaymentResultApplication;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentCloseEvidenceGateway;
import io.github.loadup.pay.payment.domain.model.PaymentCloseEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Preserves verified unpaid-close acknowledgements across Y3 rollback without creating a new submission right.
 */
@Service
public class PaymentCloseService {
    private final PaymentAcceptanceGateway payments;
    private final PaymentAttemptGateway attempts;
    private final PaymentExecutionLocks locks;
    private final PaymentCloseEvidenceGateway evidence;
    private final PaymentChannelGateway channels;
    private final PaymentResultService results;
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentCloseEvidenceConverter converter;
    private final Clock clock;

    public PaymentCloseService(PaymentAcceptanceGateway payments, PaymentAttemptGateway attempts, PaymentExecutionLocks locks,
                               PaymentCloseEvidenceGateway evidence, PaymentChannelGateway channels, PaymentResultService results,
                               PaymentAcceptanceTransactions transactions, PaymentCloseEvidenceConverter converter,
                               Clock clock) {
        this.payments = payments;
        this.attempts = attempts;
        this.locks = locks;
        this.evidence = evidence;
        this.channels = channels;
        this.results = results;
        this.transactions = transactions;
        this.converter = converter;
        this.clock = clock;
    }

    public PaymentResultApplication close(String tenant, StoredPaymentAttempt original) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        DomainChecks.id(tenant);
        if (original == null || original.attempt().executionEpoch() <= 0) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        return transactions.withTenantContext(tenant, () -> closeOriginal(tenant, original));
    }

    private PaymentResultApplication closeOriginal(String tenant, StoredPaymentAttempt original) {
        var saved = transactions.readSnapshot(tenant, () -> {
            var current = attempts.find(tenant, original.attempt().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var accepted = payments.find(tenant, original.attempt().paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            PaymentExecutionBindings.observable(accepted, current);
            sameAttempt(original, current);
            var known = checked(evidence.find(tenant, current.attempt().id()), current);
            if (known.isEmpty() && accepted.payment().state() != PaymentState.CLOSING) {
                throw new CommonException(INVALID_STATE);
            }
            return known;
        });
        if (saved.isPresent()) {
            return results.record(tenant, converter.toObservation(saved.get()));
        }

        var target = new ChannelPaymentTarget(original.attempt().identity(), original.attempt().amount());
        var observed = channels.close(target);
        if (observed == null || !target.identity().equals(observed.identity()) || !target.amount().equals(observed.amount())
                || observed.source() != PaymentObservation.Source.QUERY_RESPONSE && observed.source() != PaymentObservation.Source.CLOSE_RESPONSE) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (observed.kind() != ChannelPaymentOutcome.Kind.NO_COLLECTION) {
            return results.record(tenant, observed);
        }
        if (observed.source() != PaymentObservation.Source.CLOSE_RESPONSE) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var recordedAt = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
        var proof = transactions.writeLocal(tenant, () -> {
            // The same parent -> payment gate -> payment -> attempt order as Y3; no remote IO under these locks.
            var locked = locks.lock(tenant, original.attempt().paymentId(), original.callerServiceId());
            var current = attempts.lock(tenant, original.attempt().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            PaymentExecutionBindings.observableContext(locked, current);
            sameAttempt(original, current);
            var prior = checked(evidence.find(tenant, current.attempt().id()), current);
            if (prior.isPresent()) {
                return prior.get();
            }
            var value = converter.toEvidence(observed, current.attempt().executionEpoch(), recordedAt);
            evidence.insert(tenant, value);
            return value;
        });
        // Failure here leaves the immutable proof committed. A later recovery applies this original observation only.
        return results.record(tenant, converter.toObservation(proof));
    }

    private Optional<PaymentCloseEvidence> checked(Optional<PaymentCloseEvidence> value, StoredPaymentAttempt current) {
        value.ifPresent(proof -> {
            if (!proof.identity().equals(current.attempt().identity()) || !proof.amount().equals(current.attempt().amount())
                    || proof.executionEpoch() != current.attempt().executionEpoch()) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        });
        return value;
    }

    private void sameAttempt(StoredPaymentAttempt original, StoredPaymentAttempt current) {
        if (!original.attempt().identity().equals(current.attempt().identity()) || !original.attempt().amount().equals(current.attempt().amount())
                || original.attempt().method() != current.attempt().method() || original.attempt().executionEpoch() != current.attempt().executionEpoch()
                || !original.callerServiceId().equals(current.callerServiceId()) || !original.paymentRequestDigest().equals(current.paymentRequestDigest())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
