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
package io.github.loadup.pay.limitcenter.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.limitcenter.app.converter.LimitConverter;
import io.github.loadup.pay.limitcenter.app.converter.LimitConverterImpl;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.model.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.RefundCountRestorePolicy;
import io.github.loadup.pay.limitcenter.client.support.LimitPolicyHash;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyRevisionDO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LimitPolicyCodecTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LimitConverterImpl.class);
    final LimitConverter converter = context.getBean(LimitConverter.class);
    final LimitPolicyCodec codec =
            new LimitPolicyCodec(JsonMapper.builder().build(), converter, validation.getValidator());

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    LimitPolicyRevisionDO row() {
        var rule = new LimitRuleDTO(
                "scope",
                "merchant",
                LimitMetric.AMOUNT,
                "CNY",
                LimitWindowType.LIFETIME,
                1,
                9007199254740993L,
                true,
                true);
        var unsigned = new LimitPolicyActivateCommand(
                "policy",
                1,
                "a".repeat(64),
                "merchant",
                RefundAmountRestorePolicy.ORIGINAL_BUCKETS,
                RefundCountRestorePolicy.NO_RESTORE,
                List.of(rule));
        var command = new LimitPolicyActivateCommand(
                "policy",
                1,
                LimitPolicyHash.compute("tenant", unsigned),
                "merchant",
                unsigned.refundAmountRestore(),
                unsigned.refundCountRestore(),
                unsigned.rules());
        var at = LocalDateTime.of(2026, 10, 21, 10, 0, 5, 123456000);
        var value = converter.toPolicy(command, at);
        var row = new LimitPolicyRevisionDO();
        row.setTenantId("tenant");
        row.setPolicyId("policy");
        row.setPolicyVersion(1);
        row.setPolicyHash(command.policyHash());
        row.setPayeeMerchantId("merchant");
        row.setActivatedAt(at);
        row.setCompiledRules(codec.encode(value));
        return row;
    }

    @Test
    void largeIntegerThresholdAndActivationMicrosecondsSurviveStorage() {
        var row = row();
        var value = codec.decode("tenant", row);
        assertEquals(9007199254740993L, value.rules().getFirst().threshold());
        assertEquals(row.getActivatedAt(), value.activatedAt());
    }

    @Test
    void tamperingWithThresholdOrTenantDoesNotYieldAnActivatedPolicy() {
        var row = row();
        row.setCompiledRules(row.getCompiledRules().replace("9007199254740993", "9007199254740992"));
        assertThrows(CommonException.class, () -> codec.decode("tenant", row));
        assertThrows(CommonException.class, () -> codec.decode("different-tenant", row()));
    }

    @Test
    void theRawPolicyPayloadIsHiddenFromDiagnostics() {
        var row = row();
        row.setCompiledRules("synthetic-private-policy");
        assertFalse(row.toString().contains("synthetic-private-policy"));
    }
}
