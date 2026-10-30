package io.github.loadup.pay.payment.adapter.rpc;

import io.github.loadup.pay.payment.client.facade.PaymentVerificationFacade;
import io.github.loadup.pay.payment.rpc.PaymentVerificationRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.DubboPaymentVerificationRpcTriple;
import io.github.loadup.pay.payment.rpc.proto.PaymentVerificationRpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationQuery;
import org.apache.dubbo.config.annotation.DubboService;

/** Protobuf IDL entry point for reconciliation verification reads. */
@DubboService(interfaceClass = PaymentVerificationRpc.class)
public class PaymentVerificationRpcService extends DubboPaymentVerificationRpcTriple.PaymentVerificationRpcImplBase {
    private final PaymentVerificationFacade paymentVerification;

    public PaymentVerificationRpcService(
            PaymentVerificationFacade paymentVerification) {
        this.paymentVerification = paymentVerification;
    }

    @Override
    public WirePaymentVerificationDTO verify(WirePaymentVerificationQuery request) {
        return PaymentVerificationRpcMapper.INSTANCE.toWire(paymentVerification.verify(PaymentVerificationRpcMapper.INSTANCE.fromWire(request)));
    }
}
