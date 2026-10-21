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
import io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.enums.MerchantAction;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.product.app.port.CustomerEligibilityLookup;
import io.github.loadup.pay.product.app.security.ProductActorProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;

@Component
public class CustomerEligibilityHttpLookup implements CustomerEligibilityLookup {
    private final HttpTemplate http;
    private final ProductActorProvider actors;
    private final JsonMapper mapper;

    public CustomerEligibilityHttpLookup(HttpTemplate http, ProductActorProvider actors, JsonMapper mapper) {
        this.http = http;
        this.actors = actors;
        this.mapper = mapper;
    }

    @Override
    public MerchantEligibilityDTO authorizeNewOrder(String tenant, String payee) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (!tenant.equals(actor.tenantId())
                || actor.customerAccessToken() == null
                || actor.customerAccessToken().isBlank()) throw new CommonException(ACTION_FORBIDDEN);
        try {
            var response = http.exchange(
                    "customer",
                    "authorize",
                    new HttpCall(
                            Map.of(),
                            Map.of(),
                            Map.of("Authorization", "Bearer " + actor.customerAccessToken()),
                            new MerchantActionAuthorizeCommand(payee, MerchantAction.CREATE_ORDER)));
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            SuccessResponse<MerchantEligibilityDTO> body =
                    mapper.readValue(response.getBody(), new TypeReference<>() {});
            if (body.result() == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            if (CustomerResultCodeEnum.NOT_FOUND.getCode().equals(body.result().getCode())
                    && CustomerResultCodeEnum.NOT_FOUND
                            .getStatus()
                            .equals(body.result().getStatus())
                    && body.data() == null)
                return new MerchantEligibilityDTO(
                        false, CustomerResultCodeEnum.NOT_FOUND.getCode(), null, 0, null, null);
            if (!CommonResultCodeEnum.SUCCESS.getCode().equals(body.result().getCode())
                    || !CommonResultCodeEnum.SUCCESS
                            .getStatus()
                            .equals(body.result().getStatus())
                    || body.data() == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            MerchantEligibilityDTO data = body.data();
            if (data.merchantFactVersion() < 0) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            if (data.allowed()) {
                if (data.merchant() == null
                        || !payee.equals(data.merchant().id())
                        || !tenant.equals(data.merchant().tenantId())
                        || !data.merchant().active()
                        || data.merchantFactVersion() < 1
                        || data.grantId() == null
                        || data.grantVersion() == null
                        || data.grantVersion() < 1
                        || !"ALLOWED".equals(data.reasonCode())) throw new CommonException(DEPENDENCY_UNAVAILABLE);
                if (java.util.stream.Stream.of(
                                data.merchant().merchantCode(),
                                data.merchant().type(),
                                data.merchant().industry(),
                                data.merchant().country())
                        .anyMatch(value -> value == null || value.isBlank() || value.length() > 1024))
                    throw new CommonException(DEPENDENCY_UNAVAILABLE);
            } else if (!(CustomerResultCodeEnum.ACTION_FORBIDDEN.getCode().equals(data.reasonCode())
                            || CustomerResultCodeEnum.MERCHANT_INACTIVE
                                    .getCode()
                                    .equals(data.reasonCode()))
                    || data.merchant() != null
                    || data.grantId() != null
                    || data.grantVersion() != null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            return data;
        } catch (RuntimeException failure) {
            // Jackson/transport failures may contain payload data; expose only a fixed local result code.
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
