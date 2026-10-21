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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxHandler;
import io.github.loadup.pay.product.app.model.PolicyActivationDispatch;
import io.github.loadup.pay.product.app.service.PolicyPublicationService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class PolicyActivationOutboxHandler implements OutboxHandler {
    private final PolicyPublicationService service;
    private final JsonMapper json;

    public PolicyActivationOutboxHandler(PolicyPublicationService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @Override
    public String eventType() {
        return PolicyActivationDispatch.TYPE;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null
                || !eventType().equals(event.message().type())
                || event.message().version() != 1
                || !event.message().tenantId().equals(TenantUtil.getTenantId()))
            throw new CommonException(SNAPSHOT_INVALID);
        PolicyActivationDispatch dispatch;
        try {
            dispatch = json.readValue(event.message().payload(), PolicyActivationDispatch.class);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (dispatch == null || !event.message().businessId().equals(dispatch.publicationId()))
            throw new CommonException(SNAPSHOT_INVALID);
        service.deliver(event.message().tenantId(), event.id(), dispatch.publicationId());
    }
}
