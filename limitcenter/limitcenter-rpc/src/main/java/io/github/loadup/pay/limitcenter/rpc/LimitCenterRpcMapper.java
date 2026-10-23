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
package io.github.loadup.pay.limitcenter.rpc;

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
public interface LimitCenterRpcMapper {
    LimitCenterRpcMapper INSTANCE = Mappers.getMapper(LimitCenterRpcMapper.class);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "policyVersion", source = "policyVersion")
    @Mapping(target = "policyHash", source = "policyHash")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "refundAmountRestore", source = "refundAmountRestore")
    @Mapping(target = "refundCountRestore", source = "refundCountRestore")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivateCommand toWireLimitPolicyActivateCommand(io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand value);

    @Mapping(target = "rules", source = "rulesList")
    io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand fromWireLimitPolicyActivateCommand(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivateCommand value);

    @AfterMapping
    default void appendLimitPolicyActivateCommand(io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand value, @MappingTarget io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivateCommand.Builder builder) {
        if (value.rules() != null) for (var item : value.rules()) builder.addRules(toWireLimitRuleDTO(item));
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "ruleScopeId", source = "ruleScopeId")
    @Mapping(target = "merchantId", source = "merchantId")
    @Mapping(target = "metric", source = "metric")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "window", source = "window")
    @Mapping(target = "weekStart", source = "weekStart")
    @Mapping(target = "threshold", source = "threshold")
    @Mapping(target = "enforcementEnabled", source = "enforcementEnabled")
    @Mapping(target = "meteringEnabled", source = "meteringEnabled")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRuleDTO toWireLimitRuleDTO(io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO value);

    io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO fromWireLimitRuleDTO(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRuleDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "policyVersion", source = "policyVersion")
    @Mapping(target = "policyHash", source = "policyHash")
    @Mapping(target = "activatedAt", source = "activatedAt")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivationDTO toWireLimitPolicyActivationDTO(io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO value);

    io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO fromWireLimitPolicyActivationDTO(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivationDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "initiatedAt", source = "initiatedAt")
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "policyVersion", source = "policyVersion")
    @Mapping(target = "policyHash", source = "policyHash")
    @Mapping(target = "requestDigest", source = "requestDigest")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReserveCommand toWireLimitReserveCommand(io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand value);

    io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand fromWireLimitReserveCommand(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReserveCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "reservationId", source = "reservationId")
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "policyId", source = "policyId")
    @Mapping(target = "policyVersion", source = "policyVersion")
    @Mapping(target = "initiatedAt", source = "initiatedAt")
    @Mapping(target = "version", source = "version")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO toWireLimitReservationDTO(io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO value);

    io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO fromWireLimitReservationDTO(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitOperationQuery toWireLimitOperationQuery(io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery value);

    io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery fromWireLimitOperationQuery(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitOperationQuery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "attemptId", source = "attemptId")
    @Mapping(target = "requestKey", source = "requestKey")
    @Mapping(target = "previousAttemptEvidenceId", source = "previousAttemptEvidenceId")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitBeginExecutionCommand toWireLimitBeginExecutionCommand(io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand value);

    io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand fromWireLimitBeginExecutionCommand(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitBeginExecutionCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "attemptId", source = "attemptId")
    @Mapping(target = "executionEpoch", source = "executionEpoch")
    @Mapping(target = "permit", source = "permit")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionPermitDTO toWireLimitExecutionPermitDTO(io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO value);

    io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO fromWireLimitExecutionPermitDTO(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionPermitDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "attemptId", source = "attemptId")
    @Mapping(target = "executionEpoch", source = "executionEpoch")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "permit", source = "permit")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerifyCommand toWireLimitExecutionVerifyCommand(io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand value);

    io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand fromWireLimitExecutionVerifyCommand(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerifyCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "valid", source = "valid")
    @Mapping(target = "reasonCode", source = "reasonCode")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerificationDTO toWireLimitExecutionVerificationDTO(io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO value);

    io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO fromWireLimitExecutionVerificationDTO(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerificationDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExpireCommand toWireLimitExpireCommand(io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand value);

    io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand fromWireLimitExpireCommand(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExpireCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "paymentVersion", source = "paymentVersion")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "successEvidenceId", source = "successEvidenceId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "succeededAt", source = "succeededAt")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitConfirmCommand toWireLimitConfirmCommand(io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand value);

    io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand fromWireLimitConfirmCommand(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitConfirmCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "paymentVersion", source = "paymentVersion")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "noCollectionEvidenceId", source = "noCollectionEvidenceId")
    @Mapping(target = "reasonCode", source = "reasonCode")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseCommand toWireLimitReleaseCommand(io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand value);

    io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand fromWireLimitReleaseCommand(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "operationId", source = "operationId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "fenced", source = "fenced")
    @Mapping(target = "reservation", source = "reservation")
    @Mapping(target = "paymentVersion", source = "paymentVersion")
    @Mapping(target = "noCollectionEvidenceId", source = "noCollectionEvidenceId")
    io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseDTO toWireLimitReleaseDTO(io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO value);

    io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO fromWireLimitReleaseDTO(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseDTO value);

}
