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
package io.github.loadup.pay.limitcenter.client.support;

import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO;
import io.github.loadup.pay.limitcenter.client.enums.LimitMetric;
import io.github.loadup.pay.limitcenter.client.enums.LimitWindowType;
import io.github.loadup.pay.limitcenter.client.enums.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.client.enums.RefundCountRestorePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LimitPolicyHashTest {
    private LimitRuleDTO rule(String scope, long threshold) {
        return new LimitRuleDTO(
                scope, "merchant", LimitMetric.AMOUNT, "CNY", LimitWindowType.MONTH, 1, threshold, true, true);
    }

    private LimitPolicyActivateCommand command(int version, List<LimitRuleDTO> rules) {
        return new LimitPolicyActivateCommand(
                "policy",
                version,
                "a".repeat(64),
                "merchant",
                RefundAmountRestorePolicy.NO_RESTORE,
                RefundCountRestorePolicy.NO_RESTORE,
                rules);
    }

    @Test
    void inputOrderingDoesNotChangeTheCanonicalHash() {
        var first = rule("first", 100);
        var second = rule("second", 200);
        assertEquals(
                LimitPolicyHash.compute("tenant", command(1, List.of(first, second))),
                LimitPolicyHash.compute("tenant", command(1, List.of(second, first))));
    }

    @Test
    void tenantAndRevisionAreBoundToThePayload() {
        var command = command(1, List.of(rule("scope", 100)));
        assertNotEquals(LimitPolicyHash.compute("tenant", command), LimitPolicyHash.compute("other", command));
        assertNotEquals(
                LimitPolicyHash.compute("tenant", command),
                LimitPolicyHash.compute("tenant", command(2, command.rules())));
    }

    @Test
    void integerValuesAboveDoublePrecisionRemainDistinct() {
        assertNotEquals(
                LimitPolicyHash.compute("tenant", command(1, List.of(rule("scope", 9007199254740992L)))),
                LimitPolicyHash.compute("tenant", command(1, List.of(rule("scope", 9007199254740993L)))));
    }
}
