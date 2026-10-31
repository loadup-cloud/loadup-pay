/*
 * #%L
 * LoadUp Pay Payment Adapter
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

@DubboService(interfaceClass = PaymentCaptureRpc.class)
public class PaymentCaptureRpcService extends DubboPaymentCaptureRpcTriple.PaymentCaptureRpcImplBase {
    private final PaymentCaptureFacade paymentCapture;

    private final PaymentCaptureRpcMapper paymentCaptureRpcMapper;

    public PaymentCaptureRpcService(
            PaymentCaptureRpcMapper paymentCaptureRpcMapper, PaymentCaptureFacade paymentCapture) {
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
