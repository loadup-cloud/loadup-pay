package io.github.loadup.pay.customer.rpc;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.rpc.proto.RpcScalarMapper;
import io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO;
import io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.rpc.proto.WireMerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.rpc.proto.WireMerchantEligibilityDTO;
import io.github.loadup.pay.customer.rpc.proto.WireMerchantProfileDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(config=LoadUpMapStructConfig.class, componentModel="default", unmappedSourcePolicy=ReportingPolicy.IGNORE, collectionMappingStrategy=CollectionMappingStrategy.ADDER_PREFERRED, nullValueCheckStrategy=NullValueCheckStrategy.ALWAYS, uses={RpcScalarMapper.class})
public interface CustomerRpcMapper {
    public static final CustomerRpcMapper INSTANCE = (CustomerRpcMapper)Mappers.getMapper(CustomerRpcMapper.class);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="payeeMerchantId", source="payeeMerchantId"), @Mapping(target="action", source="action")})
    public WireMerchantActionAuthorizeCommand toWireMerchantActionAuthorizeCommand(MerchantActionAuthorizeCommand value);

    public MerchantActionAuthorizeCommand fromWireMerchantActionAuthorizeCommand(WireMerchantActionAuthorizeCommand value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="allowed", source="allowed"), @Mapping(target="reasonCode", source="reasonCode"), @Mapping(target="merchant", source="merchant"), @Mapping(target="merchantFactVersion", source="merchantFactVersion"), @Mapping(target="grantId", source="grantId"), @Mapping(target="grantVersion", source="grantVersion")})
    public WireMerchantEligibilityDTO toWireMerchantEligibilityDTO(MerchantEligibilityDTO value);

    public MerchantEligibilityDTO fromWireMerchantEligibilityDTO(WireMerchantEligibilityDTO value);

    @BeanMapping(ignoreByDefault=true)
    @Mappings(value={@Mapping(target="id", source="id"), @Mapping(target="tenantId", source="tenantId"), @Mapping(target="merchantCode", source="merchantCode"), @Mapping(target="type", source="type"), @Mapping(target="industry", source="industry"), @Mapping(target="country", source="country"), @Mapping(target="province", source="province"), @Mapping(target="city", source="city"), @Mapping(target="active", source="active")})
    public WireMerchantProfileDTO toWireMerchantProfileDTO(MerchantProfileDTO value);

    public MerchantProfileDTO fromWireMerchantProfileDTO(WireMerchantProfileDTO value);
}
