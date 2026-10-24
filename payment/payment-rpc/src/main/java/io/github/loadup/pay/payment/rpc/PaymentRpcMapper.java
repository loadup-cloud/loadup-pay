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

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.rpc.grpc.GrpcScalarMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(config = LoadUpMapStructConfig.class, componentModel = "default",
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        uses = {GrpcScalarMapper.class, io.github.loadup.pay.product.rpc.ProductRpcMapper.class})
public interface PaymentRpcMapper {
    PaymentRpcMapper INSTANCE = Mappers.getMapper(PaymentRpcMapper.class);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    io.github.loadup.pay.payment.rpc.proto.WireIdQuery toWireIdQuery(io.github.loadup.commons.request.query.IdQuery value);

    io.github.loadup.commons.request.query.IdQuery fromWireIdQuery(io.github.loadup.pay.payment.rpc.proto.WireIdQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "allocationId", source = "allocationId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "inputKey", source = "inputKey")
    @Mapping(target = "choice", source = "choice")
    @Mapping(target = "interaction", source = "interaction")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentInputPrepareCommand toWirePaymentInputPrepareCommand(io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand value);

    io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand fromWirePaymentInputPrepareCommand(io.github.loadup.pay.payment.rpc.proto.WirePaymentInputPrepareCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "method", source = "method")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentChoiceDTO toWirePaymentChoiceDTO(io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO value);

    io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO fromWirePaymentChoiceDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentChoiceDTO value);

