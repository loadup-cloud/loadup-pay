package io.github.loadup.productcenter.app.model;

import io.github.loadup.productcenter.domain.model.MerchantEligibilityFacts;
import java.util.Map;

/**
 * Verified eligibility and the merchant facts needed by the admission policy.
 */
public record AdmissionEligibility(MerchantEligibilityFacts decision, Map<String, String> merchantFacts) {
    public AdmissionEligibility {
        merchantFacts = Map.copyOf(merchantFacts);
    }
}
