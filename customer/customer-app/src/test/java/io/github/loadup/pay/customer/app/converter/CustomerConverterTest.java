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
package io.github.loadup.pay.customer.app.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.loadup.pay.customer.domain.model.GrantState;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CustomerConverterTest {
    @Test
    void mapsEnumVersionAndMicrosecondPrecisionThroughTheSpringConverter() {
        try (var context = new AnnotationConfigApplicationContext(CustomerConverterImpl.class)) {
            CustomerConverter converter = context.getBean(CustomerConverter.class);
            var from = LocalDateTime.of(2026, 10, 21, 10, 0, 0, 123456000);
            var model = MerchantRelationship.init("relation", "parent", "child", from, null)
                    .activate();
            var dto = converter.toDTO(model);
            assertEquals(GrantState.ACTIVE, dto.state());
            assertEquals(1, dto.version());
            assertEquals(8 * 3600, dto.validFrom().getOffset().getTotalSeconds());
            assertEquals(from, dto.validFrom().toLocalDateTime());
        }
    }

    @Test
    void deniedDecisionContainsNoMerchantOrGrantFacts() {
        try (var context = new AnnotationConfigApplicationContext(CustomerConverterImpl.class)) {
            var decision = context.getBean(CustomerConverter.class).toEligibility(false, "DENIED", null, null, null);
            assertFalse(decision.allowed());
            assertNull(decision.merchant());
            assertNull(decision.grantId());
            assertNull(decision.grantVersion());
            assertEquals(0, decision.merchantFactVersion());
        }
    }
}
