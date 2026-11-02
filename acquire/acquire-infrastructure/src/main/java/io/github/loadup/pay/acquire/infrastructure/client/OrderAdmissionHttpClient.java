package io.github.loadup.pay.acquire.infrastructure.client;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.pay.acquire.app.port.OrderAdmissionClient;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.client.http.ProductHttpService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class OrderAdmissionHttpClient implements OrderAdmissionClient {
    private final AcquireActorProvider actors;
    private final ProductHttpService productHttp;

    public OrderAdmissionHttpClient(ProductHttpService productHttp, AcquireActorProvider actors) {
        this.productHttp = productHttp;
        this.actors = actors;
    }

    @Override
    public OrderAdmissionDTO authorize(OrderAdmissionAuthorizeCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        var actor = actors.current();
        if (actor.productAccessToken() == null || actor.productAccessToken().isBlank()) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        try {
            return HttpServiceResponses.requiredData(
                    productHttp.authorize("Bearer " + actor.productAccessToken(), command));
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
