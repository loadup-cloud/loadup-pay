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
package io.github.loadup.pay.payment.rpc;

import io.github.loadup.components.rpc.grpc.RpcOperation;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.rpc.proto.PaymentStatementRpcGrpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementQuery;
import org.springframework.stereotype.Component;

@Component
public final class PaymentStatementRpcOperations {
    private final RpcOperation<PaymentStatementQuery, PaymentStatementDTO, WirePaymentStatementQuery, WirePaymentStatementDTO> download;

    public PaymentStatementRpcOperations(PaymentStatementRpcMapper mapper) {
        download = new RpcOperation<>("payment-statement", PaymentStatementRpcGrpc.getDownloadMethod(), mapper::toWire, mapper::fromWire);
    }

    public RpcOperation<PaymentStatementQuery, PaymentStatementDTO, WirePaymentStatementQuery, WirePaymentStatementDTO> download() {
        return download;
    }
}
