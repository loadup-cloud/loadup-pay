package io.github.loadup.pay.payment.adapter.rpc;

import io.github.loadup.pay.payment.client.facade.PaymentCaptureFacade;
import io.github.loadup.pay.payment.rpc.PaymentCaptureRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.DubboPaymentCaptureRpcTriple;
import io.github.loadup.pay.payment.rpc.proto.PaymentCaptureRpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCapturePage;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureRetryCommand;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Protobuf IDL entry point for reconciliation capture reads.
 */
@DubboService(interfaceClass = PaymentCaptureRpc.class)
public class PaymentCaptureRpcService extends DubboPaymentCaptureRpcTriple.PaymentCaptureRpcImplBase {
    private final PaymentCaptureFacade paymentCapture;

    private final PaymentCaptureRpcMapper paymentCaptureRpcMapper;

    public PaymentCaptureRpcService(PaymentCaptureRpcMapper paymentCaptureRpcMapper, PaymentCaptureFacade paymentCapture) {
        this.paymentCaptureRpcMapper = paymentCaptureRpcMapper;
        this.paymentCapture = paymentCapture;
    }

    @Override
    public WirePaymentCaptureDTO create(WirePaymentCaptureCreateCommand request) {
        return paymentCaptureRpcMapper.toWire(paymentCapture.create(paymentCaptureRpcMapper.fromWire(request)));
    }

    @Override
    public WirePaymentCaptureDTO retry(WirePaymentCaptureRetryCommand request) {
        return paymentCaptureRpcMapper.toWire(paymentCapture.retry(paymentCaptureRpcMapper.fromWire(request)));
    }

    @Override
    public WirePaymentCaptureDTO detail(WirePaymentCaptureQuery request) {
        return paymentCaptureRpcMapper.toWire(paymentCapture.detail(paymentCaptureRpcMapper.fromWire(request)));
    }

    @Override
    public WirePaymentCapturePage lines(WirePaymentCaptureLinesQuery request) {
        return paymentCaptureRpcMapper.toWire(paymentCapture.lines(paymentCaptureRpcMapper.fromWire(request)));
    }
}
