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
package io.github.loadup.pay.limitcenter.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.limitcenter.app.converter.*;
import io.github.loadup.pay.limitcenter.app.port.PaymentEvidenceClient;
import io.github.loadup.pay.limitcenter.app.security.LimitActor;
import io.github.loadup.pay.limitcenter.client.command.*;
import io.github.loadup.pay.limitcenter.client.dto.*;
import io.github.loadup.pay.limitcenter.client.facade.LimitSettlementFacade;
import io.github.loadup.pay.limitcenter.domain.gateway.*;
import io.github.loadup.pay.limitcenter.domain.model.*;
import io.github.loadup.pay.limitcenter.domain.policy.LimitWindowPolicy;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.*;

@Service
public class LimitSettlementService implements LimitSettlementFacade {
    private final LimitTransactions transactions;
    private final MerchantLimitGateway reservations;
    private final LimitPolicyGateway policies;
    private final LimitSettlementGateway settlements;
    private final PaymentEvidenceClient payment;
    private final LimitConverter converter;
    private final LimitSettlementConverter mapping;
    private final IdGenerator ids;
    private final Validator validator;
    private final String paymentService;
    private final LimitWindowPolicy windows = new LimitWindowPolicy();

    public LimitSettlementService(LimitTransactions transactions, MerchantLimitGateway reservations,
            LimitPolicyGateway policies, LimitSettlementGateway settlements, PaymentEvidenceClient payment,
            LimitConverter converter, LimitSettlementConverter mapping, IdGenerator ids, Validator validator,
            @Value("${loadup.modules.limitcenter.payment-service-id:payment}") String paymentService) {
        this.transactions = transactions; this.reservations = reservations; this.policies = policies;
        this.settlements = settlements; this.payment = payment; this.converter = converter; this.mapping = mapping;
        this.ids = ids; this.validator = validator; this.paymentService = paymentService;
        DomainChecks.id(paymentService);
    }

