/*
 * #%L
 * LoadUp Pay Customer Adapter
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
package io.github.loadup.pay.customer.adapter.rpc;

import io.github.loadup.pay.customer.client.facade.MerchantEligibilityFacade;
import io.github.loadup.pay.customer.rpc.CustomerRpcMapper;
import io.github.loadup.pay.customer.rpc.proto.CustomerRpc;
import io.github.loadup.pay.customer.rpc.proto.DubboCustomerRpcTriple;
import io.github.loadup.pay.customer.rpc.proto.WireMerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.rpc.proto.WireMerchantEligibilityDTO;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(interfaceClass = CustomerRpc.class)
public class CustomerRpcService extends DubboCustomerRpcTriple.CustomerRpcImplBase {
    private final MerchantEligibilityFacade merchantEligibility;

    private final CustomerRpcMapper customerRpcMapper;

    public CustomerRpcService(CustomerRpcMapper customerRpcMapper, MerchantEligibilityFacade merchantEligibility) {
        this.customerRpcMapper = customerRpcMapper;
        this.merchantEligibility = merchantEligibility;
    }

    @Override
    public WireMerchantEligibilityDTO authorize(WireMerchantActionAuthorizeCommand request) {
        return customerRpcMapper.toWireMerchantEligibilityDTO(
                merchantEligibility.authorize(customerRpcMapper.fromWireMerchantActionAuthorizeCommand(request)));
    }
}
