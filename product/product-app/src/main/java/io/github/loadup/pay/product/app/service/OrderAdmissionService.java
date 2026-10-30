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
package io.github.loadup.pay.product.app.service;

import io.github.loadup.commons.contract.ContractReference;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.modules.contract.domain.model.ContractDecision;
import io.github.loadup.modules.contract.domain.model.ContractRecord;
import io.github.loadup.modules.contract.domain.model.ContractRuntimeResolver;
import io.github.loadup.modules.contract.domain.model.ContractStatus;
import io.github.loadup.modules.contract.domain.model.MerchantContract;
import io.github.loadup.modules.contract.domain.model.MerchantContractRevision;
import io.github.loadup.modules.contract.domain.model.TypedValue;
import io.github.loadup.pay.product.app.converter.OrderAdmissionConverter;
import io.github.loadup.pay.product.app.model.ActivatedLimitPolicy;
import io.github.loadup.pay.product.app.model.CompiledAcquiringTerms;
import io.github.loadup.pay.product.app.model.EncodedAcquiringTerms;
import io.github.loadup.pay.product.app.port.CompiledTermsCodec;
import io.github.loadup.pay.product.app.port.CurrentContractGateway;
import io.github.loadup.pay.product.app.port.CustomerEligibilityLookup;
import io.github.loadup.pay.product.app.security.ProductActor;
import io.github.loadup.pay.product.app.security.ProductActorProvider;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.client.facade.OrderAdmissionFacade;
import io.github.loadup.pay.product.domain.gateway.OrderAdmissionGateway;
import io.github.loadup.pay.product.domain.model.AdmissionDecision;
import io.github.loadup.pay.product.domain.model.AdmissionIntent;
import io.github.loadup.pay.product.domain.model.ContractAdmissionState;
import io.github.loadup.pay.product.domain.model.MerchantContractFacts;
import io.github.loadup.pay.product.domain.model.MerchantEligibilityFacts;
import io.github.loadup.pay.product.domain.model.OrderAdmission;
import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;
import io.github.loadup.pay.product.domain.policy.OrderAdmissionPolicy;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;


import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.UNSUPPORTED_POLICY;

@Service
public class OrderAdmissionService implements OrderAdmissionFacade {
    private final ProductActorProvider actors;
    private final OrderAdmissionGateway admissions;
    private final CurrentContractGateway contracts;
    private final CustomerEligibilityLookup customer;
    private final AcquiringTermsCompiler compiler;
    private final CompiledTermsCodec codec;
    private final OrderAdmissionConverter converter;
    private final Validator validator;
    private final IdGenerator ids;
    private final Clock clock;
    private final long materializeSeconds;
    private final TransactionTemplate transaction;
    private final OrderAdmissionPolicy policy = new OrderAdmissionPolicy();

