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

import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskHandler;
import io.github.loadup.productcenter.app.model.PolicyActivationDispatch;
import io.github.loadup.productcenter.app.service.PolicyPublicationService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PolicyActivationTaskHandler implements TaskHandler {
    private final PolicyPublicationService service;
    private final JsonMapper json;

    public PolicyActivationTaskHandler(PolicyPublicationService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @Override
    public String taskType() {
        return PolicyActivationDispatch.TYPE;
    }

    @Override
    public void handle(TaskExecution event) {
        if (event == null || !taskType().equals(event.taskType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        PolicyActivationDispatch dispatch;
        try {
            dispatch = json.readValue(event.payload(), PolicyActivationDispatch.class);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (dispatch == null || !event.bizId().equals(dispatch.publicationId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        service.deliver(event.id(), dispatch.publicationId());
    }
}
