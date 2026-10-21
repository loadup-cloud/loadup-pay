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
package io.github.loadup.pay.customer.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantActionGrantDO;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantRelationshipDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface AuthorizationStorageConverter {
    @Mapping(target = "version", source = "rowVersion")
    MerchantRelationship toRelationship(MerchantRelationshipDO row);

    @Mapping(target = "version", source = "rowVersion")
    MerchantActionGrant toGrant(MerchantActionGrantDO row);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "rowVersion", source = "model.version")
    @Mapping(target = "relationType", constant = "MANAGEMENT")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    MerchantRelationshipDO toDO(String tenant, MerchantRelationship model);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "rowVersion", source = "model.version")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    MerchantActionGrantDO toDO(String tenant, MerchantActionGrant model);
}
