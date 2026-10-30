package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.acquire.app.port.OrderAdmissionClient;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.rpc.ProductRpcMapper;
import io.github.loadup.pay.product.rpc.proto.ProductRpc;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

@Component
public class OrderAdmissionRpcClient implements OrderAdmissionClient {
    @DubboReference(url = "tri://product-headless:50051")
    private ProductRpc productRpc;

    private final AcquireActorProvider actors;

    public OrderAdmissionRpcClient(AcquireActorProvider actors) {
        this.actors = actors;
    }

    @Override
    public OrderAdmissionDTO authorize(OrderAdmissionAuthorizeCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor.productAccessToken() == null || actor.productAccessToken().isBlank())
            throw new CommonException(ACTION_FORBIDDEN);
        try {
            var response = RpcTokenContext.call(
                    actor.productAccessToken(),
                    () -> ProductRpcMapper.INSTANCE.fromWireOrderAdmissionDTO(productRpc.authorize(
                            ProductRpcMapper.INSTANCE.toWireOrderAdmissionAuthorizeCommand(command))));
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
