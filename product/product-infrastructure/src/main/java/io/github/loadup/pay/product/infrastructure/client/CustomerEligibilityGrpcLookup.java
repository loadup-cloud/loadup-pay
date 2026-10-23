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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.enums.MerchantAction;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.product.app.port.CustomerEligibilityLookup;
import io.github.loadup.pay.product.app.security.ProductActorProvider;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.customer.rpc.CustomerRpcOperations.CUSTOMER_AUTHORIZE;

@Component
public class CustomerEligibilityGrpcLookup implements CustomerEligibilityLookup {
    private final GrpcTransport rpc;
    private final ProductActorProvider actors;

    public CustomerEligibilityGrpcLookup(GrpcTransport rpc, ProductActorProvider actors) {
        this.rpc = rpc;
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
            MerchantEligibilityDTO data = rpc.call(CUSTOMER_AUTHORIZE,
                    new MerchantActionAuthorizeCommand(payee, MerchantAction.CREATE_ORDER), actor.customerAccessToken());
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
            if (CustomerResultCodeEnum.NOT_FOUND.getCode().equals(RpcServerSupport.resultCode(failure)))
                return new MerchantEligibilityDTO(false, CustomerResultCodeEnum.NOT_FOUND.getCode(), null, 0, null, null);
            // Never expose remote descriptions or arbitrary codes.
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
