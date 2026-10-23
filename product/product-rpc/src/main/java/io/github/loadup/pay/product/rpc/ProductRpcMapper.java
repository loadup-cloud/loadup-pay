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
package io.github.loadup.pay.product.rpc;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.rpc.grpc.GrpcScalarMapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
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
        uses = {GrpcScalarMapper.class})
public interface ProductRpcMapper {
    ProductRpcMapper INSTANCE = Mappers.getMapper(ProductRpcMapper.class);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "productScopeKey", source = "productScopeKey")
    @Mapping(target = "requestDigest", source = "requestDigest")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "requestedDueAt", source = "requestedDueAt")
    io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionAuthorizeCommand toWireOrderAdmissionAuthorizeCommand(io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand value);

    io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand fromWireOrderAdmissionAuthorizeCommand(io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionAuthorizeCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "admissionId", source = "admissionId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "decision", source = "decision")
    @Mapping(target = "reasonCode", source = "reasonCode")
    @Mapping(target = "requestDigest", source = "requestDigest")
    @Mapping(target = "contractId", source = "contractId")
    @Mapping(target = "revision", source = "revision")
    @Mapping(target = "generation", source = "generation")
    @Mapping(target = "snapshotHash", source = "snapshotHash")
    @Mapping(target = "compiledTermsHash", source = "compiledTermsHash")
    @Mapping(target = "admittedAt", source = "admittedAt")
    @Mapping(target = "materializeBefore", source = "materializeBefore")
    @Mapping(target = "terms", source = "terms")
    io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO toWireOrderAdmissionDTO(io.github.loadup.pay.product.client.dto.OrderAdmissionDTO value);

    io.github.loadup.pay.product.client.dto.OrderAdmissionDTO fromWireOrderAdmissionDTO(io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "schemaVersion", source = "schemaVersion")
    @Mapping(target = "order", source = "order")
    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "fee", source = "fee")
    @Mapping(target = "refund", source = "refund")
    @Mapping(target = "notification", source = "notification")
    @Mapping(target = "limits", source = "limits")
    @Mapping(target = "routingProfileRef", source = "routingProfileRef")
    @Mapping(target = "returnTargetCode", source = "returnTargetCode")
    io.github.loadup.pay.product.rpc.proto.WireAcquiringTermsDTO toWireAcquiringTermsDTO(io.github.loadup.pay.product.client.dto.AcquiringTermsDTO value);

    @Mapping(target = "capabilities", source = "capabilitiesList")
    io.github.loadup.pay.product.client.dto.AcquiringTermsDTO fromWireAcquiringTermsDTO(io.github.loadup.pay.product.rpc.proto.WireAcquiringTermsDTO value);

    @AfterMapping
    default void appendAcquiringTermsDTO(io.github.loadup.pay.product.client.dto.AcquiringTermsDTO value, @MappingTarget io.github.loadup.pay.product.rpc.proto.WireAcquiringTermsDTO.Builder builder) {
        if (value.capabilities() != null) for (var item : value.capabilities()) builder.addCapabilities(toWireChannelCapabilityDTO(item));
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "waitSeconds", source = "waitSeconds")
    @Mapping(target = "maxWaitSeconds", source = "maxWaitSeconds")
    @Mapping(target = "unpaidAction", source = "unpaidAction")
    @Mapping(target = "extensionAllowed", source = "extensionAllowed")
    @Mapping(target = "extensionMaxSeconds", source = "extensionMaxSeconds")
    @Mapping(target = "sessionSeconds", source = "sessionSeconds")
    io.github.loadup.pay.product.rpc.proto.WireOrderTermsDTO toWireOrderTermsDTO(io.github.loadup.pay.product.client.dto.OrderTermsDTO value);

    io.github.loadup.pay.product.client.dto.OrderTermsDTO fromWireOrderTermsDTO(io.github.loadup.pay.product.rpc.proto.WireOrderTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "minAmountMinor", source = "minAmountMinor")
    @Mapping(target = "maxAmountMinor", source = "maxAmountMinor")
    @Mapping(target = "maxSuccessfulPayments", source = "maxSuccessfulPayments")
    @Mapping(target = "allowExactRemainderBelowMin", source = "allowExactRemainderBelowMin")
    io.github.loadup.pay.product.rpc.proto.WirePaymentTermsDTO toWirePaymentTermsDTO(io.github.loadup.pay.product.client.dto.PaymentTermsDTO value);

    io.github.loadup.pay.product.client.dto.PaymentTermsDTO fromWirePaymentTermsDTO(io.github.loadup.pay.product.rpc.proto.WirePaymentTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "rate", source = "rate")
    @Mapping(target = "fixedMinor", source = "fixedMinor")
    @Mapping(target = "rounding", source = "rounding")
    @Mapping(target = "minimumMinor", source = "minimumMinor")
    @Mapping(target = "maximumMinor", source = "maximumMinor")
    @Mapping(target = "refundPolicy", source = "refundPolicy")
    io.github.loadup.pay.product.rpc.proto.WireFeeTermsDTO toWireFeeTermsDTO(io.github.loadup.pay.product.client.dto.FeeTermsDTO value);

    io.github.loadup.pay.product.client.dto.FeeTermsDTO fromWireFeeTermsDTO(io.github.loadup.pay.product.rpc.proto.WireFeeTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "partialAllowed", source = "partialAllowed")
    @Mapping(target = "windowSeconds", source = "windowSeconds")
    @Mapping(target = "allocationPolicy", source = "allocationPolicy")
    io.github.loadup.pay.product.rpc.proto.WireRefundTermsDTO toWireRefundTermsDTO(io.github.loadup.pay.product.client.dto.RefundTermsDTO value);

    io.github.loadup.pay.product.client.dto.RefundTermsDTO fromWireRefundTermsDTO(io.github.loadup.pay.product.rpc.proto.WireRefundTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "targetRef", source = "targetRef")
    @Mapping(target = "signingIdentityRef", source = "signingIdentityRef")
    @Mapping(target = "timeoutMillis", source = "timeoutMillis")
    @Mapping(target = "retryInitialSeconds", source = "retryInitialSeconds")
    @Mapping(target = "retryMultiplier", source = "retryMultiplier")
    @Mapping(target = "retryMaximumSeconds", source = "retryMaximumSeconds")
    @Mapping(target = "retryMaxAttempts", source = "retryMaxAttempts")
    @Mapping(target = "retryWindowSeconds", source = "retryWindowSeconds")
    io.github.loadup.pay.product.rpc.proto.WireNotificationTermsDTO toWireNotificationTermsDTO(io.github.loadup.pay.product.client.dto.NotificationTermsDTO value);

    io.github.loadup.pay.product.client.dto.NotificationTermsDTO fromWireNotificationTermsDTO(io.github.loadup.pay.product.rpc.proto.WireNotificationTermsDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "policyVersion", source = "policyVersion")
    @Mapping(target = "policyHash", source = "policyHash")
    @Mapping(target = "parentAggregationEnabled", source = "parentAggregationEnabled")
    @Mapping(target = "parentPolicyRef", source = "parentPolicyRef")
    @Mapping(target = "amountRestore", source = "amountRestore")
    @Mapping(target = "countRestore", source = "countRestore")
    io.github.loadup.pay.product.rpc.proto.WireLimitPolicyRefDTO toWireLimitPolicyRefDTO(io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO value);

    io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO fromWireLimitPolicyRefDTO(io.github.loadup.pay.product.rpc.proto.WireLimitPolicyRefDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "accessMode", source = "accessMode")
    io.github.loadup.pay.product.rpc.proto.WireChannelCapabilityDTO toWireChannelCapabilityDTO(io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO value);

    @Mapping(target = "methods", source = "methodsList")
    io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO fromWireChannelCapabilityDTO(io.github.loadup.pay.product.rpc.proto.WireChannelCapabilityDTO value);

    @AfterMapping
    default void appendChannelCapabilityDTO(io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO value, @MappingTarget io.github.loadup.pay.product.rpc.proto.WireChannelCapabilityDTO.Builder builder) {
        if (value.methods() != null) for (var item : value.methods()) builder.addMethods(item.name());
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    io.github.loadup.pay.product.rpc.proto.WireIdQuery toWireIdQuery(io.github.loadup.commons.request.query.IdQuery value);

    io.github.loadup.commons.request.query.IdQuery fromWireIdQuery(io.github.loadup.pay.product.rpc.proto.WireIdQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "admissionId", source = "admissionId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "orderAmountMinor", source = "orderAmountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "contractId", source = "contractId")
    @Mapping(target = "revision", source = "revision")
    @Mapping(target = "generation", source = "generation")
    @Mapping(target = "snapshotHash", source = "snapshotHash")
    @Mapping(target = "compiledTermsHash", source = "compiledTermsHash")
    @Mapping(target = "terms", source = "terms")
    io.github.loadup.pay.product.rpc.proto.WireAdmittedOrderTermsDTO toWireAdmittedOrderTermsDTO(io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO value);

    io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO fromWireAdmittedOrderTermsDTO(io.github.loadup.pay.product.rpc.proto.WireAdmittedOrderTermsDTO value);

}
