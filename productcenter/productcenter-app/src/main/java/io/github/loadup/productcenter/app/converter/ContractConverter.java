/*
 * Copyright (C) 2025-2026 LoadUp Cloud
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
package io.github.loadup.productcenter.app.converter;

import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.productcenter.app.support.ContractMappingSupport;
import io.github.loadup.productcenter.client.dto.CatalogDefinitions;
import io.github.loadup.productcenter.client.dto.CatalogVersionDTO;
import io.github.loadup.productcenter.client.dto.ConfigurationDTO;
import io.github.loadup.productcenter.client.dto.ContractDecisionDTO;
import io.github.loadup.productcenter.client.dto.ContractItemDTO;
import io.github.loadup.productcenter.client.dto.MerchantContractDTO;
import io.github.loadup.productcenter.client.dto.ValueDTO;
import io.github.loadup.productcenter.domain.model.CatalogEntry;
import io.github.loadup.productcenter.domain.model.ContractDecision;
import io.github.loadup.productcenter.domain.model.ContractRecord;
import io.github.loadup.productcenter.domain.model.MerchantContractRevision;
import io.github.loadup.productcenter.domain.model.ParameterDefinition;
import io.github.loadup.productcenter.domain.model.ResolvedConfiguration;
import io.github.loadup.productcenter.domain.model.ResolvedContractItem;
import io.github.loadup.productcenter.domain.model.TypedValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class, uses = ContractMappingSupport.class)
public interface ContractConverter {
    @Mapping(target = "definition", source = "content")
    CatalogVersionDTO toCatalog(CatalogEntry source);

    @Mapping(
            target = "defaultValue",
            expression = "java(ContractMappingSupport.value(source.type(),source.defaultValue()))")
    @Mapping(
            target = "allowedValues",
            expression = "java(ContractMappingSupport.values(source.type(),source.allowedValues()))")
    @Mapping(target = "editableLayers", expression = "java(ContractMappingSupport.layers(source.editableLayers()))")
    ParameterDefinition toParameter(CatalogDefinitions.Parameter source);

    TypedValue toValue(ValueDTO source);

    ValueDTO toValueDTO(TypedValue source);

    ConfigurationDTO toConfiguration(ResolvedConfiguration source);

    ContractDecisionDTO toDecision(ContractDecision source);

    ContractItemDTO toItem(ResolvedContractItem source);

    @Mapping(target = "id", source = "header.id")
    @Mapping(target = "merchantId", source = "header.merchantId")
    @Mapping(target = "scopeKey", source = "header.scopeKey")
    @Mapping(target = "planVersionId", source = "header.planVersionId")
    @Mapping(target = "status", source = "header.status")
    @Mapping(target = "generation", source = "header.generation")
    @Mapping(target = "createdAt", source = "header.createdAt")
    @Mapping(target = "updatedAt", source = "header.updatedAt")
    @Mapping(target = "revision", source = "snapshot.revision")
    @Mapping(target = "effectiveFrom", source = "snapshot.effectiveFrom")
    @Mapping(target = "effectiveTo", source = "snapshot.effectiveTo")
    @Mapping(target = "snapshotHash", expression = "java(snapshot.snapshotHash())")
    @Mapping(target = "items", source = "snapshot.items")
    MerchantContractDTO toContract(ContractRecord header, MerchantContractRevision snapshot);
}
