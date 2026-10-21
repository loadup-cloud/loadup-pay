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
import io.github.loadup.modules.merchant.infrastructure.dataobject.MerchantDO;
import io.github.loadup.pay.customer.domain.model.MerchantFacts;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface MerchantFactsConverter {
    @Mapping(target = "merchantId", source = "id")
    @Mapping(target = "active", expression = "java(\"ACTIVE\".equals(row.getStatus()))")
    @Mapping(target = "version", source = "rowVersion")
    MerchantFacts toFacts(MerchantDO row);
}
