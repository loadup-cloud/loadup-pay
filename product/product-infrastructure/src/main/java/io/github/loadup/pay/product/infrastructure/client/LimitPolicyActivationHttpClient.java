package io.github.loadup.pay.product.infrastructure.client;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.pay.product.app.port.PolicyActivationClient;
import io.github.loadup.pay.product.infrastructure.config.PolicyPublicationTransportProperties;
import io.github.loadup.pay.security.ServiceAccessTokens;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LimitPolicyActivationHttpClient implements PolicyActivationClient {
    private final ServiceAccessTokens tokens;
    private final PolicyPublicationTransportProperties properties;
    private final LimitCenterHttpService limitCenterHttp;

    public LimitPolicyActivationHttpClient(
            LimitCenterHttpService limitCenterHttp,
            PolicyPublicationTransportProperties properties,
            ServiceAccessTokens tokens) {
        this.limitCenterHttp = limitCenterHttp;
        this.properties = properties;
        this.tokens = tokens;
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
            return HttpServiceResponses.requiredData(limitCenterHttp.activate("Bearer " + credential(), command));
        } catch (RuntimeException invalid) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential() {
        return tokens.token(properties.tenantId(), properties.serviceId(), properties.audience());
    }
}
