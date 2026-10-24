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
import io.github.loadup.pay.acquire.app.converter.CheckoutConverter;
import io.github.loadup.pay.acquire.app.port.CheckoutPresentation;
import io.github.loadup.pay.acquire.app.port.CheckoutTokens;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.client.command.CheckoutSessionCreateCommand;
import io.github.loadup.pay.acquire.client.dto.CheckoutDTO;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionGateway;
import io.github.loadup.pay.acquire.domain.model.CheckoutSession;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionScope;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.commons.validation.DomainChecks;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ORDER_NOT_PAYABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class CheckoutSessionOperations {
    private final CheckoutSessionGateway sessions;
    private final OrderCreationCodec codec;
    private final CheckoutTokens tokens;
    private final CheckoutPresentation presentation;
    private final AcquireOrderConverter ordersConverter;
    private final CheckoutConverter converter;
    private final IdGenerator ids;
    private final Clock clock;

    public CheckoutSessionOperations(
            CheckoutSessionGateway sessions,
            OrderCreationCodec codec,
            CheckoutTokens tokens,
            CheckoutPresentation presentation,
            AcquireOrderConverter ordersConverter,
            CheckoutConverter converter,
            IdGenerator ids,
            @Qualifier("acquireClock") Clock clock) {
        this.sessions = sessions;
        this.codec = codec;
        this.tokens = tokens;
        this.presentation = presentation;
        this.ordersConverter = ordersConverter;
        this.converter = converter;
        this.ids = ids;
        this.clock = clock;
    }

    public CheckoutDTO renew(AcquireActor actor, StoredAcquireOrder order, CheckoutSessionCreateCommand command) {
        requireTransaction();
        String target = target(order, command.returnTargetCode());
        String digest = digest(actor, command, target);
        var prior = sessions.findRequest(actor.tenantId(), command.orderId(), command.requestKey());
        if (prior.isPresent()) {
            checked(actor, order, prior.get(), command.requestKey(), digest, target);
            return present(actor, order, prior.get());
        }
        var prepared = prepare(actor, order, ids.generate(), command.requestKey(), digest, target);
        var result = present(actor, order, prepared);
        sessions.insert(actor.tenantId(), prepared);
        return result;
    }

    /**
     * Prepare credentials before any order writes; persist inside the caller's A1 transaction.
     */
    public StoredCheckoutSession prepareInitial(
            AcquireActor actor, StoredAcquireOrder order, String sessionId, String requestedTarget) {
        requireTransaction();
        String target = target(order, requestedTarget);
        String key = initialKey(sessionId);
        var command = new CheckoutSessionCreateCommand(order.order().id(), key, target);
        var prepared = prepare(actor, order, sessionId, key, digest(actor, command, target), target);
        present(actor, order, prepared);
        return prepared;
    }

    public void insertInitial(String tenant, StoredCheckoutSession prepared) {
        requireTransaction();
        sessions.insert(tenant, prepared);
    }

    public CheckoutDTO replayInitial(AcquireActor actor, StoredAcquireOrder order, String sessionId) {
        var stored =
                sessions.find(actor.tenantId(), sessionId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        String target = target(order, stored.scope().returnTargetCode());
        String key = initialKey(sessionId);
        String digest =
                digest(actor, new CheckoutSessionCreateCommand(order.order().id(), key, target), target);
        checked(actor, order, stored, key, digest, target);
        if (!sessionId.equals(stored.session().id())) throw new CommonException(SNAPSHOT_INVALID);
        return present(actor, order, stored);
    }

    private String initialKey(String id) {
        DomainChecks.id(id);
        return "initial/" + id;
    }

    private String target(StoredAcquireOrder order, String requested) {
        var admission = codec.decodeAdmission(order.admissionPayload(), order.payloadHash());
        String target = requested == null ? admission.terms().returnTargetCode() : requested;
        DomainChecks.text(target, 128);
        if (!target.equals(admission.terms().returnTargetCode())) throw new CommonException(ACTION_FORBIDDEN);
        return target;
    }

    private void checked(
            AcquireActor actor,
            StoredAcquireOrder order,
            StoredCheckoutSession stored,
            String requestKey,
            String digest,
            String target) {
        if (!actor.scopeHash().equals(order.callerScopeHash())
                || !stored.requestKey().equals(requestKey)
                || !stored.requestDigest().equals(digest)
                || !stored.scope().callerScopeHash().equals(actor.scopeHash())
                || !stored.scope().returnTargetCode().equals(target)
                || !stored.session().orderId().equals(order.order().id())
                || !stored.payeeMerchantId().equals(order.order().payeeMerchantId()))
            throw new CommonException(IDEMPOTENCY_CONFLICT);
    }

    private StoredCheckoutSession prepare(
            AcquireActor actor, StoredAcquireOrder order, String id, String requestKey, String digest, String target) {
        if (!actor.scopeHash().equals(order.callerScopeHash())) throw new CommonException(ACTION_FORBIDDEN);
        var admission = codec.decodeAdmission(order.admissionPayload(), order.payloadHash());
        LocalDateTime now = now();
        if (!order.order().canAcceptNewPayment(now)) throw new CommonException(ORDER_NOT_PAYABLE);
        LocalDateTime expiry =
                now.plusSeconds(admission.terms().order().sessionSeconds()).truncatedTo(ChronoUnit.SECONDS);
        if (order.order().unpaidAction() == UnpaidOrderAction.CLOSE
                && expiry.isAfter(order.order().orderDueAt()))
            expiry = order.order().orderDueAt().truncatedTo(ChronoUnit.SECONDS);
        if (!expiry.isAfter(now) || expiry.getYear() > 9998) throw new CommonException(ORDER_NOT_PAYABLE);
        var session = CheckoutSession.init(id, order.order().id(), expiry).activate(now);
        String nonce = tokens.nonce();
        String keyRef = tokens.keyRef();
        String keyVersion = tokens.activeKeyVersion();
        String token = tokens.issue(session, nonce, keyRef, keyVersion);
        var scope = new CheckoutSessionScope(1, actor.scopeHash(), target, Set.of("ORDER_SUMMARY", "PAYMENT_START", "PAYMENT_PROGRESS"));
        return converter.toStored(
                session,
                order.order().payeeMerchantId(),
                requestKey,
                digest,
                tokens.hash(token),
                nonce,
                keyRef,
                keyVersion,
                presentation.allowedOrigin(),
                scope,
                0);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }

    private CheckoutDTO present(AcquireActor actor, StoredAcquireOrder order, StoredCheckoutSession stored) {
        if (stored.revokedAt() != null) throw new CommonException(SESSION_UNAVAILABLE);
        if (!presentation.allowedOrigin().equals(stored.allowedOrigin()))
            throw new CommonException(SESSION_UNAVAILABLE);
        // Rebuild the original token, never renew the session or select the new active key on replay.
        String token =
                tokens.issue(stored.session(), stored.tokenNonce(), stored.tokenKeyRef(), stored.tokenKeyVersion());
        if (!tokens.matches(token, stored)) throw new CommonException(SESSION_UNAVAILABLE);
        return converter.toCheckout(
                ordersConverter.toDTO(order, now()),
                stored.session().id(),
                presentation.url(actor.tenantId(), order.order().id(), token),
                stored.session().expiresAt());
    }

    private String digest(AcquireActor actor, CheckoutSessionCreateCommand command, String target) {
        var framed = new StringBuilder();
        for (String field : List.of(
                "pay-checkout-session-v1",
                actor.tenantId(),
                actor.scopeHash(),
                command.orderId(),
                command.requestKey(),
                target))
            framed.append(field.getBytes(StandardCharsets.UTF_8).length)
                    .append(':')
                    .append(field);
        return tokens.hash(framed.toString());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
