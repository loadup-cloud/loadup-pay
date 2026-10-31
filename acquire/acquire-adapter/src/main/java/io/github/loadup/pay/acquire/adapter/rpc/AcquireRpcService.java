/*
 * #%L
 * LoadUp Pay Acquire Adapter
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
package io.github.loadup.pay.acquire.adapter.rpc;

import io.github.loadup.pay.acquire.client.facade.FeeRefundOutcomeFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentOutcomeFacade;
import io.github.loadup.pay.acquire.client.facade.RefundNotificationFacade;
import io.github.loadup.pay.acquire.rpc.AcquireRpcMapper;
import io.github.loadup.pay.acquire.rpc.proto.AcquireRpc;
import io.github.loadup.pay.acquire.rpc.proto.DubboAcquireRpcTriple;
import io.github.loadup.pay.acquire.rpc.proto.WireFeeRefundAckDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfPaymentOutcomeEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfRefundMerchantEventDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.rpc.proto.WireRefundNotificationAckDTO;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(interfaceClass = AcquireRpc.class)
public class AcquireRpcService extends DubboAcquireRpcTriple.AcquireRpcImplBase {
    private final PaymentOutcomeFacade paymentOutcome;
    private final FeeRefundOutcomeFacade feeRefundOutcome;
    private final RefundNotificationFacade refundNotification;

    private final AcquireRpcMapper acquireRpcMapper;

    public AcquireRpcService(
            AcquireRpcMapper acquireRpcMapper,
            PaymentOutcomeFacade paymentOutcome,
            FeeRefundOutcomeFacade feeRefundOutcome,
            RefundNotificationFacade refundNotification) {
        this.acquireRpcMapper = acquireRpcMapper;
        this.paymentOutcome = paymentOutcome;
        this.feeRefundOutcome = feeRefundOutcome;
        this.refundNotification = refundNotification;
    }

    @Override
    public WirePaymentOutcomeAckDTO paymentOutcome(WirePayEventEnvelopeOfPaymentOutcomeEventDTO request) {
        return acquireRpcMapper.toWirePaymentOutcomeAckDTO(
                paymentOutcome.accept(acquireRpcMapper.fromWirePayEventEnvelopeOfPaymentOutcomeEventDTO(request)));
    }

    @Override
    public WireFeeRefundAckDTO feeRefund(WirePayEventEnvelopeOfOrderRefundSummaryEventDTO request) {
        return acquireRpcMapper.toWireFeeRefundAckDTO(feeRefundOutcome.accept(
                acquireRpcMapper.fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(request)));
    }

    @Override
    public WireRefundNotificationAckDTO refundNotification(WirePayEventEnvelopeOfRefundMerchantEventDTO request) {
        return acquireRpcMapper.toWireRefundNotificationAckDTO(
                refundNotification.accept(acquireRpcMapper.fromWirePayEventEnvelopeOfRefundMerchantEventDTO(request)));
    }
}
