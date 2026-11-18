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
package io.github.loadup.productcenter.infrastructure.outbox;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.productcenter.app.model.PolicyActivationDispatch;
import io.github.loadup.productcenter.app.service.PolicyPublicationService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PolicyActivationTaskHandlerTest {
    final PolicyPublicationService service = mock(PolicyPublicationService.class);
    final PolicyActivationTaskHandler handler =
            new PolicyActivationTaskHandler(service, JsonMapper.builder().build());

    TaskExecution event(String businessId) {
        return new TaskExecution(
                "event",
                TaskSubmission.immediate(
                        PolicyActivationDispatch.TYPE,
                        businessId,
                        JsonUtil.toJson(new PolicyActivationDispatch("publication"))),
                Instant.parse("2026-10-21T02:00:00Z"),
                null);
    }

    @Test
    void aMatchingFrameworkTenantAndEnvelopeDispatchTheStoredIntent() {
        handler.handle(event("publication"));
        verify(service).deliver("event", "publication");
    }

    @Test
    void businessBindingMismatchNeverInvokesTheDeliveryService() {
        assertThrows(CommonException.class, () -> handler.handle(event("different")));
        verifyNoInteractions(service);
    }
}
