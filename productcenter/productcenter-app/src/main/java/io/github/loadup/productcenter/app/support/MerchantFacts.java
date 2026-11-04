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
package io.github.loadup.productcenter.app.support;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.app.converter.ContractConverter;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.client.spi.MerchantFactsProvider;
import io.github.loadup.productcenter.domain.model.TypedValue;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class MerchantFacts {
    private final ObjectProvider<MerchantFactsProvider> providers;
    private final ContractConverter converter;

    public MerchantFacts(ObjectProvider<MerchantFactsProvider> providers, ContractConverter converter) {
        this.providers = providers;
        this.converter = converter;
    }

    public Map<String, TypedValue> load(String merchant) {
        MerchantFactsProvider provider = providers.getIfAvailable();
        if (provider == null) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_PROVIDER_UNAVAILABLE);
        }
        var profile = provider.load(merchant);
        if (profile == null || !merchant.equals(profile.merchantId()) || !profile.active() || profile.facts() == null) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_UNVERIFIED);
        }
        if (profile.facts().size() > 256) {
            throw new CommonException(ContractResultCodeEnum.TOO_MANY_MERCHANT_FACTS);
        }
        Map<String, TypedValue> values = new HashMap<>();
        profile.facts().forEach((key, value) -> {
            if (key == null || value == null || !key.startsWith("merchant.")) {
                throw new CommonException(ContractResultCodeEnum.UNTRUSTED_FACT_NAMESPACE);
            }
            values.put(key, converter.toValue(value));
        });
        return Map.copyOf(values);
    }
}
