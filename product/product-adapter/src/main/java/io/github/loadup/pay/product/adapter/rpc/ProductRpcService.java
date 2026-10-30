package io.github.loadup.pay.product.adapter.rpc;

import io.github.loadup.pay.product.client.facade.OrderAdmissionFacade;
import io.github.loadup.pay.product.rpc.ProductRpcMapper;
import io.github.loadup.pay.product.rpc.proto.DubboProductRpcTriple;
import io.github.loadup.pay.product.rpc.proto.ProductRpc;
import io.github.loadup.pay.product.rpc.proto.WireIdQuery;
import io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Protobuf IDL entry point for the product service.
 */
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
        return productRpcMapper.toWireOrderAdmissionDTO(orderAdmission.authorize(
                productRpcMapper.fromWireOrderAdmissionAuthorizeCommand(request)));
    }

    @Override
    public WireOrderAdmissionDTO detail(WireIdQuery request) {
        return productRpcMapper.toWireOrderAdmissionDTO(
                orderAdmission.detail(productRpcMapper.fromWireIdQuery(request)));
    }
}
