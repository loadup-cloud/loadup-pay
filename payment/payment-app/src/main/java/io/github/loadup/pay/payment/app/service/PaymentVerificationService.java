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
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.converter.PaymentVerificationConverter;
import io.github.loadup.pay.payment.app.port.PaymentVerificationTargetReader;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.enums.PaymentLedgerKind;
import io.github.loadup.pay.payment.client.enums.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.client.facade.PaymentVerificationFacade;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Original submitted owner target and read-only provider query only. No Y2/Y3, close, refund submit, or outbox writes.
 */
@Service
public class PaymentVerificationService implements PaymentVerificationFacade {
    private final PaymentActorProvider actors;
    private final PaymentVerificationTargetReader reader;
    private final PaymentChannelGateway channels;
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentVerificationConverter converter;
    private final Validator validator;
    private final Clock clock;
    private final String reconciliationService;

    public PaymentVerificationService(PaymentActorProvider actors, PaymentVerificationTargetReader reader, PaymentChannelGateway channels,
                                      PaymentAcceptanceTransactions transactions, PaymentVerificationConverter converter, Validator validator, @Qualifier("paymentClock") Clock clock,
                                      @Value("${loadup.modules.payment.reconciliation-service-id:reconciliation}") String reconciliationService) {
        this.actors = actors;
        this.reader = reader;
        this.channels = channels;
        this.transactions = transactions;
        this.converter = converter;
        this.validator = validator;
        this.clock = clock;
        this.reconciliationService = DomainChecks.id(reconciliationService);
    }

    @Override
    public PaymentVerificationDTO verify(PaymentVerificationQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor == null || !reconciliationService.equals(actor.serviceId()) || !actor.permissions().contains("pay:payment:reconciliation:verify"))
            throw new CommonException(ACTION_FORBIDDEN);
        if (query == null || !validator.validate(query).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var started = now();
        return transactions.withTenantContext(actor.tenantId(), () -> {
            var target = transactions.readSnapshot(actor.tenantId(), 10, () -> reader.read(actor.tenantId(), query));
            if (target == null) throw new CommonException(SNAPSHOT_INVALID);
            if (target.disposition() != PaymentVerificationDisposition.OBSERVED)
                return converter.missing(query, target.disposition(), started, now());
            if (query.kind() == PaymentLedgerKind.PAYMENT) {
                if (target.payment() == null) throw new CommonException(SNAPSHOT_INVALID);
                identity(query, target.payment().identity());
                var observation = channel(() -> channels.query(target.payment()));
                if (observation == null || observation.source() != PaymentObservation.Source.QUERY_RESPONSE || !target.payment().identity().equals(observation.identity())
                        || !target.payment().amount().equals(observation.amount()))
                    throw new CommonException(SNAPSHOT_INVALID);
                return converter.payment(query, target, observation, started, now(), clock);
            }
            if (target.refund() == null) throw new CommonException(SNAPSHOT_INVALID);
            identity(query, target.refund().original());
            if (!query.channelRefundNo().equals(target.refund().channelRefundNo()))
                throw new CommonException(SNAPSHOT_INVALID);
            var observation = channel(() -> channels.queryRefund(target.refund()));
            if (observation == null || observation.source() != RefundObservation.Source.QUERY_RESPONSE || !target.refund().refundId().equals(observation.refundId())
                    || !target.refund().original().equals(observation.original()) || !target.refund().channelRefundNo().equals(observation.channelRefundNo())
                    || !target.refund().originalChannelTransactionNo().equals(observation.originalChannelTransactionNo())
                    || !target.refund().amount().equals(observation.amount()))
                throw new CommonException(SNAPSHOT_INVALID);
            return converter.refund(query, target, observation, started, now(), clock);
        });
    }

    private void identity(PaymentVerificationQuery query, ChannelPaymentIdentity identity) {
        if (identity.channel() != query.channel() || !identity.accountScopeHash().equals(query.accountScopeHash()) || !identity.channelRequestNo().equals(query.merchantPaymentNo()))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private <T> T channel(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
