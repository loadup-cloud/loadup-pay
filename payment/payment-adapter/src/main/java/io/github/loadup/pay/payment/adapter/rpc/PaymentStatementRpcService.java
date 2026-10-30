package io.github.loadup.pay.payment.adapter.rpc;

import io.github.loadup.pay.payment.client.facade.PaymentStatementFacade;
import io.github.loadup.pay.payment.rpc.PaymentStatementRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.DubboPaymentStatementRpcTriple;
import io.github.loadup.pay.payment.rpc.proto.PaymentStatementRpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementQuery;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Protobuf IDL entry point for the payment statement download.
 */
@DubboService(interfaceClass = PaymentStatementRpc.class)
public class PaymentStatementRpcService extends DubboPaymentStatementRpcTriple.PaymentStatementRpcImplBase {
    private final PaymentStatementFacade paymentStatement;

    private final PaymentStatementRpcMapper paymentStatementRpcMapper;

    public PaymentStatementRpcService(PaymentStatementRpcMapper paymentStatementRpcMapper, PaymentStatementFacade paymentStatement) {
        this.paymentStatementRpcMapper = paymentStatementRpcMapper;
        this.paymentStatement = paymentStatement;
    }

    @Override
    public WirePaymentStatementDTO download(WirePaymentStatementQuery request) {
        return paymentStatementRpcMapper.toWire(paymentStatement.download(paymentStatementRpcMapper.fromWire(request)));
    }
}
