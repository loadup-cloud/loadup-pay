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
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LimitPolicyServiceTest {
    final LimitServiceFixture f = new LimitServiceFixture();

    @AfterEach
    void close() {
        f.close();
    }

    @Test
    void aRepeatedOldActivationReturnsItsOriginalAckWithoutUpdatingThresholds() {
        var original = f.policy(1, 100);
        when(f.policies.lockOrCreateHead("tenant", "policy", "merchant"))
                .thenReturn(new LimitPolicyHead("policy", "merchant", 2, 2));
        when(f.policies.findRevision("tenant", "policy", 1)).thenReturn(Optional.of(original));
        assertEquals(
                original.activatedAt(),
                f.policyService.activate(f.activation(1, 100)).activatedAt().toLocalDateTime());
        verify(f.policies, never()).lockRule(anyString(), any());
        verify(f.policies, never()).updateHead(anyString(), any(), anyLong());
    }

    @Test
    void changedPayloadWithTheSameRevisionConflicts() {
        when(f.policies.lockOrCreateHead("tenant", "policy", "merchant"))
                .thenReturn(new LimitPolicyHead("policy", "merchant", 1, 1));
        when(f.policies.findRevision("tenant", "policy", 1)).thenReturn(Optional.of(f.policy(1, 100)));
        var error = assertThrows(CommonException.class, () -> f.policyService.activate(f.activation(1, 200)));
        assertSame(LimitCenterResultCodeEnum.POLICY_CONFLICT, error.getResultCode());
        assertEquals(1, f.manager.rollbacks);
    }

    @Test
    void thresholdRevisionUpdatesRuntimeButNeverTouchesBuckets() {
        var first = f.policy(1, 100);
        var next = f.policy(2, 50);
        when(f.policies.lockOrCreateHead("tenant", "policy", "merchant"))
                .thenReturn(new LimitPolicyHead("policy", "merchant", 1, 1));
        when(f.policies.findRevision("tenant", "policy", 1)).thenReturn(Optional.of(first));
        when(f.policies.lockRule(eq("tenant"), any()))
                .thenReturn(Optional.of(
                        new RuntimeLimitRule("policy", 1, first.rules().getFirst(), 0)));
        assertEquals(2, f.policyService.activate(f.activation(2, 50)).policyVersion());
        verify(f.policies)
                .updateRule(
                        eq("tenant"), argThat(rule -> rule.rule().threshold() == 50 && rule.rowVersion() == 1), eq(0L));
        verifyNoInteractions(f.reservations);
    }

    @Test
    void activationCannotSkipARevision() {
        when(f.policies.lockOrCreateHead("tenant", "policy", "merchant"))
                .thenReturn(new LimitPolicyHead("policy", "merchant", 0, 0));
        assertThrows(CommonException.class, () -> f.policyService.activate(f.activation(2, 100)));
        verify(f.policies, never()).insertRevision(anyString(), any());
    }

    @Test
    void theProvidedHashMustMatchTheEntirePolicyAndTrustedTenant() {
        var original = f.activation(1, 100);
        var invalid = new LimitPolicyActivateCommand(
                original.policyId(),
                1,
                "f".repeat(64),
                "merchant",
                original.refundAmountRestore(),
                original.refundCountRestore(),
                original.rules());
        assertThrows(CommonException.class, () -> f.policyService.activate(invalid));
        verifyNoInteractions(f.policies);
    }
}