    default io.github.loadup.pay.payment.rpc.proto.WirePaymentInteractionDTO toWirePaymentInteractionDTO(io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO value) {
        if (value == null) throw new IllegalArgumentException("RPC value is required");
        var result = io.github.loadup.pay.payment.rpc.proto.WirePaymentInteractionDTO.newBuilder();
        if (value instanceof io.github.loadup.pay.payment.client.dto.NoInteractionDTO variant) result.setOption0(toWireNoInteractionDTO(variant));
        else if (value instanceof io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO variant) result.setOption1(toWirePaymentCodeInteractionDTO(variant));
        else if (value instanceof io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO variant) result.setOption2(toWireChannelIdentityInteractionDTO(variant));
        else if (value instanceof io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO variant) result.setOption3(toWireH5SceneInteractionDTO(variant));
        else throw new IllegalArgumentException("Unknown RPC variant");
        return result.build();
    }
    default io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO fromWirePaymentInteractionDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentInteractionDTO value) {
        return switch (value.getVariantCase()) {
            case OPTION0 -> fromWireNoInteractionDTO(value.getOption0());
            case OPTION1 -> fromWirePaymentCodeInteractionDTO(value.getOption1());
            case OPTION2 -> fromWireChannelIdentityInteractionDTO(value.getOption2());
            case OPTION3 -> fromWireH5SceneInteractionDTO(value.getOption3());
            default -> throw new IllegalArgumentException("RPC variant is required");
        };
    }
    @BeanMapping(ignoreByDefault = true)
    io.github.loadup.pay.payment.rpc.proto.WireNoInteractionDTO toWireNoInteractionDTO(io.github.loadup.pay.payment.client.dto.NoInteractionDTO value);

    io.github.loadup.pay.payment.client.dto.NoInteractionDTO fromWireNoInteractionDTO(io.github.loadup.pay.payment.rpc.proto.WireNoInteractionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentCode", source = "paymentCode")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentCodeInteractionDTO toWirePaymentCodeInteractionDTO(io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO value);

    io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO fromWirePaymentCodeInteractionDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentCodeInteractionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "identifier", source = "identifier")
    @Mapping(target = "appScope", source = "appScope")
    @Mapping(target = "identityType", source = "identityType")
    io.github.loadup.pay.payment.rpc.proto.WireChannelIdentityInteractionDTO toWireChannelIdentityInteractionDTO(io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO value);

    io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO fromWireChannelIdentityInteractionDTO(io.github.loadup.pay.payment.rpc.proto.WireChannelIdentityInteractionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "payerClientIp", source = "payerClientIp")
    @Mapping(target = "sceneType", expression = "java(value.sceneType().name())")
    io.github.loadup.pay.payment.rpc.proto.WireH5SceneInteractionDTO toWireH5SceneInteractionDTO(io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO value);

    @BeforeMapping
    default void requireH5SceneFields(io.github.loadup.pay.payment.rpc.proto.WireH5SceneInteractionDTO value) {
        if (value == null || !value.hasPayerClientIp() || !value.hasSceneType()) {
            throw new IllegalArgumentException("RPC H5 scene fields are required");
        }
    }

    @Mapping(target = "sceneType", expression = "java(io.github.loadup.pay.channel.api.model.H5SceneType.valueOf(value.getSceneType()))")
    io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO fromWireH5SceneInteractionDTO(io.github.loadup.pay.payment.rpc.proto.WireH5SceneInteractionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "inputRef", source = "inputRef")
    @Mapping(target = "inputDigest", source = "inputDigest")
    @Mapping(target = "expiresAt", source = "expiresAt")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentInputDTO toWirePaymentInputDTO(io.github.loadup.pay.payment.client.dto.PaymentInputDTO value);

    io.github.loadup.pay.payment.client.dto.PaymentInputDTO fromWirePaymentInputDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentInputDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "allocationId", source = "allocationId")
    @Mapping(target = "admissionId", source = "admissionId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "requestDigest", source = "requestDigest")
    @Mapping(target = "choice", source = "choice")
    @Mapping(target = "preparedInputRef", source = "preparedInputRef")
    @Mapping(target = "admittedTerms", source = "admittedTerms")
    @Mapping(target = "subject", source = "subject")
    @Mapping(target = "payableUntil", source = "payableUntil")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentExecuteCommand toWirePaymentExecuteCommand(io.github.loadup.pay.payment.client.command.PaymentExecuteCommand value);

    io.github.loadup.pay.payment.client.command.PaymentExecuteCommand fromWirePaymentExecuteCommand(io.github.loadup.pay.payment.rpc.proto.WirePaymentExecuteCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "allocationId", source = "allocationId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "activeAttemptId", source = "activeAttemptId")
    @Mapping(target = "action", source = "action")
    @Mapping(target = "terminalEvidenceId", source = "terminalEvidenceId")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "initiatedAt", source = "initiatedAt")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO toWirePaymentExecutionDTO(io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO value);

    io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO fromWirePaymentExecutionDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "type", source = "type")
    @Mapping(target = "expiresAt", source = "expiresAt")
    @Mapping(target = "data", source = "data")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentActionDTO toWirePaymentActionDTO(io.github.loadup.pay.payment.client.dto.PaymentActionDTO value);

    io.github.loadup.pay.payment.client.dto.PaymentActionDTO fromWirePaymentActionDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentActionDTO value);

    default io.github.loadup.pay.payment.rpc.proto.WirePaymentActionDataDTO toWirePaymentActionDataDTO(io.github.loadup.pay.payment.client.dto.PaymentActionDataDTO value) {
        if (value == null) throw new IllegalArgumentException("RPC value is required");
        var result = io.github.loadup.pay.payment.rpc.proto.WirePaymentActionDataDTO.newBuilder();
        if (value instanceof io.github.loadup.pay.payment.client.dto.NoActionDTO variant) result.setOption0(toWireNoActionDTO(variant));
        else if (value instanceof io.github.loadup.pay.payment.client.dto.WaitActionDTO variant) result.setOption1(toWireWaitActionDTO(variant));
        else if (value instanceof io.github.loadup.pay.payment.client.dto.RedirectActionDTO variant) result.setOption2(toWireRedirectActionDTO(variant));
        else if (value instanceof io.github.loadup.pay.payment.client.dto.WechatJsapiActionDTO variant) result.setOption3(toWireWechatJsapiActionDTO(variant));
        else if (value instanceof io.github.loadup.pay.payment.client.dto.AlipayJsapiActionDTO variant) result.setOption4(toWireAlipayJsapiActionDTO(variant));
        else throw new IllegalArgumentException("Unknown RPC variant");
        return result.build();
    }
    default io.github.loadup.pay.payment.client.dto.PaymentActionDataDTO fromWirePaymentActionDataDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentActionDataDTO value) {
        return switch (value.getVariantCase()) {
            case OPTION0 -> fromWireNoActionDTO(value.getOption0());
            case OPTION1 -> fromWireWaitActionDTO(value.getOption1());
            case OPTION2 -> fromWireRedirectActionDTO(value.getOption2());
            case OPTION3 -> fromWireWechatJsapiActionDTO(value.getOption3());
            case OPTION4 -> fromWireAlipayJsapiActionDTO(value.getOption4());
            default -> throw new IllegalArgumentException("RPC variant is required");
        };
    }
    @BeanMapping(ignoreByDefault = true)
    io.github.loadup.pay.payment.rpc.proto.WireNoActionDTO toWireNoActionDTO(io.github.loadup.pay.payment.client.dto.NoActionDTO value);

    io.github.loadup.pay.payment.client.dto.NoActionDTO fromWireNoActionDTO(io.github.loadup.pay.payment.rpc.proto.WireNoActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "recommendedPollAfterMillis", source = "recommendedPollAfterMillis")
    io.github.loadup.pay.payment.rpc.proto.WireWaitActionDTO toWireWaitActionDTO(io.github.loadup.pay.payment.client.dto.WaitActionDTO value);

    io.github.loadup.pay.payment.client.dto.WaitActionDTO fromWireWaitActionDTO(io.github.loadup.pay.payment.rpc.proto.WireWaitActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "redirectUrl", source = "redirectUrl")
    io.github.loadup.pay.payment.rpc.proto.WireRedirectActionDTO toWireRedirectActionDTO(io.github.loadup.pay.payment.client.dto.RedirectActionDTO value);

    io.github.loadup.pay.payment.client.dto.RedirectActionDTO fromWireRedirectActionDTO(io.github.loadup.pay.payment.rpc.proto.WireRedirectActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "appId", source = "appId")
    @Mapping(target = "timeStamp", source = "timeStamp")
    @Mapping(target = "nonceStr", source = "nonceStr")
    @Mapping(target = "packageValue", source = "packageValue")
    @Mapping(target = "signType", source = "signType")
    @Mapping(target = "paySign", source = "paySign")
    io.github.loadup.pay.payment.rpc.proto.WireWechatJsapiActionDTO toWireWechatJsapiActionDTO(io.github.loadup.pay.payment.client.dto.WechatJsapiActionDTO value);

    io.github.loadup.pay.payment.client.dto.WechatJsapiActionDTO fromWireWechatJsapiActionDTO(io.github.loadup.pay.payment.rpc.proto.WireWechatJsapiActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "tradeNo", source = "tradeNo")
    io.github.loadup.pay.payment.rpc.proto.WireAlipayJsapiActionDTO toWireAlipayJsapiActionDTO(io.github.loadup.pay.payment.client.dto.AlipayJsapiActionDTO value);

    io.github.loadup.pay.payment.client.dto.AlipayJsapiActionDTO fromWireAlipayJsapiActionDTO(io.github.loadup.pay.payment.rpc.proto.WireAlipayJsapiActionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "executionFenced", source = "executionFenced")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentOutcomeDTO toWirePaymentOutcomeDTO(io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO value);

    io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO fromWirePaymentOutcomeDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentOutcomeDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "limitOperationId", source = "limitOperationId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "executionFenced", source = "executionFenced")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentSettlementFactsDTO toWirePaymentSettlementFactsDTO(io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO value);

    io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO fromWirePaymentSettlementFactsDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentSettlementFactsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "requestKey", source = "requestKey")
    @Mapping(target = "fenceEpoch", source = "fenceEpoch")
    @Mapping(target = "reasonCode", source = "reasonCode")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseCommand toWirePaymentCloseCommand(io.github.loadup.pay.payment.client.command.PaymentCloseCommand value);

    io.github.loadup.pay.payment.client.command.PaymentCloseCommand fromWirePaymentCloseCommand(io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "fenceEpoch", source = "fenceEpoch")
    @Mapping(target = "fenced", source = "fenced")
    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "noCollectionEvidenceId", source = "noCollectionEvidenceId")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseDTO toWirePaymentCloseDTO(io.github.loadup.pay.payment.client.dto.PaymentCloseDTO value);

    io.github.loadup.pay.payment.client.dto.PaymentCloseDTO fromWirePaymentCloseDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "closeEpoch", source = "closeEpoch")
    @Mapping(target = "requestKey", source = "requestKey")
    io.github.loadup.pay.payment.rpc.proto.WireParentPaymentFenceCommand toWireParentPaymentFenceCommand(io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand value);

    io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand fromWireParentPaymentFenceCommand(io.github.loadup.pay.payment.rpc.proto.WireParentPaymentFenceCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "closeEpoch", source = "closeEpoch")
    @Mapping(target = "fenced", source = "fenced")
    @Mapping(target = "allChildrenConverged", source = "allChildrenConverged")
    @Mapping(target = "unknownCount", source = "unknownCount")
    io.github.loadup.pay.payment.rpc.proto.WireParentFenceDTO toWireParentFenceDTO(io.github.loadup.pay.payment.client.dto.ParentFenceDTO value);

    io.github.loadup.pay.payment.client.dto.ParentFenceDTO fromWireParentFenceDTO(io.github.loadup.pay.payment.rpc.proto.WireParentFenceDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "closeEpoch", source = "closeEpoch")
    io.github.loadup.pay.payment.rpc.proto.WireParentFenceStatusQuery toWireParentFenceStatusQuery(io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery value);

    io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery fromWireParentFenceStatusQuery(io.github.loadup.pay.payment.rpc.proto.WireParentFenceStatusQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "requestKey", source = "requestKey")
    @Mapping(target = "choice", source = "choice")
    @Mapping(target = "preparedInputRef", source = "preparedInputRef")
    @Mapping(target = "previousAttemptEvidenceId", source = "previousAttemptEvidenceId")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentAttemptStartCommand toWirePaymentAttemptStartCommand(io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand value);

    io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand fromWirePaymentAttemptStartCommand(io.github.loadup.pay.payment.rpc.proto.WirePaymentAttemptStartCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "allocationId", source = "allocationId")
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "paymentVersion", source = "paymentVersion")
    @Mapping(target = "evidenceId", source = "evidenceId")
    @Mapping(target = "executionFenced", source = "executionFenced")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentOutcomeEventDTO toWirePaymentOutcomeEventDTO(io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO value);

    io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO fromWirePaymentOutcomeEventDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentOutcomeEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "accessMode", source = "accessMode")
    @Mapping(target = "initiatedAt", source = "initiatedAt")
    @Mapping(target = "succeededAt", source = "succeededAt")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    io.github.loadup.pay.payment.rpc.proto.WirePaymentSnapshotEventDTO toWirePaymentSnapshotEventDTO(io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO value);

    io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO fromWirePaymentSnapshotEventDTO(io.github.loadup.pay.payment.rpc.proto.WirePaymentSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "refundId", source = "refundId")
    @Mapping(target = "refundRequestId", source = "refundRequestId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "refundCreatedAt", source = "refundCreatedAt")
    @Mapping(target = "succeededAt", source = "succeededAt")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    io.github.loadup.pay.payment.rpc.proto.WireRefundSnapshotEventDTO toWireRefundSnapshotEventDTO(io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO value);

    io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO fromWireRefundSnapshotEventDTO(io.github.loadup.pay.payment.rpc.proto.WireRefundSnapshotEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "refundedMinor", source = "refundedMinor")
    @Mapping(target = "refundReservedMinor", source = "refundReservedMinor")
    @Mapping(target = "refundSummaryVersion", source = "refundSummaryVersion")
    io.github.loadup.pay.payment.rpc.proto.WireOrderRefundSummaryEventDTO toWireOrderRefundSummaryEventDTO(io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO value);

    io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO fromWireOrderRefundSummaryEventDTO(io.github.loadup.pay.payment.rpc.proto.WireOrderRefundSummaryEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "refund", source = "refund")
    @Mapping(target = "paymentAmountMinor", source = "paymentAmountMinor")
    @Mapping(target = "paymentVersion", source = "paymentVersion")
    @Mapping(target = "paymentEvidenceId", source = "paymentEvidenceId")
    @Mapping(target = "paymentAttemptId", source = "paymentAttemptId")
    @Mapping(target = "paymentInitiatedAt", source = "paymentInitiatedAt")
    @Mapping(target = "paymentRefundVersion", source = "paymentRefundVersion")
    io.github.loadup.pay.payment.rpc.proto.WireRefundSettlementFactsDTO toWireRefundSettlementFactsDTO(io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO value);

    io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO fromWireRefundSettlementFactsDTO(io.github.loadup.pay.payment.rpc.proto.WireRefundSettlementFactsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "refundId", source = "refundId")
    @Mapping(target = "refundRequestId", source = "refundRequestId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "refundVersion", source = "refundVersion")
    @Mapping(target = "cumulativePaymentRefundedMinor", source = "cumulativePaymentRefundedMinor")
    @Mapping(target = "evidenceId", source = "evidenceId")
    io.github.loadup.pay.payment.rpc.proto.WireRefundOutcomeEventDTO toWireRefundOutcomeEventDTO(io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO value);

    io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO fromWireRefundOutcomeEventDTO(io.github.loadup.pay.payment.rpc.proto.WireRefundOutcomeEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "aggregateType", source = "aggregateType")
    @Mapping(target = "refundId", source = "refundId")
    @Mapping(target = "refundRequestId", source = "refundRequestId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "merchantRefundNo", source = "merchantRefundNo")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "succeededMinor", source = "succeededMinor")
    @Mapping(target = "failedMinor", source = "failedMinor")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    @Mapping(target = "succeededAt", source = "succeededAt")
    io.github.loadup.pay.payment.rpc.proto.WireRefundMerchantEventDTO toWireRefundMerchantEventDTO(io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "aggregateType", source = "aggregateType")
    @Mapping(target = "refundId", expression = "java(value.hasRefundId() ? value.getRefundId() : null)")
    @Mapping(target = "refundRequestId", source = "refundRequestId")
    @Mapping(target = "paymentId", expression = "java(value.hasPaymentId() ? value.getPaymentId() : null)")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "merchantRefundNo", source = "merchantRefundNo")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "succeededMinor", source = "succeededMinor")
    @Mapping(target = "failedMinor", source = "failedMinor")
    @Mapping(target = "sourceVersion", source = "sourceVersion")
    @Mapping(target = "succeededAt", source = "succeededAt")
    io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO fromWireRefundMerchantEventDTO(io.github.loadup.pay.payment.rpc.proto.WireRefundMerchantEventDTO value);

    @BeforeMapping
    default void requireRefundMerchantEventFields(io.github.loadup.pay.payment.rpc.proto.WireRefundMerchantEventDTO value) {
        if (value == null || !value.hasAggregateType() || !value.hasRefundRequestId() || !value.hasOrderId()
                || !value.hasPayeeMerchantId() || !value.hasMerchantRefundNo() || !value.hasAmountMinor()
                || !value.hasCurrency() || !value.hasState() || !value.hasSucceededMinor()
                || !value.hasFailedMinor() || !value.hasSourceVersion()) {
            throw new IllegalArgumentException("RPC refund merchant event fields are required");
        }
    }

}
