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
package io.github.loadup.pay.product.infrastructure.outbox;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.pay.product.app.model.PolicyActivationDispatch;
import io.github.loadup.pay.product.app.service.PolicyPublicationService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PolicyActivationOutboxHandlerTest {
    final PolicyPublicationService service = mock(PolicyPublicationService.class);
    final PolicyActivationOutboxHandler handler =
            new PolicyActivationOutboxHandler(service, JsonMapper.builder().build());

    OutboxEvent event(String tenant, String businessId, int version) {
        return new OutboxEvent(
                "event",
                new OutboxMessage(
                        PolicyActivationDispatch.TYPE,
                        version,
                        businessId,
                        JsonUtil.toJson(new PolicyActivationDispatch("publication"))),
                Instant.parse("2026-10-21T02:00:00Z"),
                null);
    }

    @Test
    void aMatchingFrameworkTenantAndEnvelopeDispatchTheStoredIntent() {
        TenantUtil.runWithTenant("tenant", () -> handler.handle(event("tenant", "publication", 1)));
        verify(service).deliver("tenant", "event", "publication");
    }

    @Test
    void tenantOrBusinessBindingMismatchNeverInvokesTheDeliveryService() {
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> handler.handle(event("other", "publication", 1)));
            assertThrows(CommonException.class, () -> handler.handle(event("tenant", "different", 1)));
        });
        verifyNoInteractions(service);
    }

    @Test
    void anUnknownPayloadVersionIsNotSilentlyAcknowledged() {
        TenantUtil.runWithTenant(
                "tenant",
                () -> assertThrows(CommonException.class, () -> handler.handle(event("tenant", "publication", 2))));
        verifyNoInteractions(service);
    }
}
