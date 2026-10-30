package io.github.loadup.pay.product.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcExceptionCodes;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.domain.model.MerchantAction;
import io.github.loadup.pay.customer.rpc.CustomerRpcMapper;
import io.github.loadup.pay.customer.rpc.proto.CustomerRpc;
import io.github.loadup.pay.product.app.port.CustomerEligibilityLookup;
import io.github.loadup.pay.product.app.security.ProductActorProvider;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;

@Component
public class CustomerEligibilityRpcLookup implements CustomerEligibilityLookup {
    @DubboReference(url = "tri://customer-headless:50051")
    private CustomerRpc customerRpc;

    private final ProductActorProvider actors;

    public CustomerEligibilityRpcLookup(ProductActorProvider actors) {
        this.actors = actors;
    }

    @Override
    public MerchantEligibilityDTO authorizeNewOrder(String tenant, String payee) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (!tenant.equals(actor.tenantId())
                || actor.customerAccessToken() == null
                || actor.customerAccessToken().isBlank()) throw new CommonException(ACTION_FORBIDDEN);
        try {
            var command = new MerchantActionAuthorizeCommand(payee, MerchantAction.CREATE_ORDER);
            MerchantEligibilityDTO data = RpcTokenContext.call(
                    actor.customerAccessToken(),
                    () -> CustomerRpcMapper.INSTANCE.fromWireMerchantEligibilityDTO(customerRpc.authorize(
                            CustomerRpcMapper.INSTANCE.toWireMerchantActionAuthorizeCommand(command))));
            if (data == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
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
            if (CustomerResultCodeEnum.NOT_FOUND.getCode().equals(RpcExceptionCodes.resultCode(failure)))
                return new MerchantEligibilityDTO(false, CustomerResultCodeEnum.NOT_FOUND.getCode(), null, 0, null, null);
            // Never expose remote descriptions or arbitrary codes.
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
