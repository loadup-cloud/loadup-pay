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
package io.github.loadup.pay.customer.rpc;

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
public interface CustomerRpcMapper {
    CustomerRpcMapper INSTANCE = Mappers.getMapper(CustomerRpcMapper.class);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "payeeMerchantId", source = "payeeMerchantId")
    @Mapping(target = "action", source = "action")
    io.github.loadup.pay.customer.rpc.proto.WireMerchantActionAuthorizeCommand toWireMerchantActionAuthorizeCommand(io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand value);

    io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand fromWireMerchantActionAuthorizeCommand(io.github.loadup.pay.customer.rpc.proto.WireMerchantActionAuthorizeCommand value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "allowed", source = "allowed")
    @Mapping(target = "reasonCode", source = "reasonCode")
    @Mapping(target = "merchant", source = "merchant")
    @Mapping(target = "merchantFactVersion", source = "merchantFactVersion")
    @Mapping(target = "grantId", source = "grantId")
    @Mapping(target = "grantVersion", source = "grantVersion")
    io.github.loadup.pay.customer.rpc.proto.WireMerchantEligibilityDTO toWireMerchantEligibilityDTO(io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO value);

    io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO fromWireMerchantEligibilityDTO(io.github.loadup.pay.customer.rpc.proto.WireMerchantEligibilityDTO value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "merchantCode", source = "merchantCode")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "industry", source = "industry")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "province", source = "province")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "active", source = "active")
    io.github.loadup.pay.customer.rpc.proto.WireMerchantProfileDTO toWireMerchantProfileDTO(io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO value);

    io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO fromWireMerchantProfileDTO(io.github.loadup.pay.customer.rpc.proto.WireMerchantProfileDTO value);

}
