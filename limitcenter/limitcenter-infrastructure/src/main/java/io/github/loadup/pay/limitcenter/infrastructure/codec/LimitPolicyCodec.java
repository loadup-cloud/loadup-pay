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
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.support.LimitPolicyHash;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyRevisionDO;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class LimitPolicyCodec {
    private final JsonMapper json;
    private final LimitConverter converter;
    private final Validator validator;

    public LimitPolicyCodec(JsonMapper json, LimitConverter converter, Validator validator) {
        this.json = json;
        this.converter = converter;
        this.validator = validator;
    }

    public String encode(ActivatedLimitPolicy policy) {
        return json.writeValueAsString(converter.toCommand(policy));
    }

    public ActivatedLimitPolicy decode(String tenant, LimitPolicyRevisionDO row) {
        try {
            var command = json.readValue(row.getCompiledRules(), LimitPolicyActivateCommand.class);
            if (!tenant.equals(row.getTenantId())
                    || !validator.validate(command).isEmpty()
                    || !command.policyId().equals(row.getPolicyId())
                    || command.policyVersion() != row.getPolicyVersion()
                    || !command.payeeMerchantId().equals(row.getPayeeMerchantId())
                    || !command.policyHash().equals(row.getPolicyHash())
                    || !command.policyHash().equals(LimitPolicyHash.compute(tenant, command)))
                throw new CommonException(SNAPSHOT_INVALID);
            return converter.toPolicy(command, row.getActivatedAt());
        } catch (CommonException known) {
            throw known;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
