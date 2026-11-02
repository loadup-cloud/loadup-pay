/*
 * #%L
 * LoadUp Pay Product Infrastructure
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
package io.github.loadup.pay.product.infrastructure.client;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcMapper;
import io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpc;
import io.github.loadup.pay.product.app.port.PolicyActivationClient;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LimitPolicyActivationRpcClient implements PolicyActivationClient {
    private final PolicyPublicationTransportProperties properties;
    private final LimitCenterRpcMapper limitCenterRpcMapper;

    @DubboReference
    private LimitCenterRpc limitCenterRpc;

    public LimitPolicyActivationRpcClient(
            LimitCenterRpcMapper limitCenterRpcMapper, PolicyPublicationTransportProperties properties) {
        this.limitCenterRpcMapper = limitCenterRpcMapper;
        this.properties = properties;
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public LimitPolicyActivationDTO activate(String tenant, LimitPolicyActivateCommand command) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            var response = RpcTokenContext.call(
                    credential(),
                    () -> limitCenterRpcMapper.fromWireLimitPolicyActivationDTO(
                            limitCenterRpc.activate(limitCenterRpcMapper.toWireLimitPolicyActivateCommand(command))));
            if (response == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            return response;
        } catch (RuntimeException invalid) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential() {
        // Reopen the mounted file for every attempt, allowing an external issuer/agent to rotate short-lived
        // credentials.
        try (var input = Files.newInputStream(properties.tokenFile())) {
            byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            String token = new String(bytes, StandardCharsets.US_ASCII).strip();
            if (!token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            return token;
        } catch (IOException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
