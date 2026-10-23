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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.OrderClosureConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentClosureClient;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCloseCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderClosureFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class OrderClosureService implements AcquireOrderClosureFacade {
    private static final int BATCH_SIZE = 16;
    private final AcquireActorProvider actors;
    private final OrderCreationGateway orders;
    private final OrderClosureGateway store;
    private final PaymentClosureClient payment;
    private final PaymentOutcomeOperations outcomes;
    private final OrderClosureConverter converter;
    private final AcquireOrderConverter orderConverter;
    private final OrderCreationCodec codec;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final OrderSnapshotEvents snapshots;

    public OrderClosureService(AcquireActorProvider actors, OrderCreationGateway orders, OrderClosureGateway store,
                               PaymentClosureClient payment, PaymentOutcomeOperations outcomes, OrderClosureConverter converter,
                               AcquireOrderConverter orderConverter, OrderCreationCodec codec, OrderCreationTransactions transactions,
                               IdGenerator ids, Validator validator, @Qualifier("acquireClock") Clock clock, OrderSnapshotEvents snapshots) {
        this.actors = actors;
        this.orders = orders;
        this.store = store;
        this.payment = payment;
        this.outcomes = outcomes;
        this.converter = converter;
        this.orderConverter = orderConverter;
        this.codec = codec;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.snapshots = snapshots;
    }

    @Override
    public AcquireOrderDTO close(AcquireOrderCloseCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!actor.permissions().contains("pay:acquire:close")) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!payment.supportsTenant(actor.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(command.id());
            DomainChecks.text(command.requestKey(), 128);
            DomainChecks.text(command.reasonCode(), 64);
            String digest = digest(actor, command);
            // C0 is durable before network calls, so new A2 holds cannot enter the order again.
            var intent = transactions.write(actor.tenantId(), () -> {
                var order = orders.lockOrder(actor.tenantId(), command.id());
                owned(actor, order);
                var prior = store.findIntent(actor.tenantId(), command.id());
                if (prior.isPresent()) {
                    var value = prior.get();
                    value.requireSameRequest(actor.scopeHash(), command.requestKey(), digest);
                    bound(order, value);
                    return value;
                }
                if (order.order().state() != AcquireOrderState.INIT && order.order().state() != AcquireOrderState.PENDING)
                    throw new CommonException(INVALID_STATE);
                var closing = order.order().requestClose();
                var value = converter.toIntent(command, closing, ids.generate(), actor.scopeHash(), digest, now());
                store.start(actor.tenantId(), closing, value, order.order().version());
                snapshots.publish(actor.tenantId(), closing.id(), closing.version());
                return value;
            });
            var original = current(actor, intent);
            if (terminal(original.order())) return orderConverter.toDTO(original, now());
            var fenced = payment.fenceParent(actor.tenantId(), converter.toFence(intent));
            requireParent(intent, fenced);
            var batch = store.heldAfter(actor.tenantId(), intent.orderId(), intent.cursorPaymentId(), BATCH_SIZE);
            if (batch.isEmpty() && !intent.cursorPaymentId().isEmpty())
                batch = store.heldAfter(actor.tenantId(), intent.orderId(), "", BATCH_SIZE);
            for (var allocation : batch) {
                outcomes.bound(actor, original, allocation);
                var closed = payment.close(actor.tenantId(), converter.toPaymentClose(intent, allocation));
                outcomes.applyClose(actor, intent, allocation, closed);
            }
            String nextCursor = batch.size() == BATCH_SIZE ? batch.getLast().allocation().paymentId() : "";
            advance(actor, intent, nextCursor);
            var status = payment.parentFenceStatus(actor.tenantId(), converter.toStatusQuery(intent));
            requireParent(intent, status);
            // Fencing alone never proves that all local holds or remote collection attempts have converged.
            return transactions.write(actor.tenantId(), () -> {
                var order = orders.lockOrder(actor.tenantId(), intent.orderId());
                owned(actor, order);
                bound(order, store.findIntent(actor.tenantId(), intent.orderId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)));
                if (terminal(order.order())) return orderConverter.toDTO(order, now());
                if (!status.allChildrenConverged() || order.order().paymentReservedMinor() != 0
                        || order.order().reservedPaymentCount() != 0 || !store.allAllocationsSettled(actor.tenantId(), intent.orderId()))
                    return orderConverter.toDTO(order, now());
                var closed = order.order().finishClose(converter.toEvidence(intent, status));
                store.finish(actor.tenantId(), closed, order.order().version(), now());
                snapshots.publish(actor.tenantId(), closed.id(), closed.version());
                return orderConverter.toDTO(orders.findOrder(actor.tenantId(), intent.orderId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)), now());
            });
        });
    }

    private void advance(AcquireActor actor, OrderCloseIntent attempted, String nextCursor) {
        transactions.write(actor.tenantId(), () -> {
            var order = orders.lockOrder(actor.tenantId(), attempted.orderId());
            owned(actor, order);
            var latest = store.findIntent(actor.tenantId(), attempted.orderId())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            bound(order, latest);
            latest.requireSameRequest(attempted.callerScopeHash(), attempted.requestKey(), attempted.requestDigest());
            if (!terminal(order.order()) && latest.rowVersion() == attempted.rowVersion())
                store.advanceCursor(actor.tenantId(), latest.advanceCursor(nextCursor), latest.rowVersion());
            return true;
        });
    }

    private StoredAcquireOrder current(AcquireActor actor, OrderCloseIntent intent) {
        var order = orders.findOrder(actor.tenantId(), intent.orderId())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        owned(actor, order);
        bound(order, intent);
        return order;
    }

    private void bound(StoredAcquireOrder order, OrderCloseIntent intent) {
        if (!order.order().id().equals(intent.orderId()) || !order.order().payeeMerchantId().equals(intent.payeeMerchantId())
                || !order.callerScopeHash().equals(intent.callerScopeHash()) || order.order().closeEpoch() != intent.closeEpoch()
                || !(order.order().state() == AcquireOrderState.CLOSING || terminal(order.order())))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private void requireParent(OrderCloseIntent intent, ParentFenceDTO response) {
        if (response == null || !validator.validate(response).isEmpty() || !intent.orderId().equals(response.orderId())
                || !response.fenced() || response.closeEpoch() < intent.closeEpoch() || response.unknownCount() < 0
                || response.allChildrenConverged() && response.unknownCount() != 0)
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private boolean terminal(AcquireOrder order) {
        return order.state() == AcquireOrderState.PAID || order.state() == AcquireOrderState.CLOSED;
    }

    private void owned(AcquireActor actor, StoredAcquireOrder order) {
        if (!order.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private String digest(AcquireActor actor, AcquireOrderCloseCommand command) {
        var canonical = new StringBuilder();
        for (String field : List.of("acquire.order-close.v1", actor.tenantId(), actor.scopeHash(), command.id(), command.requestKey(), command.reasonCode()))
            canonical.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        return codec.checksum(canonical.toString());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
