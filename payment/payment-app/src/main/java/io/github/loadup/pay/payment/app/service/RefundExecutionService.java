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
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.converter.RefundExecutionConverter;
import io.github.loadup.pay.payment.app.model.RefundExecutionWork;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition;
import io.github.loadup.pay.payment.domain.model.RefundState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.OUTCOME_UNKNOWN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * One serial step. Durable scheduling and verified channel binders supply invocations; no merchant success endpoint.
 */
@Service
public class RefundExecutionService {
    private final ObjectProvider<PaymentChannelGateway> channels;
    private final RefundExecutionLocks locks;
    private final RefundResultGateway changes;
    private final RefundResultService results;
    private final RefundExecutionConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final Clock clock;
    private final RefundProjectionEvents projections;

    public RefundExecutionService(ObjectProvider<PaymentChannelGateway> channels, RefundExecutionLocks locks, RefundResultGateway changes,
                                  RefundResultService results, RefundExecutionConverter converter, PaymentAcceptanceTransactions transactions,
                                  Clock clock, RefundProjectionEvents projections) {
        this.channels = channels;
        this.locks = locks;
        this.changes = changes;
        this.results = results;
        this.converter = converter;
        this.transactions = transactions;
        this.clock = clock;
        this.projections = projections;
    }

    public void progress(String tenant, String requestId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        DomainChecks.id(tenant);
        DomainChecks.id(requestId);
        var channel = channels.getIfAvailable();
        if (channel == null) {
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        }
        var work = transactions.writeLocal(tenant, () -> prepare(tenant, requestId, channel));
        if (work.isEmpty()) {
            return;
        }
        var step = work.get();
        final RefundObservation observation;
        try {
            observation = transactions.withTenantContext(tenant, () -> step.firstSubmission()
                    ? channel.submitRefund(step.prepared()) : channel.recoverRefund(step.prepared()));
        } catch (RuntimeException unavailable) {
            results.uncertain(tenant, requestId, step.prepared().refundId());
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
        boolean validSource = observation != null && (step.firstSubmission()
                ? observation.source() == RefundObservation.Source.SUBMIT_RESPONSE
                : observation.source() == RefundObservation.Source.QUERY_RESPONSE || observation.source() == RefundObservation.Source.SUBMIT_RESPONSE
                && (observation.kind() == ChannelRefundOutcome.Kind.ACCEPTED
                || observation.kind() == ChannelRefundOutcome.Kind.UNKNOWN));
        if (!validSource || !observation.refundId().equals(step.prepared().refundId())) {
            results.uncertain(tenant, requestId, step.prepared().refundId());
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
        var application = results.record(tenant, observation);
        if (application.disposition() == RefundReceiptDisposition.CONFLICT) {
            throw new CommonException(OUTCOME_UNKNOWN);
        }
    }

    private Optional<RefundExecutionWork> prepare(String tenant, String requestId, PaymentChannelGateway channel) {
        var stored = locks.request(tenant, requestId);
        var first = stored.allocations().stream().filter(c -> !c.refund().terminal()).findFirst();
        if (first.isEmpty()) {
            return Optional.empty();
        }
        var locked = locks.child(tenant, stored, first.get().refund().id());
        var prepared = converter.toPrepared(locked.child().child(), locked.capacity());
        // Strictly a local registry lookup. Capability checks must not load credentials or make network requests.
        if (!channel.canRefund(prepared)) {
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        }
        boolean winner = first.get().refund().state() == RefundState.INIT;
        if (winner) {
            var started = first.get().refund().beginSubmission();
            var change = new RefundProgressChange(locked.child(), started, stored.request(), stored.request().apply(started),
                    locked.capacity(), locked.capacity(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS), null, null);
            changes.update(tenant, change);
            projections.changed(tenant, change);
        }
        return Optional.of(new RefundExecutionWork(requestId, prepared, winner));
    }
}
