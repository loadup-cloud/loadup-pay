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
package io.github.loadup.productcenter.domain.model;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import java.util.Objects;

public record ResolvedContractItem(
        String productCode,
        int productVersion,
        String capabilityCode,
        ResolvedConfiguration configuration,
        Condition usageCondition,
        ValueOrigin bundleOrigin) {
    public ResolvedContractItem {
        productCode = ContractChecks.code(productCode);
        productVersion = ContractChecks.version(productVersion);
        capabilityCode = ContractChecks.code(capabilityCode);
        Objects.requireNonNull(bundleOrigin, "bundle origin");
        if (bundleOrigin.layer() != ConfigurationLayer.BUNDLE) {
            throw new CommonException(ContractResultCodeEnum.INVALID_BUNDLE_ORIGIN);
        }
        Objects.requireNonNull(configuration, "configuration");
        new ConditionEvaluator().validate(usageCondition);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
