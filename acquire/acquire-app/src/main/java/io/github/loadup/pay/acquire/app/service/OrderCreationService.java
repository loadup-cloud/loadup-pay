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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.port.OrderAdmissionClient;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.pay.acquire.client.command.CheckoutCreateCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.dto.CheckoutDTO;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderCreationFacade;
import io.github.loadup.pay.acquire.client.facade.CheckoutOrderFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderFee;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.domain.model.AdmissionDecision;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ADMISSION_EXPIRED;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONTRACT_NOT_ADMITTING;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ORDER_NOT_PAYABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class OrderCreationService implements AcquireOrderCreationFacade, CheckoutOrderFacade {
    private final AcquireActorProvider actors;
    private final OrderCreationGateway store;
    private final OrderAdmissionClient product;
    private final OrderCreationCodec codec;
    private final AcquireOrderConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final CheckoutSessionOperations sessions;
    private final OrderSnapshotEvents snapshots;
    private final OrderDeadlineTasks deadlineTasks;

    public OrderCreationService(AcquireActorProvider actors, OrderCreationGateway store, OrderAdmissionClient product, OrderCreationCodec codec, AcquireOrderConverter converter, OrderCreationTransactions transactions, CheckoutSessionOperations sessions, IdGenerator ids, Validator validator, @Qualifier("acquireClock") Clock clock, OrderSnapshotEvents snapshots, OrderDeadlineTasks deadlineTasks) {
        this.actors = actors;
        this.store = store;
        this.product = product;
        this.codec = codec;
        this.converter = converter;
        this.transactions = transactions;
        this.sessions = sessions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.snapshots = snapshots;
        this.deadlineTasks = deadlineTasks;
    }

    @Override
    public AcquireOrderDTO create(AcquireOrderCreateCommand command) {
        validate(command);
        var actor = actor("pay:acquire:create");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.withTenantContext(actor.tenantId(), () -> converter.toDTO(result(actor, createIntent(actor, command, null, null, true)), now()));
    }

    @Override
    public CheckoutDTO checkout(CheckoutCreateCommand command) {
        validate(command);
        var actor = actor("pay:acquire:checkout");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.withTenantContext(actor.tenantId(), () -> {
            if (command.returnTargetCode() != null) DomainChecks.text(command.returnTargetCode(), 128);
            var intent = createIntent(actor, converter.toOrderCreate(command), command, null, true);
            return sessions.replayInitial(actor, result(actor, intent), intent.initialSessionId());
        });
    }

    @Override
    public AcquireOrderDTO createDirectPay(io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand command) {
        return directPayOrder(command, true);
    }

    @Override
    public AcquireOrderDTO resumeDirectPay(io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand command) {
        return directPayOrder(command, false);
    }

    private AcquireOrderDTO directPayOrder(io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand command, boolean allowCreate) {
        validate(command);
        if (command.firstPaymentAmountMinor() > command.amountMinor() || !command.interactionKind().supports(command.choice().method()))
            throw new CommonException(INVALID_ARGUMENT);
        var actor = actor("pay:acquire:create");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.withTenantContext(actor.tenantId(), () -> converter.toDTO(result(actor, createIntent(actor, converter.toOrderCreate(command), null, command, allowCreate)), now()));
    }

    private OrderCreateIntent createIntent(AcquireActor actor, AcquireOrderCreateCommand command, CheckoutCreateCommand checkout, io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand directPay, boolean allowCreate) {
        DomainChecks.id(command.payeeMerchantId());
        DomainChecks.id(command.merchantOrderNo());
        DomainChecks.text(command.productScopeKey(), 128);
        DomainChecks.text(command.subject(), 200);
        DomainChecks.cnyAmount(command.amountMinor(), command.currency());
        if (command.requestedDueAt() != null) time(command.requestedDueAt());
        String mode = directPay != null ? "DIRECT_PAY" : checkout == null ? "ORDER" : "CHECKOUT";
        String digest = directPay != null ? codec.directPayDigest(actor.tenantId(), actor.scopeHash(), directPay) : checkout == null ? codec.requestDigest(actor.tenantId(), actor.scopeHash(), command) : codec.checkoutDigest(actor.tenantId(), actor.scopeHash(), checkout);
        // A0 commits before any network call. A losing unique insert rolls back before retry/read.
        var intent = transactions.writeLocal(actor.tenantId(), () -> {
            var previous = store.findIntent(actor.tenantId(), command.payeeMerchantId(), command.merchantOrderNo());
            if (previous.isPresent()) return checked(previous.get(), actor, command, checkout, directPay, digest);
            if (!allowCreate) throw new CommonException(NOT_FOUND);
            var created = new OrderCreateIntent(ids.generate(), ids.generate(), command.payeeMerchantId(), command.merchantOrderNo(), digest, actor.scopeHash(), mode, checkout == null ? null : ids.generate(), directPay != null ? codec.encodeDirectPay(directPay) : checkout == null ? codec.encodeIntent(command) : codec.encodeCheckout(checkout), "INIT", null, null, null, 0);
            store.insertIntent(actor.tenantId(), created);
            return created;
        });
        if (!"INIT".equals(intent.state())) return intent;
        // Product replays the original decision for this stable order id, even after contract stop.
        var admission = product.authorize(converter.toAdmissionCommand(intent, command));
        validateAdmission(intent, admission);
        String payload = admission.decision() == AdmissionDecision.ADMITTED ? codec.encodeAdmission(admission) : null;
        String checksum = payload == null ? null : codec.checksum(payload);
        var complete = transactions.writeLocal(actor.tenantId(), () -> {
            var locked = checked(store.lockIntent(actor.tenantId(), intent.id()), actor, command, checkout, directPay, digest);
            if (!"INIT".equals(locked.state())) return locked;
            if (admission.decision() == AdmissionDecision.DENIED) {
                var denied = locked.finish("REJECTED", admission.admissionId(), null, CONTRACT_NOT_ADMITTING.getCode());
                store.updateIntent(actor.tenantId(), denied, locked.rowVersion());
                return denied;
            }
            var admittedAt = time(admission.admittedAt());
            var before = time(admission.materializeBefore());
            if (!now().isBefore(before)) {
                var expired = locked.finish("EXPIRED", admission.admissionId(), before, ADMISSION_EXPIRED.getCode());
                store.updateIntent(actor.tenantId(), expired, locked.rowVersion());
                return expired;
            }
            var terms = admission.terms();
            var due = command.requestedDueAt() == null ? admittedAt.plusSeconds(terms.order().waitSeconds()) : time(command.requestedDueAt());
            if (!due.isAfter(admittedAt) || due.isAfter(admittedAt.plusSeconds(terms.order().maxWaitSeconds())))
                throw new CommonException(SNAPSHOT_INVALID);
            var order = AcquireOrder.init(locked.orderId(), locked.payeeMerchantId(), DomainChecks.cnyAmount(command.amountMinor(), command.currency()), due, converter.toUnpaidAction(terms.order().unpaidAction()), converter.toPaymentPolicy(terms.payment()));
            var stored = converter.toStored(order, command, converter.toProof(admission), admittedAt, actor.scopeHash(), ids.generate(), payload, checksum);
            var fee = OrderFee.init(ids.generate(), order.id(), order.amount(), converter.toFeePolicy(terms.fee()));
            StoredCheckoutSession initial = null;
            if (checkout != null) {
                try {
                    initial = sessions.prepareInitial(actor, stored, locked.initialSessionId(), checkout.returnTargetCode());
                } catch (CommonException failure) {
                    if (failure.getResultCode() != ACTION_FORBIDDEN && failure.getResultCode() != ORDER_NOT_PAYABLE)
                        throw failure;
                    var rejected = locked.finish("REJECTED", admission.admissionId(), before, failure.getResultCode().getCode());
                    store.updateIntent(actor.tenantId(), rejected, locked.rowVersion());
                    return rejected;
                }
            }
            store.insertOrder(actor.tenantId(), stored, fee, codec.encodeFee(fee.policy()));
            deadlineTasks.register(actor.tenantId(), order, 1L);
            if (initial != null) sessions.insertInitial(actor.tenantId(), initial);
            var finished = locked.finish("COMPLETED", admission.admissionId(), before, "SUCCESS");
            store.updateIntent(actor.tenantId(), finished, locked.rowVersion());
            snapshots.publish(actor.tenantId(), order.id(), order.version());
            return finished;
        });
        // Throw only after committing a stable rejection/expiry; do not roll its evidence back.
        return complete;
    }

    private OrderCreateIntent checked(OrderCreateIntent intent, AcquireActor actor, AcquireOrderCreateCommand command, CheckoutCreateCommand checkout, io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand directPay, String digest) {
        if (!intent.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
        if (!intent.entryMode().equals(directPay != null ? "DIRECT_PAY" : checkout == null ? "ORDER" : "CHECKOUT") || !intent.requestDigest().equals(digest) || (directPay != null ? !codec.decodeDirectPay(intent).equals(directPay) : checkout == null ? !codec.decodeIntent(intent).equals(command) : !codec.decodeCheckout(intent).equals(checkout)))
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        return intent;
    }

    private StoredAcquireOrder result(AcquireActor actor, OrderCreateIntent intent) {
        if ("REJECTED".equals(intent.state())) {
            for (var code : new io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum[]{CONTRACT_NOT_ADMITTING, ACTION_FORBIDDEN, ORDER_NOT_PAYABLE})
                if (code.getCode().equals(intent.resultCode())) throw new CommonException(code);
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if ("EXPIRED".equals(intent.state())) {
            if (!ADMISSION_EXPIRED.getCode().equals(intent.resultCode())) throw new CommonException(SNAPSHOT_INVALID);
            throw new CommonException(ADMISSION_EXPIRED);
        }
        if (!"COMPLETED".equals(intent.state())) throw new CommonException(INVALID_STATE);
        if (!"SUCCESS".equals(intent.resultCode())) throw new CommonException(SNAPSHOT_INVALID);
        var order = store.findOrder(actor.tenantId(), intent.orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        owned(actor, order);
        if (!order.admission().admissionId().equals(intent.admissionId()) || !order.admission().requestDigest().equals(intent.requestDigest()))
            throw new CommonException(SNAPSHOT_INVALID);
        return order;
    }

    @Override
    public AcquireOrderDTO detail(IdQuery query) {
        validate(query);
        var actor = actor("pay:acquire:read");
        return transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var order = store.findOrder(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            owned(actor, order);
            return converter.toDTO(order, now());
        });
    }

    private void owned(AcquireActor actor, StoredAcquireOrder order) {
        if (!actor.scopeHash().equals(order.callerScopeHash())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private AcquireActor actor(String permission) {
        var actor = actors.current();
        if (!actor.permissions().contains(permission)) throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }

    private LocalDateTime time(OffsetDateTime value) {
        if (value == null || value.getNano() % 1000 != 0) throw new CommonException(INVALID_ARGUMENT);
        var local = BusinessTime.local(value);
        if (local.getYear() < 1970 || local.getYear() > 9998) throw new CommonException(INVALID_ARGUMENT);
        return local;
    }

    private void validateAdmission(OrderCreateIntent intent, OrderAdmissionDTO value) {
        try {
            if (value == null || !validator.validate(value).isEmpty() || value.decision() == null || !intent.orderId().equals(value.orderId()) || !intent.payeeMerchantId().equals(value.payeeMerchantId()) || !intent.requestDigest().equals(value.requestDigest()))
                throw new CommonException(SNAPSHOT_INVALID);
            DomainChecks.id(value.admissionId());
            DomainChecks.text(value.reasonCode(), 64);
            if (value.decision() == AdmissionDecision.DENIED) {
                if (value.contractId() != null || value.revision() != null || value.generation() != null || value.snapshotHash() != null || value.compiledTermsHash() != null || value.admittedAt() != null || value.materializeBefore() != null || value.terms() != null)
                    throw new CommonException(SNAPSHOT_INVALID);
                return;
            }
            if (value.decision() != AdmissionDecision.ADMITTED || !"ALLOW".equals(value.reasonCode()) || value.terms() == null || value.terms().schemaVersion() != 1 || value.revision() == null || value.revision() < 1 || value.generation() == null || value.generation() < 1 || value.compiledTermsHash() == null || !value.compiledTermsHash().matches("[0-9a-f]{64}"))
                throw new CommonException(SNAPSHOT_INVALID);
            DomainChecks.id(value.contractId());
            DomainChecks.text(value.snapshotHash(), 128);
            var admitted = time(value.admittedAt());
            var before = time(value.materializeBefore());
            if (!before.isAfter(admitted) || admitted.isAfter(now())) throw new CommonException(SNAPSHOT_INVALID);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
