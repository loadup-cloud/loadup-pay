/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.limitcenter.app.service;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.POLICY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.limitcenter.app.converter.LimitConverter;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.facade.LimitPolicyFacade;
import io.github.loadup.pay.limitcenter.client.support.LimitPolicyHash;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitPolicyGateway;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class LimitPolicyService implements LimitPolicyFacade {
    private final LimitTransactions transactions;
    private final LimitPolicyGateway policies;
    private final LimitConverter converter;
    private final Clock clock;

    public LimitPolicyService(
            LimitTransactions transactions, LimitPolicyGateway policies, LimitConverter converter, Clock clock) {
        this.transactions = transactions;
        this.policies = policies;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public LimitPolicyActivationDTO activate(LimitPolicyActivateCommand command) {
        var actor = transactions.authorize(command, "pay:limit:activate");
        return transactions.writeLocal(actor, POLICY_CONFLICT, () -> {
            var next = converter.toPolicy(command, LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
            if (!command.policyHash().equals(LimitPolicyHash.compute(actor.tenantId(), command))) {
                throw new CommonException(INVALID_ARGUMENT);
            }
            var head = policies.lockOrCreateHead(next.policyId(), next.payeeMerchantId());
            if (!head.policyId().equals(next.policyId())
                    || !head.payeeMerchantId().equals(next.payeeMerchantId())) {
                throw new CommonException(POLICY_CONFLICT);
            }
            var prior = policies.findRevision(next.policyId(), next.policyVersion());
            if (prior.isPresent()) {
                if (!prior.get().policyHash().equals(next.policyHash())) {
                    throw new CommonException(POLICY_CONFLICT);
                }
                // An old ACK replay must not roll runtime thresholds back to an older revision.
                return converter.toDTO(prior.get());
            }
            var advanced = head.advance(next);
            if (head.latestVersion() > 0) {
                next.requireStableLineage(policies.findRevision(head.policyId(), head.latestVersion())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)));
            }
            for (var rule : next.rules()) {
                var current = policies.lockRule(rule);
                if (current.isEmpty()) {
                    if (head.latestVersion() != 0) {
                        throw new CommonException(SNAPSHOT_INVALID);
                    }
                    policies.insertRule(new RuntimeLimitRule(next.policyId(), next.policyVersion(), rule, 0));
                } else {
                    var existing = current.get();
                    if (existing.policyVersion() != head.latestVersion()) {
                        throw new CommonException(POLICY_CONFLICT);
                    }
                    policies.updateRule(
                            existing.revise(next.policyId(), next.policyVersion(), rule), existing.rowVersion());
                }
            }
            policies.insertRevision(next);
            policies.updateHead(advanced, head.rowVersion());
            return converter.toDTO(next);
        });
    }
}
