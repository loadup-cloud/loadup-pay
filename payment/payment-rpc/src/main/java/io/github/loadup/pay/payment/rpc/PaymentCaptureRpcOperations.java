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

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.components.rpc.grpc.RpcOperation;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.proto.PaymentCaptureRpcGrpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCapturePage;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureRetryCommand;
import org.springframework.stereotype.Component;

/**
 * Typed provider-owned methods; the transport owns channel, credentials and deadline.
 */
@Component
public final class PaymentCaptureRpcOperations {
    private final RpcOperation<PaymentCaptureCreateCommand, PaymentCaptureDTO, WirePaymentCaptureCreateCommand, WirePaymentCaptureDTO> create;
    private final RpcOperation<PaymentCaptureRetryCommand, PaymentCaptureDTO, WirePaymentCaptureRetryCommand, WirePaymentCaptureDTO> retry;
    private final RpcOperation<IdQuery, PaymentCaptureDTO, WirePaymentCaptureQuery, WirePaymentCaptureDTO> detail;
    private final RpcOperation<PaymentCaptureLinesQuery, PageDTO<PaymentLedgerLineDTO>, WirePaymentCaptureLinesQuery, WirePaymentCapturePage> lines;

    public PaymentCaptureRpcOperations(PaymentCaptureRpcMapper mapper) {
        create = new RpcOperation<>("payment", PaymentCaptureRpcGrpc.getCreateMethod(), mapper::toWire, mapper::fromWire);
        retry = new RpcOperation<>("payment", PaymentCaptureRpcGrpc.getRetryMethod(), mapper::toWire, mapper::fromWire);
        detail = new RpcOperation<>("payment", PaymentCaptureRpcGrpc.getDetailMethod(), mapper::toWire, mapper::fromWire);
        lines = new RpcOperation<>("payment", PaymentCaptureRpcGrpc.getLinesMethod(), mapper::toWire, mapper::fromWire);
    }

    public RpcOperation<PaymentCaptureCreateCommand, PaymentCaptureDTO, WirePaymentCaptureCreateCommand, WirePaymentCaptureDTO> create() {
        return create;
    }

    public RpcOperation<PaymentCaptureRetryCommand, PaymentCaptureDTO, WirePaymentCaptureRetryCommand, WirePaymentCaptureDTO> retry() {
        return retry;
    }

    public RpcOperation<IdQuery, PaymentCaptureDTO, WirePaymentCaptureQuery, WirePaymentCaptureDTO> detail() {
        return detail;
    }

    public RpcOperation<PaymentCaptureLinesQuery, PageDTO<PaymentLedgerLineDTO>, WirePaymentCaptureLinesQuery, WirePaymentCapturePage> lines() {
        return lines;
    }
}
