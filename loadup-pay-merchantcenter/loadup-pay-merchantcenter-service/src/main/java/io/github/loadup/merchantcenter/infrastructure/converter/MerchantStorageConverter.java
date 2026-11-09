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
package io.github.loadup.merchantcenter.infrastructure.converter;

import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.merchantcenter.domain.model.Merchant;
import io.github.loadup.merchantcenter.domain.model.MerchantBasicInfo;
import io.github.loadup.merchantcenter.infrastructure.dataobject.MerchantDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface MerchantStorageConverter {

    MerchantBasicInfo toInfo(MerchantDO source);

    @Mapping(target = "info", source = "source")
    Merchant toMerchant(MerchantDO source);

    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "merchantCode", source = "info.merchantCode")
    @Mapping(target = "name", source = "info.name")
    @Mapping(target = "shortName", source = "info.shortName")
    @Mapping(target = "type", source = "info.type")
    @Mapping(target = "industry", source = "info.industry")
    @Mapping(target = "country", source = "info.country")
    @Mapping(target = "province", source = "info.province")
    @Mapping(target = "city", source = "info.city")
    @Mapping(target = "address", source = "info.address")
    @Mapping(target = "registrationNo", source = "info.registrationNo")
    @Mapping(target = "contactName", source = "info.contactName")
    @Mapping(target = "contactPhone", source = "info.contactPhone")
    @Mapping(target = "contactEmail", source = "info.contactEmail")
    MerchantDO toDO(Merchant source);
}
