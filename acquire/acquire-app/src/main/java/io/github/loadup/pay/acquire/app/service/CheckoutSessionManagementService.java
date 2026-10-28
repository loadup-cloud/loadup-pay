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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.app.converter.CheckoutSessionManagementConverter;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.CheckoutSessionRevokeCommand;
import io.github.loadup.pay.acquire.client.dto.CheckoutSessionRevocationDTO;
import io.github.loadup.pay.acquire.client.dto.CheckoutSessionStatusDTO;
import io.github.loadup.pay.acquire.client.facade.CheckoutSessionManagementFacade;
import io.github.loadup.pay.acquire.client.query.CheckoutSessionRevocationPageQuery;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionGateway;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionRevocationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionRevocationReceipt;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.VERSION_CONFLICT;

/**
 * Permanent issuer-owned revocation is independent of payment and session business lifecycles.
 */
@Service
public class CheckoutSessionManagementService implements CheckoutSessionManagementFacade {
    private final AcquireActorProvider actors;
    private final OrderCreationGateway orders;
    private final CheckoutSessionGateway sessions;
    private final CheckoutSessionRevocationGateway receipts;
    private final CheckoutSessionManagementConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;

    public CheckoutSessionManagementService(AcquireActorProvider actors, OrderCreationGateway orders, CheckoutSessionGateway sessions,
                                            CheckoutSessionRevocationGateway receipts, CheckoutSessionManagementConverter converter, OrderCreationTransactions transactions,
                                            IdGenerator ids, Validator validator, @Qualifier("acquireClock") Clock clock) {
        this.actors = actors;
        this.orders = orders;
        this.sessions = sessions;
        this.receipts = receipts;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    public CheckoutSessionStatusDTO detail(IdQuery query) {
        validate(query);
        var actor = actor("pay:acquire:session:read");
        return transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var session = sessions.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var order = orders.findOrder(actor.tenantId(), session.session().orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            owned(actor, order, session);
            return converter.toStatus(session);
        });
    }

    @Override
    public CheckoutSessionRevocationDTO revoke(CheckoutSessionRevokeCommand command) {
        validate(command);
        var actor = actor("pay:acquire:session:revoke");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(command.id());
            DomainChecks.text(command.requestKey(), 128);
            DomainChecks.text(command.reason(), 500);
            String digest = DigestUtils.sha256(JsonUtil.toJson(List.of("checkout-session-revoke-v1", actor.tenantId(), actor.scopeHash(),
                    command.id(), command.requestKey(), command.expectedVersion(), command.reason())));
            var known = receipts.find(actor.tenantId(), actor.scopeHash(), command.requestKey());
            if (known.isPresent()) return replay(actor, known.get(), digest);
            var observed = sessions.find(actor.tenantId(), command.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            return transactions.writeLocal(actor.tenantId(), () -> {
                var existing = receipts.find(actor.tenantId(), actor.scopeHash(), command.requestKey());
                if (existing.isPresent()) return replay(actor, existing.get(), digest);
                var order = orders.lockOrder(actor.tenantId(), observed.session().orderId());
                var before = sessions.lock(actor.tenantId(), command.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
                owned(actor, order, before);
                // A concurrent original request can commit while this transaction waits on the order/session locks.
                var serialized = receipts.find(actor.tenantId(), actor.scopeHash(), command.requestKey());
                if (serialized.isPresent()) return replay(actor, serialized.get(), digest);
                if (before.rowVersion() != command.expectedVersion()) throw new CommonException(VERSION_CONFLICT);
                var at = now();
                var after = before.revoke(command.expectedVersion(), at);
                if (!after.equals(before)) sessions.revoke(actor.tenantId(), before, after);
                var receipt = converter.toReceipt(ids.generate(), command, before, after, actor.scopeHash(), digest, at);
                receipts.insert(actor.tenantId(), receipt);
                return converter.toDTO(receipt);
            });
        });
    }

    @Override
    public PageDTO<CheckoutSessionRevocationDTO> revocations(CheckoutSessionRevocationPageQuery query) {
        validate(query);
        var actor = actor("pay:acquire:session:read");
        return transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(query.sessionId());
            var session = sessions.find(actor.tenantId(), query.sessionId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            var order = orders.findOrder(actor.tenantId(), session.session().orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            owned(actor, order, session);
            var page = receipts.page(actor.tenantId(), query.sessionId(), query.page(), query.size());
            for (var receipt : page.records()) {
                if (!receipt.sessionId().equals(query.sessionId()) || !receipt.orderId().equals(order.order().id())
                        || !receipt.callerScopeHash().equals(actor.scopeHash()))
                    throw new CommonException(SNAPSHOT_INVALID);
            }
            return PageDTO.of(page.map(converter::toDTO));
        });
    }

    private AcquireActor actor(String permission) {
        var actor = actors.current();
        if (!actor.permissions().contains(permission)) throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    private void owned(AcquireActor actor, StoredAcquireOrder order, StoredCheckoutSession session) {
        if (!order.callerScopeHash().equals(actor.scopeHash()) || !session.scope().callerScopeHash().equals(actor.scopeHash()))
            throw new CommonException(ACTION_FORBIDDEN);
        if (!session.session().orderId().equals(order.order().id()) || !session.payeeMerchantId().equals(order.order().payeeMerchantId()))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private CheckoutSessionRevocationDTO replay(AcquireActor actor, CheckoutSessionRevocationReceipt receipt, String digest) {
        if (!receipt.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
        if (!receipt.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toDTO(receipt);
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
