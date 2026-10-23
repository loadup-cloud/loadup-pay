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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.limitcenter.app.converter.LimitConverter;
import io.github.loadup.pay.limitcenter.app.security.LimitActor;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.facade.LimitReservationFacade;
import io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitPolicyGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.MerchantLimitGateway;
import io.github.loadup.pay.limitcenter.domain.model.GateState;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketChange;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.domain.policy.LimitWindowPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.POLICY_NOT_ACTIVE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class LimitReservationService implements LimitReservationFacade {
    private final LimitTransactions transactions;
    private final LimitPolicyGateway policies;
    private final MerchantLimitGateway reservations;
    private final LimitConverter converter;
    private final IdGenerator ids;
    private final Clock clock;
    private final long reserveSeconds;
    private final LimitWindowPolicy windows = new LimitWindowPolicy();

    public LimitReservationService(
            LimitTransactions transactions,
            LimitPolicyGateway policies,
            MerchantLimitGateway reservations,
            LimitConverter converter,
            IdGenerator ids,
            @Qualifier("limitcenterClock") Clock clock,
            @Value("${loadup.modules.limitcenter.reserve-seconds:120}") long reserveSeconds) {
        if (reserveSeconds < 1 || reserveSeconds > 86400)
            throw new IllegalArgumentException("Reservation duration must be 1 to 86400 seconds");
        this.transactions = transactions;
        this.policies = policies;
        this.reservations = reservations;
        this.converter = converter;
        this.ids = ids;
        this.clock = clock;
        this.reserveSeconds = reserveSeconds;
    }

    private static void requireOwner(LimitActor actor, StoredLimitReservation stored) {
        if (!stored.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    @Override
    public LimitReservationDTO reserve(LimitReserveCommand command) {
        var actor = transactions.authorize(command, "pay:limit:reserve");
        return transactions.write(actor, IDEMPOTENCY_CONFLICT, () -> reserve(actor, command));
    }

    private LimitReservationDTO reserve(LimitActor actor, LimitReserveCommand command) {
        String tenant = actor.tenantId();
        DomainChecks.id(command.operationId());
        DomainChecks.id(command.paymentId());
        DomainChecks.id(command.orderId());
        DomainChecks.id(command.payeeMerchantId());
        DomainChecks.id(command.policyId());
        var amount = DomainChecks.amount(command.amountMinor(), command.currency());
        LocalDateTime initiated = BusinessTime.local(command.initiatedAt());
        if (initiated.getNano() % 1000 != 0 || initiated.getYear() < 1970 || initiated.getYear() >= 9999)
            throw new CommonException(INVALID_ARGUMENT);
        var gate = reservations.lockOrCreateGate(tenant, command.operationId(), command.paymentId());
        if (!gate.operationId().equals(command.operationId())
                || !gate.paymentId().equals(command.paymentId())) throw new CommonException(IDEMPOTENCY_CONFLICT);
        if (gate.state() == GateState.FENCED) throw new CommonException(EXECUTION_FENCED);
        var prior = reservations.findByOperation(tenant, command.operationId());
        if (prior.isPresent()) {
            var stored = prior.get();
            requireOwner(actor, stored);
            stored.reservation()
                    .requireSameIntent(
                            command.paymentId(),
                            command.orderId(),
                            command.payeeMerchantId(),
                            amount,
                            command.policyId(),
                            command.policyVersion(),
                            initiated);
            if (!stored.policyHash().equals(command.policyHash())
                    || !stored.requestDigest().equals(command.requestDigest()))
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            gate.requireOpen();
            return converter.toDTO(stored.reservation());
        }
        if (gate.state() != GateState.INIT) throw new CommonException(SNAPSHOT_INVALID);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (initiated.isAfter(now)) throw new CommonException(INVALID_ARGUMENT);
        var head =
                policies.lockHead(tenant, command.policyId()).orElseThrow(() -> new CommonException(POLICY_NOT_ACTIVE));
        var policy = policies.findRevision(tenant, command.policyId(), command.policyVersion())
                .orElseThrow(() -> new CommonException(POLICY_NOT_ACTIVE));
        if (head.latestVersion() < command.policyVersion()
                || !head.payeeMerchantId().equals(command.payeeMerchantId())
                || !policy.payeeMerchantId().equals(command.payeeMerchantId())
                || !policy.policyHash().equals(command.policyHash())) throw new CommonException(POLICY_NOT_ACTIVE);
        List<LimitBucketChange> changes = new ArrayList<>();
        for (var rule : policy.rules()) {
            var runtime = policies.lockRule(tenant, rule).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            RuntimeLimitRule.requireSameShape(rule, runtime.rule());
            if (!runtime.ownerPolicyId().equals(policy.policyId()) || runtime.policyVersion() != head.latestVersion())
                throw new CommonException(SNAPSHOT_INVALID);
            var key = new LimitBucketKey(
                    rule.ruleScopeId(),
                    rule.merchantId(),
                    rule.metric(),
                    rule.currency(),
                    rule.window(),
                    windows.at(rule.window(), initiated, rule.weekStart()));
            changes.add(new LimitBucketChange(
                    key,
                    rule.metric() == LimitMetric.AMOUNT ? amount.getCent() : 1,
                    runtime.rule().threshold(),
                    runtime.rule().enforcementEnabled()));
        }
        var before = reservations.lockBuckets(
                tenant, changes.stream().map(LimitBucketChange::key).toList());
        var after = before.reserve(changes);
        var reservation = LimitReservation.init(
                        ids.generate(),
                        command.operationId(),
                        command.paymentId(),
                        command.orderId(),
                        command.payeeMerchantId(),
                        amount,
                        command.policyId(),
                        command.policyVersion(),
                        initiated,
                        now.plusSeconds(reserveSeconds))
                .reserve();
        var items = changes.stream()
                .map(change -> new LimitReservationItem(
                        ids.generate(), reservation.id(), change.key(), change.requested(), 0, 0))
                .toList();
        reservations.insertReservation(
                tenant,
                new StoredLimitReservation(
                        reservation, command.policyHash(), command.requestDigest(), actor.serviceId()),
                items,
                before,
                after);
        reservations.saveGate(tenant, gate.open());
        return converter.toDTO(reservation);
    }

    @Override
    public LimitReservationDTO detail(LimitOperationQuery query) {
        var actor = transactions.authorize(query, "pay:limit:read");
        return transactions.read(actor, () -> {
            DomainChecks.id(query.operationId());
            var stored = reservations
                    .findByOperation(actor.tenantId(), query.operationId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            requireOwner(actor, stored);
            return converter.toDTO(stored.reservation());
        });
    }
}
