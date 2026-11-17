package io.github.loadup.productcenter.infrastructure.converter;

import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO;
import io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO;
import io.github.loadup.productcenter.app.model.AdmissionEligibility;
import io.github.loadup.productcenter.domain.model.MerchantEligibilityFacts;
import java.util.HashMap;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface AdmissionEligibilityConverter {
    @Mapping(target = "payeeMerchantId", source = "payee")
    @Mapping(target = "eligible", source = "result.allowed")
    @Mapping(target = "reasonCode", source = "result.reasonCode")
    @Mapping(target = "merchantVersion", source = "result.merchantFactVersion")
    @Mapping(target = "authorizationVersion", source = "result.grantVersion")
    MerchantEligibilityFacts toDecision(String payee, MerchantEligibilityDTO result);

    default AdmissionEligibility toEligibility(String payee, MerchantEligibilityDTO result) {
        return new AdmissionEligibility(toDecision(payee, result), merchantFacts(result.merchant()));
    }

    default Map<String, String> merchantFacts(MerchantProfileDTO merchant) {
        if (merchant == null) {
            return Map.of();
        }
        Map<String, String> facts = new HashMap<>();
        facts.put("merchant.id", merchant.id());
        facts.put("merchant.code", merchant.merchantCode());
        facts.put("merchant.type", merchant.type());
        facts.put("merchant.industry", merchant.industry());
        facts.put("merchant.country", merchant.country());
        facts.put("merchant.active", Boolean.toString(merchant.active()));
        if (merchant.province() != null) {
            facts.put("merchant.province", merchant.province());
        }
        if (merchant.city() != null) {
            facts.put("merchant.city", merchant.city());
        }
        return Map.copyOf(facts);
    }
}
