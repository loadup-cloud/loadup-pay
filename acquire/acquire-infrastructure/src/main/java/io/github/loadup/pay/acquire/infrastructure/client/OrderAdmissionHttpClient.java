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
package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.acquire.app.port.OrderAdmissionClient;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

@Component
public class OrderAdmissionHttpClient implements OrderAdmissionClient {
    private final HttpTemplate http;
    private final AcquireActorProvider actors;
    private final JsonMapper mapper;

    public OrderAdmissionHttpClient(HttpTemplate http, AcquireActorProvider actors, JsonMapper mapper) {
        this.http = http;
        this.actors = actors;
        this.mapper = mapper;
    }

    @Override
    public OrderAdmissionDTO authorize(OrderAdmissionAuthorizeCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor.productAccessToken() == null || actor.productAccessToken().isBlank())
            throw new CommonException(ACTION_FORBIDDEN);
        try {
            var response = http.exchange(
                    "product",
                    "authorize",
                    new HttpCall(
                            Map.of(),
                            Map.of(),
                            Map.of("Authorization", "Bearer " + actor.productAccessToken()),
                            command));
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            SuccessResponse<OrderAdmissionDTO> body = mapper.readValue(response.getBody(), new TypeReference<>() {
            });
            if (body.result() == null
                    || !CommonResultCodeEnum.SUCCESS
                    .getCode()
                    .equals(body.result().getCode())
                    || !CommonResultCodeEnum.SUCCESS
                    .getStatus()
                    .equals(body.result().getStatus())
                    || body.data() == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return body.data();
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
