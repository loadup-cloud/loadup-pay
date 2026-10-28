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
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.OrderExtensionConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.pay.acquire.client.command.AcquireOrderExtendCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderExtensionFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderExtensionGateway;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ORDER_NOT_PAYABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.VERSION_CONFLICT;

@Service
public class OrderExtensionService implements AcquireOrderExtensionFacade {
    private final AcquireActorProvider actors;
    private final OrderCreationGateway orders;
    private final OrderExtensionGateway store;
    private final OrderCreationCodec codec;
    private final AcquireOrderConverter orderConverter;
    private final OrderExtensionConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final OrderSnapshotEvents snapshots;
    private final OrderDeadlineTasks deadlineTasks;

    public OrderExtensionService(AcquireActorProvider actors, OrderCreationGateway orders, OrderExtensionGateway store,
                                 OrderCreationCodec codec, AcquireOrderConverter orderConverter, OrderExtensionConverter converter,
                                 OrderCreationTransactions transactions, IdGenerator ids, Validator validator,
                                 @Qualifier("acquireClock") Clock clock, OrderSnapshotEvents snapshots, OrderDeadlineTasks deadlineTasks) {
        this.actors = actors;
        this.orders = orders;
        this.store = store;
        this.codec = codec;
        this.orderConverter = orderConverter;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.snapshots = snapshots;
        this.deadlineTasks = deadlineTasks;
    }

    @Override
    public AcquireOrderDTO extend(AcquireOrderExtendCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!actor.permissions().contains("pay:acquire:extend")) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.writeLocal(actor.tenantId(), () -> {
            DomainChecks.id(command.id());
            DomainChecks.text(command.requestKey(), 128);
            var requested = inputTime(command.requestedDueAt());
            String digest = digest(actor, command, requested);
            var stored = orders.lockOrder(actor.tenantId(), command.id());
            if (!stored.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
            var order = stored.order();
            long deadlineVersion = store.deadlineVersion(actor.tenantId(), order);
            var previous = store.findReceipt(actor.tenantId(), command.id(), command.requestKey());
            if (previous.isPresent()) {
                var receipt = previous.get();
                if (!receipt.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
                receipt.requireSameRequest(actor.scopeHash(), digest, command.expectedVersion(), requested);
                try {
                    receipt.requireAppliedTo(order, deadlineVersion);
                } catch (DomainViolation corrupt) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                return orderConverter.toDTO(stored, now());
            }
            if (order.version() != command.expectedVersion()) throw new CommonException(VERSION_CONFLICT);
            var admission = codec.decodeAdmission(stored.admissionPayload(), stored.payloadHash());
            var terms = admission.terms().order();
            var appliedAt = now();
            if (!order.canAcceptNewPayment(appliedAt)) throw new CommonException(ORDER_NOT_PAYABLE);
            if (!terms.extensionAllowed()) throw new CommonException(ACTION_FORBIDDEN);
            var originalDue = originalDue(actor, stored, terms);
            var extended = order.extend(requested, originalDue, terms.extensionAllowed(), terms.extensionMaxSeconds(), appliedAt);
            var receipt = converter.toReceipt(command, order, ids.generate(), actor.scopeHash(), digest,
                    originalDue, requested, Math.incrementExact(deadlineVersion), appliedAt);
            store.extend(actor.tenantId(), extended, receipt);
            deadlineTasks.register(actor.tenantId(), extended, receipt.deadlineVersion());
            snapshots.publish(actor.tenantId(), extended.id(), extended.version());
            return orderConverter.toDTO(orders.findOrder(actor.tenantId(), command.id())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)), now());
        });
    }

    private LocalDateTime originalDue(AcquireActor actor, StoredAcquireOrder order, OrderTermsDTO terms) {
        try {
            var intent = orders.findIntent(actor.tenantId(), order.order().payeeMerchantId(), order.merchantOrderNo())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!"COMPLETED".equals(intent.state()) || !order.order().id().equals(intent.orderId())
                    || !actor.scopeHash().equals(intent.callerScopeHash())
                    || !order.admission().requestDigest().equals(intent.requestDigest()))
                throw new CommonException(SNAPSHOT_INVALID);
            AcquireOrderCreateCommand original;
            String digest;
            if ("ORDER".equals(intent.entryMode())) {
                original = codec.decodeIntent(intent);
                digest = codec.requestDigest(actor.tenantId(), actor.scopeHash(), original);
            } else if ("CHECKOUT".equals(intent.entryMode())) {
                var checkout = codec.decodeCheckout(intent);
                original = orderConverter.toOrderCreate(checkout);
                digest = codec.checkoutDigest(actor.tenantId(), actor.scopeHash(), checkout);
            } else if ("DIRECT_PAY".equals(intent.entryMode())) {
                var direct = codec.decodeDirectPay(intent);
                original = orderConverter.toOrderCreate(direct);
                digest = codec.directPayDigest(actor.tenantId(), actor.scopeHash(), direct);
            } else throw new CommonException(SNAPSHOT_INVALID);
            var due = original.requestedDueAt() == null
                    ? order.admittedAt().plusSeconds(terms.waitSeconds()) : inputTime(original.requestedDueAt());
            if (!digest.equals(intent.requestDigest()) || !due.isAfter(order.admittedAt())
                    || due.isAfter(order.admittedAt().plusSeconds(terms.maxWaitSeconds()))
                    || due.isAfter(order.order().orderDueAt()) || due.getYear() > 9998)
                throw new CommonException(SNAPSHOT_INVALID);
            return due;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private LocalDateTime inputTime(OffsetDateTime value) {
        var time = BusinessTime.local(value);
        if (time.getYear() < 1970 || time.getYear() > 9998 || time.getNano() % 1000 != 0)
            throw new CommonException(INVALID_ARGUMENT);
        return time;
    }

    private String digest(AcquireActor actor, AcquireOrderExtendCommand command, LocalDateTime requested) {
        var framed = new StringBuilder();
        for (String field : List.of("acquire.order-extend.v1", actor.tenantId(), actor.scopeHash(), command.id(),
                command.requestKey(), requested.toString(), Long.toString(command.expectedVersion())))
            framed.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        return codec.checksum(framed.toString());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
