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

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.product.app.port.PolicyActivationClient;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;

@Component
public class LimitPolicyActivationHttpClient implements PolicyActivationClient {
    private final HttpTemplate http;
    private final JsonMapper json;
    private final PolicyPublicationTransportProperties properties;

    public LimitPolicyActivationHttpClient(
            HttpTemplate http, JsonMapper json, PolicyPublicationTransportProperties properties) {
        this.http = http;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public LimitPolicyActivationDTO activate(String tenant, LimitPolicyActivateCommand command) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var result = http.exchange(
                    "limitcenter",
                    "activate",
                    new HttpCall(Map.of(), Map.of(), Map.of("Authorization", "Bearer " + credential()), command));
            if (!result.getStatusCode().is2xxSuccessful() || result.getBody() == null)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            SuccessResponse<LimitPolicyActivationDTO> body = json.readValue(result.getBody(), new TypeReference<>() {});
            if (body.result() == null
                    || body.data() == null
                    || !CommonResultCodeEnum.SUCCESS
                            .getCode()
                            .equals(body.result().getCode())
                    || !CommonResultCodeEnum.SUCCESS
                            .getStatus()
                            .equals(body.result().getStatus())) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return body.data();
        } catch (RuntimeException invalid) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential() {
        // Reopen the mounted file for every attempt, allowing an external issuer/agent to rotate short-lived
        // credentials.
        try (var input = Files.newInputStream(properties.tokenFile())) {
            byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            String token = new String(bytes, StandardCharsets.US_ASCII).strip();
            if (!token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return token;
        } catch (IOException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
