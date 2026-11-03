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
package io.github.loadup.pay.acquire.infrastructure.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OrderCreationStorageConverterTest {
    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(OrderCreationStorageConverter.class.getPackageName());
    private final OrderCreationStorageConverter converter = context.getBean(OrderCreationStorageConverter.class);

    @AfterEach
    void close() {
        context.close();
    }

    private OrderCreateIntent intent(String mode, String sessionId) {
        return new OrderCreateIntent(
                "intent",
                "order",
                "merchant",
                "merchant-order",
                "a".repeat(64),
                "b".repeat(64),
                mode,
                sessionId,
                "payload",
                "INIT",
                null,
                null,
                null,
                0);
    }

    @Test
    void checkoutRoundTripPreservesItsEntryAndOriginalSessionThroughCompletion() {
        var initial = intent("CHECKOUT", "initial-session");
        var completed = initial.finish("COMPLETED", "admission", LocalDateTime.of(2026, 10, 22, 12, 0), "SUCCESS");
        for (var value : List.of(initial, completed)) {
            var row = converter.toIntentDO(value);
            assertEquals("CHECKOUT", row.getEntryMode());
            assertEquals("initial-session", row.getInitialSessionId());
            assertEquals(value, converter.toIntent(row));
        }
    }

    @Test
    void ordinaryOrderRemainsDistinctAndHasNoInitialSession() {
        var value = intent("ORDER", null);
        var row = converter.toIntentDO(value);
        assertEquals("ORDER", row.getEntryMode());
        assertNull(row.getInitialSessionId());
        assertEquals(value, converter.toIntent(row));
    }
}