    @Override
    public LimitReservationDTO confirm(LimitConfirmCommand command) {
        var actor = actor(command, "pay:limit:confirm");
        return transactions.read(actor, () -> {
            ids(command.operationId(), command.paymentId(), command.businessEventId(), command.successEvidenceId());
            var first = reservations.findByOperation(actor.tenantId(), command.operationId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            owner(actor, first);
            var facts = facts(actor, command.operationId(), command.paymentId());
            var source = facts.payment();
            if (source.state() != PaymentState.SUCCEEDED || source.version() != command.paymentVersion()
                    || !command.successEvidenceId().equals(source.terminalEvidenceId())
                    || source.activeAttemptId() == null || source.activeAttemptId().isBlank()
                    || source.amountMinor() != command.amountMinor() || !source.currency().equals(command.currency()))
                throw new CommonException(OUTCOME_CONFLICT);
            String digest = digest("CONFIRM", actor, command.operationId(), command.paymentId(),
                    Long.toString(command.paymentVersion()), command.successEvidenceId(), Long.toString(command.amountMinor()),
                    command.currency(), BusinessTime.local(command.succeededAt()).toString());
            return transactions.write(actor, OUTCOME_CONFLICT, () -> {
                var gate = reservations.lockGate(actor.tenantId(), command.operationId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                var current = reservations.findByOperation(actor.tenantId(), command.operationId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                owner(actor, current); bind(current, facts);
                var prior = settlements.findReceipt(actor.tenantId(), command.operationId());
                if (prior.isPresent()) {
                    prior.get().requireSame(command.paymentId(), "CONFIRM", digest, command.paymentVersion(), command.successEvidenceId());
                    if (current.reservation().state() != LimitReservationState.CONFIRMED
                            || current.reservation().paymentSourceVersion() != command.paymentVersion())
                        throw new CommonException(SNAPSHOT_INVALID);
                    return converter.toDTO(current.reservation());
                }
                var value = current.reservation();
                if (!gate.paymentId().equals(value.paymentId()) || gate.state() != GateState.OPEN)
                    throw new CommonException(EXECUTION_FENCED);
                if (value.state() != LimitReservationState.EXECUTING
                        || !Objects.equals(value.activeAttemptId(), source.activeAttemptId()))
                    throw new CommonException(OUTCOME_CONFLICT);
                var items = items(actor, current);
                var before = reservations.lockExistingBuckets(actor.tenantId(), items.stream().map(LimitReservationItem::bucket).toList());
                var after = before.confirm(items);
                var changed = current.transitionTo(value.confirm(command.paymentId(), DomainChecks.amount(command.amountMinor(), command.currency()), command.paymentVersion()));
                reservations.updateBuckets(actor.tenantId(), before, after);
                settlements.updateItems(actor.tenantId(), items, items.stream().map(LimitReservationItem::confirm).toList());
                reservations.updateReservation(actor.tenantId(), changed, value.version());
                settlements.insertReceipt(actor.tenantId(), mapping.toReceipt(ids.generate(), command.operationId(), command.paymentId(),
                        "CONFIRM", command.businessEventId(), digest, command.paymentVersion(), command.successEvidenceId()));
                return converter.toDTO(changed.reservation());
            });
        });
    }

    @Override
    public LimitReleaseDTO release(LimitReleaseCommand command) {
        var actor = actor(command, "pay:limit:release");
        return transactions.read(actor, () -> {
            ids(command.operationId(), command.paymentId(), command.businessEventId(), command.noCollectionEvidenceId());
            DomainChecks.text(command.reasonCode(), 64);
            var existing = reservations.findByOperation(actor.tenantId(), command.operationId());
            existing.ifPresent(value -> owner(actor, value));
            var facts = facts(actor, command.operationId(), command.paymentId());
            var source = facts.payment();
            if (!(source.state() == PaymentState.CLOSED || source.state() == PaymentState.FAILED)
                    || !facts.executionFenced() || source.version() != command.paymentVersion()
                    || !command.noCollectionEvidenceId().equals(source.terminalEvidenceId()))
                throw new CommonException(OUTCOME_CONFLICT);
            String digest = digest("RELEASE", actor, command.operationId(), command.paymentId(),
                    Long.toString(command.paymentVersion()), command.noCollectionEvidenceId(), command.reasonCode());
            return transactions.write(actor, OUTCOME_CONFLICT, () -> {
                var gate = reservations.lockOrCreateGate(actor.tenantId(), command.operationId(), command.paymentId());
                if (!gate.paymentId().equals(command.paymentId())) throw new CommonException(OUTCOME_CONFLICT);
                var current = reservations.findByOperation(actor.tenantId(), command.operationId());
                current.ifPresent(value -> { owner(actor, value); bind(value, facts); });
                var prior = settlements.findReceipt(actor.tenantId(), command.operationId());
                if (prior.isPresent()) {
                    prior.get().requireSame(command.paymentId(), "RELEASE", digest, command.paymentVersion(), command.noCollectionEvidenceId());
                    if (gate.state() != GateState.FENCED || gate.paymentSourceVersion() != command.paymentVersion()
                            || !command.noCollectionEvidenceId().equals(gate.noCollectionEvidenceId()))
                        throw new CommonException(SNAPSHOT_INVALID);
                    return mapping.toRelease(command.operationId(), command.paymentId(), current.map(v -> converter.toDTO(v.reservation())).orElse(null),
                            command.paymentVersion(), command.noCollectionEvidenceId());
                }
                if (gate.state() == GateState.FENCED) throw new CommonException(SNAPSHOT_INVALID);
                StoredLimitReservation changed = current.orElse(null);
                if (current.isPresent()) {
                    var stored = current.get(); var value = stored.reservation();
                    if (value.state() == LimitReservationState.RESERVED || value.state() == LimitReservationState.EXECUTING) {
                        var items = items(actor, stored);
                        var before = reservations.lockExistingBuckets(actor.tenantId(), items.stream().map(LimitReservationItem::bucket).toList());
                        var after = before.releaseReserved(items);
                        changed = stored.transitionTo(value.release(mapping.toReleaseEvidence(command.paymentId(), value.amount(),
                                command.paymentVersion(), command.noCollectionEvidenceId(), true)));
                        reservations.updateBuckets(actor.tenantId(), before, after);
                        reservations.updateReservation(actor.tenantId(), changed, value.version());
                    } else if (value.state() != LimitReservationState.EXPIRED) throw new CommonException(OUTCOME_CONFLICT);
                }
                settlements.fenceGate(actor.tenantId(), gate.fence(command.paymentVersion(), command.noCollectionEvidenceId()));
                settlements.insertReceipt(actor.tenantId(), mapping.toReceipt(ids.generate(), command.operationId(), command.paymentId(),
                        "RELEASE", command.businessEventId(), digest, command.paymentVersion(), command.noCollectionEvidenceId()));
                return mapping.toRelease(command.operationId(), command.paymentId(), changed == null ? null : converter.toDTO(changed.reservation()),
                        command.paymentVersion(), command.noCollectionEvidenceId());
            });
        });
    }

    private LimitActor actor(Object command, String permission) {
        var actor = transactions.authorize(command, permission);
        if (!paymentService.equals(actor.serviceId()) || !payment.supportsTenant(actor.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return actor;
    }

    private PaymentSettlementFactsDTO facts(LimitActor actor, String operation, String paymentId) {
        var result = payment.paymentFacts(actor.tenantId(), paymentId);
        if (result == null || !validator.validate(result).isEmpty() || !operation.equals(result.limitOperationId()))
            throw new CommonException(SNAPSHOT_INVALID);
        var value = result.payment();
        if (value == null || value.state() == null || !paymentId.equals(value.paymentId()) || value.amountMinor() <= 0
                || !"CNY".equals(value.currency()) || value.orderId() == null || value.orderId().isBlank()
                || value.version() <= 0 || value.terminalEvidenceId() == null || value.terminalEvidenceId().isBlank()
                || value.action() != null || value.initiatedAt() == null) throw new CommonException(OUTCOME_CONFLICT);
        return result;
    }

    private void bind(StoredLimitReservation stored, PaymentSettlementFactsDTO facts) {
        var value = stored.reservation(); var source = facts.payment();
        if (!value.operationId().equals(facts.limitOperationId()) || !value.paymentId().equals(source.paymentId())
                || !value.orderId().equals(source.orderId()) || !value.payeeMerchantId().equals(facts.payeeMerchantId())
                || value.amount().getCent() != source.amountMinor() || !value.amount().getCurrencyValue().equals(source.currency())
                || !value.initiatedAt().equals(BusinessTime.local(source.initiatedAt())))
            throw new CommonException(OUTCOME_CONFLICT);
    }

    private List<LimitReservationItem> items(LimitActor actor, StoredLimitReservation stored) {
        var value = stored.reservation();
        var head = policies.lockHead(actor.tenantId(), value.policyId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!head.payeeMerchantId().equals(value.payeeMerchantId()) || head.latestVersion() < value.policyVersion())
            throw new CommonException(SNAPSHOT_INVALID);
        var policy = policies.findRevision(actor.tenantId(), value.policyId(), value.policyVersion())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!policy.policyHash().equals(stored.policyHash()) || !policy.payeeMerchantId().equals(value.payeeMerchantId()))
            throw new CommonException(SNAPSHOT_INVALID);
        var items = reservations.loadItems(actor.tenantId(), value.id());
        var expected = new HashMap<LimitBucketKey,Long>();
        for (var rule : policy.rules()) expected.put(new LimitBucketKey(rule.ruleScopeId(), rule.merchantId(), rule.metric(),
                rule.currency(), rule.window(), windows.at(rule.window(), value.initiatedAt(), rule.weekStart())),
                rule.metric() == LimitMetric.AMOUNT ? value.amount().getCent() : 1L);
        var seen = new HashSet<LimitBucketKey>();
        if (items.size() != expected.size()) throw new CommonException(SNAPSHOT_INVALID);
        for (var item : items) if (!item.reservationId().equals(value.id()) || !seen.add(item.bucket())
                || !Objects.equals(expected.get(item.bucket()), item.reserved()) || item.consumed() != 0 || item.restored() != 0)
            throw new CommonException(SNAPSHOT_INVALID);
        return items;
    }

    private void owner(LimitActor actor, StoredLimitReservation stored) {
        if (!actor.serviceId().equals(stored.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void ids(String operation, String paymentId, String event, String evidence) {
        DomainChecks.id(operation); DomainChecks.id(paymentId); DomainChecks.id(event); DomainChecks.id(evidence);
    }

    private String digest(String kind, LimitActor actor, String... fields) {
        var canonical = new StringBuilder();
        for (String value : java.util.stream.Stream.concat(java.util.stream.Stream.of("limit.settlement.v1", kind, actor.tenantId(), actor.serviceId()),
                Arrays.stream(fields)).toList()) canonical.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
        return DigestUtils.sha256(canonical.toString());
    }
}
