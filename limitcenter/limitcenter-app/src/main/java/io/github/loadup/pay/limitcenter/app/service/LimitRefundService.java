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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.limitcenter.app.converter.LimitRefundConverter;
import io.github.loadup.pay.limitcenter.app.port.PaymentEvidenceClient;
import io.github.loadup.pay.limitcenter.app.security.LimitActor;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.client.facade.LimitRefundFacade;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitPolicyGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitRefundGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitSettlementGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.MerchantLimitGateway;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.domain.model.GateState;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitItemRestore;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitRefundReceipt;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.domain.policy.LimitWindowPolicy;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.domain.model.RefundState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;


import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.DEPENDENCY_NOT_READY;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.OUTCOME_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class LimitRefundService implements LimitRefundFacade {
    private final LimitTransactions transactions;
    private final MerchantLimitGateway reservations;
    private final LimitPolicyGateway policies;
    private final LimitSettlementGateway settlements;
    private final LimitRefundGateway receipts;
    private final PaymentEvidenceClient payment;
    private final LimitRefundConverter converter;
    private final IdGenerator ids;
    private final Clock clock;
    private final String paymentService;
    private final LimitWindowPolicy windows = new LimitWindowPolicy();

    public LimitRefundService(LimitTransactions transactions, MerchantLimitGateway reservations, LimitPolicyGateway policies,
                              LimitSettlementGateway settlements, LimitRefundGateway receipts, PaymentEvidenceClient payment,
                              LimitRefundConverter converter, IdGenerator ids, Clock clock,
                              @Value("${loadup.modules.limitcenter.payment-service-id:payment}") String paymentService) {
        this.transactions = transactions;
        this.reservations = reservations;
        this.policies = policies;
        this.settlements = settlements;
        this.receipts = receipts;
        this.payment = payment;
        this.converter = converter;
        this.ids = ids;
        this.clock = clock;
        this.paymentService = DomainChecks.id(paymentService);
    }

    @Override
    public LimitRestoreDTO restoreRefund(LimitRefundRestoreCommand command) {
        var actor = transactions.authorize(command, "pay:limit:refund:restore");
        if (!paymentService.equals(actor.serviceId()) || !payment.supportsTenant(actor.tenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        return transactions.withTenantContext(actor, () -> {
            DomainChecks.id(command.operationId());
            DomainChecks.id(command.paymentId());
            DomainChecks.id(command.refundId());
            DomainChecks.id(command.businessEventId());
            DomainChecks.id(command.refundEvidenceId());
            if (command.refundAmountMinor() > command.cumulativePaymentRefundedMinor()) {
                throw new CommonException(INVALID_ARGUMENT);
            }
            var digest = digest(actor, command);
            var prior = receipts.find(actor.tenantId(), command.refundId());
            if (prior.isPresent()) {
                return replay(actor, prior.get(), digest);
            }
            var first = reservations.findByOperation(actor.tenantId(), command.operationId())
                    .orElseThrow(() -> new CommonException(DEPENDENCY_NOT_READY));
            owner(actor, first);
            // Query before acquiring any local database transaction or lock.
            var facts = payment.refundFacts(actor.tenantId(), command.refundId());
            bind(command, first, facts);
            return transactions.writeLocal(actor, OUTCOME_CONFLICT, () -> {
                var gate = reservations.lockGate(actor.tenantId(), command.operationId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                var known = receipts.find(actor.tenantId(), command.refundId());
                if (known.isPresent()) {
                    return replay(actor, known.get(), digest);
                }
                var stored = reservations.findByOperation(actor.tenantId(), command.operationId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                owner(actor, stored);
                bind(command, stored, facts);
                var value = stored.reservation();
                if (!value.paymentId().equals(gate.paymentId()) || gate.state() != GateState.OPEN) {
                    throw new CommonException(OUTCOME_CONFLICT);
                }
                if (value.state() != LimitReservationState.CONFIRMED) {
                    if (value.state() == LimitReservationState.RESERVED || value.state() == LimitReservationState.EXECUTING) {
                        throw new CommonException(DEPENDENCY_NOT_READY);
                    }
                    throw new CommonException(OUTCOME_CONFLICT);
                }
                var confirmation = settlements.findReceipt(actor.tenantId(), command.operationId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!"CONFIRM".equals(confirmation.kind()) || !value.paymentId().equals(confirmation.paymentId())
                        || confirmation.paymentVersion() != facts.paymentVersion()
                        || value.paymentSourceVersion() != facts.paymentVersion()
                        || !confirmation.evidenceId().equals(facts.paymentEvidenceId())
                        || !Objects.equals(value.activeAttemptId(), facts.paymentAttemptId())) {
                    throw new CommonException(OUTCOME_CONFLICT);
                }
                var head = policies.lockHead(actor.tenantId(), value.policyId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!head.payeeMerchantId().equals(value.payeeMerchantId()) || head.latestVersion() < value.policyVersion()) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                var policy = policies.findRevision(actor.tenantId(), value.policyId(), value.policyVersion())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!policy.policyHash().equals(stored.policyHash()) || !policy.payeeMerchantId().equals(value.payeeMerchantId())) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                var items = items(actor, stored, policy);
                var before = reservations.lockExistingBuckets(actor.tenantId(), items.stream().map(LimitReservationItem::bucket).toList());
                var changes = items.stream().map(item -> item.restoreRefund(value, facts.refund().cumulativePaymentRefundedMinor(),
                        policy.refundAmountRestore(), policy.refundCountRestore())).toList();
                var after = before.restoreRefund(changes);
                long amount = changes.stream().filter(c -> c.item().bucket().metric() == LimitMetric.AMOUNT)
                        .mapToLong(LimitItemRestore::delta).max().orElse(0);
                boolean count = changes.stream().anyMatch(c -> c.item().bucket().metric() == LimitMetric.PAYMENT_COUNT && c.delta() > 0);
                reservations.updateBuckets(actor.tenantId(), before, after);
                settlements.updateItems(actor.tenantId(), items, changes.stream().map(LimitItemRestore::item).toList());
                var receipt = converter.toReceipt(ids.generate(), actor.serviceId(), command, digest,
                        facts.refund().cumulativePaymentRefundedMinor(), facts.paymentRefundVersion(),
                        amount > 0 || count ? "RESTORED" : "NO_ACTION", amount, count,
                        LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
                receipts.insert(actor.tenantId(), receipt);
                return converter.toDTO(receipt);
            });
        });
    }

    private List<LimitReservationItem> items(LimitActor actor, StoredLimitReservation stored, ActivatedLimitPolicy policy) {
        var value = stored.reservation();
        var expected = new HashMap<LimitBucketKey, Long>();
        for (var rule : policy.rules()) {
            expected.put(new LimitBucketKey(rule.ruleScopeId(), rule.merchantId(), rule.metric(), rule.currency(), rule.window(),
                            windows.at(rule.window(), value.initiatedAt(), rule.weekStart())),
                    rule.metric() == LimitMetric.AMOUNT ? value.amount().getCent() : 1L);
        }
        var items = reservations.loadItems(actor.tenantId(), value.id());
        var seen = new HashSet<LimitBucketKey>();
        if (items.size() != expected.size() || items.isEmpty()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        for (var item : items) {
            if (!value.id().equals(item.reservationId()) || !seen.add(item.bucket())
                    || !Objects.equals(expected.get(item.bucket()), item.reserved()) || item.consumed() != item.reserved()) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        }
        return items;
    }

    private void bind(LimitRefundRestoreCommand command, StoredLimitReservation stored, RefundSettlementFactsDTO facts) {
        if (facts == null || facts.refund() == null) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var refund = facts.refund();
        var value = stored.reservation();
        if (refund.state() != RefundState.SUCCEEDED || !command.refundId().equals(refund.refundId())
                || !command.paymentId().equals(refund.paymentId()) || !command.operationId().equals(refund.operationId())
                || command.refundVersion() != refund.refundVersion() || !command.refundEvidenceId().equals(refund.evidenceId())
                || command.refundAmountMinor() != refund.amountMinor() || !command.currency().equals(refund.currency())
                || command.cumulativePaymentRefundedMinor() > refund.cumulativePaymentRefundedMinor()
                || !value.operationId().equals(refund.operationId()) || !value.paymentId().equals(refund.paymentId())
                || !value.orderId().equals(refund.orderId()) || !value.payeeMerchantId().equals(refund.payeeMerchantId())
                || !value.amount().getCurrencyValue().equals(refund.currency()) || value.amount().getCent() != facts.paymentAmountMinor()
                || !value.initiatedAt().equals(BusinessTime.local(facts.paymentInitiatedAt()))) {
            throw new CommonException(OUTCOME_CONFLICT);
        }
    }

    private void owner(LimitActor actor, StoredLimitReservation stored) {
        if (!actor.serviceId().equals(stored.callerServiceId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }

    private LimitRestoreDTO replay(LimitActor actor, LimitRefundReceipt receipt, String digest) {
        if (!receipt.callerServiceId().equals(actor.serviceId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (!receipt.requestDigest().equals(digest)) {
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        }
        return converter.toDTO(receipt);
    }

    private String digest(LimitActor actor, LimitRefundRestoreCommand command) {
        var canonical = new StringBuilder("limit.refund.v1/");
        for (var field : List.of(actor.tenantId(), actor.serviceId(), command.operationId(), command.paymentId(), command.refundId(),
                Long.toString(command.refundVersion()), command.businessEventId(), Long.toString(command.refundAmountMinor()),
                Long.toString(command.cumulativePaymentRefundedMinor()), command.currency(), command.refundEvidenceId())) {
            canonical.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
        return DigestUtils.sha256(canonical.toString());
    }
}
