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

public final class PaymentRpcOperations {
    private PaymentRpcOperations() {}

    public static final RpcOperation<io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand, io.github.loadup.pay.payment.client.dto.PaymentInputDTO, io.github.loadup.pay.payment.rpc.proto.WirePaymentInputPrepareCommand, io.github.loadup.pay.payment.rpc.proto.WirePaymentInputDTO> PAYMENT_INPUT_PREPARE =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getInputPrepareMethod(),
                    PaymentRpcMapper.INSTANCE::toWirePaymentInputPrepareCommand, PaymentRpcMapper.INSTANCE::fromWirePaymentInputDTO);

    public static final RpcOperation<io.github.loadup.pay.payment.client.command.PaymentExecuteCommand, io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO, io.github.loadup.pay.payment.rpc.proto.WirePaymentExecuteCommand, io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO> PAYMENT_ACCEPT =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getAcceptMethod(),
                    PaymentRpcMapper.INSTANCE::toWirePaymentExecuteCommand, PaymentRpcMapper.INSTANCE::fromWirePaymentExecutionDTO);

    public static final RpcOperation<io.github.loadup.commons.request.query.IdQuery, io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO, io.github.loadup.pay.payment.rpc.proto.WireIdQuery, io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO> PAYMENT_DETAIL =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getDetailMethod(),
                    PaymentRpcMapper.INSTANCE::toWireIdQuery, PaymentRpcMapper.INSTANCE::fromWirePaymentExecutionDTO);

    public static final RpcOperation<io.github.loadup.commons.request.query.IdQuery, io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO, io.github.loadup.pay.payment.rpc.proto.WireIdQuery, io.github.loadup.pay.payment.rpc.proto.WirePaymentOutcomeDTO> PAYMENT_OUTCOME =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getOutcomeMethod(),
                    PaymentRpcMapper.INSTANCE::toWireIdQuery, PaymentRpcMapper.INSTANCE::fromWirePaymentOutcomeDTO);

    public static final RpcOperation<io.github.loadup.commons.request.query.IdQuery, io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO, io.github.loadup.pay.payment.rpc.proto.WireIdQuery, io.github.loadup.pay.payment.rpc.proto.WirePaymentSettlementFactsDTO> PAYMENT_FACTS =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getFactsMethod(),
                    PaymentRpcMapper.INSTANCE::toWireIdQuery, PaymentRpcMapper.INSTANCE::fromWirePaymentSettlementFactsDTO);

    public static final RpcOperation<io.github.loadup.pay.payment.client.command.PaymentCloseCommand, io.github.loadup.pay.payment.client.dto.PaymentCloseDTO, io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseCommand, io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseDTO> PAYMENT_CLOSE =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getCloseMethod(),
                    PaymentRpcMapper.INSTANCE::toWirePaymentCloseCommand, PaymentRpcMapper.INSTANCE::fromWirePaymentCloseDTO);

    public static final RpcOperation<io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand, io.github.loadup.pay.payment.client.dto.ParentFenceDTO, io.github.loadup.pay.payment.rpc.proto.WireParentPaymentFenceCommand, io.github.loadup.pay.payment.rpc.proto.WireParentFenceDTO> PAYMENT_FENCE_PARENT =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getFenceParentMethod(),
                    PaymentRpcMapper.INSTANCE::toWireParentPaymentFenceCommand, PaymentRpcMapper.INSTANCE::fromWireParentFenceDTO);

    public static final RpcOperation<io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery, io.github.loadup.pay.payment.client.dto.ParentFenceDTO, io.github.loadup.pay.payment.rpc.proto.WireParentFenceStatusQuery, io.github.loadup.pay.payment.rpc.proto.WireParentFenceDTO> PAYMENT_PARENT_FENCE_STATUS =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getParentFenceStatusMethod(),
                    PaymentRpcMapper.INSTANCE::toWireParentFenceStatusQuery, PaymentRpcMapper.INSTANCE::fromWireParentFenceDTO);

    public static final RpcOperation<io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand, io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO, io.github.loadup.pay.payment.rpc.proto.WirePaymentAttemptStartCommand, io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO> PAYMENT_START_ATTEMPT =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getStartAttemptMethod(),
                    PaymentRpcMapper.INSTANCE::toWirePaymentAttemptStartCommand, PaymentRpcMapper.INSTANCE::fromWirePaymentExecutionDTO);

    public static final RpcOperation<io.github.loadup.commons.request.query.IdQuery, io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO, io.github.loadup.pay.payment.rpc.proto.WireIdQuery, io.github.loadup.pay.payment.rpc.proto.WireOrderRefundSummaryEventDTO> PAYMENT_REFUND_SUMMARY =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getRefundSummaryMethod(),
                    PaymentRpcMapper.INSTANCE::toWireIdQuery, PaymentRpcMapper.INSTANCE::fromWireOrderRefundSummaryEventDTO);

    public static final RpcOperation<io.github.loadup.commons.request.query.IdQuery, io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO, io.github.loadup.pay.payment.rpc.proto.WireIdQuery, io.github.loadup.pay.payment.rpc.proto.WireRefundSettlementFactsDTO> PAYMENT_REFUND_FACTS =
            new RpcOperation<>("payment", io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.getRefundFactsMethod(),
                    PaymentRpcMapper.INSTANCE::toWireIdQuery, PaymentRpcMapper.INSTANCE::fromWireRefundSettlementFactsDTO);
}
