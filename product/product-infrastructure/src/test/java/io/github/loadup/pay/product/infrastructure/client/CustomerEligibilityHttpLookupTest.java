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
package io.github.loadup.pay.product.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.result.FailureResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.product.app.security.ProductActor;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerEligibilityHttpLookupTest {
    private final HttpTemplate http = mock(HttpTemplate.class);
    private final ProductActor actor =
            new ProductActor("tenant", "MERCHANT_APP", "app", "merchant", "synthetic", Set.of());
    private final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    private final CustomerEligibilityHttpLookup lookup = new CustomerEligibilityHttpLookup(http, () -> actor, mapper);

    private void response(Object value) {
        when(http.exchange(eq("customer"), eq("authorize"), any(HttpCall.class)))
                .thenReturn(ResponseEntity.ok(mapper.writeValueAsBytes(value)));
    }

    @Test
    void acceptsOnlyASuccessEnvelopeBoundToTheExpectedTenantAndPayee() {
        response(SuccessResponse.of(new MerchantEligibilityDTO(
                true,
                "ALLOWED",
                new MerchantProfileDTO("merchant", "tenant", "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true),
                1,
                "grant",
                1L)));
        assertTrue(lookup.authorizeNewOrder("tenant", "merchant").allowed());
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "different-merchant"));
    }

    @Test
    void wrongTenantProfileCannotBeUsedAsAuthoritativeFacts() {
        response(SuccessResponse.of(new MerchantEligibilityDTO(
                true,
                "ALLOWED",
                new MerchantProfileDTO("merchant", "other", "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true),
                1,
                "grant",
                1L)));
        var error = assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
        assertSame(ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE, error.getResultCode());
    }

    @Test
    void authenticationFailureIsNotConvertedIntoABusinessDenial() {
        response(FailureResponse.of(CustomerResultCodeEnum.ACTION_FORBIDDEN));
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("tenant", "merchant"));
    }

    @Test
    void verifiedNotFoundAndExplicitIneligibilityRemainBusinessDenials() {
        response(FailureResponse.of(CustomerResultCodeEnum.NOT_FOUND));
        assertEquals(
                CustomerResultCodeEnum.NOT_FOUND.getCode(),
                lookup.authorizeNewOrder("tenant", "merchant").reasonCode());
        response(SuccessResponse.of(new MerchantEligibilityDTO(
                false, CustomerResultCodeEnum.MERCHANT_INACTIVE.getCode(), null, 0, null, null)));
        assertFalse(lookup.authorizeNewOrder("tenant", "merchant").allowed());
    }
}
