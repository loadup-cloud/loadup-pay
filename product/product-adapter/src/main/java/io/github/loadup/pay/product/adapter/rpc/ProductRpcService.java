/*
 * #%L
 * LoadUp Pay Product Adapter
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
package io.github.loadup.pay.product.adapter.rpc;

import io.github.loadup.pay.product.client.facade.OrderAdmissionFacade;
import io.github.loadup.pay.product.rpc.ProductRpcMapper;
import io.github.loadup.pay.product.rpc.proto.DubboProductRpcTriple;
import io.github.loadup.pay.product.rpc.proto.ProductRpc;
import io.github.loadup.pay.product.rpc.proto.WireIdQuery;
import io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(interfaceClass = ProductRpc.class)
public class ProductRpcService extends DubboProductRpcTriple.ProductRpcImplBase {
    private final OrderAdmissionFacade orderAdmission;

    private final ProductRpcMapper productRpcMapper;

    public ProductRpcService(ProductRpcMapper productRpcMapper, OrderAdmissionFacade orderAdmission) {
        this.productRpcMapper = productRpcMapper;
        this.orderAdmission = orderAdmission;
    }

    @Override
    public WireOrderAdmissionDTO authorize(WireOrderAdmissionAuthorizeCommand request) {
        return productRpcMapper.toWireOrderAdmissionDTO(
                orderAdmission.authorize(productRpcMapper.fromWireOrderAdmissionAuthorizeCommand(request)));
    }

    @Override
    public WireOrderAdmissionDTO detail(WireIdQuery request) {
        return productRpcMapper.toWireOrderAdmissionDTO(
                orderAdmission.detail(productRpcMapper.fromWireIdQuery(request)));
    }
}
