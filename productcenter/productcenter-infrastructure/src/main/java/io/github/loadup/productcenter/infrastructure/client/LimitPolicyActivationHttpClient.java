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
package io.github.loadup.productcenter.infrastructure.client;

import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.productcenter.app.model.limitcenter.command.LimitPolicyActivateCommand;
import io.github.loadup.productcenter.app.model.limitcenter.dto.LimitPolicyActivationDTO;
import io.github.loadup.productcenter.app.port.PolicyActivationClient;
import io.github.loadup.productcenter.infrastructure.config.PolicyPublicationTransportProperties;
import io.github.loadup.productcenter.infrastructure.converter.RemoteContractConverter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LimitPolicyActivationHttpClient implements PolicyActivationClient {
    private final RemoteContractConverter contracts;
    private final ServiceAccessTokens tokens;
    private final PolicyPublicationTransportProperties properties;
    private final LimitCenterHttpService limitCenterHttp;

    public LimitPolicyActivationHttpClient(
            LimitCenterHttpService limitCenterHttp,
            PolicyPublicationTransportProperties properties,
            ServiceAccessTokens tokens,
            RemoteContractConverter contracts) {
        this.contracts = contracts;
        this.limitCenterHttp = limitCenterHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public LimitPolicyActivationDTO activate(LimitPolicyActivateCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (!TenantUtil.hasTenantId()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            return HttpServiceResponses.requiredData(contracts.fromLimitcenterLimitPolicyActivationDTOSuccessResponse(
                    limitCenterHttp.activate("Bearer " + credential(), contracts.toRemote(command))));
        } catch (RuntimeException invalid) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential() {
        return tokens.token(properties.serviceId(), properties.audience());
    }
}