    public OrderAdmissionService(
            ProductActorProvider actors,
            OrderAdmissionGateway admissions,
            CurrentContractGateway contracts,
            CustomerEligibilityLookup customer,
            AcquiringTermsCompiler compiler,
            CompiledTermsCodec codec,
            OrderAdmissionConverter converter,
            Validator validator,
            IdGenerator ids,
            @Qualifier("productClock") Clock clock,
            @Value("${loadup.modules.product.materialize-seconds:300}") long materializeSeconds,
            PlatformTransactionManager transactionManager) {
        if (materializeSeconds < 1 || materializeSeconds > 86400)
            throw new IllegalArgumentException("Product materialization window must be between 1 and 86400 seconds");
        this.actors = actors;
        this.admissions = admissions;
        this.contracts = contracts;
        this.customer = customer;
        this.compiler = compiler;
        this.codec = codec;
        this.converter = converter;
        this.validator = validator;
        this.ids = ids;
        this.clock = clock;
        this.materializeSeconds = materializeSeconds;
        transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static Map<String, TypedValue> trustedFacts(
            io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO merchant, AdmissionIntent intent) {
        if (merchant == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
        Map<String, TypedValue> values = new HashMap<>();
        values.put("merchant.id", TypedValue.text(merchant.id()));
        values.put("merchant.code", TypedValue.text(merchant.merchantCode()));
        values.put("merchant.type", TypedValue.text(merchant.type()));
        values.put("merchant.industry", TypedValue.text(merchant.industry()));
        values.put("merchant.country", TypedValue.text(merchant.country()));
        values.put("merchant.active", TypedValue.bool(merchant.active()));
        if (merchant.province() != null) values.put("merchant.province", TypedValue.text(merchant.province()));
        if (merchant.city() != null) values.put("merchant.city", TypedValue.text(merchant.city()));
        values.put("transaction.amountMinor", TypedValue.integer(intent.amount().getCent()));
        values.put("transaction.currency", TypedValue.text(intent.amount().getCurrencyValue()));
        return Map.copyOf(values);
    }

    @Override
    public OrderAdmissionDTO authorize(OrderAdmissionAuthorizeCommand command) {
        validate(command);
        ProductActor actor = requireActor("pay:product:admit");
        // The customer network call must never join a caller's local transaction.
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return invoke(actor, () -> {
            AdmissionIntent intent = new AdmissionIntent(
                    command.orderId(),
                    command.payeeMerchantId(),
                    command.productScopeKey(),
                    command.requestDigest(),
                    DomainChecks.cnyAmount(command.amountMinor(), command.currency()),
                    command.requestedDueAt() == null ? null : BusinessTime.local(command.requestedDueAt()),
                    actor.scopeHash());
            var prior = admissions.findByOrder(actor.tenantId(), intent.orderId());
            if (prior.isPresent()) return replay(prior.get(), intent);
            var eligibility = customer.authorizeNewOrder(actor.tenantId(), intent.payeeMerchantId());
            var merchant = converter.toFacts(intent.payeeMerchantId(), eligibility);
            String admissionId = ids.generate();
            try {
                StoredOrderAdmission stored = Objects.requireNonNull(transaction.execute(status -> {
                    var first = admissions.findByOrder(actor.tenantId(), intent.orderId());
                    if (first.isPresent()) {
                        return checked(first.get(), intent);
                    }
                    var header =
                            contracts.lockByScope(actor.tenantId(), intent.payeeMerchantId(), intent.productScopeKey());
                    // Serialize against both status changes and another admission waiting on this contract.
                    var afterLock = admissions.findByOrder(actor.tenantId(), intent.orderId());
                    if (afterLock.isPresent()) {
                        return checked(afterLock.get(), intent);
                    }
                    Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    LocalDateTime now = LocalDateTime.ofInstant(at, clock.getZone());
                    StoredOrderAdmission decision;
                    if (!merchant.eligible()) decision = denied(admissionId, intent, merchant.reasonCode(), merchant);
                    else if (header.isEmpty()) decision = denied(admissionId, intent, "CONTRACT_NOT_FOUND", merchant);
                    else decision = decide(admissionId, intent, header.get(), eligibility, merchant, at, now);
                    admissions.insert(actor.tenantId(), decision);
                    return decision;
                }));
                return present(stored);
            } catch (Exception collision) {
                // TransactionTemplate has rolled back the losing insert before this fresh primary read.
                return replay(
                        admissions
                                .findByOrder(actor.tenantId(), intent.orderId())
                                .orElseThrow(() -> new CommonException(DEPENDENCY_UNAVAILABLE)),
                        intent);
            }
        });
    }

    private StoredOrderAdmission decide(
            String id,
            AdmissionIntent intent,
            ContractRecord header,
            io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO eligibility,
            MerchantEligibilityFacts merchant,
            Instant at,
            LocalDateTime now) {
        if (!header.tenantId().equals(TenantUtil.getTenantId())
                || !header.merchantId().equals(intent.payeeMerchantId())
                || !header.scopeKey().equals(intent.productScopeKey())) throw new CommonException(SNAPSHOT_INVALID);
        if (header.status() != ContractStatus.NORMAL) return denied(id, intent, "CONTRACT_NOT_ADMISSIBLE", merchant);
        MerchantContractRevision snapshot = contracts.snapshot(header);
        var selected = snapshot.items().entrySet().stream()
                .filter(entry -> entry.getValue().configuration().values().containsKey("trade.amountModel"))
                .toList();
        if (selected.size() != 1) throw new CommonException(UNSUPPORTED_POLICY);
        var item = selected.getFirst();
        var view = new MerchantContract(
                header.tenantId(),
                header.merchantId(),
                header.scopeKey(),
                header.id(),
                header.status(),
                header.generation(),
                List.of(snapshot));
        ContractDecision runtime = new ContractRuntimeResolver()
                .resolve(view, item.getKey(), at, trustedFacts(eligibility.merchant(), intent));
        if (!runtime.allowed())
            return denied(id, intent, "CONTRACT_" + runtime.reason().name(), merchant);
        ActivatedLimitPolicy publication = contracts
                .publication(header.tenantId(), header.id(), snapshot.revision())
                .orElse(null);
        if (publication == null || !publication.activeAt(now)) return denied(id, intent, "POLICY_NOT_ACTIVE", merchant);
        AcquiringTermsDTO terms = compiler.compile(runtime.configuration(), publication);
        if (!policy.acceptsRequestedDeadline(intent, now, terms.order().maxWaitSeconds()))
            return denied(id, intent, "ORDER_DEADLINE_INVALID", merchant);
        EncodedAcquiringTerms compiled = codec.encode(new CompiledAcquiringTerms(
                1,
                header.id(),
                snapshot.revision(),
                snapshot.snapshotHash(),
                terms,
                runtime.configuration().origins()));
        var reference = new ContractReference(
                header.id(), snapshot.revision(), header.generation(), snapshot.snapshotHash(), compiled.hash());
        var current = new MerchantContractFacts(
                header.merchantId(),
                reference,
                ContractAdmissionState.ACTIVE,
                LocalDateTime.ofInstant(snapshot.effectiveFrom(), clock.getZone()),
                snapshot.effectiveTo() == null
                        ? null
                        : LocalDateTime.ofInstant(snapshot.effectiveTo(), clock.getZone()),
                true);
        OrderAdmission admitted = policy.authorize(
                id,
                intent.orderId(),
                intent.payeeMerchantId(),
                intent.requestDigest(),
                current,
                merchant,
                now,
                now.plusSeconds(materializeSeconds));
        return new StoredOrderAdmission(
                intent, admitted, merchant.merchantVersion(), merchant.authorizationVersion(), compiled.json());
    }

    private StoredOrderAdmission denied(
            String id, AdmissionIntent intent, String reason, MerchantEligibilityFacts merchant) {
        return new StoredOrderAdmission(
                intent,
                new OrderAdmission(
                        id,
                        intent.orderId(),
                        intent.payeeMerchantId(),
                        intent.requestDigest(),
                        AdmissionDecision.DENIED,
                        reason,
                        null,
                        null,
                        null),
                merchant.merchantVersion(),
                merchant.authorizationVersion(),
                null);
    }

    @Override
    public OrderAdmissionDTO detail(IdQuery query) {
        validate(query);
        ProductActor actor = requireActor("pay:product:read");
        return invoke(actor, () -> {
            DomainChecks.id(query.id());
            StoredOrderAdmission stored =
                    admissions.findById(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            if (!actor.scopeHash().equals(stored.intent().callerScopeHash()))
                throw new CommonException(ACTION_FORBIDDEN);
            return present(stored);
        });
    }

    private OrderAdmissionDTO replay(StoredOrderAdmission stored, AdmissionIntent intent) {
        return present(checked(stored, intent));
    }

    private StoredOrderAdmission checked(StoredOrderAdmission stored, AdmissionIntent intent) {
        if (!stored.intent().callerScopeHash().equals(intent.callerScopeHash()))
            throw new CommonException(ACTION_FORBIDDEN);
        stored.intent().requireSame(intent);
        return stored;
    }

    private OrderAdmissionDTO present(StoredOrderAdmission stored) {
        AcquiringTermsDTO terms = stored.admission().decision() == AdmissionDecision.ADMITTED
                ? codec.decode(stored).terms()
                : null;
        return converter.toDTO(stored.admission(), terms);
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private ProductActor requireActor(String permission) {
        ProductActor actor = actors.current();
        if (!actor.permissions().contains(permission)) throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    private <T> T invoke(ProductActor actor, Supplier<T> work) {
        try {
            return TenantUtil.callWithTenant(actor.tenantId(), work::get);
        } catch (DomainViolation violation) {
            throw new CommonException(
                    violation.reason() == DomainViolation.Reason.CONFLICT ? IDEMPOTENCY_CONFLICT : INVALID_ARGUMENT);
        } catch (DateTimeException invalidTime) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }
}
