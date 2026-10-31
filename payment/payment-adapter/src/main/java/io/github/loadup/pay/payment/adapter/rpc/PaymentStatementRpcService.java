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

import io.github.loadup.pay.payment.client.facade.PaymentStatementFacade;
import io.github.loadup.pay.payment.rpc.PaymentStatementRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.DubboPaymentStatementRpcTriple;
import io.github.loadup.pay.payment.rpc.proto.PaymentStatementRpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementQuery;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(interfaceClass = PaymentStatementRpc.class)
public class PaymentStatementRpcService extends DubboPaymentStatementRpcTriple.PaymentStatementRpcImplBase {
    private final PaymentStatementFacade paymentStatement;

    private final PaymentStatementRpcMapper paymentStatementRpcMapper;

    public PaymentStatementRpcService(
            PaymentStatementRpcMapper paymentStatementRpcMapper, PaymentStatementFacade paymentStatement) {
        this.paymentStatementRpcMapper = paymentStatementRpcMapper;
        this.paymentStatement = paymentStatement;
    }

    @Override
    public WirePaymentStatementDTO download(WirePaymentStatementQuery request) {
        return paymentStatementRpcMapper.toWire(paymentStatement.download(paymentStatementRpcMapper.fromWire(request)));
    }
}
