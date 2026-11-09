/*
 * Copyright (C) 2026 LoadUp Cloud
 *
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
 */
package io.github.loadup.acquire.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.result.SuccessResponse;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(config = LoadUpMapStructConfig.class, subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface RemoteContractConverter {
    io.github.loadup.payment.client.command.ParentPaymentFenceCommand toRemote(
            io.github.loadup.acquire.app.model.payment.command.ParentPaymentFenceCommand value);

    io.github.loadup.acquire.app.model.payment.command.ParentPaymentFenceCommand toLocal(
            io.github.loadup.payment.client.command.ParentPaymentFenceCommand value);

    io.github.loadup.payment.client.command.PaymentCloseCommand toRemote(
            io.github.loadup.acquire.app.model.payment.command.PaymentCloseCommand value);

    io.github.loadup.acquire.app.model.payment.command.PaymentCloseCommand toLocal(
            io.github.loadup.payment.client.command.PaymentCloseCommand value);

    io.github.loadup.payment.client.command.PaymentExecuteCommand toRemote(
            io.github.loadup.acquire.app.model.payment.command.PaymentExecuteCommand value);

    io.github.loadup.acquire.app.model.payment.command.PaymentExecuteCommand toLocal(
            io.github.loadup.payment.client.command.PaymentExecuteCommand value);

    io.github.loadup.payment.client.command.PaymentInputExpireCommand toRemote(
            io.github.loadup.acquire.app.model.payment.command.PaymentInputExpireCommand value);

    io.github.loadup.acquire.app.model.payment.command.PaymentInputExpireCommand toLocal(
            io.github.loadup.payment.client.command.PaymentInputExpireCommand value);

    io.github.loadup.payment.client.command.PaymentInputPrepareCommand toRemote(
            io.github.loadup.acquire.app.model.payment.command.PaymentInputPrepareCommand value);

    io.github.loadup.acquire.app.model.payment.command.PaymentInputPrepareCommand toLocal(
            io.github.loadup.payment.client.command.PaymentInputPrepareCommand value);

    io.github.loadup.payment.client.dto.AlipayJsapiActionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.AlipayJsapiActionDTO value);

    io.github.loadup.acquire.client.dto.payment.AlipayJsapiActionDTO toLocal(
            io.github.loadup.payment.client.dto.AlipayJsapiActionDTO value);

    io.github.loadup.payment.client.dto.ChannelIdentityInteractionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.ChannelIdentityInteractionDTO value);

    io.github.loadup.acquire.client.dto.payment.ChannelIdentityInteractionDTO toLocal(
            io.github.loadup.payment.client.dto.ChannelIdentityInteractionDTO value);

    io.github.loadup.payment.client.dto.H5SceneInteractionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.H5SceneInteractionDTO value);

    io.github.loadup.acquire.client.dto.payment.H5SceneInteractionDTO toLocal(
            io.github.loadup.payment.client.dto.H5SceneInteractionDTO value);

    io.github.loadup.payment.client.dto.NoActionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.NoActionDTO value);

    io.github.loadup.acquire.client.dto.payment.NoActionDTO toLocal(
            io.github.loadup.payment.client.dto.NoActionDTO value);

    io.github.loadup.payment.client.dto.NoInteractionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.NoInteractionDTO value);

    io.github.loadup.acquire.client.dto.payment.NoInteractionDTO toLocal(
            io.github.loadup.payment.client.dto.NoInteractionDTO value);

    io.github.loadup.payment.client.dto.ParentFenceDTO toRemote(
            io.github.loadup.acquire.app.model.payment.dto.ParentFenceDTO value);

    io.github.loadup.acquire.app.model.payment.dto.ParentFenceDTO toLocal(
            io.github.loadup.payment.client.dto.ParentFenceDTO value);

    io.github.loadup.payment.client.dto.PaymentActionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.PaymentActionDTO value);

    io.github.loadup.acquire.client.dto.payment.PaymentActionDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentActionDTO value);

    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.NoActionDTO.class,
            target = io.github.loadup.payment.client.dto.NoActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.WaitActionDTO.class,
            target = io.github.loadup.payment.client.dto.WaitActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.RedirectActionDTO.class,
            target = io.github.loadup.payment.client.dto.RedirectActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.WechatJsapiActionDTO.class,
            target = io.github.loadup.payment.client.dto.WechatJsapiActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.AlipayJsapiActionDTO.class,
            target = io.github.loadup.payment.client.dto.AlipayJsapiActionDTO.class)
    io.github.loadup.payment.client.dto.PaymentActionDataDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.PaymentActionDataDTO value);

    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.NoActionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.NoActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.WaitActionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.WaitActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.RedirectActionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.RedirectActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.WechatJsapiActionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.WechatJsapiActionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.AlipayJsapiActionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.AlipayJsapiActionDTO.class)
    io.github.loadup.acquire.client.dto.payment.PaymentActionDataDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentActionDataDTO value);

    io.github.loadup.payment.client.dto.PaymentChoiceDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.PaymentChoiceDTO value);

    io.github.loadup.acquire.client.dto.payment.PaymentChoiceDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentChoiceDTO value);

    io.github.loadup.payment.client.dto.PaymentCloseDTO toRemote(
            io.github.loadup.acquire.app.model.payment.dto.PaymentCloseDTO value);

    io.github.loadup.acquire.app.model.payment.dto.PaymentCloseDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentCloseDTO value);

    io.github.loadup.payment.client.dto.PaymentCodeInteractionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.PaymentCodeInteractionDTO value);

    io.github.loadup.acquire.client.dto.payment.PaymentCodeInteractionDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentCodeInteractionDTO value);

    io.github.loadup.payment.client.dto.PaymentExecutionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.PaymentExecutionDTO value);

    io.github.loadup.acquire.client.dto.payment.PaymentExecutionDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentExecutionDTO value);

    io.github.loadup.payment.client.dto.PaymentInputDTO toRemote(
            io.github.loadup.acquire.app.model.payment.dto.PaymentInputDTO value);

    io.github.loadup.acquire.app.model.payment.dto.PaymentInputDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentInputDTO value);

    io.github.loadup.payment.client.dto.PaymentInputExpiryDTO toRemote(
            io.github.loadup.acquire.app.model.payment.dto.PaymentInputExpiryDTO value);

    io.github.loadup.acquire.app.model.payment.dto.PaymentInputExpiryDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentInputExpiryDTO value);

    io.github.loadup.payment.client.dto.PaymentInputLookupDTO toRemote(
            io.github.loadup.acquire.app.model.payment.dto.PaymentInputLookupDTO value);

    io.github.loadup.acquire.app.model.payment.dto.PaymentInputLookupDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentInputLookupDTO value);

    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.NoInteractionDTO.class,
            target = io.github.loadup.payment.client.dto.NoInteractionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.PaymentCodeInteractionDTO.class,
            target = io.github.loadup.payment.client.dto.PaymentCodeInteractionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.ChannelIdentityInteractionDTO.class,
            target = io.github.loadup.payment.client.dto.ChannelIdentityInteractionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.acquire.client.dto.payment.H5SceneInteractionDTO.class,
            target = io.github.loadup.payment.client.dto.H5SceneInteractionDTO.class)
    io.github.loadup.payment.client.dto.PaymentInteractionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.PaymentInteractionDTO value);

    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.NoInteractionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.NoInteractionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.PaymentCodeInteractionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.PaymentCodeInteractionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.ChannelIdentityInteractionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.ChannelIdentityInteractionDTO.class)
    @SubclassMapping(
            source = io.github.loadup.payment.client.dto.H5SceneInteractionDTO.class,
            target = io.github.loadup.acquire.client.dto.payment.H5SceneInteractionDTO.class)
    io.github.loadup.acquire.client.dto.payment.PaymentInteractionDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentInteractionDTO value);

    io.github.loadup.payment.client.dto.PaymentOutcomeDTO toRemote(
            io.github.loadup.acquire.app.model.payment.dto.PaymentOutcomeDTO value);

    io.github.loadup.acquire.app.model.payment.dto.PaymentOutcomeDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentOutcomeDTO value);

    io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO toRemote(
            io.github.loadup.acquire.app.model.payment.dto.PaymentSettlementFactsDTO value);

    io.github.loadup.acquire.app.model.payment.dto.PaymentSettlementFactsDTO toLocal(
            io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO value);

    io.github.loadup.payment.client.dto.RedirectActionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.RedirectActionDTO value);

    io.github.loadup.acquire.client.dto.payment.RedirectActionDTO toLocal(
            io.github.loadup.payment.client.dto.RedirectActionDTO value);

    io.github.loadup.payment.client.dto.WaitActionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.WaitActionDTO value);

    io.github.loadup.acquire.client.dto.payment.WaitActionDTO toLocal(
            io.github.loadup.payment.client.dto.WaitActionDTO value);

    io.github.loadup.payment.client.dto.WechatJsapiActionDTO toRemote(
            io.github.loadup.acquire.client.dto.payment.WechatJsapiActionDTO value);

    io.github.loadup.acquire.client.dto.payment.WechatJsapiActionDTO toLocal(
            io.github.loadup.payment.client.dto.WechatJsapiActionDTO value);

    io.github.loadup.payment.client.dto.terms.AcquiringTermsDTO toPaymentAcquiringTermsDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.AcquiringTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.AcquiringTermsDTO toLocal(
            io.github.loadup.payment.client.dto.terms.AcquiringTermsDTO value);

    io.github.loadup.payment.client.dto.terms.AdmittedOrderTermsDTO toPaymentAdmittedOrderTermsDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.AdmittedOrderTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.AdmittedOrderTermsDTO toLocal(
            io.github.loadup.payment.client.dto.terms.AdmittedOrderTermsDTO value);

    io.github.loadup.payment.client.dto.terms.ChannelCapabilityDTO toPaymentChannelCapabilityDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.ChannelCapabilityDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.ChannelCapabilityDTO toLocal(
            io.github.loadup.payment.client.dto.terms.ChannelCapabilityDTO value);

    io.github.loadup.payment.client.dto.terms.FeeTermsDTO toPaymentFeeTermsDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.FeeTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.FeeTermsDTO toLocal(
            io.github.loadup.payment.client.dto.terms.FeeTermsDTO value);

    io.github.loadup.payment.client.dto.terms.LimitPolicyRefDTO toPaymentLimitPolicyRefDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.LimitPolicyRefDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.LimitPolicyRefDTO toLocal(
            io.github.loadup.payment.client.dto.terms.LimitPolicyRefDTO value);

    io.github.loadup.payment.client.dto.terms.NotificationTermsDTO toPaymentNotificationTermsDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.NotificationTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.NotificationTermsDTO toLocal(
            io.github.loadup.payment.client.dto.terms.NotificationTermsDTO value);

    io.github.loadup.payment.client.dto.terms.OrderTermsDTO toPaymentOrderTermsDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.OrderTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.OrderTermsDTO toLocal(
            io.github.loadup.payment.client.dto.terms.OrderTermsDTO value);

    io.github.loadup.payment.client.dto.terms.PaymentTermsDTO toPaymentPaymentTermsDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.PaymentTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.PaymentTermsDTO toLocal(
            io.github.loadup.payment.client.dto.terms.PaymentTermsDTO value);

    io.github.loadup.payment.client.dto.terms.RefundTermsDTO toPaymentRefundTermsDTO(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.RefundTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.RefundTermsDTO toLocal(
            io.github.loadup.payment.client.dto.terms.RefundTermsDTO value);

    io.github.loadup.payment.client.enums.FeeRefundPolicy toPaymentFeeRefundPolicy(
            io.github.loadup.acquire.client.enums.FeeRefundPolicy value);

    io.github.loadup.acquire.client.enums.FeeRefundPolicy toLocal(
            io.github.loadup.payment.client.enums.FeeRefundPolicy value);

    io.github.loadup.payment.client.enums.PaymentActionType toRemote(
            io.github.loadup.acquire.client.enums.PaymentActionType value);

    io.github.loadup.acquire.client.enums.PaymentActionType toLocal(
            io.github.loadup.payment.client.enums.PaymentActionType value);

    io.github.loadup.payment.client.enums.PaymentInputExpiryDisposition toRemote(
            io.github.loadup.acquire.app.model.payment.enums.PaymentInputExpiryDisposition value);

    io.github.loadup.acquire.app.model.payment.enums.PaymentInputExpiryDisposition toLocal(
            io.github.loadup.payment.client.enums.PaymentInputExpiryDisposition value);

    io.github.loadup.payment.client.enums.RefundAllocationPolicy toPaymentRefundAllocationPolicy(
            io.github.loadup.acquire.app.model.productcenter.enums.RefundAllocationPolicy value);

    io.github.loadup.acquire.app.model.productcenter.enums.RefundAllocationPolicy toLocal(
            io.github.loadup.payment.client.enums.RefundAllocationPolicy value);

    io.github.loadup.payment.client.enums.RefundAmountRestorePolicy toPaymentRefundAmountRestorePolicy(
            io.github.loadup.acquire.app.model.productcenter.enums.RefundAmountRestorePolicy value);

    io.github.loadup.acquire.app.model.productcenter.enums.RefundAmountRestorePolicy toLocal(
            io.github.loadup.payment.client.enums.RefundAmountRestorePolicy value);

    io.github.loadup.payment.client.enums.RefundCountRestorePolicy toPaymentRefundCountRestorePolicy(
            io.github.loadup.acquire.app.model.productcenter.enums.RefundCountRestorePolicy value);

    io.github.loadup.acquire.app.model.productcenter.enums.RefundCountRestorePolicy toLocal(
            io.github.loadup.payment.client.enums.RefundCountRestorePolicy value);

    io.github.loadup.payment.client.enums.UnpaidOrderAction toPaymentUnpaidOrderAction(
            io.github.loadup.acquire.client.enums.UnpaidOrderAction value);

    io.github.loadup.acquire.client.enums.UnpaidOrderAction toLocal(
            io.github.loadup.payment.client.enums.UnpaidOrderAction value);

    io.github.loadup.payment.client.query.ParentFenceStatusQuery toRemote(
            io.github.loadup.acquire.app.model.payment.query.ParentFenceStatusQuery value);

    io.github.loadup.acquire.app.model.payment.query.ParentFenceStatusQuery toLocal(
            io.github.loadup.payment.client.query.ParentFenceStatusQuery value);

    io.github.loadup.payment.client.query.PaymentInputLookupQuery toRemote(
            io.github.loadup.acquire.app.model.payment.query.PaymentInputLookupQuery value);

    io.github.loadup.acquire.app.model.payment.query.PaymentInputLookupQuery toLocal(
            io.github.loadup.payment.client.query.PaymentInputLookupQuery value);

    io.github.loadup.productcenter.client.command.OrderAdmissionAuthorizeCommand toRemote(
            io.github.loadup.acquire.app.model.productcenter.command.OrderAdmissionAuthorizeCommand value);

    io.github.loadup.acquire.app.model.productcenter.command.OrderAdmissionAuthorizeCommand toLocal(
            io.github.loadup.productcenter.client.command.OrderAdmissionAuthorizeCommand value);

    io.github.loadup.productcenter.client.dto.OrderAdmissionDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.OrderAdmissionDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.OrderAdmissionDTO toLocal(
            io.github.loadup.productcenter.client.dto.OrderAdmissionDTO value);

    io.github.loadup.productcenter.client.dto.terms.AcquiringTermsDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.AcquiringTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.AcquiringTermsDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.AcquiringTermsDTO value);

    io.github.loadup.productcenter.client.dto.terms.AdmittedOrderTermsDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.AdmittedOrderTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.AdmittedOrderTermsDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.AdmittedOrderTermsDTO value);

    io.github.loadup.productcenter.client.dto.terms.ChannelCapabilityDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.ChannelCapabilityDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.ChannelCapabilityDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.ChannelCapabilityDTO value);

    io.github.loadup.productcenter.client.dto.terms.FeeTermsDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.FeeTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.FeeTermsDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.FeeTermsDTO value);

    io.github.loadup.productcenter.client.dto.terms.LimitPolicyRefDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.LimitPolicyRefDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.LimitPolicyRefDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.LimitPolicyRefDTO value);

    io.github.loadup.productcenter.client.dto.terms.NotificationTermsDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.NotificationTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.NotificationTermsDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.NotificationTermsDTO value);

    io.github.loadup.productcenter.client.dto.terms.OrderTermsDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.OrderTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.OrderTermsDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.OrderTermsDTO value);

    io.github.loadup.productcenter.client.dto.terms.PaymentTermsDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.PaymentTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.PaymentTermsDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.PaymentTermsDTO value);

    io.github.loadup.productcenter.client.dto.terms.RefundTermsDTO toRemote(
            io.github.loadup.acquire.app.model.productcenter.dto.terms.RefundTermsDTO value);

    io.github.loadup.acquire.app.model.productcenter.dto.terms.RefundTermsDTO toLocal(
            io.github.loadup.productcenter.client.dto.terms.RefundTermsDTO value);

    io.github.loadup.productcenter.client.enums.AdmissionDecision toRemote(
            io.github.loadup.acquire.app.model.productcenter.enums.AdmissionDecision value);

    io.github.loadup.acquire.app.model.productcenter.enums.AdmissionDecision toLocal(
            io.github.loadup.productcenter.client.enums.AdmissionDecision value);

    io.github.loadup.productcenter.client.enums.FeeRefundPolicy toRemote(
            io.github.loadup.acquire.client.enums.FeeRefundPolicy value);

    io.github.loadup.acquire.client.enums.FeeRefundPolicy toLocal(
            io.github.loadup.productcenter.client.enums.FeeRefundPolicy value);

    io.github.loadup.productcenter.client.enums.RefundAllocationPolicy toRemote(
            io.github.loadup.acquire.app.model.productcenter.enums.RefundAllocationPolicy value);

    io.github.loadup.acquire.app.model.productcenter.enums.RefundAllocationPolicy toLocal(
            io.github.loadup.productcenter.client.enums.RefundAllocationPolicy value);

    io.github.loadup.productcenter.client.enums.RefundAmountRestorePolicy toRemote(
            io.github.loadup.acquire.app.model.productcenter.enums.RefundAmountRestorePolicy value);

    io.github.loadup.acquire.app.model.productcenter.enums.RefundAmountRestorePolicy toLocal(
            io.github.loadup.productcenter.client.enums.RefundAmountRestorePolicy value);

    io.github.loadup.productcenter.client.enums.RefundCountRestorePolicy toRemote(
            io.github.loadup.acquire.app.model.productcenter.enums.RefundCountRestorePolicy value);

    io.github.loadup.acquire.app.model.productcenter.enums.RefundCountRestorePolicy toLocal(
            io.github.loadup.productcenter.client.enums.RefundCountRestorePolicy value);

    io.github.loadup.productcenter.client.enums.UnpaidOrderAction toRemote(
            io.github.loadup.acquire.client.enums.UnpaidOrderAction value);

    io.github.loadup.acquire.client.enums.UnpaidOrderAction toLocal(
            io.github.loadup.productcenter.client.enums.UnpaidOrderAction value);

    io.github.loadup.realtime.client.dto.ProjectionAckDTO toRemote(
            io.github.loadup.acquire.app.model.realtime.dto.ProjectionAckDTO value);

    io.github.loadup.acquire.app.model.realtime.dto.ProjectionAckDTO toLocal(
            io.github.loadup.realtime.client.dto.ProjectionAckDTO value);

    default SuccessResponse<io.github.loadup.acquire.app.model.payment.dto.ParentFenceDTO>
            fromPaymentParentFenceDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.ParentFenceDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.app.model.payment.dto.PaymentCloseDTO>
            fromPaymentPaymentCloseDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.PaymentCloseDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.client.dto.payment.PaymentExecutionDTO>
            fromPaymentPaymentExecutionDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.PaymentExecutionDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.app.model.payment.dto.PaymentInputDTO>
            fromPaymentPaymentInputDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.PaymentInputDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.app.model.payment.dto.PaymentInputExpiryDTO>
            fromPaymentPaymentInputExpiryDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.PaymentInputExpiryDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.app.model.payment.dto.PaymentInputLookupDTO>
            fromPaymentPaymentInputLookupDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.PaymentInputLookupDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.app.model.payment.dto.PaymentOutcomeDTO>
            fromPaymentPaymentOutcomeDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.PaymentOutcomeDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.app.model.payment.dto.PaymentSettlementFactsDTO>
            fromPaymentPaymentSettlementFactsDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.app.model.productcenter.dto.OrderAdmissionDTO>
            fromProductcenterOrderAdmissionDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.productcenter.client.dto.OrderAdmissionDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }

    default SuccessResponse<io.github.loadup.acquire.app.model.realtime.dto.ProjectionAckDTO>
            fromRealtimeProjectionAckDTOSuccessResponse(
                    SuccessResponse<io.github.loadup.realtime.client.dto.ProjectionAckDTO> response) {
        return response == null ? null : new SuccessResponse<>(response.result(), toLocal(response.data()));
    }
}
