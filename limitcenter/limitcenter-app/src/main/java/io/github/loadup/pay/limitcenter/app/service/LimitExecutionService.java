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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.limitcenter.app.converter.LimitConverter;
import io.github.loadup.pay.limitcenter.app.port.ExecutionPermitIssuer;
import io.github.loadup.pay.limitcenter.app.security.LimitActor;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.facade.LimitExecutionFacade;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitPolicyGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.MerchantLimitGateway;
import io.github.loadup.pay.limitcenter.domain.model.GateState;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitExecutionGrant;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.domain.policy.LimitWindowPolicy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;


import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.OUTCOME_UNKNOWN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.PERMIT_UNAVAILABLE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.RESERVATION_EXPIRED;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class LimitExecutionService implements LimitExecutionFacade {
    private final LimitTransactions transactions;
    private final MerchantLimitGateway reservations;
    private final LimitPolicyGateway policies;
    private final ExecutionPermitIssuer permits;
    private final LimitConverter converter;
    private final Clock clock;
    private final LimitWindowPolicy windows = new LimitWindowPolicy();

    public LimitExecutionService(
            LimitTransactions transactions,
            MerchantLimitGateway reservations,
            LimitPolicyGateway policies,
            ExecutionPermitIssuer permits,
            LimitConverter converter,
            Clock clock) {
        this.transactions = transactions;
        this.reservations = reservations;
        this.policies = policies;
        this.permits = permits;
        this.converter = converter;
        this.clock = clock;
    }

    private static void owner(LimitActor actor, StoredLimitReservation stored) {
        if (!actor.serviceId().equals(stored.callerServiceId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }

    private static boolean equalHash(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public LimitExecutionPermitDTO beginExecution(LimitBeginExecutionCommand command) {
        var actor = transactions.authorize(command, "pay:limit:execute");
        return transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> {
            DomainChecks.id(command.attemptId());
            DomainChecks.text(command.requestKey(), 128);
            var stored = lockOperation(actor, command.operationId());
            var value = stored.reservation();
            if (value.state() == LimitReservationState.EXECUTING) {
                if (!command.attemptId().equals(value.activeAttemptId())) {
                    throw new CommonException(OUTCOME_UNKNOWN);
                }
                var grant = grant(actor.tenantId(), value);
                if (!command.requestKey().equals(grant.requestKey()) || command.previousAttemptEvidenceId() != null) {
                    throw new CommonException(IDEMPOTENCY_CONFLICT);
                }
                String permit = issue(actor, stored, grant);
                if (!equalHash(grant.permitHash(), DigestUtils.sha256(permit))) {
                    throw new CommonException(PERMIT_UNAVAILABLE);
                }
                return converter.toPermitDTO(grant, permit);
            }
            if (value.state() == LimitReservationState.EXPIRED) {
                throw new CommonException(RESERVATION_EXPIRED);
            }
            if (value.state() != LimitReservationState.RESERVED) {
                throw new CommonException(INVALID_STATE);
            }
            if (command.previousAttemptEvidenceId() != null) {
                throw new CommonException(INVALID_ARGUMENT);
            }
            LocalDateTime now = now();
            if (!now.isBefore(value.reserveExpiresAt())) {
                throw new CommonException(RESERVATION_EXPIRED);
            }
            // Public evidence identifiers never establish proof of a previous channel attempt's final outcome.
            var next = stored.transitionTo(value.beginExecution(command.attemptId(), now));
            String key = permits.activeKeyId();
            String permit = permits.issue(
                    actor.tenantId(),
                    next,
                    command.attemptId(),
                    next.reservation().executionEpoch(),
                    command.requestKey(),
                    key);
            var grant = new LimitExecutionGrant(
                    value.operationId(),
                    value.paymentId(),
                    command.attemptId(),
                    next.reservation().executionEpoch(),
                    command.requestKey(),
                    key,
                    DigestUtils.sha256(permit));
            reservations.insertExecutionGrant(actor.tenantId(), grant);
            reservations.updateReservation(actor.tenantId(), next, value.version());
            return converter.toPermitDTO(grant, permit);
        });
    }

    @Override
    public LimitExecutionVerificationDTO verifyExecution(LimitExecutionVerifyCommand command) {
        var actor = transactions.authorize(command, "pay:limit:verify");
        return transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> {
            DomainChecks.id(command.paymentId());
            DomainChecks.id(command.attemptId());
            var stored = lockOperation(actor, command.operationId());
            var value = stored.reservation();
            if (value.state() != LimitReservationState.EXECUTING) {
                return new LimitExecutionVerificationDTO(false, "NOT_EXECUTING");
            }
            var grant = grant(actor.tenantId(), value);
            boolean valid = value.paymentId().equals(command.paymentId())
                    && value.activeAttemptId().equals(command.attemptId())
                    && value.executionEpoch() == command.executionEpoch()
                    && value.amount().getCent() == command.amountMinor()
                    && value.amount().getCurrencyValue().equals(command.currency())
                    && equalHash(grant.permitHash(), DigestUtils.sha256(command.permit()));
            return new LimitExecutionVerificationDTO(valid, valid ? "VALID" : "PERMIT_MISMATCH");
        });
    }

    @Override
    public LimitReservationDTO expire(LimitExpireCommand command) {
        var actor = transactions.authorize(command, "pay:limit:expire");
        return transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> {
            var stored = lockOperation(actor, command.operationId());
            var value = stored.reservation();
            LocalDateTime now = now();
            // No TTL recovery is ever applied to EXECUTING, including UNKNOWN channel outcomes.
            if (value.state() != LimitReservationState.RESERVED || now.isBefore(value.reserveExpiresAt())) {
                return converter.toDTO(value);
            }
            var items = reservations.loadItems(actor.tenantId(), value.id());
            var policy = policies.findRevision(actor.tenantId(), value.policyId(), value.policyVersion())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!policy.policyHash().equals(stored.policyHash())
                    || !policy.payeeMerchantId().equals(value.payeeMerchantId())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            Map<LimitBucketKey, Long> expected = new HashMap<>();
            for (var rule : policy.rules()) {
                expected.put(
                        new LimitBucketKey(
                                rule.ruleScopeId(),
                                rule.merchantId(),
                                rule.metric(),
                                rule.currency(),
                                rule.window(),
                                windows.at(rule.window(), value.initiatedAt(), rule.weekStart())),
                        rule.metric() == LimitMetric.AMOUNT ? value.amount().getCent() : 1L);
            }
            var seen = new HashSet<LimitBucketKey>();
            if (items.size() != expected.size()) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            for (var item : items) {
                if (!item.reservationId().equals(value.id())
                        || !seen.add(item.bucket())
                        || !Objects.equals(expected.get(item.bucket()), item.reserved())
                        || item.consumed() != 0
                        || item.restored() != 0) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
            }
            var before = reservations.lockExistingBuckets(
                    actor.tenantId(),
                    items.stream().map(LimitReservationItem::bucket).toList());
            var after = before.releaseReserved(items);
            var next = stored.transitionTo(value.expire(now));
            reservations.updateBuckets(actor.tenantId(), before, after);
            reservations.updateReservation(actor.tenantId(), next, value.version());
            return converter.toDTO(next.reservation());
        });
    }

    private StoredLimitReservation lockOperation(LimitActor actor, String operation) {
        DomainChecks.id(operation);
        var first = reservations
                .findByOperation(actor.tenantId(), operation)
                .orElseThrow(() -> new CommonException(NOT_FOUND));
        owner(actor, first);
        var gate = reservations
                .lockGate(actor.tenantId(), operation)
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!gate.operationId().equals(operation)
                || !gate.paymentId().equals(first.reservation().paymentId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (gate.state() == GateState.FENCED) {
            throw new CommonException(EXECUTION_FENCED);
        }
        if (gate.state() != GateState.OPEN) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        // The gate serializes every operation mutation; read again after waiting for it.
        var stored = reservations
                .findByOperation(actor.tenantId(), operation)
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        owner(actor, stored);
        var value = stored.reservation();
        if (!operation.equals(value.operationId()) || !gate.paymentId().equals(value.paymentId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var head = policies.lockHead(actor.tenantId(), value.policyId())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!value.policyId().equals(head.policyId())
                || !value.payeeMerchantId().equals(head.payeeMerchantId())
                || value.policyVersion() > head.latestVersion()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return stored;
    }

    private LimitExecutionGrant grant(String tenant, LimitReservation value) {
        var grant = reservations
                .findExecutionGrant(tenant, value.operationId(), value.executionEpoch())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!grant.operationId().equals(value.operationId())
                || !grant.paymentId().equals(value.paymentId())
                || !grant.attemptId().equals(value.activeAttemptId())
                || grant.executionEpoch() != value.executionEpoch()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return grant;
    }

    private String issue(LimitActor actor, StoredLimitReservation stored, LimitExecutionGrant grant) {
        return permits.issue(
                actor.tenantId(),
                stored,
                grant.attemptId(),
                grant.executionEpoch(),
                grant.requestKey(),
                grant.permitKeyId());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
