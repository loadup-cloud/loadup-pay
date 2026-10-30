package io.github.loadup.pay.customer.adapter.rpc;

import io.github.loadup.pay.customer.client.facade.MerchantEligibilityFacade;
import io.github.loadup.pay.customer.rpc.CustomerRpcMapper;
import io.github.loadup.pay.customer.rpc.proto.CustomerRpc;
import io.github.loadup.pay.customer.rpc.proto.DubboCustomerRpcTriple;
import io.github.loadup.pay.customer.rpc.proto.WireMerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.rpc.proto.WireMerchantEligibilityDTO;
import org.apache.dubbo.config.annotation.DubboService;

/** Protobuf IDL entry point for the customer service. */
@DubboService(interfaceClass = CustomerRpc.class)
public class CustomerRpcService extends DubboCustomerRpcTriple.CustomerRpcImplBase {
    private final MerchantEligibilityFacade merchantEligibility;

    public CustomerRpcService(MerchantEligibilityFacade merchantEligibility) {
        this.merchantEligibility = merchantEligibility;
    }

    @Override
    public WireMerchantEligibilityDTO authorize(WireMerchantActionAuthorizeCommand request) {
        return CustomerRpcMapper.INSTANCE.toWireMerchantEligibilityDTO(merchantEligibility.authorize(
                CustomerRpcMapper.INSTANCE.fromWireMerchantActionAuthorizeCommand(request)));
    }
}
