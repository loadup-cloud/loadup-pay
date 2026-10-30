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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.OrderClosureConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentClosureClient;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCloseCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import jakarta.validation.Validator;
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
public class OrderClosureOperations {
    private static final int BATCH_SIZE = 16;
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
    private final OrderClosureTasks tasks;

    public OrderClosureOperations(OrderCreationGateway orders, OrderClosureGateway store,
                                  PaymentClosureClient payment, PaymentOutcomeOperations outcomes, OrderClosureConverter converter,
                                  AcquireOrderConverter orderConverter, OrderCreationCodec codec, OrderCreationTransactions transactions,
                                  IdGenerator ids, Validator validator, Clock clock, OrderSnapshotEvents snapshots, OrderClosureTasks tasks) {
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
        this.tasks = tasks;
    }

    AcquireOrderDTO close(OrderPaymentScope actor, AcquireOrderCloseCommand command) {
        requireNoTransaction(actor);
        if (command == null || !validator.validate(command).isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        return transactions.withTenantContext(actor.tenantId(), () -> {
            var intent = transactions.writeLocal(actor.tenantId(), () -> {
                var order = orders.lockOrder(actor.tenantId(), command.id());
                return startLocked(actor, order, command);
            });
            return resume(actor, intent);
        });
    }

    /**
     * Caller holds the order lock; C0 and its first native continuation commit atomically.
     */
    OrderCloseIntent startLocked(OrderPaymentScope actor, StoredAcquireOrder order, AcquireOrderCloseCommand command) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
        if (!actor.tenantId().equals(io.github.loadup.commons.util.TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (command == null || !validator.validate(command).isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        DomainChecks.id(command.id());
        DomainChecks.text(command.requestKey(), 128);
        DomainChecks.text(command.reasonCode(), 64);
        owned(actor, order);
        if (!order.order().id().equals(command.id())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        String digest = digest(actor, command);
        var prior = store.findIntent(actor.tenantId(), command.id());
        if (prior.isPresent()) {
            var value = prior.get();
            value.requireSameRequest(actor.scopeHash(), command.requestKey(), digest);
            bound(order, value);
            return value;
        }
        if (order.order().state() != AcquireOrderState.INIT && order.order().state() != AcquireOrderState.PENDING) {
            throw new CommonException(INVALID_STATE);
        }
        var closing = order.order().requestClose();
        var intent = converter.toIntent(command, closing, ids.generate(), actor.scopeHash(), digest, now());
        store.start(actor.tenantId(), closing, intent, order.order().version());
        tasks.register(actor.tenantId(), intent);
        snapshots.publish(actor.tenantId(), closing.id(), closing.version());
        return intent;
    }

    /**
     * Continue the persisted request without requiring a live merchant token or creating another C0.
     */
    AcquireOrderDTO resume(OrderPaymentScope actor, OrderCloseIntent intent) {
        requireNoTransaction(actor);
        return transactions.withTenantContext(actor.tenantId(), () -> {
            var original = current(actor, intent);
            if (terminal(original.order())) {
                return orderConverter.toDTO(original, now());
            }
            var fenced = payment.fenceParent(actor.tenantId(), converter.toFence(intent));
            requireParent(intent, fenced);
            var batch = store.heldAfter(actor.tenantId(), intent.orderId(), intent.cursorPaymentId(), BATCH_SIZE);
            if (batch.isEmpty() && !intent.cursorPaymentId().isEmpty()) {
                batch = store.heldAfter(actor.tenantId(), intent.orderId(), "", BATCH_SIZE);
            }
            for (var allocation : batch) {
                outcomes.bound(new OrderPaymentScope(actor.tenantId(), actor.scopeHash()), original, allocation);
                var request = converter.toPaymentClose(intent, allocation);
                var closed = payment.close(actor.tenantId(), request);
                outcomes.applyClose(new OrderPaymentScope(actor.tenantId(), actor.scopeHash()), request, closed);
            }
            String nextCursor = batch.size() == BATCH_SIZE ? batch.getLast().allocation().paymentId() : "";
            advance(actor, intent, nextCursor);
            var status = payment.parentFenceStatus(actor.tenantId(), converter.toStatusQuery(intent));
            requireParent(intent, status);
            // Fencing alone never proves that all local holds or remote collection attempts have converged.
            return transactions.writeLocal(actor.tenantId(), () -> {
                var order = orders.lockOrder(actor.tenantId(), intent.orderId());
                owned(actor, order);
                bound(order, store.findIntent(actor.tenantId(), intent.orderId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)));
                if (terminal(order.order())) {
                    return orderConverter.toDTO(order, now());
                }
                if (!status.allChildrenConverged() || order.order().paymentReservedMinor() != 0
                        || order.order().reservedPaymentCount() != 0 || !store.allAllocationsSettled(actor.tenantId(), intent.orderId())) {
                    return orderConverter.toDTO(order, now());
                }
                var closed = order.order().finishClose(converter.toEvidence(intent, status));
                store.finish(actor.tenantId(), closed, order.order().version(), now());
                snapshots.publish(actor.tenantId(), closed.id(), closed.version());
                return orderConverter.toDTO(orders.findOrder(actor.tenantId(), intent.orderId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)), now());
            });
        });
    }

    /**
     * Checks the supplied consistent view without acquiring locks or starting another close request.
     */
    boolean isCurrent(OrderPaymentScope actor, StoredAcquireOrder order, OrderCloseIntent intent, long generation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        owned(actor, order);
        bound(order, intent);
        if (generation > intent.rowVersion()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return generation == intent.rowVersion() && !terminal(order.order());
    }

    private void requireNoTransaction(OrderPaymentScope actor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (!payment.supportsTenant(actor.tenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }

    private void advance(OrderPaymentScope actor, OrderCloseIntent attempted, String nextCursor) {
        transactions.writeLocal(actor.tenantId(), () -> {
            var order = orders.lockOrder(actor.tenantId(), attempted.orderId());
            owned(actor, order);
            var latest = store.findIntent(actor.tenantId(), attempted.orderId())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            bound(order, latest);
            latest.requireSameRequest(attempted.callerScopeHash(), attempted.requestKey(), attempted.requestDigest());
            if (!terminal(order.order()) && latest.rowVersion() == attempted.rowVersion()) {
                var advanced = latest.advanceCursor(nextCursor);
                store.advanceCursor(actor.tenantId(), advanced, latest.rowVersion());
                tasks.schedule(actor.tenantId(), advanced, now());
            }
            return true;
        });
    }

    private StoredAcquireOrder current(OrderPaymentScope actor, OrderCloseIntent intent) {
        var order = orders.findOrder(actor.tenantId(), intent.orderId())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        owned(actor, order);
        bound(order, intent);
        return order;
    }

    private void bound(StoredAcquireOrder order, OrderCloseIntent intent) {
        if (!order.order().id().equals(intent.orderId()) || !order.order().payeeMerchantId().equals(intent.payeeMerchantId())
                || !order.callerScopeHash().equals(intent.callerScopeHash()) || order.order().closeEpoch() != intent.closeEpoch()
                || !(order.order().state() == AcquireOrderState.CLOSING || terminal(order.order()))) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void requireParent(OrderCloseIntent intent, ParentFenceDTO response) {
        if (response == null || !validator.validate(response).isEmpty() || !intent.orderId().equals(response.orderId())
                || !response.fenced() || response.closeEpoch() < intent.closeEpoch() || response.unknownCount() < 0
                || response.allChildrenConverged() && response.unknownCount() != 0) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private boolean terminal(AcquireOrder order) {
        return order.state() == AcquireOrderState.PAID || order.state() == AcquireOrderState.CLOSED;
    }

    private void owned(OrderPaymentScope actor, StoredAcquireOrder order) {
        if (!order.callerScopeHash().equals(actor.scopeHash())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }

    private String digest(OrderPaymentScope actor, AcquireOrderCloseCommand command) {
        var canonical = new StringBuilder();
        for (String field : List.of("acquire.order-close.v1", actor.tenantId(), actor.scopeHash(), command.id(), command.requestKey(), command.reasonCode())) {
            canonical.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
        return codec.checksum(canonical.toString());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
