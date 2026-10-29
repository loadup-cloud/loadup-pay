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
package io.github.loadup.pay.product.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO;
import io.github.loadup.pay.limitcenter.client.support.LimitPolicyHash;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.model.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.RefundCountRestorePolicy;
import io.github.loadup.pay.product.domain.model.PolicyPublication;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyPublicationCodecImplTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final PolicyPublicationCodecImpl codec =
            new PolicyPublicationCodecImpl(JsonMapper.builder().build(), validation.getValidator());

    @AfterEach
    void close() {
        validation.close();
    }

    LimitPolicyActivateCommand command() {
        var rules = new ArrayList<LimitRuleDTO>();
        for (var window : LimitWindowType.values())
            for (var metric : LimitMetric.values())
                rules.add(new LimitRuleDTO(
                        "stable", "merchant", metric, "CNY", window, 1, 9007199254740993L, true, true));
        var unsigned = new LimitPolicyActivateCommand(
                "policy",
                1,
                "0".repeat(64),
                "merchant",
                RefundAmountRestorePolicy.ORIGINAL_BUCKETS,
                RefundCountRestorePolicy.NO_RESTORE,
                rules);
        return new LimitPolicyActivateCommand(
                "policy",
                1,
                LimitPolicyHash.compute("tenant", unsigned),
                "merchant",
                unsigned.refundAmountRestore(),
                unsigned.refundCountRestore(),
                rules);
    }

    PolicyPublication stored(String payload) {
        return new PolicyPublication(
                "publication",
                "contract",
                1,
                "snapshot",
                "merchant",
                "scope",
                "policy",
                1,
                command().policyHash(),
                payload,
                "PUBLISHING",
                "event",
                LocalDateTime.of(2026, 10, 21, 10, 0),
                null,
                1,
                1);
    }

    @Test
    void theFrozenCommandRetainsIntegersAboveDoublePrecision() {
        var value = codec.decode("tenant", stored(codec.encode("tenant", command())));
        assertEquals(9007199254740993L, value.rules().getFirst().threshold());
        assertEquals(command().policyHash(), value.policyHash());
    }

    @Test
    void mutatedPayloadOrTenantCannotBeUsedAsTheFrozenActivationIntent() {
        var payload = codec.encode("tenant", command());
        assertThrows(CommonException.class, () -> codec.decode("other-tenant", stored(payload)));
        assertThrows(
                CommonException.class,
                () -> codec.decode("tenant", stored(payload.replace("9007199254740993", "9007199254740992"))));
    }

    @Test
    void anIncompleteCounterShapeIsNotAVersionOnePublication() {
        var original = command();
        var rules = original.rules().subList(0, 7);
        var incomplete = new LimitPolicyActivateCommand(
                "policy",
                1,
                original.policyHash(),
                "merchant",
                original.refundAmountRestore(),
                original.refundCountRestore(),
                rules);
        assertThrows(CommonException.class, () -> codec.encode("tenant", incomplete));
    }
}
