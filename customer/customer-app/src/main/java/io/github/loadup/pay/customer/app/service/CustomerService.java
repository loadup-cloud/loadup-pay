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
package io.github.loadup.pay.customer.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.modules.merchant.client.facade.MerchantQueryFacade;
import io.github.loadup.pay.customer.app.converter.CustomerConverter;
import io.github.loadup.pay.customer.app.security.CustomerActor;
import io.github.loadup.pay.customer.app.security.CustomerActorProvider;
import io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.client.command.MerchantActionGrantCommand;
import io.github.loadup.pay.customer.client.command.MerchantActionGrantRevokeCommand;
import io.github.loadup.pay.customer.client.command.MerchantRelationshipCreateCommand;
import io.github.loadup.pay.customer.client.command.MerchantRelationshipRevokeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantActionGrantDTO;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.dto.MerchantRelationshipDTO;
import io.github.loadup.pay.customer.client.facade.MerchantAuthorizationFacade;
import io.github.loadup.pay.customer.client.facade.MerchantEligibilityFacade;
import io.github.loadup.pay.customer.domain.gateway.MerchantAuthorizationGateway;
import io.github.loadup.pay.customer.domain.gateway.MerchantFactsGateway;
import io.github.loadup.pay.customer.domain.model.MerchantAction;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantFacts;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.function.Function;

