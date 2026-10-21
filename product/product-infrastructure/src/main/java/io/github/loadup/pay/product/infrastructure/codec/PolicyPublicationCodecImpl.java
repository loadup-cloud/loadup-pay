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
import io.github.loadup.pay.limitcenter.client.support.LimitPolicyHash;
import io.github.loadup.pay.product.app.port.PolicyPublicationCodec;
import io.github.loadup.pay.product.domain.model.PolicyPublication;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class PolicyPublicationCodecImpl implements PolicyPublicationCodec {
    private final JsonMapper json;
    private final Validator validator;

    public PolicyPublicationCodecImpl(JsonMapper json, Validator validator) {
        this.json = json;
        this.validator = validator;
    }

    private void check(String tenant, LimitPolicyActivateCommand value) {
        if (value == null
                || !validator.validate(value).isEmpty()
                || value.policyVersion() != 1
                || value.rules().size() != 8
                || value.rules().stream()
                        .anyMatch(rule -> !value.payeeMerchantId().equals(rule.merchantId()))
                || !value.policyHash().equals(LimitPolicyHash.compute(tenant, value)))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    @Override
    public String encode(String tenant, LimitPolicyActivateCommand value) {
        check(tenant, value);
        return json.writeValueAsString(value);
    }

    @Override
    public LimitPolicyActivateCommand decode(String tenant, PolicyPublication publication) {
        try {
            var value = json.readValue(publication.activationPayload(), LimitPolicyActivateCommand.class);
            check(tenant, value);
            if (!value.policyId().equals(publication.policyId())
                    || value.policyVersion() != publication.policyVersion()
                    || !value.policyHash().equals(publication.policyHash())
                    || !value.payeeMerchantId().equals(publication.payeeMerchantId()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (CommonException known) {
            throw known;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
