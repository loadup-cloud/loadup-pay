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

import io.github.loadup.pay.payment.client.facade.PaymentVerificationFacade;
import io.github.loadup.pay.payment.rpc.PaymentVerificationRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.DubboPaymentVerificationRpcTriple;
import io.github.loadup.pay.payment.rpc.proto.PaymentVerificationRpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationQuery;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(interfaceClass = PaymentVerificationRpc.class)
public class PaymentVerificationRpcService extends DubboPaymentVerificationRpcTriple.PaymentVerificationRpcImplBase {
    private final PaymentVerificationFacade paymentVerification;

    private final PaymentVerificationRpcMapper paymentVerificationRpcMapper;

    public PaymentVerificationRpcService(
            PaymentVerificationRpcMapper paymentVerificationRpcMapper, PaymentVerificationFacade paymentVerification) {
        this.paymentVerificationRpcMapper = paymentVerificationRpcMapper;
        this.paymentVerification = paymentVerification;
    }

    @Override
    public WirePaymentVerificationDTO verify(WirePaymentVerificationQuery request) {
        return paymentVerificationRpcMapper.toWire(
                paymentVerification.verify(paymentVerificationRpcMapper.fromWire(request)));
    }
}
