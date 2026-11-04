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
package io.github.loadup.merchantcenter.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.merchantcenter.client.command.MerchantCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantUpdateCommand;
import io.github.loadup.merchantcenter.client.dto.MerchantDTO;
import io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO;
import io.github.loadup.merchantcenter.domain.model.Merchant;
import io.github.loadup.merchantcenter.domain.model.MerchantBasicInfo;
import io.github.loadup.merchantcenter.domain.model.MerchantStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class, imports = MerchantStatus.class)
public interface MerchantConverter {
    MerchantBasicInfo toInfo(MerchantCreateCommand command);

    MerchantBasicInfo toInfo(MerchantUpdateCommand command);

    @Mapping(target = "merchantCode", source = "update.merchantCode")
    @Mapping(target = "name", source = "update.name")
    @Mapping(target = "shortName", source = "update.shortName")
    @Mapping(target = "type", source = "update.type")
    @Mapping(target = "industry", source = "update.industry")
    @Mapping(target = "country", source = "update.country")
    @Mapping(target = "province", source = "update.province")
    @Mapping(target = "city", source = "update.city")
    @Mapping(target = "address", expression = "java(update.address() == null ? previous.address() : update.address())")
    @Mapping(
            target = "registrationNo",
            expression = "java(update.registrationNo() == null ? previous.registrationNo() : update.registrationNo())")
    @Mapping(
            target = "contactName",
            expression = "java(update.contactName() == null ? previous.contactName() : update.contactName())")
    @Mapping(
            target = "contactPhone",
            expression = "java(update.contactPhone() == null ? previous.contactPhone() : update.contactPhone())")
    @Mapping(
            target = "contactEmail",
            expression = "java(update.contactEmail() == null ? previous.contactEmail() : update.contactEmail())")
    MerchantBasicInfo merge(MerchantBasicInfo update, MerchantBasicInfo previous);

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
    MerchantDTO toDTO(Merchant source);

    @Mapping(target = "active", expression = "java(source.status() == MerchantStatus.ACTIVE)")
    @Mapping(target = "merchantCode", source = "info.merchantCode")
    @Mapping(target = "type", source = "info.type")
    @Mapping(target = "industry", source = "info.industry")
    @Mapping(target = "country", source = "info.country")
    @Mapping(target = "province", source = "info.province")
    @Mapping(target = "city", source = "info.city")
    MerchantProfileDTO toProfile(Merchant source);
}
