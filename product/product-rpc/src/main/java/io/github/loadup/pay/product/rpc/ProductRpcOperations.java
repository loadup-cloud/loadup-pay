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
package io.github.loadup.pay.product.rpc;

import io.github.loadup.components.rpc.grpc.RpcOperation;

public final class ProductRpcOperations {
    private ProductRpcOperations() {}

    public static final RpcOperation<io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand, io.github.loadup.pay.product.client.dto.OrderAdmissionDTO, io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionAuthorizeCommand, io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO> PRODUCT_AUTHORIZE =
            new RpcOperation<>("product", io.github.loadup.pay.product.rpc.proto.ProductRpcGrpc.getAuthorizeMethod(),
                    ProductRpcMapper.INSTANCE::toWireOrderAdmissionAuthorizeCommand, ProductRpcMapper.INSTANCE::fromWireOrderAdmissionDTO);

    public static final RpcOperation<io.github.loadup.commons.request.query.IdQuery, io.github.loadup.pay.product.client.dto.OrderAdmissionDTO, io.github.loadup.pay.product.rpc.proto.WireIdQuery, io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO> PRODUCT_DETAIL =
            new RpcOperation<>("product", io.github.loadup.pay.product.rpc.proto.ProductRpcGrpc.getDetailMethod(),
                    ProductRpcMapper.INSTANCE::toWireIdQuery, ProductRpcMapper.INSTANCE::fromWireOrderAdmissionDTO);
}
