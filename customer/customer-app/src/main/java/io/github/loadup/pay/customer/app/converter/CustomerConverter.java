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
package io.github.loadup.pay.customer.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.customer.client.dto.MerchantActionGrantDTO;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import io.github.loadup.pay.customer.client.dto.MerchantRelationshipDTO;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantFacts;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface CustomerConverter {
    MerchantRelationshipDTO toDTO(MerchantRelationship model);

    MerchantActionGrantDTO toDTO(MerchantActionGrant model);

    @Mapping(target = "merchantFactVersion", source = "facts.version")
    @Mapping(target = "grantId", source = "grant.id")
    @Mapping(target = "grantVersion", expression = "java(grant == null ? null : grant.version())")
    MerchantEligibilityDTO toEligibility(
            boolean allowed,
            String reasonCode,
            MerchantProfileDTO merchant,
            MerchantFacts facts,
            MerchantActionGrant grant);

    default OffsetDateTime toExternal(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
