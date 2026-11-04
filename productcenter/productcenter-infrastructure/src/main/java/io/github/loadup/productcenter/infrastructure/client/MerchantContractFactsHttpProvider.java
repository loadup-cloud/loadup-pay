/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.productcenter.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.merchantcenter.client.http.MerchantProfileHttpService;
import io.github.loadup.productcenter.client.dto.MerchantProfileDTO;
import io.github.loadup.productcenter.client.dto.ValueDTO;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.client.spi.MerchantFactsProvider;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MerchantContractFactsHttpProvider implements MerchantFactsProvider {
    private final MerchantProfileHttpService merchants;
    private final ServiceAccessTokens tokens;
    private final String audience;

    public MerchantContractFactsHttpProvider(
            MerchantProfileHttpService merchants,
            ServiceAccessTokens tokens,
            @Value("${loadup.modules.product.customer-audience}") String audience) {
        this.merchants = merchants;
        this.tokens = tokens;
        this.audience = audience;
    }

    @Override
    public MerchantProfileDTO load(String id) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_PROVIDER_UNAVAILABLE);
        }
        try {
            var profile = HttpServiceResponses.requiredData(
                    merchants.profile("Bearer " + tokens.token("productcenter", audience), new IdQuery(id)));
            if (!id.equals(profile.id())) {
                throw new CommonException(ContractResultCodeEnum.MERCHANT_UNVERIFIED);
            }
            Map<String, ValueDTO> facts = new HashMap<>();
            put(facts, "merchant.code", profile.merchantCode());
            put(facts, "merchant.type", profile.type());
            put(facts, "merchant.industry", profile.industry());
            put(facts, "merchant.country", profile.country());
            put(facts, "merchant.province", profile.province());
            put(facts, "merchant.city", profile.city());
            return new MerchantProfileDTO(id, profile.active(), Map.copyOf(facts));
        } catch (RuntimeException failure) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_PROVIDER_UNAVAILABLE);
        }
    }

    private static void put(Map<String, ValueDTO> facts, String key, String value) {
        if (value != null && !value.isBlank()) {
            facts.put(key, new ValueDTO("STRING", value));
        }
    }
}
