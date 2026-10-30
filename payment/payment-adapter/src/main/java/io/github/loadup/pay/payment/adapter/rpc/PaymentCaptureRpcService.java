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

/** Protobuf IDL entry point for reconciliation capture reads. */
@DubboService(interfaceClass = PaymentCaptureRpc.class)
public class PaymentCaptureRpcService extends DubboPaymentCaptureRpcTriple.PaymentCaptureRpcImplBase {
    private final PaymentCaptureFacade paymentCapture;

    public PaymentCaptureRpcService(PaymentCaptureFacade paymentCapture) {
        this.paymentCapture = paymentCapture;
    }

    @Override
    public WirePaymentCaptureDTO create(WirePaymentCaptureCreateCommand request) {
        return PaymentCaptureRpcMapper.INSTANCE.toWire(paymentCapture.create(PaymentCaptureRpcMapper.INSTANCE.fromWire(request)));
    }

    @Override
    public WirePaymentCaptureDTO retry(WirePaymentCaptureRetryCommand request) {
        return PaymentCaptureRpcMapper.INSTANCE.toWire(paymentCapture.retry(PaymentCaptureRpcMapper.INSTANCE.fromWire(request)));
    }

    @Override
    public WirePaymentCaptureDTO detail(WirePaymentCaptureQuery request) {
        return PaymentCaptureRpcMapper.INSTANCE.toWire(paymentCapture.detail(PaymentCaptureRpcMapper.INSTANCE.fromWire(request)));
    }

    @Override
    public WirePaymentCapturePage lines(WirePaymentCaptureLinesQuery request) {
        return PaymentCaptureRpcMapper.INSTANCE.toWire(paymentCapture.lines(PaymentCaptureRpcMapper.INSTANCE.fromWire(request)));
    }
}
