package io.github.loadup.pay.product.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcMapper;
import io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpc;
import io.github.loadup.pay.product.app.port.PolicyActivationClient;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;

@Component
public class LimitPolicyActivationRpcClient implements PolicyActivationClient {
    @DubboReference(url = "tri://limitcenter-headless:50051")
    private LimitCenterRpc limitCenterRpc;

    private final PolicyPublicationTransportProperties properties;

    public LimitPolicyActivationRpcClient(PolicyPublicationTransportProperties properties) {
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
            var response = RpcTokenContext.call(
                    credential(),
                    () -> LimitCenterRpcMapper.INSTANCE.fromWireLimitPolicyActivationDTO(limitCenterRpc.activate(
                            LimitCenterRpcMapper.INSTANCE.toWireLimitPolicyActivateCommand(command))));
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
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
