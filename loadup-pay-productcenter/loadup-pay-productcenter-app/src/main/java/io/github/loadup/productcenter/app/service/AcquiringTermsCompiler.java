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
package io.github.loadup.productcenter.app.service;

import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.UNSUPPORTED_POLICY;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.app.converter.CommercialPolicyConverter;
import io.github.loadup.productcenter.app.model.ActivatedLimitPolicy;
import io.github.loadup.productcenter.client.dto.terms.AcquiringTermsDTO;
import io.github.loadup.productcenter.domain.policy.ValidatedCommercialPolicy;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/** Projects a validated policy and its matching receipt into the existing public terms schema. */
@Component
public class AcquiringTermsCompiler {
    private final Validator validator;
    private final CommercialPolicyConverter converter;

    public AcquiringTermsCompiler(Validator validator, CommercialPolicyConverter converter) {
        this.validator = validator;
        this.converter = converter;
    }

    public AcquiringTermsDTO compile(ValidatedCommercialPolicy policy, ActivatedLimitPolicy activation) {
        try {
            if (!policy.limits().policyId().equals(activation.policyId())) {
                throw new CommonException(UNSUPPORTED_POLICY);
            }
            var terms = converter.toTerms(policy, activation);
            if (!validator.validate(terms).isEmpty()) {
                throw new CommonException(UNSUPPORTED_POLICY);
            }
            return terms;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw new CommonException(UNSUPPORTED_POLICY);
        }
    }
}