import static io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum.CONFLICT;
import static io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum.GRANT_CONFLICT;
import static io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum.MERCHANT_INACTIVE;
import static io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum.RELATIONSHIP_CONFLICT;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CustomerService implements MerchantAuthorizationFacade, MerchantEligibilityFacade {
    private final CustomerActorProvider actors;
    private final MerchantFactsGateway facts;
    private final MerchantAuthorizationGateway authorization;
    private final MerchantQueryFacade merchants;
    private final CustomerCommandExecutor commands;
    private final CustomerConverter converter;
    private final IdGenerator ids;
    private final Clock clock;
    private final Validator validator;

    public CustomerService(
            CustomerActorProvider actors,
            MerchantFactsGateway facts,
            MerchantAuthorizationGateway authorization,
            MerchantQueryFacade merchants,
            CustomerCommandExecutor commands,
            CustomerConverter converter,
            IdGenerator ids,
            @Qualifier("customerClock") Clock clock,
            Validator validator) {
        this.actors = actors;
        this.facts = facts;
        this.authorization = authorization;
        this.merchants = merchants;
        this.commands = commands;
        this.converter = converter;
        this.ids = ids;
        this.clock = clock;
        this.validator = validator;
    }

    private static LocalDateTime local(OffsetDateTime value) {
        if (value == null) return null;
        // The database timestamp must preserve the accepted validity boundary to microsecond precision.
        if (value.getNano() % 1000 != 0) throw new CommonException(INVALID_ARGUMENT);
        return BusinessTime.local(value);
    }

    private static void expected(long actual, long requested) {
        if (actual != requested) throw new CommonException(CONFLICT);
    }

    @Override
    public MerchantRelationshipDTO createRelationship(MerchantRelationshipCreateCommand command) {
        return invoke(
                command,
                true,
                actor -> commands.execute(
                        actor,
                        "customer.relationship.create.v1",
                        command.requestKey(),
                        command,
                        MerchantRelationshipDTO.class,
                        () -> facts.lock(actor.tenantId(), command.childMerchantId()),
                        () -> {
                            active(actor.tenantId(), command.childMerchantId());
                            active(actor.tenantId(), command.parentMerchantId());
                            if (authorization
                                    .findLiveRelationship(
                                            actor.tenantId(), command.parentMerchantId(), command.childMerchantId())
                                    .isPresent()) throw new CommonException(RELATIONSHIP_CONFLICT);
                            MerchantRelationship model = MerchantRelationship.init(
                                            ids.generate(),
                                            command.parentMerchantId(),
                                            command.childMerchantId(),
                                            local(command.validFrom()),
                                            local(command.validTo()))
                                    .activate();
                            return new CustomerCommandExecutor.Prepared<>(
                                    model.id(),
                                    converter.toDTO(model),
                                    () -> authorization.saveRelationship(actor.tenantId(), model, -1));
                        }));
    }

    @Override
    public MerchantActionGrantDTO grant(MerchantActionGrantCommand command) {
        return invoke(
                command,
                true,
                actor -> commands.execute(
                        actor,
                        "customer.action.grant.v1",
                        command.requestKey(),
                        command,
                        MerchantActionGrantDTO.class,
                        () -> facts.lock(actor.tenantId(), command.payeeMerchantId()),
                        () -> {
                            active(actor.tenantId(), command.payeeMerchantId());
                            PrincipalType type = PrincipalType.valueOf(
                                    command.principalType().name());
                            MerchantAction action =
                                    MerchantAction.valueOf(command.action().name());
                            if (type == PrincipalType.MERCHANT) active(actor.tenantId(), command.principalId());
                            if (command.relationshipId() != null) {
                                MerchantRelationship relationship =
                                        authorization.lockRelationship(actor.tenantId(), command.relationshipId());
                                if (!relationship.childMerchantId().equals(command.payeeMerchantId())
                                        || !relationship.activeAt(LocalDateTime.now(clock))
                                        || type == PrincipalType.MERCHANT
                                        && !relationship
                                        .parentMerchantId()
                                        .equals(command.principalId()))
                                    throw new CommonException(ACTION_FORBIDDEN);
                                active(actor.tenantId(), relationship.parentMerchantId());
                            }
                            if (authorization
                                    .findGrant(
                                            actor.tenantId(),
                                            type,
                                            command.principalId(),
                                            command.payeeMerchantId(),
                                            action)
                                    .isPresent()) throw new CommonException(GRANT_CONFLICT);
                            MerchantActionGrant model = MerchantActionGrant.init(
                                            ids.generate(),
                                            type,
                                            command.principalId(),
                                            command.payeeMerchantId(),
                                            command.relationshipId(),
                                            action,
                                            local(command.validFrom()),
                                            local(command.validTo()))
                                    .activate();
                            return new CustomerCommandExecutor.Prepared<>(
                                    model.id(),
                                    converter.toDTO(model),
                                    () -> authorization.saveGrant(actor.tenantId(), model, -1));
                        }));
    }

    @Override
    public MerchantRelationshipDTO revokeRelationship(MerchantRelationshipRevokeCommand command) {
        return invoke(
                command,
                true,
                actor -> commands.execute(
                        actor,
                        "customer.relationship.revoke.v1",
                        command.requestKey(),
                        command,
                        MerchantRelationshipDTO.class,
                        () -> {
                            MerchantRelationship found = authorization
                                    .findRelationship(actor.tenantId(), command.id())
                                    .orElseThrow(() -> new CommonException(NOT_FOUND));
                            facts.lock(actor.tenantId(), found.childMerchantId());
                        },
                        () -> {
                            MerchantRelationship previous =
                                    authorization.lockRelationship(actor.tenantId(), command.id());
                            expected(previous.version(), command.expectedVersion());
                            MerchantRelationship revoked = previous.revoke();
                            return new CustomerCommandExecutor.Prepared<>(
                                    revoked.id(), converter.toDTO(revoked), () -> {
                                if (revoked != previous)
                                    authorization.saveRelationship(
                                            actor.tenantId(), revoked, previous.version());
                            });
                        }));
    }

    @Override
    public MerchantActionGrantDTO revokeGrant(MerchantActionGrantRevokeCommand command) {
        return invoke(
                command,
                true,
                actor -> commands.execute(
                        actor,
                        "customer.action.revoke.v1",
                        command.requestKey(),
                        command,
                        MerchantActionGrantDTO.class,
                        () -> {
                            MerchantActionGrant found = authorization
                                    .findGrantById(actor.tenantId(), command.id())
                                    .orElseThrow(() -> new CommonException(NOT_FOUND));
                            facts.lock(actor.tenantId(), found.payeeMerchantId());
                        },
                        () -> {
                            MerchantActionGrant previous = authorization.lockGrant(actor.tenantId(), command.id());
                            expected(previous.version(), command.expectedVersion());
                            MerchantActionGrant revoked = previous.revoke();
                            return new CustomerCommandExecutor.Prepared<>(
                                    revoked.id(), converter.toDTO(revoked), () -> {
                                if (revoked != previous)
                                    authorization.saveGrant(actor.tenantId(), revoked, previous.version());
                            });
                        }));
    }

    @Override
    public MerchantEligibilityDTO authorize(MerchantActionAuthorizeCommand command) {
        return invoke(command, false, actor -> {
            MerchantFacts payee = facts.lock(actor.tenantId(), command.payeeMerchantId());
            if (!payee.active()) return denied(MERCHANT_INACTIVE.getCode());
            MerchantAction action = MerchantAction.valueOf(command.action().name());
            MerchantActionGrant grant = authorization
                    .findGrant(
                            actor.tenantId(),
                            actor.principalType(),
                            actor.principalId(),
                            command.payeeMerchantId(),
                            action)
                    .orElse(null);
            MerchantRelationship relationship = grant == null || grant.relationshipId() == null
                    ? null
                    : authorization
                    .findRelationship(actor.tenantId(), grant.relationshipId())
                    .orElse(null);
            if (grant == null
                    || !grant.allows(
                    actor.principalType(),
                    actor.principalId(),
                    actor.merchantId(),
                    command.payeeMerchantId(),
                    action,
                    LocalDateTime.now(clock),
                    relationship)) return denied(ACTION_FORBIDDEN.getCode());
            if (actor.merchantId() != null
                    && !facts.find(actor.tenantId(), actor.merchantId())
                    .map(MerchantFacts::active)
                    .orElse(false)) return denied(MERCHANT_INACTIVE.getCode());
            return converter.toEligibility(
                    true,
                    "ALLOWED",
                    merchants
                            .find(actor.tenantId(), command.payeeMerchantId())
                            .orElseThrow(() -> new CommonException(NOT_FOUND)),
                    payee,
                    grant);
        });
    }

    private MerchantEligibilityDTO denied(String reason) {
        return converter.toEligibility(false, reason, null, null, null);
    }

    private void active(String tenant, String id) {
        if (!facts.find(tenant, id)
                .orElseThrow(() -> new CommonException(NOT_FOUND))
                .active()) throw new CommonException(MERCHANT_INACTIVE);
    }

    private <T> T invoke(Object command, boolean management, Function<CustomerActor, T> operation) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        CustomerActor actor = actors.current();
        if (management && !actor.canManage()) throw new CommonException(ACTION_FORBIDDEN);
        try {
            return TenantUtil.callWithTenant(actor.tenantId(), () -> operation.apply(actor));
        } catch (DomainViolation violation) {
            throw new CommonException(
                    switch (violation.reason()) {
                        case INVALID_STATE -> INVALID_STATE;
                        case CONFLICT -> CONFLICT;
                        default -> INVALID_ARGUMENT;
                    });
        }
    }
}
