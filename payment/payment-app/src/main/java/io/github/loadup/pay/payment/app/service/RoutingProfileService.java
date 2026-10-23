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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.RoutingProfileConverter;
import io.github.loadup.pay.payment.app.port.RoutingProfileCodec;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.command.RoutingProfilePublishCommand;
import io.github.loadup.pay.payment.client.dto.RoutingProfileDTO;
import io.github.loadup.pay.payment.client.facade.RoutingProfileFacade;
import io.github.loadup.pay.payment.client.query.RoutingProfileDetailQuery;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingManagementGateway;
import io.github.loadup.pay.payment.domain.gateway.RoutingProfileGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingState;
import io.github.loadup.pay.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.pay.payment.domain.model.RoutableChannelBinding;
import io.github.loadup.pay.payment.domain.model.RoutingProfileSpec;
import io.github.loadup.pay.payment.domain.model.RoutingRule;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.TreeMap;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CHANNEL_BINDING_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ROUTING_PROFILE_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;

@Service
public class RoutingProfileService implements RoutingProfileFacade {
    private final PaymentActorProvider actors;
    private final RoutingProfileGateway profiles;
    private final ChannelBindingManagementGateway bindings;
    private final RoutingProfileConverter converter;
    private final RoutingProfileCodec codec;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final String managementService;

    public RoutingProfileService(PaymentActorProvider actors, RoutingProfileGateway profiles,
                                 ChannelBindingManagementGateway bindings, RoutingProfileConverter converter, RoutingProfileCodec codec,
                                 PaymentAcceptanceTransactions transactions, IdGenerator ids, Validator validator,
                                 @Qualifier("paymentClock") Clock clock,
                                 @Value("${loadup.modules.payment.channel-management-service-id:pay-ops}") String managementService) {
        this.actors = actors;
        this.profiles = profiles;
        this.bindings = bindings;
        this.converter = converter;
        this.codec = codec;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.managementService = DomainChecks.id(managementService);
    }

    @Override
    public RoutingProfileDTO publish(RoutingProfilePublishCommand command) {
        validate(command);
        var actor = actor("pay:payment:routing:publish");
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.text(command.requestKey(), 128);
            var spec = converter.toSpec(command.spec());
            String hash = codec.checksum(codec.encode(spec));
            String digest = codec.requestDigest(actor.tenantId(), actor.serviceId(), command.requestKey(), hash);
            return transactions.write(actor.tenantId(), () -> {
                var prior = profiles.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey());
                if (prior.isPresent()) {
                    var original = prior.get();
                    if (!original.requestDigest().equals(digest) || !original.spec().equals(spec)
                            || !original.contentHash().equals(hash)) throw new CommonException(IDEMPOTENCY_CONFLICT);
                    return converter.toDTO(original);
                }
                // A reference is a version, never a mutable "latest" alias.
                if (profiles.find(actor.tenantId(), spec.profileRef()).isPresent())
                    throw new CommonException(IDEMPOTENCY_CONFLICT);
                requireBindings(actor.tenantId(), spec);
                var profile = converter.toStored(ids.generate(), spec, hash, actor.serviceId(), command.requestKey(), digest,
                        LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone()));
                profiles.insert(actor.tenantId(), profile);
                return converter.toDTO(profile);
            });
        });
    }

    private void requireBindings(String tenant, RoutingProfileSpec spec) {
        // Resolve immutable head ids first, then use the same id lock order as attempt selection.
        var resolved = new TreeMap<String, ChannelMerchantBinding>();
        for (var code : spec.rules().stream().map(RoutingRule::bindingCode).distinct().toList()) {
            var head = bindings.find(tenant, spec.payeeMerchantId(), code)
                    .orElseThrow(() -> new CommonException(CHANNEL_BINDING_UNAVAILABLE));
            if (!head.payeeMerchantId().equals(spec.payeeMerchantId()) || !head.bindingCode().equals(code))
                throw new CommonException(SNAPSHOT_INVALID);
            resolved.put(head.id(), head);
        }
        var locked = new HashMap<String, RoutableChannelBinding>();
        for (var prior : resolved.values()) {
            var head = bindings.lock(tenant, spec.payeeMerchantId(), prior.bindingCode())
                    .orElseThrow(() -> new CommonException(CHANNEL_BINDING_UNAVAILABLE));
            if (!head.id().equals(prior.id()) || !head.bindingCode().equals(prior.bindingCode())
                    || !head.payeeMerchantId().equals(spec.payeeMerchantId()))
                throw new CommonException(SNAPSHOT_INVALID);
            if (head.state() != ChannelBindingState.ACTIVE) throw new CommonException(CHANNEL_BINDING_UNAVAILABLE);
            var revision = bindings.revision(tenant, head.currentRevisionId())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            try {
                locked.put(head.bindingCode(), new RoutableChannelBinding(head, revision));
            } catch (RuntimeException corrupt) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        }
        for (var rule : spec.rules()) {
            var binding = locked.get(rule.bindingCode()).revision().binding();
            if (!spec.payeeMerchantId().equals(binding.payeeMerchantId()) || binding.channel() != rule.channel()
                    || binding.accessMode() != rule.accessMode() || !binding.capabilities().contains(rule.method()))
                throw new CommonException(UNSUPPORTED_CAPABILITY);
        }
    }

    @Override
    public RoutingProfileDTO detail(RoutingProfileDetailQuery query) {
        validate(query);
        var actor = actor("pay:payment:routing:read");
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(query.payeeMerchantId());
            DomainChecks.text(query.profileRef(), 128);
            var profile = profiles.find(actor.tenantId(), query.profileRef())
                    .orElseThrow(() -> new CommonException(ROUTING_PROFILE_UNAVAILABLE));
            if (!profile.spec().payeeMerchantId().equals(query.payeeMerchantId()))
                throw new CommonException(ROUTING_PROFILE_UNAVAILABLE);
            return converter.toDTO(profile);
        });
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
}
