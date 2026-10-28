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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.payment.app.converter.ChannelBindingConverter;
import io.github.loadup.pay.payment.app.port.ChannelBindingCodec;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.command.ChannelBindingPublishCommand;
import io.github.loadup.pay.payment.client.command.ChannelBindingRevokeCommand;
import io.github.loadup.pay.payment.client.command.ChannelBindingSuspendCommand;
import io.github.loadup.pay.payment.client.dto.ChannelBindingChangeDTO;
import io.github.loadup.pay.payment.client.dto.ChannelBindingDTO;
import io.github.loadup.pay.payment.client.facade.ChannelBindingFacade;
import io.github.loadup.pay.payment.client.query.ChannelBindingDetailQuery;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingManagementGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingChangeReceipt;
import io.github.loadup.pay.payment.domain.model.ChannelBindingState;
import io.github.loadup.pay.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.pay.payment.domain.model.StoredChannelBindingRevision;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class ChannelBindingService implements ChannelBindingFacade {
    private final PaymentActorProvider actors;
    private final ChannelBindingManagementGateway store;
    private final ChannelBindingConverter converter;
    private final ChannelBindingCodec codec;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final String managementService;

    public ChannelBindingService(PaymentActorProvider actors, ChannelBindingManagementGateway store,
                                 ChannelBindingConverter converter, ChannelBindingCodec codec, PaymentAcceptanceTransactions transactions,
                                 IdGenerator ids, Validator validator, @Qualifier("paymentClock") Clock clock,
                                 @Value("${loadup.modules.payment.channel-management-service-id:pay-ops}") String managementService) {
        this.actors = actors;
        this.store = store;
        this.converter = converter;
        this.codec = codec;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.managementService = DomainChecks.id(managementService);
    }

    @Override
    public ChannelBindingChangeDTO publish(ChannelBindingPublishCommand command) {
        validate(command);
        var actor = actor("pay:payment:binding:publish");
        return transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(command.payeeMerchantId());
            DomainChecks.id(command.bindingCode());
            DomainChecks.text(command.requestKey(), 128);
            // The typed identity validates channel and mode; executable capabilities belong to registered providers.
            var candidate = converter.toBinding(command, "candidate", codec.accountScopeHash(command.channel(), converter.toIdentity(command.identity())));
            String digest = codec.requestDigest(actor.tenantId(), actor.serviceId(), "PUBLISH", command);
            return transactions.writeLocal(actor.tenantId(), () -> {
                var prior = store.lock(actor.tenantId(), command.payeeMerchantId(), command.bindingCode());
                if (prior.isPresent()) {
                    var receipt = store.receipt(actor.tenantId(), prior.get().id(), command.requestKey());
                    if (receipt.isPresent()) return replay(actor, prior.get(), receipt.get(), "PUBLISH", digest);
                }
                var head = prior.orElseGet(() -> new ChannelMerchantBinding(ids.generate(), command.payeeMerchantId(),
                        command.bindingCode(), command.channel(), command.accessMode(), ChannelBindingState.INIT, null, 0, 0, 0));
                if (head.rowVersion() != command.expectedVersion()) throw new CommonException(CONCURRENT_UPDATE);
                if (head.state() == ChannelBindingState.REVOKED) throw new CommonException(INVALID_STATE);
                if (head.channel() != command.channel() || head.accessMode() != command.accessMode())
                    throw new CommonException(IDEMPOTENCY_CONFLICT);
                if (prior.isPresent()) requireRevision(actor.tenantId(), head);
                String revisionId = ids.generate();
                var binding = converter.toBinding(command, revisionId, candidate.accountScopeHash());
                int ordinal = Math.incrementExact(head.currentRevision());
                var publishedAt = now();
                String payload = codec.encodeIdentity(binding.identity());
                var revision = converter.toStored(binding, head.id(), ordinal, codec.checksum(payload), actor.serviceId(), publishedAt);
                var updated = head.publish(binding, ordinal, command.priority());
                store.insertRevision(actor.tenantId(), revision);
                if (prior.isPresent()) store.updateHead(actor.tenantId(), updated, head.rowVersion());
                else store.insertHead(actor.tenantId(), updated);
                var receipt = receipt(actor, updated, "PUBLISH", command.requestKey(), digest, publishedAt);
                store.insertReceipt(actor.tenantId(), receipt);
                return converter.toChange(receipt);
            });
        });
    }

    @Override
    public ChannelBindingChangeDTO suspend(ChannelBindingSuspendCommand command) {
        validate(command);
        return changeState(actor("pay:payment:binding:suspend"), command.payeeMerchantId(), command.bindingCode(),
                command.requestKey(), command.expectedVersion(), "SUSPEND", command);
    }

    @Override
    public ChannelBindingChangeDTO revoke(ChannelBindingRevokeCommand command) {
        validate(command);
        return changeState(actor("pay:payment:binding:revoke"), command.payeeMerchantId(), command.bindingCode(),
                command.requestKey(), command.expectedVersion(), "REVOKE", command);
    }

    private ChannelBindingChangeDTO changeState(PaymentActor actor, String payee, String bindingCode, String key,
                                                long expectedVersion, String kind, Object command) {
        return transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(payee);
            DomainChecks.id(bindingCode);
            DomainChecks.text(key, 128);
            String digest = codec.requestDigest(actor.tenantId(), actor.serviceId(), kind, command);
            return transactions.writeLocal(actor.tenantId(), () -> {
                var head = store.lock(actor.tenantId(), payee, bindingCode).orElseThrow(() -> new CommonException(NOT_FOUND));
                var prior = store.receipt(actor.tenantId(), head.id(), key);
                if (prior.isPresent()) return replay(actor, head, prior.get(), kind, digest);
                if (head.rowVersion() != expectedVersion) throw new CommonException(CONCURRENT_UPDATE);
                if (head.state() == ChannelBindingState.REVOKED && "SUSPEND".equals(kind))
                    throw new CommonException(INVALID_STATE);
                requireRevision(actor.tenantId(), head);
                var updated = "REVOKE".equals(kind) ? head.revoke() : head.suspend();
                store.updateHead(actor.tenantId(), updated, head.rowVersion());
                var receipt = receipt(actor, updated, kind, key, digest, now());
                store.insertReceipt(actor.tenantId(), receipt);
                return converter.toChange(receipt);
            });
        });
    }

    @Override
    public ChannelBindingDTO detail(ChannelBindingDetailQuery query) {
        validate(query);
        var actor = actor("pay:payment:binding:read");
        return transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(query.payeeMerchantId());
            DomainChecks.id(query.bindingCode());
            var head = store.find(actor.tenantId(), query.payeeMerchantId(), query.bindingCode())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            return converter.toDTO(head, requireRevision(actor.tenantId(), head));
        });
    }

    private StoredChannelBindingRevision requireRevision(String tenant, ChannelMerchantBinding head) {
        var revision = store.revision(tenant, head.currentRevisionId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!head.id().equals(revision.bindingId()) || head.currentRevision() != revision.revision()
                || !head.payeeMerchantId().equals(revision.binding().payeeMerchantId())
                || head.channel() != revision.binding().channel() || head.accessMode() != revision.binding().accessMode())
            throw new CommonException(SNAPSHOT_INVALID);
        return revision;
    }

    private ChannelBindingChangeDTO replay(PaymentActor actor, ChannelMerchantBinding head, ChannelBindingChangeReceipt receipt,
                                           String kind, String digest) {
        if (!receipt.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!receipt.kind().equals(kind) || !receipt.requestDigest().equals(digest))
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        if (!head.id().equals(receipt.bindingId()) || head.rowVersion() < receipt.appliedVersion()
                || head.currentRevision() < receipt.revision()) throw new CommonException(SNAPSHOT_INVALID);
        requireRevision(actor.tenantId(), head);
        var original = store.revision(actor.tenantId(), receipt.bindingRevisionId())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!head.id().equals(original.bindingId()) || original.revision() != receipt.revision()
                || !head.payeeMerchantId().equals(original.binding().payeeMerchantId()))
            throw new CommonException(SNAPSHOT_INVALID);
        return converter.toChange(receipt);
    }

    private ChannelBindingChangeReceipt receipt(PaymentActor actor, ChannelMerchantBinding head, String kind,
                                                String key, String digest, LocalDateTime at) {
        return new ChannelBindingChangeReceipt(ids.generate(), head.id(), kind, key, digest, actor.serviceId(),
                head.currentRevisionId(), head.currentRevision(), head.state(), head.rowVersion(), at);
    }

    private PaymentActor actor(String permission) {
        var actor = actors.current();
        if (!managementService.equals(actor.serviceId()) || !actor.permissions().contains(permission))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
